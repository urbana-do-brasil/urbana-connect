# Tasks: Resiliência de turnos Hermes no chat manual da POC

**Input**: Design documents from `/specs/003-hermes-turn-resilience/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`
**Status**: 55/55 tarefas concluídas

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparar o ambiente e registrar a baseline sem alterar o comportamento.

- [x] T001 Confirmar a branch `003-hermes-turn-resilience`, Java 21, Docker Engine e os serviços POC em `specs/003-hermes-turn-resilience/quickstart.md`
- [x] T002 [P] Registrar a baseline de testes e limitações ambientais em `specs/003-hermes-turn-resilience/quickstart.md`

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: definir estados, contratos e claims que bloqueiam as histórias de usuário.

### Testes primeiro

- [x] T003 [P] Escrever testes de transição e retry seguro para `ReceptionTurn` em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/ReceptionTurnTest.java`
- [x] T004 [P] Escrever testes de claim exclusivo e recuperação de lease em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoActiveTurnLeaseGatewayTest.java`
- [x] T005 [P] Escrever testes de parsing do resumo de turno em `apps/poc-chat/src/api/contracts.test.ts`

### Implementação da fundação

- [x] T006 Implementar estados e metadados duráveis do turno em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/ReceptionTurn.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/ReceptionTurnStatus.java`
- [x] T007 Criar a porta e os documentos/repositorios Mongo para eventos POC duráveis em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/PocPendingEventGateway.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/SpringDataReceptionTurnRepository.java`
- [x] T008 Evoluir a porta de turnos e leases para consultas/claims por contato e estado em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/ReceptionTurnGateway.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/ActiveTurnLeaseGateway.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ActiveTurnLeaseService.java`
- [x] T009 Definir classificação de fase para falhas Hermes em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGateway.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/HermesSessionsGateway.java`
- [x] T010 Atualizar wiring/configuração sem segredos em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionConfiguration.java` e `apps/urbana-connect-api/src/main/resources/application-poc.yml`

**Checkpoint**: estados, ports e contratos compilam; os testes fundacionais falham antes das implementações e passam depois delas.

## Phase 3: User Story 1 — Acompanhar resposta lenta sem falso erro (Priority: P1) 🎯 MVP

**Goal**: aceitar a entrada de forma durável, processar Hermes fora do ciclo HTTP e manter o acompanhamento até uma conclusão canônica.

**Independent Test**: uma resposta controlada depois de 35s e outra depois do antigo limite de 120s produzem uma única chamada Hermes, uma única saída e nenhum erro sintético da UI.

### Testes primeiro — backend

- [x] T011 [P] [US1] Escrever teste para persistir/recuperar evento aceito antes do flush em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/PocReceptionIngressTest.java`
- [x] T012 [P] [US1] Escrever teste para worker assíncrono e paralelismo entre contatos em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/PocReceptionWorkerTest.java`
- [x] T013 [P] [US1] Escrever teste para resposta Hermes lenta, estado `DELAYED` e projeção segura em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionOrchestratorTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorControllerTest.java`
- [x] T014 [P] [US1] Escrever teste para reconciliação pelo histórico após timeout em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java`

### Testes primeiro — frontend

- [x] T015 [P] [US1] Escrever teste para polling além de 120s e estado `DELAYED` em `apps/poc-chat/src/state/conversationTracker.failure.test.ts`
- [x] T016 [P] [US1] Escrever teste para erro temporário de GET sem habilitar retry em `apps/poc-chat/src/state/conversationTracker.failure.test.ts` e `apps/poc-chat/src/state/conversationReducer.test.ts`
- [x] T017 [P] [US1] Escrever teste visual para espera/demora/falha técnica em `apps/poc-chat/src/components/FailureState.test.tsx` e `apps/poc-chat/src/components/ChatView.test.tsx`

### Implementação — backend

- [x] T018 [US1] Persistir eventos aceitos, status de claim e recuperação pós-restart em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionIngress.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/`
- [x] T019 [US1] Implementar executor local assíncrono com serialização por contato e paralelismo entre contatos em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionWorker.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionConfiguration.java`
- [x] T020 [US1] Remover retry imediato e conectar o worker ao ciclo de batch em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionIngress.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionBatchFlushScheduler.java`
- [x] T021 [US1] Persistir `RUNNING/DELAYED/RECONCILING` e manter o gate durante timeout ambíguo em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ActiveTurnLeaseService.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoActiveTurnLeaseGateway.java`
- [x] T022 [US1] Implementar reconciliação de resposta pelo histórico Hermes em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationService.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/HermesSessionService.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGateway.java`
- [x] T023 [US1] Expor somente o resumo seguro do turno na projeção em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorController.java`
- [x] T024 [US1] Ajustar timeout, lease, heartbeat e intervalo de recuperação em `apps/urbana-connect-api/src/main/resources/application-poc.yml` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionConfiguration.java`

