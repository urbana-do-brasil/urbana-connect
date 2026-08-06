# Data Model: Chat local para testes manuais da Urba

Este documento separa o estado visual descartável do navegador da projeção
canônica mantida pela Urbana Connect. Nenhuma nova entidade de domínio ou coleção
MongoDB é criada por esta feature.

## 1. LocalContact

Contato conhecido apenas pela interface local.

| Field | Type | Persistence | Rules |
| --- | --- | --- | --- |
| `contactAlias` | string | local | `^manual-[a-f0-9-]{36}$`, único, máximo 64 caracteres; nunca exibido como nome do cliente |
| `displayName` | string | local | trim; 1–80 caracteres; nunca enviado ao backend |
| `createdAt` | ISO-8601 string | local | definido uma vez na criação |
| `lastOpenedAt` | ISO-8601 string | local | atualizado ao selecionar o contato |
| `archived` | boolean | local | remove da lista principal sem apagar o backend |
| `lastReadMessageId` | string/null | local | cursor visual; não contém texto ou fato do cliente |

### Invariants

- Dois contatos podem ter `displayName` igual, mas nunca `contactAlias` igual.
- Alterar `displayName` não altera identidade, memória ou histórico.
- Arquivar não chama operação de exclusão remota.
- O alias é gerado com `crypto.randomUUID()` e não deriva do nome amigável.

## 2. PersistedUiState

Envelope único do armazenamento local.

| Field | Type | Rules |
| --- | --- | --- |
| `schemaVersion` | integer | valor inicial `1`; versão desconhecida resulta em estado vazio seguro |
| `contacts` | `LocalContact[]` | aliases únicos; entradas inválidas são descartadas individualmente |
| `activeContactAlias` | string/null | deve apontar para contato não arquivado ou ser redefinido |

### Explicitly forbidden fields

O estado persistido não pode conter:

- texto de mensagem;
- transcript ou resposta da Urba;
- fatos, estágio comercial ou estado de pagamento;
- `eventId` ou `correlationId` de turnos;
- token, header de autorização ou configuração secreta;
- payload de retry.

## 3. ConversationProjection

Visão de leitura retornada pela Urbana Connect. O frontend valida somente o
subconjunto abaixo e ignora os demais campos técnicos do contrato existente.

| Field | Type | Usage |
| --- | --- | --- |
| `contactId` | string | valida que a resposta pertence ao alias solicitado |
| `conversation.mode` | `AI \| HUMAN` ou ausente | encerra indicador automático em handoff; não é exibido como painel |
| `messages` | `CanonicalMessage[]` | fonte canônica do histórico mostrado no chat |

Uma conversa ainda inexistente pode retornar `conversation` vazio e `messages`
vazio.

## 4. CanonicalMessage

Mensagem persistida pelo núcleo Hermes-first.

| Field | Type | Rules |
| --- | --- | --- |
| `id` | string | chave primária visual da mensagem canônica |
| `eventId` | string | identifica entrada ou saída idempotente |
| `correlationId` | string | relaciona fragmentos e resposta de um turno |
| `contactId` | string | deve coincidir com o contato carregado |
| `direction` | `INBOUND \| OUTBOUND` | define lado do balão |
| `senderType` | `CONTACT \| URBA \| HUMAN \| SYSTEM` | valida coerência com a direção; o MVP renderiza somente os remetentes conversacionais aplicáveis |
| `type` | string | somente `TEXT` é renderizado no MVP |
| `text` | string/null | obrigatório para item textual visível |
| `createdAt` | ISO-8601 string | ordenação canônica primária |

### Mapping rules

- `INBOUND + CONTACT + TEXT` vira balão da pessoa.
- `OUTBOUND + URBA + TEXT` vira balão da Urba.
- Tipos não textuais são ignorados visualmente no MVP, sem modificar a origem.
- Conteúdo é renderizado como texto escapado; apenas URLs `http`/`https`
  reconhecidas recebem link seguro.
- IDs e correlações nunca são apresentados na interface.

## 5. PendingSend

Estado efêmero de uma mensagem iniciada pelo navegador. Existe somente em
memória durante a sessão da página.

| Field | Type | Rules |
| --- | --- | --- |
| `eventId` | string | `ui-<UUID>`; estável em todas as tentativas |
| `contactAlias` | string | contato de destino imutável |
| `text` | string | 1–8.000 caracteres após validação; não persistido localmente |
| `occurredAt` | ISO-8601 string | estável entre retries |
| `attempts` | integer | envio inicial + no máximo uma retentativa automática de transporte |
| `state` | enum | transição definida abaixo |
| `lastError` | string/null | mensagem técnica sanitizada, não atribuída à Urba |

### State transitions

```text
DRAFT
  └── send ──> ACCEPTING
                 ├── 202/409 ──> WAITING
                 ├── uncertain transport ──> RETRYING ──> WAITING | FAILED
                 └── validation/terminal error ──> FAILED

WAITING
  ├── canonical inbound observed ──> PERSISTED_WAITING
  ├── canonical outbound observed ──> COMPLETED
  ├── conversation HUMAN ──> BLOCKED_BY_HUMAN
  └── UI timeout ──> FAILED_RETRYABLE

FAILED_RETRYABLE
  └── manual retry with same eventId ──> ACCEPTING
```

`COMPLETED` e `BLOCKED_BY_HUMAN` são terminais para o envio. Uma resposta
canônica de contingência produzida pelo backend também leva a `COMPLETED`.

## 6. ConversationUiState

Estado em memória por contato.

| Field | Type | Purpose |
| --- | --- | --- |
| `messages` | `CanonicalMessage[]` | snapshot atual da fonte canônica |
| `optimisticMessages` | `PendingSend[]` | entradas ainda não reconciliadas |
| `loadState` | `IDLE \| LOADING \| READY \| ERROR` | leitura da projeção |
| `processingState` | `IDLE \| WAITING \| FAILED_RETRYABLE \| HUMAN` | indicador do composer/chat |
| `unread` | boolean | resposta chegou enquanto contato não estava ativo |
| `lastSuccessfulSyncAt` | ISO-8601/null | controle de acompanhamento e diagnóstico interno |

### Reconciliation

1. Validar `contactId` da projeção.
2. Remover duplicatas canônicas por `id`; em fallback, usar combinação de
   `eventId`, direção e `correlationId`.
3. Ordenar por `createdAt`, preservando ordem recebida em empate.
4. Substituir item otimista quando o mesmo `eventId` aparecer como `INBOUND`.
5. Considerar o turno respondido quando houver `OUTBOUND` com correlação
   correspondente ou quando a conversa estiver em modo `HUMAN`.
6. Marcar não lido apenas se uma nova saída aparecer em contato não ativo.

## 7. Relationships

```text
PersistedUiState
  └── 1..50 LocalContact
          ├── 1 ConversationUiState (memory only)
          ├── 0..n PendingSend (memory only)
          └── 0..n CanonicalMessage (fetched, never persisted by UI)

LocalContact.contactAlias
  └── maps to backend contactId "poc:<contactAlias>"
```

O prefixo `poc:` é acrescentado pela controladora sintética existente; o
frontend envia somente `contactAlias` no path.
