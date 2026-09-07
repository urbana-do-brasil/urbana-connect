# Implementation Plan: Fechamento operacional da PEE-23

**Branch**: `011-pee23-closeout`
**Date**: 2026-09-02
**Spec**: [spec.md](spec.md)

## Resumo

Implementar o contrato aprovado em camadas independentes, mantendo o Hermes
como caminho único e adiando o adapter específico de checkout até a discovery
do provedor. A página de termos será um artefato estático same-origin servido
pelo backend, com API protegida por token efêmero; o estado do consentimento,
conversa e mensagens a enviar será persistido no Mongo/outbox antes de qualquer
chamada externa.

## Contexto técnico

- **Backend**: Java 21, Spring Boot 3.4.13, Spring Web/Security, Spring Data
  MongoDB, JavaMailSender e Micrometer.
- **Hermes**: Sessions API e plugin `integrations/hermes-agent`; sem nova
  ferramenta e sem state machine legada como fallback.
- **Armazenamento**: MongoDB para catálogo, conversa, auditoria de termos,
  sessões efêmeras e outbox de entrega.
- **UI**: HTML/CSS/JS pequeno em `src/main/resources/static/termos/`, servido
  pelo mesmo origin da API. Não criar um segundo frontend/deploy para este
  requisito.
- **Homologação**: overlay HML, ingress HTTPS, mailbox de teste e checkout
  sandbox; nenhum deploy ou ativação de PROD nesta execução sem aprovação
  operacional separada.

## Constitution Check

*Gate inicial e pós-design: PASS, com gates operacionais ainda pendentes.*

- **I. Stack oficial — PASS**: Java 21, Spring Boot 3.4.x, Gradle e MongoDB;
  a UI será um recurso estático do backend, sem nova stack de runtime.
- **II. Clean Architecture — PASS**: domínio/aplicação definem sessão,
  auditoria, outbox e políticas; Mongo, WhatsApp, SMTP e HTTP permanecem nas
  bordas.
- **III. Specification/Test-first — PASS**: esta spec foi aprovada antes do
  código e as tarefas exigem red tests antes de cada implementação.
- **IV. Quality gate — PASS**: Gradle/JaCoCo mínimo de 60%, testes Mongo/MVC/UI,
  plugin e corpus fazem parte do gate; nenhuma aprovação será inferida só de
  cobertura.
- **V. Homolog-first — PASS**: PR volta para `hml`, validação funcional ocorre
  no HML e só depois pode haver promoção para `main`.

Os gates de conteúdo jurídico, provider, credenciais, ingress, SMTP e cluster
continuam bloqueantes para a conclusão operacional, mas não são violações da
constituição.

## Decisões de arquitetura

### 1. Consentimento web

Criar um agregado/sessão efêmera (`TermsConsentSession`) separado dos eventos
append-only (`TermsConsentAuditEvent`). A sessão mantém somente o estado atual
do link; cada transição gera um evento imutável. `prepare_terms` cria a sessão
e o evento `ISSUED` em transação, calcula SHA-256 do artefato jurídico imutável
e enfileira o link. O registro textual `TermsConsentAudit` existente não deve
ser apenas ampliado: deve ser substituído/adaptado para o novo evento sem
retrovalidar aceites antigos.

O link preferencial é `https://<base-configurada>/termos#t=<token>`. O token tem
entropia criptográfica mínima de 192 bits, expiração configurável (default
30 minutos), uso único e não contém PII. A persistência usa digest/HMAC; o
fragmento é removido do histórico assim que a UI carrega. A base URL é somente
de configuração validada, nunca derivada de `Host`/`X-Forwarded-*`.

A UI chama endpoints same-origin para `PAGE_PRESENTED`, `END_REACHED` e decisão
`ACCEPT`/`DECLINE`. Cada endpoint valida novamente o vínculo conversa/contato/
contratação/serviço/ambiente/versão/hash. `ACCEPT` exige `END_REACHED` no
servidor. A primeira decisão vence; replay idêntico é idempotente e decisão
oposta retorna conflito sem reescrever a auditoria.

`ACCEPT` altera a conversa e cria a mensagem de opções PIX/cartão no outbox na
mesma transação Mongo. `DECLINE` cria retorno ao menu. Expiração ou fechamento
não são decisões e não geram recusa.

### 2. Entrega e handoff

Usar uma outbox transacional para continuar o WhatsApp depois do callback web e
para enviar a mensagem fixa/e-mail de handoff. O worker resolve o endereço do
contato por `destinationRef`, registra provider ID, tentativas e resultado. O
webhook registra o endereço do canal em uma projeção interna idempotente; a
outbox guarda apenas a referência opaca e nunca tenta reverter o hash. A
interface de canal deve passar a retornar um resultado que diferencie falha
pré-dispatch de timeout pós-dispatch; a segunda categoria não pode sofrer retry
cego sem idempotência/consulta do provider.

Nenhuma chamada Hermes, WhatsApp ou SMTP ocorre dentro da transação de domínio.
Falha terminal muda a posse para HUMAN e gera uma notificação única; o conteúdo
exato ao cliente é definido na spec.

### 3. Hermes-only

O webhook injeta uma dependência obrigatória de `HermesWebhookMessageHandler`.
Remover `ObjectProvider`/fallback e a ativação condicional que deixa o HML cair
na state machine antiga. Remover somente classes/configurações exclusivas do
legado; preservar gateways e modelos compartilhados usados pelo Hermes.

