# Tasks: Núcleo conversacional Hermes-first

**Input**: Design documents from `/specs/001-hermes-conversational-core/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`  
**Method**: SDD + TDD; testes de comportamento devem falhar antes da implementação correspondente.

**Status**: 59/59 tarefas concluídas; resultado `verified` na branch `feat/pee-101`

## Phase 1: Setup

**Purpose**: preparar configuração local reproduzível sem alterar o profile Hermes pessoal.

- [x] T001 Criar variáveis POC sem segredos em `.env.poc.example` e garantir exclusão de `.env.poc` em `.gitignore`
- [x] T002 [P] Criar identidade do profile em `integrations/hermes-agent/profile/SOUL.md` e configuração restrita em `integrations/hermes-agent/profile/config.yaml.example`
- [x] T003 [P] Criar manifest e schemas das seis ferramentas em `integrations/hermes-agent/plugins/urbana-domain/plugin.yaml` e `integrations/hermes-agent/plugins/urbana-domain/schemas.py`
- [x] T004 Fixar Hermes `v2026.8.3` e criar instalação isolada, smoke do contrato nativo e validação do profile em `integrations/hermes-agent/scripts/install-local.sh`, `integrations/hermes-agent/scripts/run-local.sh` e `integrations/hermes-agent/scripts/smoke-contract.sh`
- [x] T005 [P] Criar profile Spring POC em `apps/urbana-connect-api/src/main/resources/application-poc.yml`

---

## Phase 2: Foundational

**Purpose**: contratos, persistência, integração Hermes e segurança que bloqueiam todas as histórias.

- [x] T006 [P] Escrever testes de invariantes de `ReceptionConversation` em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/ReceptionConversationTest.java`
- [x] T007 [P] Escrever testes de versão/procedência de fatos em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/CustomerFactTest.java`
- [x] T008 [P] Escrever testes do parser do contrato mínimo em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/hermes/HermesAgentOutputParserTest.java`
- [x] T009 [P] Escrever testes HTTP da Sessions API para resposta livre, uso e rotação de sessão em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGatewayTest.java`
- [x] T010 Implementar modelos centrais em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/`
- [x] T011 Implementar portas de sessão, transcript, fatos, turnos e ferramentas em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/`
- [x] T012 Implementar parser estrito, reconciliação com ledger e fallback em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/hermes/HermesAgentOutputParser.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/AgentOutputReconciler.java`
- [x] T013 Implementar cliente HTTP da Sessions API e rotação do vínculo retornado em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGateway.java`
- [x] T014 [P] Escrever testes Mongo de vínculo de sessão e transcript em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/`
- [x] T015 Implementar documentos, repositories e gateways Mongo em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/`
- [x] T016 Escrever testes de resolução/criação/recuperação de sessão em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/HermesSessionServiceTest.java`
- [x] T017 Implementar resolução e recuperação excepcional da sessão em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/HermesSessionService.java`
- [x] T018 Escrever testes de idempotência, serialização e lease atômico/expirável em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnCoordinatorTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ActiveTurnLeaseServiceTest.java`
- [x] T019 Implementar coordenação por contato e lease com revogação em `finally` em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionTurnCoordinator.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ActiveTurnLeaseService.java`
- [x] T020 Escrever testes de autenticação, lease ausente/expirado/revogado, binding da sessão, idempotência derivada e allowlist em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/poc/DomainToolControllerTest.java`
- [x] T021 Implementar API interna das ferramentas em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/DomainToolController.java` e casos de uso em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/`
- [x] T022 Implementar handlers que encaminham somente o session ID do runtime, sem identificadores escolhidos pelo modelo, em `integrations/hermes-agent/plugins/urbana-domain/tools.py` e `integrations/hermes-agent/plugins/urbana-domain/test_tools.py`

**Checkpoint**: é possível criar/reusar uma sessão, validar uma saída, auditar ferramentas e impedir acesso cruzado entre contatos.

---

## Phase 3: US1 — Primeiro atendimento com conclusão comercial (P1) 🎯 MVP

**Goal**: conduzir uma primeira compra até a espera de comprovante e liberar briefing somente após aprovação humana.

**Independent Test**: conversa sintética feliz conclui ICP, serviço, termos, comprovante, aprovação humana e briefing correto.

- [x] T023 [P] [US1] Escrever teste da barreira ICP/termos/pagamento/briefing em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java`
- [x] T024 [P] [US1] Escrever teste WebMvc do evento canônico compartilhado pelo simulador e adapter WhatsApp, além da aprovação do comprovante, em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorControllerTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/WebhookCanonicalEventMapperTest.java`
- [x] T025 [US1] Implementar checkpoints comerciais determinísticos em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- [x] T026 [US1] Implementar orquestração do turno e contrato final em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- [x] T027 [US1] Implementar evento interno canônico, mappers do simulador/WhatsApp e projeção POC sem alterar o roteamento produtivo em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/InboundConversationEvent.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/ConversationSimulatorController.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/WebhookCanonicalEventMapper.java`
- [x] T028 [US1] Criar catálogo, termos, pagamento e briefing sintéticos em `apps/urbana-connect-api/src/main/resources/poc/reception-fixtures.yml`
- [x] T029 [US1] Criar cenário feliz e asserts em `quality/conversation-corpus/scenarios/01-happy-first-contact.yml`