### Implementação — frontend

- [x] T025 [US1] Adicionar o contrato seguro de turno e parsing de estados em `apps/poc-chat/src/api/contracts.ts` e `apps/poc-chat/src/api/conversationClient.ts`
- [x] T026 [US1] Remover deadline terminal e retry automático de transporte; implementar polling com backoff e retomada em `apps/poc-chat/src/state/conversationTracker.ts`
- [x] T027 [US1] Representar `WAITING`, `DELAYED`, `RECONCILING`, falha segura e falha terminal em `apps/poc-chat/src/state/conversationReducer.ts`
- [x] T028 [US1] Atualizar mensagens de espera e falha sem fala artificial em `apps/poc-chat/src/components/ChatView.tsx`, `apps/poc-chat/src/components/FailureState.tsx` e `apps/poc-chat/src/styles.css`

### Integração da história

- [x] T029 [US1] Atualizar fixtures e testes determinísticos do chat para a projeção com turno em `apps/poc-chat/src/test/fixtures.ts`, `apps/poc-chat/src/api/conversationClient.test.ts` e `apps/poc-chat/e2e/manual-chat.spec.ts`
- [x] T030 [US1] Executar teste focado backend/frontend da US1 e registrar evidência em `specs/003-hermes-turn-resilience/tasks.md`

**Checkpoint**: a POC aceita uma mensagem rapidamente, mostra demora sem abandonar polling e reconcilia uma resposta atrasada sem duplicidade.

## Phase 4: User Story 2 — Preservar um único turno por conversa (Priority: P1)

**Goal**: impedir turnos concorrentes, ordenar mensagens posteriores e manter contatos distintos independentes.

**Independent Test**: timeout ambíguo + reenvios + nova mensagem no mesmo contato geram zero chamadas Hermes concorrentes; três contatos diferentes podem executar em paralelo.