### 4. Catálogo por ambiente

Extrair o baseline canônico para configuração por ambiente, com validação
fail-closed e recursos explicitamente marcados como sandbox/produção. O gateway
Mongo consulta o override operacional. O bootstrap cria apenas documentos
ausentes; não faz merge destrutivo em cada startup. Quando o Mongo falhar, o
serviço utiliza o baseline aprovado e emite métrica/log sem expor detalhe ao
cliente.

### 5. Fronteira de pagamento

Implementar um port/adaptador de checkout somente após a subtask de discovery
definir provider, contrato, sandbox e credenciais. Quando houver checkout por
método, o adapter recebe `PIX` ou `CARD`; se o provider só expuser checkout
único, o adapter registra essa limitação aprovada. Até lá, a ferramenta deve
recusar recurso ausente com handoff seguro, nunca usar links fixture em HML/PROD
como se fossem reais. A escolha PIX/cartão permanece no WhatsApp e o adapter
deve receber o método canônico `PIX` ou `CARD`.

## Estrutura prevista

```text
specs/011-pee23-closeout/
├── spec.md
├── plan.md
├── tasks.md
├── data-model.md
├── research.md
├── quickstart.md
└── checklists/
    ├── requirements.md
    └── acceptance.md

apps/urbana-connect-api/src/main/java/br/com/urbana/connect/
├── application/reception/       # casos de uso, outbox, handoff, termos
├── domain/reception/model/       # sessão/eventos/auditoria
├── domain/reception/port/out/    # portas de canal, outbox, termos
├── infrastructure/persistence/mongodb/reception/
├── infrastructure/whatsapp/     # resultado/idempotência de envio
└── interfaces/rest/              # endpoints de termos

apps/urbana-connect-api/src/main/resources/
├── static/termos/                # UI same-origin
└── application*.yml              # base URL, TTL, catálogo e retenção

quality/terms-ui/
├── package.json
├── playwright.config.ts
└── tests/terms-consent.spec.ts
```

## Fases e dependências

1. **Contratos e red tests** — atualizar spec, estados, portas e testes sem
   alterar produção; demonstrar que texto WhatsApp não aceita e que não existe
   endpoint web atual.
2. **Consentimento** — modelo, repositório, índices, token, endpoints, UI,
   segurança e testes unitários/MVC/Playwright. Depende dos documentos jurídicos
   somente para o conteúdo final; fixtures ficam restritas a testes.
3. **Continuação/outbox/handoff** — entrega automática pós-aceite, destino
   resolvível, retries, e-mail e fallback Hermes. O endereço do canal é
   registrado na borda do webhook e resolvido pelo worker via referência opaca;
   a fase não depende do provedor de pagamento.
4. **Hermes-only e catálogo** — remover fallback legado, aplicar propriedades +
   Mongo override e atualizar profile/plugin/corpus.
5. **Provider discovery** — subtask separada, sem escolher por inferência.
   Implementar adapter e configuração sandbox somente após decisão registrada.
6. **HML/PROD hardening** — ingress e ConfigMaps separados em
   `infra/kubernetes/app/overlays/hml/` e
   `infra/kubernetes/app/overlays/prod/`, headers, secrets, mailbox, base URL,
   deploy corrente e testes reais; depende dos insumos externos. O overlay PROD
   não contém segredos nem ativa produção nesta história.
7. **QA e aceite** — QA independente executa suíte, testes de segurança,
   corpus e checklist PEE-92; só então preparar PR para `hml`.

## Estratégia TDD e validação

- Escrever testes antes de cada alteração de produção para: token/binding,
  estados e CAS, conteúdo escapado, scroll ACK, replay, recusa/expiração,
  transação + outbox, falhas de canal, fallback Mongo e Hermes-only.
- Executar testes focados por fase; depois Gradle/JaCoCo/Sonar, plugin Hermes,
  corpus e Playwright/accessibility da tela.
- Testcontainers Mongo deve cobrir índices, corridas e rollback; mocks isolados
  não são suficientes para declarar atomicidade.
- QA não pode ser o mesmo escritor que implementou a fase. Falha de cluster,
  SMTP, WhatsApp, provider ou conteúdo jurídico deve ser reportada como
  bloqueio ambiental, não mascarada por teste local.

## Riscos e controles

| Risco | Controle |
|---|---|
| Link bearer encaminhado | token de uso único, curto, binding completo e sem PII |
| Duplicidade após timeout de WhatsApp | provider ID/idempotency quando suportado; estado `AMBIGUOUS` quando não |
| Callback sem destinatário reversível | registrar endereço protegido na fronteira do webhook e persistir apenas `destinationRef` na outbox |
| HTML jurídico inseguro | conteúdo estruturado/escapado e CSP sem terceiros |
| Seeder sobrescreve operação | bootstrap `save-if-absent`, teste de restart e métrica de fallback |
| HML continua no legado | wiring Hermes obrigatório, teste de contexto e smoke no ingress |
| Produção ativada sem critérios | overlay/segredos separados e gate explícito de operação |

## Gate de implementação

Este plano não escolhe provedor, não fornece documentos jurídicos, não cria
credenciais, não executa deploy e não transiciona Jira. Esses itens só entram
quando seus insumos e aprovações estiverem disponíveis. Até lá, o resultado é
`partial` e a PEE-23 não deve ser marcada como concluída.
