# Tasks: Atendimento comercial completo e seguro da Urba

**Input**: artefatos de design em `/specs/008-complete-urba-service-flow/`
**Prerequisites**: `spec.md`, `plan.md`, `baseline.md`, `research.md`,
`data-model.md`, `contracts/` e `quickstart.md`
**Execution model**: delta sobre baseline reconciliado com lacunas; nenhum checkbox de
produto é concluído apenas porque o arquivo já existe ou está modificado.

**Estado em 2026-08-26**: T007–T048 foram implementadas e verificadas por
testes locais focados; o Compose/Hermes foi iniciado e o smoke conversacional
feliz passou pela API. T051, T053 e T055–T058 permanecem pendentes por falta de
Playwright live, pela suíte legada ainda vermelha/coverage abaixo do limite,
pelos cinco roteiros manuais e pelo aceite/PR humano.

## Phase 1: Gates de execução e reconciliação do baseline

**Purpose**: tornar o estado local rastreável e seguro antes do primeiro
escritor. T001–T006 foram executadas; o resultado e as lacunas estão congelados
em `baseline.md`.

- [x] T001 Com o GO de execução, criar a subtarefa `PEE-105` sob PEE-23, registrar a chave e as dependências PEE-102/PEE-103/PEE-104 em `specs/008-complete-urba-service-flow/spec.md` e mover a issue para `Em andamento` antes do primeiro escritor
- [x] T002 Preservar o snapshot atual e migrar/renomear a branch descendente de `hml` para `feature/008-complete-urba-service-flow`, registrando branch e `git status` em `specs/008-complete-urba-service-flow/baseline.md`
- [x] T003 Atualizar o inventário inicial do diff preexistente por slice e associar arquivos modificados aos requisitos/tarefas em `specs/008-complete-urba-service-flow/baseline.md`
- [x] T004 Executar os testes focados já existentes de catálogo, handoff, retomada, plugin e POC e classificar cada slice como aproveitável, incompleto, conflitante ou fora de escopo em `specs/008-complete-urba-service-flow/baseline.md`
- [x] T005 Comparar o seeder duplicado legado com o canônico, removê-lo do source set por conter uma segunda classe pública e preservá-lo em `docs/plans/legacy-review/ServiceCatalogSeeder 2.java`, registrando a decisão não destrutiva em `specs/008-complete-urba-service-flow/baseline.md`
- [x] T006 Atualizar os checkboxes desta fila somente para itens comprovados pelo gate e congelar o mapa do delta remanescente em `specs/008-complete-urba-service-flow/tasks.md`

**Checkpoint**: ticket e branch conformes; todo diff conhecido está preservado,
classificado e coberto por evidência proporcional.

## Phase 2: Contratos executáveis compartilhados

**Purpose**: fixar invariantes antes de completar qualquer comportamento.

- [x] T007 [P] Reconciliar testes-base da matriz dos quatro serviços, área, preço, entregas e ausência de `DECOR` legado em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolServiceTest.java`
- [x] T008 [P] Reconciliar testes do plugin para envelopes seguros e ausência de exceção, HTTP, URL interna, identificador ou evento técnico no retorno ao Hermes em `integrations/hermes-agent/plugins/urbana-domain/test_tools.py`
- [x] T009 [P] Reconciliar testes de confirmação visível, ownership humano e idempotência do handoff em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionOrchestratorTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/HumanHandoffServiceTest.java`
- [x] T010 [P] Reconciliar testes de transição HUMANO → URBA e ausência de resposta antes da sincronização em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/ReceptionConversationTest.java`

**Checkpoint**: testes compartilhados falham somente para deltas reais e não
codificam uma máquina de estado autoritativa do ICP.

## Phase 3: User Story 1 — catálogo e explicação progressiva (P1 / MVP)

**Goal**: a Urba se apresentar e explicar os quatro serviços, diferenças,
entregas, processo, responsabilidades e limites sem inventar informação ou
encaminhar desnecessariamente.

**Independent Test**: executar a matriz factual e o Roteiro A até “como
funciona”, verificando apresentação espontânea, explicação rica, progressiva e
sem limite indevido para Decor Pintura/Fachada.

### Tests

- [x] T011 [P] [US1] Completar testes first do catálogo rico, múltiplos ambientes e regras de área em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java`
- [x] T012 [P] [US1] Completar testes first do payload de `list_available_services`, comparação e “como funciona”, comprovando que turno informativo não prepara termos, pagamento ou handoff, em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolServiceTest.java`
- [x] T013 [P] [US1] Completar o teste de contrato do seed/adaptador único e dos quatro serviços em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/servicecatalog/ServiceCatalogSeederTest.java`

### Implementation