- [x] T031 [P] [US2] Escrever matriz de falhas inequívocas versus ambíguas em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGatewayTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java`
- [x] T032 [P] [US2] Escrever teste de duas instâncias reivindicando o mesmo contato em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoActiveTurnLeaseGatewayTest.java`
- [x] T033 [P] [US2] Escrever teste de mensagem sucessora ordenada e contatos independentes em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/PocReceptionWorkerTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/PocReceptionIngressTest.java`
- [x] T034 [US2] Implementar CAS/fencing, heartbeat e bloqueio de retry durante `RECONCILING` em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ActiveTurnLeaseService.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoActiveTurnLeaseGateway.java`
- [x] T035 [US2] Implementar fila ordenada de sucessores e retry somente com `retryAllowed=true` em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionWorker.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- [x] T036 [US2] Validar corrida entre conclusão direta e reconciliador com saída idempotente em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoReceptionPersistenceTest.java`

**Checkpoint**: nenhuma ação de timeout, reload ou retry cria execução concorrente; cada contato mantém seu transcript.

## Phase 5: User Story 3 — Exibir estado técnico verdadeiro e recuperável (Priority: P1)

**Goal**: distinguir espera, demora e falha sem transformar erro técnico em mensagem da Urba.

**Independent Test**: projeções com cada estado renderizam a indicação correta; somente falha explicitamente segura mostra retry.

- [x] T037 [P] [US3] Escrever testes de `retryAllowed`, `failureClass` e ausência de segredos no contrato em `apps/poc-chat/src/api/privacyContract.test.ts` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorControllerTest.java`
- [x] T038 [P] [US3] Escrever testes de estados terminais e fallback não conversacional em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionFailureRecoveryTest.java` e `apps/poc-chat/src/components/FailureState.test.tsx`
- [x] T039 [US3] Implementar classificação terminal/segura e remover fallback conversacional para erro técnico em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- [x] T040 [US3] Implementar a exibição condicional de retry e texto técnico em `apps/poc-chat/src/components/FailureState.tsx`, `apps/poc-chat/src/components/ChatView.tsx` e `apps/poc-chat/src/state/conversationReducer.ts`

**Checkpoint**: nenhuma falha técnica aparece como fala da Urba e nenhum retry é oferecido para estado ambíguo.

## Phase 6: User Story 4 — Retomar depois de interrupção local (Priority: P2)

**Goal**: continuar um trabalho aceito depois de reload, perda de GET ou reinício do serviço.

**Independent Test**: interromper a UI e reiniciar o worker com Mongo preservado; o turno retoma ou continua em conciliação sem novo dispatch.

- [x] T041 [P] [US4] Escrever teste de recuperação de claims expirados e turnos não terminais em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/PocReceptionWorkerTest.java`
- [x] T042 [P] [US4] Escrever teste de reload/resume e erro transitório de projeção em `apps/poc-chat/src/state/conversationTracker.concurrent.test.ts`, `apps/poc-chat/src/state/conversationTracker.failure.test.ts` e `apps/poc-chat/e2e/manual-chat.spec.ts`
- [x] T043 [US4] Implementar recuperação de eventos/turnos após reinício e retomada do reconciliador em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionWorker.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationService.java`
- [x] T044 [US4] Validar retomada do polling a partir da projeção canônica em `apps/poc-chat/src/state/conversationTracker.ts` e `apps/poc-chat/src/App.tsx`

**Checkpoint**: fechar/recarregar a interface ou reiniciar o serviço não descarta uma mensagem aceita nem cria novo turno.

## Phase 7: User Story 5 — Diagnosticar causa e isolar contatos (Priority: P2)

**Goal**: produzir evidência operacional suficiente e garantir que uma conversa lenta não bloqueie as demais.

**Independent Test**: três contatos em estados diferentes geram métricas/correlação distintas e permanecem isolados.

- [x] T045 [P] [US5] Escrever testes de métricas para demora, conciliação, retry suprimido e classificação externa em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionMetricsTest.java`
- [x] T046 [P] [US5] Escrever teste E2E de três contatos e indicadores independentes em `apps/poc-chat/e2e/manual-chat.spec.ts` e `apps/poc-chat/src/App.concurrent.test.tsx`
- [x] T047 [US5] Implementar contadores/logs correlacionados sem conteúdo sensível em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionMetrics.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java` e `apps/urbana-connect-api/src/main/resources/logback-spring.xml`
- [x] T048 [US5] Executar validação final operacional, smoke direto Hermes → provedor e E2E local com evidências Mongo em `specs/003-hermes-turn-resilience/quickstart.md` e `specs/003-hermes-turn-resilience/tasks.md`

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: fechar documentação, cobertura, regressão e aceitação.

- [x] T049 [P] Atualizar o roteiro operacional e riscos residuais em `specs/003-hermes-turn-resilience/quickstart.md` e `specs/003-hermes-turn-resilience/research.md`
- [x] T050 Executar `./gradlew test jacocoTestReport` e registrar resultado em `specs/003-hermes-turn-resilience/tasks.md`
- [x] T051 [P] Executar `npm run test -- --run` e `npm run build` e registrar resultado em `specs/003-hermes-turn-resilience/tasks.md`
- [x] T052 [P] Executar `npx playwright test` e registrar resultado em `specs/003-hermes-turn-resilience/tasks.md`
- [x] T053 Executar revisão independente da spec, diff e critérios com QA em `specs/003-hermes-turn-resilience/spec.md`, `specs/003-hermes-turn-resilience/plan.md` e `specs/003-hermes-turn-resilience/tasks.md`
- [x] T054 Atualizar o checklist de requisitos da feature em `specs/003-hermes-turn-resilience/checklists/requirements.md`
- [x] T055 [US1] Corrigir o polling após um turno anterior `COMPLETED` e adicionar regressão unitária/E2E para mensagens sequenciais em `apps/poc-chat/src/state/conversationReducer.ts`, `apps/poc-chat/src/state/conversationReducer.test.ts`, `apps/poc-chat/src/state/conversationTracker.test.ts` e `apps/poc-chat/e2e/manual-chat.spec.ts`

## Evidências registradas em 2026-08-07

### Validações aprovadas

- Branch original `003-hermes-turn-resilience`; validação consolidada na
  branch `feat/pee-101`; Java 21 Temurin usado em
  `/Users/emanueljoseguimaraesbrito/.sdkman/candidates/java/21.0.12-tem`.
- Suíte backend não-containerizada: 102 classes selecionadas, 292 testes,
  `BUILD SUCCESSFUL`; os testes focados de fila durável, worker, lease/heartbeat,
  reconciliação, classificação Hermes, projeção, métricas e configuração também
  passaram.
- `./gradlew bootJar`: aprovado.
- `npm run test -- --run`: 63 testes aprovados; `npm run typecheck`, `npm run lint`
  e `npm run build`: aprovados.
- `npx playwright test` com o Chrome instalado no sistema:
  6 testes aprovados e 1 cenário live explicitamente ignorado por ausência de
  `PLAYWRIGHT_LIVE=1`. Uma tentativa posterior não iniciou o servidor Vite por
  `listen EPERM` do ambiente gerenciado; isso não altera o resultado anterior,
  mas deve ser repetido em um runtime com permissão de bind.
- `ruby quality/conversation-corpus/self-test.rb`: 18 execuções, 92 assertions, zero falhas, erros ou
  skips.
- `./integrations/hermes-agent/scripts/smoke-contract.sh`: aprovado. O smoke live separado foi
  executado e terminou com `curl (28)` após 60 segundos sem resposta; foi
  classificado como indisponibilidade externa, não como aprovação do E2E.
- `RUN_DOCKER_CHECKS=1 ./apps/poc-chat/container.test.sh`: aprovado antes da perda de
  acesso ao socket Docker.
- `docker compose build urbana-connect poc-chat` e `up -d urbana-connect poc-chat`
  foram concluídos antes da restrição atual; `/api/v1/readiness` respondeu
  `READY` e a saúde funcional da Urbana respondeu `OK`.
- A configuração POC agora usa timeout Hermes padrão de 180s e lease/claim padrão
  de 240s. Foi acrescentada uma asserção de wiring para garantir lease/claim;
  sua execução após esse ajuste permanece pendente porque o Gradle não conseguiu
  criar o `FileLockContentionHandler` no ambiente atual.
- O compose POC também foi alinhado para não sobrescrever esses valores com
  defaults menores: `HERMES_SESSION_TIMEOUT_MS=180000`,
  `HERMES_ACTIVE_TURN_LEASE_SECONDS=240` e `HERMES_POC_CLAIM_TTL=240s`.

### Validações que ficaram abertas na baseline intermediária

Os itens abaixo documentam a fotografia intermediária anterior à retomada do
Docker Desktop. Foram revalidados e encerrados na seção seguinte; permanecem
no histórico para explicar por que a entrega não foi considerada concluída antes.

- **T048**: o E2E e a evidência Mongo ainda não existiam na baseline.
- **T050**: a suíte completa estava interrompida pelo Testcontainers e pelo
  acesso restrito ao ambiente.
- **T053**: a revisão independente inicial não produziu handoff; ela foi
  encerrada após encontrar restrições ambientais. Uma nova execução independente
  permanece pendente abaixo.
- Runtime intermediário: o Docker Desktop estava indisponível; nenhum sucesso
  foi simulado e nenhum volume foi removido.

## Revalidação após retomada do Docker Desktop (2026-08-07)

- `docker context show` retornou `desktop-linux`; `docker info` respondeu com
  Docker Desktop 28.0.1. Os seis serviços POC ficaram ativos; MongoDB e
  `poc-chat` saudáveis. `/api/v1/readiness` retornou `READY` e `/health` do chat
  retornou `ok`.
- O container `urbana-connect` foi recriado com os valores efetivos
  `HERMES_SESSION_TIMEOUT_MS=180000`, `HERMES_ACTIVE_TURN_LEASE_SECONDS=240` e
  `HERMES_POC_CLAIM_TTL=240s`; `.env.poc` continuou fora do Git e sem exposição
  de credenciais.
- `./gradlew --no-daemon --max-workers=1 test jacocoTestReport`: aprovado,
  `BUILD SUCCESSFUL`; relatório XML com 58 suítes, 327 testes, zero falhas,
  erros ou skips. JaCoCo: 81.74% instruções, 60.91% branches, 82.94% linhas,
  54.72% complexidade, 84.11% métodos e 96.09% classes.
- `./gradlew --no-daemon bootJar`: aprovado; a imagem da Urbana foi reconstruída
  e o container recriado com o artefato atual.
- `./integrations/hermes-agent/scripts/smoke-contract.sh`: aprovado; `HERMES_LIVE_MODEL_SMOKE=1
  ./integrations/hermes-agent/scripts/smoke-contract.sh`: aprovado, comprovando a rota Hermes →
  OpenRouter com as chaves locais configuradas. `./integrations/hermes-agent/scripts/smoke-isolation.sh`:
  aprovado (`filesystem_isolation=ok`, profile read-only e `network_isolation=ok`).
- E2E HTTP real via `poc-chat` com contato novo: `202/QUEUED`, depois
  `COMPLETED`, `attempt=1`, `hermesChatCalls=1`, uma mensagem inbound e uma
  outbound canônica. Mongo confirmou um `reception_turns` `COMPLETED`, uma
  pendência `COMPLETED`, lease `REVOKED` e duas invocações de domínio com
  `SUCCEEDED`, sem saída duplicada.
- Playwright determinístico com Chrome local: 6 testes aprovados e 1 cenário
  live omitido por flag. Playwright live contra `http://127.0.0.1:3000`: 1 teste
  aprovado em 38.7s, cobrindo três contatos, alternância, polling e reload.
  Mongo confirmou, para os três contatos mais recentes, um turno `COMPLETED`,
  uma saída outbound por conversa e tentativas iguais a 1.
