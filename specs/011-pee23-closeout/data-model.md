# Data model: fechamento operacional da PEE-23

## TermsConsentSession

Sessão efêmera que representa um link emitido para uma contratação específica.

| Campo | Regra |
|---|---|
| `presentationId` | ID opaco e idempotente; chave do registro |
| `conversationId` / `contactId` | vínculo à conversa e ao contato existentes |
| `destinationRef` | referência protegida que permite enviar WhatsApp depois do callback; resolve no registro interno de endereços, nunca por reversão do hash |
| `contractingUnitId` | uma unidade/checkout; mudança invalida a sessão |
| `environment` | snapshot HML/PROD |
| `serviceType` | enum canônico, incluindo `DECOR_REFORMA` |
| `termsVersion` / `termsHash` | versão e SHA-256 do artefato imutável |
| `tokenDigest` | HMAC/digest do token; nunca o token em claro |
| `issuedAt` / `expiresAt` | validade parametrizada; TTL da sessão não é retenção da auditoria |
| `status` | `ISSUED`, `PAGE_PRESENTED`, `END_REACHED`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `INVALIDATED` |
| `endReachedAt` / `decidedAt` | evidências separadas |
| `decisionEventId` | idempotência da decisão |

Índices: digest único, `presentationId` único e consulta por conversa/unidade
ativa. O TTL deve atingir apenas a sessão/token, não o registro jurídico de
auditoria.

## TermsConsentAuditEvent

Registro append-only para recuperação da evidência. É um evento imutável, não a
sessão corrente nem um documento mutável com TTL. O registro textual atual não
é retrovalidado sem evento web. Uma projeção de leitura pode resumir os eventos,
mas nunca substitui a sequência original.

- `ISSUED`, `PAGE_PRESENTED`, `END_REACHED`, `ACCEPTED`, `DECLINED`, `EXPIRED`
  são documentos de evento imutáveis; uma projeção de leitura pode ser criada
  separadamente, se necessária.
- Deve preservar serviço, ambiente, contratação, recurso/version/hash, contato,
  instantes e IDs de correlação.
- `ACCEPTED` exige evento `END_REACHED`; `DECLINED` exige ação explícita.
- Replays da mesma decisão retornam a projeção já persistida; decisão oposta
  não reescreve a primeira.

Os eventos de auditoria não recebem TTL. A sessão efêmera pode ser expirada ou
invalidada sem alterar os eventos já gravados.

## DeliveryOutbox

Outbox transacional para efeitos externos:

| Campo | Regra |
|---|---|
| `eventKey` | único por efeito (`terms:{presentationId}:payment-options`, por exemplo) |
| `kind` | `WHATSAPP_TERMS_LINK`, `WHATSAPP_PAYMENT_OPTIONS`, `WHATSAPP_MENU`, `WHATSAPP_HERMES_REPLY`, `WHATSAPP_HUMAN_HANDOFF_ACK`, `EMAIL_HUMAN_HANDOFF` |
| `destinationRef` | referência resolvível, não hash unilateral do telefone; o endereço do canal é registrado na borda do webhook e lido somente pelo worker |
| `payload` | mensagem mínima; nunca token/segredo em log |
| `status` | `PENDING`, `SENDING`, `SENT`, `RETRYABLE`, `AMBIGUOUS`, `DEAD_LETTER` |
| `attempts` / `nextAttemptAt` | retry com backoff |
| `providerMessageId` | resposta do canal quando disponível |
| `correlationId` | rastreabilidade sem conteúdo sensível |

O worker não executa Hermes dentro da transação. Falha terminal de entrega
gera/atualiza posse `HUMAN` e uma única notificação de e-mail.

O registro `reception_delivery_destinations` mantém a associação entre a
referência opaca do contato e o endereço WhatsApp normalizado. Ele é uma
projeção interna protegida, escrita de forma idempotente no recebimento do
webhook; não é exposto pela API, não entra no payload da outbox e não permite
inferir um telefone a partir do hash.

## ServiceCatalog baseline/override

O documento Mongo representa o override operacional. O baseline de cada ambiente
é carregado por propriedades tipadas. O merge permitido é apenas `missing ->
baseline`; nenhum valor existente é substituído no startup. Recurso de checkout
ausente/inválido torna o serviço indisponível para aquele ambiente.

## Retenção

`terms.session.ttl` controla links; `terms.audit.retention` controla auditoria.
O segundo valor permanece parametrizado até decisão da Urba/Jurídico.