**Checkpoint**: US1 passa de forma isolada sem links ou cobranças reais.

---

## Phase 4: US2 — Atendimento flexível para contato confuso (P1)

**Goal**: esclarecer além do roteiro sem inventar oferta ou condição comercial.

**Independent Test**: persona ambígua recebe perguntas contextuais, corrige informação e conclui o fluxo com catálogo válido.

- [x] T030 [P] [US2] Escrever testes de correção e rejeição de condição inventada em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionResponsePolicyTest.java`
- [x] T031 [US2] Implementar validação pós-agente de mensagem e ação em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionResponsePolicy.java`
- [x] T032 [US2] Adicionar regras conversacionais flexíveis ao profile em `integrations/hermes-agent/profile/SOUL.md`
- [x] T033 [US2] Criar cenário confuso e asserts em `quality/conversation-corpus/scenarios/02-confused-customer.yml`

**Checkpoint**: US2 conclui sem depender da sequência literal do script e sem ultrapassar o catálogo.

---

## Phase 5: US3 — Transferência exclusiva para atendimento humano (P1)

**Goal**: interromper completamente a IA durante e após o handoff, mantendo somente a persistência das mensagens para atendimento humano.

**Independent Test**: após o handoff, novas mensagens são persistidas sem chamada ao Hermes e chamadas tardias de ferramenta não alteram o estado.

- [x] T034 [P] [US3] Escrever testes do modo humano exclusivo em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/HumanHandoffServiceTest.java`
- [x] T035 [US3] Implementar entrada definitiva no modo humano para a POC em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/HumanHandoffService.java`
- [x] T036 [US3] Integrar bloqueio pré-Hermes no `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- [x] T037 [US3] Criar cenário de handoff e asserts em `quality/conversation-corpus/scenarios/03-human-handoff.yml`

**Checkpoint**: nenhuma resposta, sugestão ou ferramenta automática é executada em modo humano.

---

## Phase 6: US4 — Contato sem intenção comercial (P2)

**Goal**: apresentar-se, responder brevemente e encerrar sem forçar ICP.

**Independent Test**: persona de contato errado não entra no funil nem gera fatos ICP.

- [x] T038 [P] [US4] Escrever teste de não progressão comercial em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/NonProspectPolicyTest.java`
- [x] T039 [US4] Implementar limite de sondagem e encerramento em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/NonProspectPolicy.java`
- [x] T040 [US4] Criar cenário não prospect e asserts em `quality/conversation-corpus/scenarios/04-non-prospect.yml`

**Checkpoint**: US4 não coleta ICP sem sinal comercial.

---

## Phase 7: US5 — Retomada de cliente recorrente (P1)

**Goal**: reutilizar sessão e fatos vigentes, sem vazamento entre contatos.

**Independent Test**: cliente retorna em outro instante, recupera serviço/fatos e não repete ICP; contato paralelo não vê esses dados.

- [x] T041 [P] [US5] Escrever teste de continuidade e isolamento em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReturningCustomerServiceTest.java`
- [x] T042 [US5] Implementar projeção de fatos vigentes e campos faltantes em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReturningCustomerService.java`
- [x] T043 [US5] Integrar recuperação por sessão existente no `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- [x] T044 [US5] Criar cenário recorrente e cenário sentinela de isolamento em `quality/conversation-corpus/scenarios/05-returning-customer.yml`

**Checkpoint**: 100% dos fatos explícitos selecionados são recuperados e zero fatos cruzam contatos.

---

## Phase 8: US6 — Multimodalidade e mensagens fragmentadas (P2)