- O bloqueio de compilação limpa causado por 32 fontes Java duplicadas com sufixo
  ` 2.java` foi resolvido sem descarte: as cópias foram movidas para
  `.codex/quarantine/003-duplicate-java-sources/` para preservação recuperável;
  artefatos gerados antigos foram removidos apenas pelo `./gradlew clean`.

### Encerramento da validação independente

- **T053**: encerrada na revalidação final; o QA independente retornou
  `VERDICT=VERIFIED`, sem editar arquivos. O handoff confirmou Gradle 327/327,
  Vitest 63/63, Playwright determinístico 6 pass, Playwright live pass com três
  contatos/reload, smokes Hermes pass e E2E real com `202 QUEUED → COMPLETED`,
  uma chamada Hermes e uma saída Mongo.

## Revalidação da correção frontend sequencial (2026-08-07)

- Causa reproduzida: após o primeiro turno `COMPLETED`, o segundo `202/QUEUED`
  recebia uma projeção stale com o turno anterior; `hasPendingWork` encerrava o
  tracker antes de o novo turno aparecer. O backend continuava processando e
  persistia a resposta.
- Correção aplicada somente no frontend: uma mensagem otimista ativa mantém o
  polling mesmo diante de um turno terminal anterior; a conclusão verdadeira do
  novo turno continua encerrando o tracker e falhas terminais não habilitam
  retry automático.
