# Quickstart e evidências — PEE-23 closeout

Este arquivo será preenchido durante a implementação. Nenhuma integração real
ou deploy é presumida pelo plano.

## Retomada da execução

Para continuar o trabalho em outra máquina, leia primeiro
[`handoff.md`](handoff.md). Ele registra o ponto atual, o próximo passo exato
(smoke exploratório do Asaas Sandbox), os gates ainda bloqueantes e os arquivos
que devem ser consultados antes de qualquer implementação de pagamento.

## Validação local esperada

```bash
cd apps/urbana-connect-api
./gradlew test jacocoTestReport

cd ../../integrations/hermes-agent/plugins/urbana-domain
python3 -m unittest test_tools.py

cd ../../../quality/conversation-corpus
ruby self-test.rb
```

Para a UI, executar o harness dedicado em `quality/terms-ui/` com navegador
headless, incluindo teclado, touch, viewport sem scroll, conteúdo curto,
offline e resposta perdida após aceite.

## Validação HML obrigatória antes de PEE-23

- confirmar deploy corrente do commit validado e readiness do cluster;
- abrir link no WebView/WhatsApp real, sem token em histórico, Referer ou logs;
- percorrer termos, aceitar/recusar, repetir callback e trocar serviço/versão;
- observar auditoria, outbox, retomada automática PIX/cartão e ausência de
  duplicidade;
- provocar falha de Hermes, Mongo, WhatsApp e SMTP e conferir handoff seguro;
- conferir quatro serviços, sandbox sem cobrança e conteúdo jurídico/hash igual
  ao aprovado para PROD;
- anexar transcripts/evidências ao checklist PEE-92.

Falha de cluster, SMTP, WhatsApp, conteúdo jurídico ou provider deve ser
classificada como bloqueio ambiental, não mascarada por um teste local.

## Evidências desta rodada — 2026-09-03

Branch de trabalho: `011-pee23-closeout` (base `hml`). O worktree contém a
implementação local e os artefatos de especificação; nenhum deploy, segredo ou
chamada a produção foi executado. O Jira foi sincronizado: PEE-23 está em
`Tarefas pendentes` e a subtask PEE-107 está em `Em andamento`.

- `git diff --check`: passou.
- `cd apps/urbana-connect-api && ./gradlew check --no-daemon -Dorg.gradle.java.home=...`:
  `BUILD SUCCESSFUL`, 521 testes Java, JaCoCo e verificação de cobertura verdes.
- A validação focada das correções Hermes/outbox/ledger também passou: handler
  oficial queue-first, mídia/comprovante encaminhados com segurança,
  retry/reconciliação do worker (inclusive resposta já concluída após falha do
  outbox), ACK de handoff idempotente, registro protegido de destino e
  redaction do URL bearer no ledger.
- `python3 -m unittest discover -s integrations/hermes-agent/plugins/urbana-domain -p 'test*.py'`:
  14 testes, todos verdes.
- `cd quality/conversation-corpus && ruby self-test.rb`: 18 execuções, 92
  asserções, 0 falhas.
- `cd quality/terms-ui && ./node_modules/.bin/playwright test --list`:
  3 testes descobertos. A execução padrão continua sem o Chromium gerenciado
  pelo Playwright neste macOS; o harness aceita `TERMS_UI_BROWSER_PATH` para
  uma instalação compatível. Com o Chrome local:
  `TERMS_UI_BROWSER_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./node_modules/.bin/playwright test`:
  3 testes passaram em 18,8s.
- `kubectl kustomize infra/kubernetes/app/overlays/hml` e `.../prod`:
  renderizados com sucesso, 5 recursos por overlay. HML renderiza Hermes
  ativo, consentimento obrigatório e catálogo sem fixture; PROD permanece
  fechado por padrão e sem ativação de Hermes.

- `git diff --check`, contratos de release/estrutura e parse YAML: verdes.

O caminho oficial do webhook agora registra o endereço WhatsApp normalizado na
coleção interna `reception_delivery_destinations` antes de enfileirar o evento.
Tipos reconhecidos de mídia (`image`, `document`, `audio` e `payment_proof`)
entram na fila; imagem/documento recebidos quando o pagamento está preparado
viram evidência `PROOF_RECEIVED` e atendimento `HUMAN`, sem confirmação
automática. O worker publica também `WHATSAPP_HUMAN_HANDOFF_ACK` no outbox e
reprocessa a intenção após falha transitória.
O outbox continua guardando somente `destinationRef`; a entrega usa provider ID,
retry pré-dispatch e estado `AMBIGUOUS` após resultado desconhecido. Respostas
Hermes e a mensagem segura de handoff também são intenções idempotentes. O
worker de entrega segue desligado nos overlays até haver mailbox, SMTP,
credenciais e contatos aprovados.

## Discovery de provedor registrada em 2026-09-03

A matriz comparativa e a recomendação técnica provisória estão em
`specs/011-pee23-closeout/research.md`. O aprofundamento solicitado pelo PO
está consolidado em `docs/plans/pee-107-discovery-providers.html`, com Asaas,
PagBank e Mercado Pago como finalistas. Nenhum provedor foi escolhido: T040 e
T041–T043 seguem pendentes, portanto não há adapter, credencial ou deploy de
pagamento.

Os comentários da rodada foram registrados no Jira em 05/09 (PEE-107 e
PEE-23); os status permanecem PEE-107 `Em andamento` e PEE-23 `Tarefas
pendentes`. O conector chegou a retornar WAF/405 em uma tentativa anterior,
mas a sincronização foi concluída posteriormente.

## Bloqueios externos ainda abertos

Para habilitar e validar o fluxo em HML ainda são necessários: os quatro
documentos jurídicos (versão, URL aprovada, conteúdo e hash), segredo do token
web, base URL HTTPS definitiva, quatro recursos de catálogo/checkout, decisão
do provedor sandbox e suas configurações HML/PROD, um `destinationRef` resolvido
sem inferir telefone a partir de hash, caixa/alias e parâmetros SMTP HML,
cluster/namespace disponíveis, número WhatsApp de teste e política de
retenção. Sem esses insumos, `TERMS_CONSENT_ENABLED`, catálogo operacional e
worker de entrega permanecem desativados/fail-closed por desenho.

O código legado de state machine não participa do caminho Hermes: o controller
falha fechado sem o handler Hermes e o bean `ConversationFlowService` fica
inativo com `hermes.poc.enabled=true`. A remoção física do conjunto legado e de
seus testes antigos permanece uma limpeza serial separada, pois os gateways e
modelos de canal ainda são compartilhados pelo adapter WhatsApp.
