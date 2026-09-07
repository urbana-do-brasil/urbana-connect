# Tasks: Fechamento operacional da PEE-23

**Input**: Design documents from `specs/011-pee23-closeout/`
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`
**Strategy**: TDD; um único escritor por conjunto de arquivos sobrepostos; QA independente só após a parada do escritor.

## Phase 1: Setup

**Purpose**: preparar a execução sem escolher provedor, criar credenciais ou alterar produção.

- [x] T001 Criar/usar a branch `011-pee23-closeout` a partir de `hml` e registrar o baseline de testes em `specs/011-pee23-closeout/quickstart.md`
- [ ] T002 [P] Conferir os insumos externos necessários (termos/versões, base URL, destinationRef, mailbox HML, cluster e WhatsApp de teste) e registrar bloqueios em `specs/011-pee23-closeout/quickstart.md`
- [x] T003 [P] Atualizar a matriz de requisitos e rastreabilidade em `specs/011-pee23-closeout/checklists/requirements.md`

## Phase 2: Foundational — contratos, segurança e entrega

**Purpose**: contratos executáveis que bloqueiam todas as histórias.

- [x] T004 [P] Definir contratos de API para sessão, apresentação, fim de leitura e decisão em `specs/011-pee23-closeout/contracts/terms-consent-api.yaml`
- [x] T005 [P] Criar testes red para estado/token/binding/idempotência de consentimento em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/TermsConsentSessionTest.java`
- [x] T006 [P] Criar testes red para outbox, destino, provider ID, retry e estado ambíguo em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/DeliveryOutboxWorkerTest.java`
- [x] T007 [P] Criar testes MVC/security red para token inválido, métodos, headers, CORS, cache, referrer e conteúdo sem HTML inseguro em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/TermsConsentControllerTest.java`
- [x] T008 Criar portas de domínio para sessão de termos, outbox/delivery result e resolução de destino em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/port/out/`
- [x] T009 Implementar configuração tipada de TTL, base URL, retenção, catálogo por ambiente e limites de retry em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/config/` e `apps/urbana-connect-api/src/main/resources/application.yml`
- [ ] T010 Executar somente os testes T005–T007 e confirmar que falham antes da implementação; registrar o resultado em `specs/011-pee23-closeout/quickstart.md`

**Checkpoint**: contratos definidos e lacunas demonstradas; nenhuma produção deve ser alterada.

## Phase 3: User Story 1 — consentimento web auditável (P1)

**Goal**: substituir aceite textual por tela web segura, auditável e vinculada ao serviço/versão.

**Independent Test**: sessão emitida, página renderizada, botão liberado somente após `END_REACHED`, aceite/recusa/expiração e replay cobertos sem liberar pagamento indevido.

### Tests first