- Testes focados: `npm run test -- --run
  src/state/conversationReducer.test.ts src/state/conversationTracker.test.ts` —
  2 arquivos, 14 testes aprovados.
- Suíte frontend: `npm run test -- --run` — 19 arquivos, 65 testes aprovados;
  `npm run lint`, `npm run typecheck` e `npm run build` aprovados.
- Playwright: suíte completa — 7 testes aprovados e 1 cenário live explicitamente
  ignorado; `manual-chat.spec.ts` — 6/6 aprovados. O novo US6 reproduz duas
  mensagens sequenciais, projeção stale, ausência de reload e ausência de POST
  duplicado.
- QA independente: `VERDICT=VERIFIED`, sem alterações, confirmando a regra de
  precedência da mensagem otimista, encerramento após resposta canônica e
  ausência de polling infinito nos cenários cobertos.
- O container `poc-chat` foi reconstruído e recriado com o bundle corrigido. Um
  smoke live adicional chegou a fazer polling contínuo, mas a primeira mensagem
  excedeu 180s no Hermes e entrou em `RECONCILING` com
  `HERMES_TIMEOUT_AFTER_DISPATCH`; a segunda mensagem não foi enviada nesse
  smoke e o caso foi classificado como latência/indisponibilidade externa, não
  como falha do frontend.