**Goal**: normalizar texto fragmentado, transcrição e imagem sem permitir que visão confirme pagamento.

**Independent Test**: fixtures de fragmentos, áudio transcrito, foto de ambiente e comprovante produzem turnos corretos.

- [x] T045 [P] [US6] Escrever testes da janela 4s/10s e eventos imediatos em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/MessageBatcherTest.java`
- [x] T046 [P] [US6] Escrever testes de mídia e comprovante em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/MediaNormalizationServiceTest.java`
- [x] T047 [US6] Implementar agrupamento temporal em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/MessageBatcher.java`
- [x] T048 [US6] Implementar porta e adapter local Whisper substituível, referências de mídia e imagem inline em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/MediaNormalizationService.java`, `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/TranscriptionGateway.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/media/LocalWhisperTranscriptionGateway.java`
- [x] T049 [US6] Garantir que comprovante produza apenas `PROOF_RECEIVED` em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- [x] T050 [US6] Criar fixtures multimodais allowlisted em `quality/conversation-corpus/fixtures/` e cenário em `quality/conversation-corpus/scenarios/06-multimodal.yml`

**Checkpoint**: mídia é auditável; aprovação de pagamento continua exclusivamente humana.

---

## Phase 9: Corpus, observability and hardening

**Purpose**: executar as cinco personas três vezes, registrar evidências e preparar o gate de homologação.

- [x] T051 [P] Criar schema e loader do corpus em `quality/conversation-corpus/schema/scenario.schema.json` e `quality/conversation-corpus/README.md`
- [x] T052 Criar runner repetível e relatório com campos de avaliação humana 1–5 em `quality/conversation-corpus/run-local.sh` e `quality/conversation-corpus/report.rb`
- [x] T053 [P] Adicionar métricas de turno, ferramenta, duração e falha em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionMetrics.java`
- [x] T054 Adicionar testes de falha, retry e sessão perdida em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionFailureRecoveryTest.java`
- [x] T055 Endurecer autenticação POC, redaction e path allowlist em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/config/SecurityConfig.java` e `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/`
- [x] T056 Validar que o profile expõe somente `urbana-domain` em `integrations/hermes-agent/scripts/verify-tool-surface.sh`
- [x] T057 Executar `./gradlew test`, `./gradlew check` e testes Python do plugin a partir de `apps/urbana-connect-api/` e `integrations/hermes-agent/plugins/urbana-domain/`
- [x] T058 Executar o corpus com três repetições e salvar relatório ignorado pelo Git em `quality/conversation-corpus/results/`
- [x] T059 Revisar `specs/001-hermes-conversational-core/quickstart.md` contra os comandos realmente validados

---

## Dependencies & Execution Order

- Phase 1 precede Phase 2.
- Phase 2 bloqueia todas as histórias.
- US1 é o MVP e precede a integração final de US3, US5 e US6 no orquestrador.
- US2 e US4 podem ser desenvolvidas após US1 sem alterar persistência.
- US3 deve concluir antes dos testes finais de corpus.
- US5 depende de sessão e fatos da fundação, não de US2/US4.
- US6 pode iniciar após a fundação, mas a regra de comprovante depende da política comercial da US1.
- Phase 9 depende de todas as histórias incluídas na POC.

## Parallel Opportunities

- T002, T003 e T005 usam arquivos distintos.
- T006–T009 descrevem contratos distintos e podem ser escritos em paralelo antes da implementação.
- T014 pode ser escrita enquanto os modelos/ports são implementados, desde que o contrato de dados não mude.
- Cenários do corpus usam arquivos independentes, mas devem seguir os contratos aprovados.
- QA começa somente depois que o Developer encerrar as alterações.

## Independent acceptance map

| Story | Evidence |
| --- | --- |
| US1 | Barreiras comerciais e jornada feliz até briefing. |
| US2 | Esclarecimento flexível, correção e zero invenção comercial. |
| US3 | Zero execução automática em modo humano. |
| US4 | Encerramento sem ICP para não prospect. |
| US5 | Retomada e isolamento de memória. |
| US6 | Fragmentos, áudio transcrito, imagem e comprovante sem aprovação automática. |

## Implementation Strategy

1. Entregar Setup + Foundational.
2. Entregar US1 como vertical slice local.
3. Integrar US3 e US5, que representam os maiores riscos de autoridade e memória.
4. Adicionar US2, US4 e US6.
5. Executar hardening e corpus completo.
6. Não alterar o webhook real nesta feature.