- [x] T014 [US1] Reconciliar e completar o modelo rico do catálogo em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/servicecatalog/model/ServiceCatalogItem.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/servicecatalog/model/ServiceType.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/servicecatalog/model/AreaRule.java`
- [x] T015 [US1] Reconciliar a fonte canônica de preço, área, processo, entregas, exclusões, responsabilidades e suporte em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- [x] T016 [US1] Reconciliar seed, documento e gateway Mongo com a fonte canônica, sem links legados ou quinto serviço, em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/servicecatalog/ServiceCatalogSeeder.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/servicecatalog/ServiceCatalogDocument.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/servicecatalog/MongoServiceCatalogGateway.java`
- [x] T017 [US1] Orientar apresentação curta, descoberta e explicação progressiva do catálogo sem script longo em `integrations/hermes-agent/profile/SOUL.md`
- [x] T018 [US1] Verificar e atualizar o payload rico consumido pelo Hermes em `specs/008-complete-urba-service-flow/contracts/reception-domain-tools.md`

**Checkpoint**: a Urba se apresenta e responde “diferença/como funciona” com o
catálogo correto, sem handoff precoce nem linguagem interna.

## Phase 4: User Story 2 — ICP, termos, pagamento e briefing (P1)

**Goal**: o SOUL coletar enriquecimento de lead no momento certo e o backend
manter somente fatos, proteções comerciais e observabilidade, sem controlar ou
travar o diálogo por ICP.

**Independent Test**: Roteiros A (passos 10–19) e E, mais injeção técnica
controlada de SC-014. O fluxo normal coleta antes dos termos; o desvio direto
emite evento interno e mantém a preparação funcional.

### Tests

- [x] T019 [P] [US2] Completar testes first de fatos globais, valor explícito mais recente, `NÃO INFORMADO`, reutilização e ausência de inferência em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionResponsePolicyTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReturningCustomerServiceTest.java`
- [x] T020 [P] [US2] Testar que serviço/ambiente/área e aceite continuam sendo hard protections, ICP incompleto nunca rejeita `prepare_terms` e `TERMS=PRESENTED` só nasce após solicitação/apresentação efetiva, em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java`
- [x] T021 [P] [US2] Escrever teste first do `ICP_SKIPPED_BEFORE_TERMS`: exatamente um evento por chave idempotente, resultado comercial inalterado e payload limitado a ids opacos, serviço, campos ausentes, ponto de detecção e momento em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolServiceTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/tools/DomainToolInvocationUseCaseTest.java`
- [x] T022 [P] [US2] Testar reidratação da thread atual integral, mensagens humanas e fatos correntes sem projeção específica que suprima mensagens por causa do ICP em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGatewayTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/HermesSessionServiceTest.java`
- [x] T023 [P] [US2] Testar que o plugin transporta contexto autorizado, mas nunca evento, exceção, valor bruto em log ou controlador de tentativas em `integrations/hermes-agent/plugins/urbana-domain/test_tools.py`
- [x] T024 [US2] Escrever antes da correção comportamental os cenários semânticos live de primeira apresentação espontânea, serviço confirmado, intenção de contratar, campos ausentes/parciais, recusa, segunda ausência, reutilização, atualização silenciosa, assunto paralelo e ICP antes dos termos em `apps/poc-chat/e2e/quality-chat.spec.ts`

### Implementation

- [x] T025 [US2] Reconciliar modelos/políticas de fatos globais, versionamento, valor corrente e cálculo de campos ausentes sem `ICPCheckpointState` ou contador conversacional em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionResponsePolicy.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReturningCustomerService.java`
- [x] T026 [US2] Reconciliar a gravação de fatos explícitos e implementar o evento idempotente sem alterar o envelope de termos nem expô-lo ao Hermes/cliente em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolService.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/DomainToolInvocationUseCase.java`
- [x] T027 [US2] Codificar no SOUL o gatilho após serviço/intenção, bloco curto de campos ausentes, uma única segunda oportunidade, recusa/ausência como `NÃO INFORMADO`, avanço automático, assunto paralelo, handoff e retomada pela thread em `integrations/hermes-agent/profile/SOUL.md`
- [x] T028 [US2] Garantir thread atual integral e fatos correntes na sessão, sem filtro específico de ICP e sem usar fala da arquiteta como fato do cliente, em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesResumeGateway.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGateway.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/HermesSessionService.java` e `integrations/hermes-agent/plugins/urbana-domain/tools.py`
- [x] T029 [US2] Reconciliar as proteções de aceite claro, pagamento antecipado e briefing somente após confirmação na política canônica em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- [x] T030 [US2] Integrar aceite, pagamento, comprovante pendente de validação humana e liberação do briefing nos boundaries/orquestração sem transformar ICP em hard gate em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolService.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`

**Checkpoint**: a conversa normal executa o ICP sem rigidez de backend; o teste
de desvio observa a regressão sem mudar o resultado ou vazar dados.