- [x] T011 [P] [US1] Criar testes de transição `ISSUED -> PAGE_PRESENTED -> END_REACHED -> ACCEPTED/DECLINED/EXPIRED` e conflitos em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/reception/model/TermsConsentSessionTest.java` e `TermsConsentAuditEventTest.java`
- [ ] T012 [P] [US1] Criar testes Mongo/Testcontainers para índices, TTL apenas da sessão, concorrência e rollback de consentimento+conversa+outbox em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoTermsConsentSessionGatewayTest.java`
- [x] T013 [P] [US1] Criar testes MVC para respostas 401/404/409/410, binding completo e headers de segurança em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/TermsConsentControllerTest.java`
- [x] T014 [P] [US1] Criar testes Playwright da tela em `quality/terms-ui/tests/terms-consent.spec.ts` com `quality/terms-ui/playwright.config.ts` para teclado/touch, conteúdo curto, scroll, offline e token ausente de histórico/Referer

### Implementation

- [x] T015 [US1] Implementar entidades `TermsConsentSession`, `TermsConsentAuditEvent`, estados/eventos e regras de decisão em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/`, substituindo o modelo textual sem retrovalidar registros antigos
- [x] T016 [US1] Implementar repositórios Mongo, índices e CAS/TTL para sessão/auditoria em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/`
- [x] T017 [US1] Implementar emissão, token HMAC/digest, binding e decisões transacionais em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/TermsConsentService.java`
- [x] T018 [US1] Implementar endpoints same-origin em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/TermsConsentController.java` e atualizar `SecurityConfig.java`
- [x] T019 [US1] Criar UI responsiva e acessível em `apps/urbana-connect-api/src/main/resources/static/termos/index.html`, `terms.css` e `terms.js`, usando fragmento, `no-store`, `no-referrer`, CSP e conteúdo escapado
- [x] T020 [US1] Integrar `prepare_terms`/`StatefulDomainToolService` para emitir o link web e impedir qualquer aceite textual em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/` e `integrations/hermes-agent/plugins/urbana-domain/`
- [x] T021 [US1] Integrar aceite/recusa à conversa e ao outbox de continuação PIX/cartão sem chamar Hermes/WhatsApp dentro da transação em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/`

**Checkpoint**: a tela e sua API são testáveis localmente; nenhum texto no WhatsApp libera pagamento.

## Phase 4: User Story 2 — Hermes-only, entrega e handoff seguro (P1)

**Goal**: remover segunda fonte de verdade e tratar falhas com mensagem fixa, e-mail e retry seguro.

**Independent Test**: webhook sem bean Hermes falha de forma explícita; falhas classificadas geram outbox/HUMAN sem duplicidade e comprovante nunca confirma pagamento.

### Tests first

- [x] T022 [P] [US2] Criar teste de contexto garantindo `HermesWebhookMessageHandler` obrigatório e ausência de fallback em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/WebhookControllerTest.java`
- [x] T023 [P] [US2] Criar testes de classificação pré-dispatch, retryável, ambígua, terminal, entrega e persistência em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/hermes/HttpHermesSessionsGatewayTest.java` e `ReceptionOrchestratorTest.java`
- [x] T024 [P] [US2] Criar testes de outbox worker, e-mail idempotente, retry/restart e `destinationRef` em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/DeliveryOutboxWorkerTest.java`
- [x] T025 [P] [US2] Criar regressões de comprovante/handoff e mensagem exata em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionOrchestratorTest.java` e `StatefulDomainToolServiceTest.java`

### Implementation

- [x] T026 [US2] Remover `ObjectProvider`/fallback e tornar o wiring Hermes obrigatório em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/WebhookController.java` e `PocReceptionConfiguration.java`
- [ ] T027 [US2] Após inventário de dependências, remover somente classes/configurações/testes exclusivamente legados e preservar componentes compartilhados em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/conversation/` e configurações relacionadas. O caminho oficial já não instancia o legado, mas a remoção física do conjunto compartilhado ainda exige uma fatia serial própria.
- [x] T028 [US2] Implementar resultado de envio com provider ID, classificação de falha e resolução segura de destino; registrar o endereço WhatsApp na entrada e resolver depois somente pela referência opaca em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/whatsapp/`, `application/reception/` e portas de canal
- [x] T029 [US2] Implementar coleção/outbox, worker, backoff, estado ambíguo e recuperação em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/` e `infrastructure/persistence/mongodb/reception/`
- [x] T030 [US2] Integrar o handoff Hermes ao SMTP por outbox, com mensagem aprovada, posse HUMAN, `Message-ID` obrigatório e e-mail idempotente em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/mail/` e `application/reception/`. A execução HML depende dos parâmetros externos de T002/T047.
- [x] T031 [US2] Atualizar profile/plugin/corpus para aceite web, Hermes-only e handoff seguro em `integrations/hermes-agent/profile/SOUL.md`, `plugins/urbana-domain/` e `quality/conversation-corpus/`

**Checkpoint**: um único caminho Hermes, handoff recuperável e nenhum detalhe técnico exposto.

## Phase 5: User Story 3 — catálogo operacional por ambiente (P1)

**Goal**: permitir mudanças de links/preços/termos sem deploy, sem sobrescrever Mongo e com fallback seguro.

**Independent Test**: alteração Mongo sobrevive a restart; registro ausente recebe baseline; falha Mongo usa propriedade do ambiente e emite métrica.

### Tests first

- [x] T032 [P] [US3] Criar testes de configuração HML/PROD, validação de recurso e fallback em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/catalog/OperationalServiceCatalogGatewayTest.java`
- [x] T033 [P] [US3] Inverter testes do seeder para `save-if-absent`, preservação de override e restart em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/servicecatalog/ServiceCatalogSeederTest.java`
- [x] T034 [P] [US3] Criar testes do gateway/policy usando catálogo efetivo, disponibilidade e quatro nomes canônicos em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java`

### Implementation

- [x] T035 [US3] Implementar propriedades tipadas de catálogo e resolver efetivo baseline+Mongo em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/catalog/` e `domain/servicecatalog/`
- [x] T036 [US3] Alterar `ServiceCatalogSeeder.java` para inicializar somente documentos ausentes e preservar valores operacionais em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/servicecatalog/ServiceCatalogSeeder.java`
- [x] T037 [US3] Fazer `CommercialPolicyService` depender do `ServiceCatalogGateway`, remover links fixture como recurso operacional e aplicar fallback/observabilidade em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- [x] T038 [US3] Configurar valores base HML/PROD, disponibilidade e base URL nos perfis e overlays explícitos `apps/urbana-connect-api/src/main/resources/application-hml.yml`, `application-prod.yml`, `infra/kubernetes/app/overlays/hml/` e `infra/kubernetes/app/overlays/prod/`, sem inserir segredos

**Checkpoint**: catálogo editável por ambiente, sem destruição de overrides.

## Phase 6: User Story 4 — discovery e checkout sandbox (P2, dependência externa)

**Goal**: escolher e integrar um provedor real de sandbox somente após decisão explícita.

**Independent Test**: candidato escolhido gera checkout sandbox para PIX/cartão conforme limitação aprovada, com configuração separada para PROD e sem confirmação financeira.

- [x] T039 [US4] Criar subtask Jira de discovery com matriz de candidatos, custos, PIX/cartão, sandbox, operação HML/PROD, idempotência, provider ID, rotação de segredos e webhook futuro; subtask `PEE-107` criada, matriz registrada em `specs/011-pee23-closeout/research.md` e aprofundamento dos finalistas (Asaas, PagBank e Mercado Pago) consolidado em `docs/plans/pee-107-discovery-providers.html` em 2026-09-03
- [ ] T040 [US4] Registrar a decisão do provedor e as limitações aprovadas em `specs/011-pee23-closeout/research.md` e no Jira, sem rejeição automática de candidatos parciais
- [ ] T041 [P] [US4] Criar testes do contrato `CheckoutGateway` e do mapeamento `PIX`/`CARD` em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/payment/`
- [ ] T042 [US4] Implementar adapter sandbox e configuração HML/PROD do provedor escolhido somente após T040 em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/payment/` e `application*.yml`
- [ ] T043 [US4] Atualizar `prepare_payment` para gerar checkout por método quando suportado, aceitar checkout único somente com limitação aprovada, recusar recurso ausente com handoff e não confirmar pagamento em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/`