### Riscos residuais registrados pelo QA

- A validação live cobre uma execução real e três contatos; não é benchmark de
  performance nem prova de produção.
- Concorrência, retries, duplicidade, timeouts, falhas Hermes/Mongo e restart
  foram cobertos pela suíte automatizada, mas não foram induzidos adicionalmente
  em uma execução live destrutiva.
- Segurança e operação de produção permanecem fora do escopo desta POC.

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: inicia imediatamente.
- **Foundational (Phase 2)**: depende do setup e bloqueia as histórias.
- **US1 (Phase 3)**: depende da fundação; é o MVP técnico.
- **US2 e US3 (Phases 4–5)**: dependem da US1 porque refinam claim, retry e projeção.
- **US4 e US5 (Phases 6–7)**: dependem das US1–US3.
- **Polish (Phase 8)**: depende de todas as histórias e das correções QA.

### Delegation ownership

- **Developer Backend**: T003–T004, T006–T010, T011–T014, T018–T024, T031–T039, T041, T043, T045, T047.
- **Developer Frontend**: T005, T015–T017, T025–T029, T037–T040, T042, T044, T046, T055.
- **Tech Lead**: T001–T002, coordenação, integração, T030, T048–T054.
- **QA Tester**: T053, T055 e validações independentes após os escritores pararem.

### Parallel Opportunities

- T003–T005 podem ser escritos em paralelo por escopo de arquivo.
- T011–T017 podem ser escritos em paralelo entre backend e frontend.
- T031–T033 e T037–T038 podem ser escritos em paralelo, respeitando a ownership.
- T045–T046 podem ser escritos em paralelo.
- T050–T052 são validações independentes e podem rodar em paralelo quando o código estiver estável.

## Implementation Strategy

1. Test-first da fundação e do cenário de timeout ambíguo.
2. Entregar US1 com fila durável, worker, projeção e polling contínuo.
3. Fechar exclusão/reconciliação e só então liberar estados de retry.
4. Validar retomada, isolamento, métricas e E2E real/controlado.
5. Marcar cada checkbox somente após comando/evidência real; nenhuma tarefa será
   concluída por inferência ou por um teste antigo.