## Phase 5: User Story 3 — handoff humano visível e seguro (P1)

**Goal**: pedido de humano/arquiteta gerar exatamente um aviso conversacional,
notificação interna e bloqueio automático coerente.

**Independent Test**: Roteiro A após comprovante e Roteiro D; repetir o pedido
e confirmar uma única mensagem/notificação e silêncio automático posterior.

### Tests

- [x] T031 [P] [US3] Completar teste de envelope HTTP seguro sem status, stack trace ou identificador exposto em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/poc/DomainToolControllerTest.java`
- [x] T032 [P] [US3] Completar testes de ack canônico antes do bloqueio, exatamente uma mensagem/notificação e resumo interno com campos presentes/ausentes do ICP em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionOrchestratorTest.java`
- [x] T033 [P] [US3] Completar o mesmo contrato para timeout, replay e reconciliação em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java`
- [x] T034 [P] [US3] Completar testes de serialização segura e mensagem neutra do plugin em `integrations/hermes-agent/plugins/urbana-domain/test_tools.py`

### Implementation

- [x] T035 [US3] Reconciliar falhas de negócio/técnicas como envelopes seguros em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/DomainToolController.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/DomainToolInvocationUseCase.java`
- [x] T036 [US3] Reconciliar handoff idempotente com ack determinístico persistido/publicado antes do bloqueio em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolService.java`
- [x] T037 [US3] Aplicar a mesma política de ack, silêncio e sanitização no caminho de reconciliação em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationService.java`
- [x] T038 [US3] Garantir pedido explícito de humano imediato e nenhuma fala técnica em `integrations/hermes-agent/profile/SOUL.md` e `integrations/hermes-agent/plugins/urbana-domain/tools.py`
- [x] T039 [US3] Reconciliar os comandos POC idempotentes de validação humana e notificação interna em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorController.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorControllerTest.java`

**Checkpoint**: o cliente é avisado exatamente uma vez e nenhuma automação fala
enquanto o ownership permanecer humano.

## Phase 6: User Story 4 — retorno HUMANO → URBA e POC de validação (P1)

**Goal**: devolver a responsabilidade por flag, sincronizar a thread canônica e
retomar proativamente somente quando houver próximo passo comprovado.

**Independent Test**: Roteiros B/C/D e retorno durante ICP; repetir a mesma
transição e verificar uma sincronização, uma mensagem proativa ou espera segura.

### Tests

- [x] T040 [P] [US4] Completar testes de transição real, epoch/versão, decisões humanas e idempotência em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/ReceptionConversationTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java`
- [x] T041 [P] [US4] Completar testes de contexto integral, decisão proativa/espera e retorno ao humano em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGatewayTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/HermesSessionServiceTest.java`
- [x] T042 [P] [US4] Completar testes de ownership, ack, controles locais e mensagens canônicas não editáveis em `apps/poc-chat/src/components/ChatView.test.tsx`, `apps/poc-chat/src/state/conversationReducer.handoff.test.ts` e `apps/poc-chat/src/api/contracts.test.ts`

### Implementation

- [x] T043 [US4] Reconciliar contrato de retomada e proteção contra resposta antes da sincronização em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationService.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/HermesResumeGateway.java`
- [x] T044 [US4] Reconciliar decisões humanas, boundary do transcript, falha terminal e no-op de retorno repetido em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/`
- [x] T045 [US4] Reconciliar projeção e controles determinísticos da arquiteta em `apps/poc-chat/src/api/`, `apps/poc-chat/src/state/`, `apps/poc-chat/src/components/ArchitectControls.tsx` e `apps/poc-chat/src/App.tsx`
- [x] T046 [US4] Garantir ack como mensagem e ownership como estado complementar em `apps/poc-chat/src/components/ChatView.tsx` e `apps/poc-chat/src/styles.css`
- [x] T047 [US4] Estender o E2E para comprovante, handoff, decisão humana, retorno proativo ao briefing e retomada dos campos de ICP ainda ausentes em `apps/poc-chat/e2e/quality-chat.spec.ts`
- [x] T048 [US4] Reconciliar build da imagem backend a partir do fonte atual, fixtures/controles não comerciais e documentação de evidências em `apps/urbana-connect-api/Dockerfile.poc.runtime-jar`, `infra/local-poc/docker-compose.poc.yml`, `infra/local-poc/README.md` e `specs/008-complete-urba-service-flow/quickstart.md`

**Checkpoint**: contexto humano chega ao Hermes antes da resposta; a Urba não
exige repetição, não inventa decisão e não repete retomada.

## Phase 7: QA independente e aceite local

- [x] T049 [P] Executar testes focados do catálogo/fatos e conferir SC-001 e a persistência/reutilização de SC-013, registrando comandos/resultados em `specs/008-complete-urba-service-flow/baseline.md`
- [x] T050 [P] Executar testes focados de SOUL, plugin, handoff, retomada e falhas seguras; conferir SC-004–SC-008 e procurar frases proibidas/valores brutos em `apps/urbana-connect-api/` e `integrations/hermes-agent/`
- [ ] T051 [P] Executar Vitest, build e Playwright live; conferir SC-002, SC-003, SC-005–SC-007, SC-009 e SC-011–SC-013, anexando o relatório gerado por `apps/poc-chat/e2e/quality-chat.spec.ts`
- [x] T052 Executar a injeção controlada de SC-014 e comprovar um evento idempotente, resultado de termos inalterado e nenhuma exposição no contrato/log de teste em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolServiceTest.java`
- [ ] T053 Executar a suíte backend, build, quality gate JaCoCo mínimo de 60%, rebuild da imagem local e healthcheck para conferir SC-010 em `apps/urbana-connect-api/` e `infra/local-poc/`
- [x] T054 Verificar por inspeção que não existe `ICPCheckpointState`, `attemptsByField` ou hard gate de ICP no código de produto e registrar a evidência em `specs/008-complete-urba-service-flow/baseline.md`
- [ ] T055 Executar os cinco roteiros manuais da seção 5.4 e registrar transcript, ownership, ids, estado factual do ICP e evidências em `specs/008-complete-urba-service-flow/quickstart.md`
- [ ] T056 Revisar FR-001–FR-064, SC-001–SC-014, checklist, baseline e riscos residuais antes de solicitar a aprovação manual de Emanuel em `specs/008-complete-urba-service-flow/checklists/requirements.md`