**Checkpoint**: sem T040, nenhum adapter/segredo/deploy de pagamento pode ser criado.

## Phase 7: Polish, HML e aceite

**Purpose**: validação independente e evidência para concluir a PEE-23.

> Estado em 2026-09-03: 38 tarefas têm implementação/documentação local
> concluída. As 12 tarefas desmarcadas dependem de insumos externos, execução
> histórica não reproduzível (red tests), Testcontainers, Sonar, navegador
> compatível, decisão/integração do provedor ou validação/aprovação em HML.

- [x] T044 [P] Atualizar ingress HTTPS, rotas `/termos`, headers e ConfigMaps separados em `infra/kubernetes/app/overlays/hml/ingress.yaml`, `infra/kubernetes/app/overlays/hml/configmap-patch.yaml`, `infra/kubernetes/app/overlays/prod/ingress.yaml` e `infra/kubernetes/app/overlays/prod/configmap-patch.yaml`, sem aplicar PROD
- [x] T045 [P] Atualizar documentação de fluxo, estados e exclusões em `docs/specs/pee-23-triagem-e-vendas.md` e `docs/specs/pee-102-catalogo-e-contexto-operacional-urba.md`
- [ ] T046 Executar Gradle/JaCoCo/Sonar, testes do plugin, corpus e UI/security; registrar comandos/resultados em `specs/011-pee23-closeout/quickstart.md`
- [ ] T047 Executar nova subtask de validação técnica corrente no HML (não reabrir PEE-88), cobrindo deploy, secrets, Mongo, URL, WhatsApp, sandbox e e-mail
- [ ] T048 Executar checklist PEE-92 no HML com transcripts atuais e obter aprovação do PO/jurídico
- [x] T049 Fazer QA independente final contra `spec.md`, `data-model.md` e `checklists/requirements.md`, classificando falhas de produto/teste/ambiente. A revisão independente encontrou e validou a correção dos P1 de mídia/comprovante, recuperação de outbox e ACK de handoff; não restaram P1/P2 no delta.
- [ ] T050 Preparar evidências finais, PR para `hml` e recomendação de transição; só depois de T047–T049 a PEE-23 pode ser marcada como concluída

## Dependências e ordem

- T001–T010 bloqueiam as histórias.
- US1 (T011–T021) fornece o consentimento necessário para US2 e o pagamento.
- US2 (T022–T031) e US3 (T032–T038) podem seguir em sequência por um único
  escritor após a fundação; não compartilhar escritores simultâneos nos mesmos
  arquivos.
- US4 (T039–T043) depende de decisão externa T040 e não pode ser inferida.
- T044–T050 dependem de US1–US4 e dos insumos HML; QA começa somente após o
  escritor parar.

## Oportunidades de paralelismo

- T004–T007 e T011–T014 podem ser escritos em arquivos distintos antes da
  implementação.
- T022–T025 e T032–T034 são conjuntos independentes de testes, mas cada história
  deve ter um único escritor de produção depois.
- T044/T045 são documentação/infra separados da implementação, mas só entram
  após os contratos estabilizados.

## Estratégia de implementação

Entregar primeiro consentimento web e handoff seguro sem provedor; depois
catálogo operacional; só então integrar o provider escolhido. Cada fase segue
red → green → refactor, com no máximo duas correções para a mesma causa antes
de escalar diagnóstico ou consultar Emanuel. Nenhuma falha de ambiente será
tratada como sucesso local.