## Phase 8: Rastreabilidade e PR para homologação

- [ ] T057 Após aceite local explícito, abrir PR de `feature/008-complete-urba-service-flow` para `hml` com objetivo, resumo, validações, riscos e ticket Jira, registrar o link em `specs/008-complete-urba-service-flow/baseline.md` e mover a issue para `Awaiting approval`
- [ ] T058 Após aprovação humana e merge, registrar a evidência em `specs/008-complete-urba-service-flow/baseline.md` e mover a issue para `Concluído`; não promover para `main` ou fazer deploy por esta tarefa

## Dependencies & Execution Order

```text
Gate 0: T001–T006
        └── contratos: T007–T010
             ├── Writer A: T011/T013–T016 → US2 T019–T020/T025/T029
             ├── Writer B: T012/T017 → US2 T021–T023/T026–T028/T030 → US3 T031–T039 → US4 T040–T041/T043–T044
             └── Writer C: US2 T024 → US4 T042/T045–T048
                        └── todos os escritores param → QA T049–T056
                                                        └── PR T057–T058
```

- T001–T006 são bloqueantes; nenhum subagente escritor começa antes do gate.
- Writer A tem exclusividade sobre catálogo, fatos, política comercial e
  persistência correspondente (`domain/servicecatalog`, `CustomerFact*`,
  `CommercialPolicyService`, `ReceptionResponsePolicy`,
  `ReturningCustomerService`, `ReceptionConversation` e testes diretamente
  desses módulos). Em T007, A edita somente a parte de catálogo/política.
- Writer B tem exclusividade sobre orquestração, ferramentas/boundaries,
  adaptador Hermes, plugin e SOUL (`StatefulDomainToolService`,
  `DomainToolInvocationUseCase`, `ReceptionOrchestrator`, reconciliação,
  sessões, controllers e testes diretamente desses módulos). Em T007, B edita
  somente a parte do payload/tool service. T026 e T030 pertencem a B.
- Writer C tem exclusividade sobre POC, E2E, compose, Dockerfile da POC e
  documentação operacional local. Nenhum writer edita `spec.md`, `plan.md`,
  `tasks.md` ou `baseline.md`; a thread principal mantém esses artefatos.
- Writer C tem exclusividade sobre POC, E2E, proxy/compose e documentação local;
  `quality-chat.spec.ts` é escrito serialmente em T024 e T047.
- T024 deve existir/falhar pelo comportamento ausente antes de T027–T030 quando
  tecnicamente aplicável; T047 depende dos contratos estabilizados de US3/US4.
- QA é T049–T056 e começa somente quando A/B/C pararem de escrever.
- T057 exige aceite local de Emanuel; T058 exige aprovação e merge humanos.

## Implementation Strategy

1. Executar Gate 0 e converter o diff existente em delta verificável.
2. Fixar os contratos executáveis T007–T010.
3. Usar no máximo três escritores com ownership disjunto; reutilizar o mesmo
   escritor para correções no próprio slice.
4. Fazer TDD no comportamento ausente e preservar testes válidos já existentes.
5. Submeter o resultado a QA independente e aos cinco roteiros manuais.
6. Só após aceite explícito abrir PR para `hml`; não fazer deploy nem promoção
   para `main` neste ciclo.
