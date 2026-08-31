# Tasks: Refinamento da conversa comercial da Urba

**Input**: Design documents from `specs/010-refine-urba-sales-dialogue/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`

**Strategy**: uma pessoa desenvolvedora é a única escritora dos arquivos de
produção sobrepostos. Os testes novos devem ser escritos e falhar antes da
implementação correspondente. A QA independente só começa depois que o escritor
parar.

## Phase 1: Setup

**Purpose**: confirmar o contrato e preparar a execução local sem alterar
infraestrutura ou criar player de pagamento.

- [x] T001 Conferir branch `010-refine-urba-sales-dialogue`, checklist aprovado e artefatos `plan.md`, `spec.md`, `research.md`, `data-model.md` e `quickstart.md`
- [x] T002 Registrar o baseline de testes atual com `apps/urbana-connect-api/gradlew test` e `python -m unittest` do plugin, separando falhas preexistentes de regressões

## Phase 2: Foundational — contratos executáveis

**Purpose**: escrever primeiro as verificações que definem o comportamento novo.

- [x] T003 [P] Criar testes para aceitar `Aceito` após termos, rejeitar aceite antecipado/negado e aceitar `Aceito` combinado com método em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java`
- [x] T004 [P] Criar testes para mensagem de pagamento com orientação de uma unidade por ambiente e comprovante em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolServiceTest.java`
- [x] T005 [P] Criar/ajustar testes do catálogo para preço, limite, entregas, suporte, exclusões e escopo da Decor Reforma em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/domain/servicecatalog/model/ServiceCatalogItemTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/CommercialPolicyServiceTest.java`
- [x] T006 [P] Atualizar os testes estáticos do profile para exigir linguagem casual, termos `Manual do Espaço`, `Tour Virtual`, suporte e orientação de quantidade sem ampliar a superfície de ferramentas em `integrations/hermes-agent/plugins/urbana-domain/test_tools.py`
- [x] T007 [P] Criar testes de unidade para `TermsConsentAudit` e o gateway/use case: campos obrigatórios, transição condicional `PRESENTED -> ACCEPTED`, primeiro aceite vence e replay é idempotente em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/TermsAcceptanceUseCaseTest.java` e `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/MongoTermsConsentAuditGatewayTest.java`
- [ ] T008 Executar somente os testes T003–T007 e confirmar que as novas expectativas falham antes da implementação

**Checkpoint**: contratos executáveis definidos; nenhuma alteração de produção
deve ser aceita se os testes novos não demonstrarem a lacuna inicial.

## Phase 3: User Story 1 — conversa casual e apresentação comercial (Priority: P1)

**Goal**: tornar a Urba mais humana, amigável e clara, apresentando serviços
com os fatos canônicos sem roteiro rígido ou termos técnicos desnecessários.

**Independent Test**: testes do profile/plugin e replay de C01–C06 demonstram
saudação curta, emoji apenas em momento leve, linguagem casual, apresentação
completa proporcional e Decor Reforma com limites/exclusões.

### Implementation for User Story 1

- [x] T009 [US1] Atualizar as instruções de identidade, voz, emojis, vocabulário, perguntas de perfil e apresentação progressiva em `integrations/hermes-agent/profile/SOUL.md`, preservando seis ferramentas e guardrails de termos/pagamento/handoff
- [x] T010 [US1] Enriquecer a apresentação canônica dos serviços — especialmente Decor Reforma com R$ 450, até 20 m², entregas, suporte e exclusões — em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/servicecatalog/model/ServiceCatalogItem.java` e seus seeders/testes
- [x] T011 [US1] Atualizar descrições textuais do catálogo exposto pelo plugin para manter os termos preferenciais e a distinção entre consulta informativa e ação comercial em `integrations/hermes-agent/plugins/urbana-domain/__init__.py`
- [x] T012 [US1] Executar os testes de catálogo e profile e confirmar que C01–C06 não introduzem preço, link ou serviço legado

**Checkpoint**: a conversa informativa e as apresentações canônicas passam sem
alterar estado comercial, superfície Hermes ou recursos de pagamento.

## Phase 4: User Story 2 — aceite e pagamento sem retrabalho (Priority: P1)

**Goal**: registrar `Aceito` no contexto correto, preservar a ordem comercial e
orientar quantidade por ambiente na mensagem de pagamento simulado.

**Independent Test**: testes de política e ferramenta passam para entradas
válidas/negadas, formas combinadas, estado `ACCEPTED -> PREPARED` e mensagem com
quantidade/comprovante; nenhum player real é criado.

### Implementation for User Story 2

- [x] T013 [US2] Implementar `TermsConsentAudit`, o contrato `TermsConsentAuditGateway` e o caso de uso de aceite com identidade opaca da unidade, em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/`, `.../port/out/` e `.../application/reception/`
- [x] T014 [US2] Implementar o documento/adaptador Mongo, repositório, índices e wiring aditivo para a evidência, preservando registros legados, em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/reception/` e `PocReceptionConfiguration.java`
- [x] T015 [US2] Implementar normalização determinística de aceite isolado, variações de pontuação/case e mensagem inequívoca com método, rejeitando negação/antecipação, em `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- [x] T016 [US2] Ajustar orquestrador/ferramenta para só aceitar após apresentação durável, usar a mensagem inbound exata, respeitar a ordem aceite → método → pagamento e não retrovalidar aceite anterior no mesmo turno em `ReceptionOrchestrator.java` e `StatefulDomainToolService.java`
- [x] T017 [US2] Atualizar a mensagem de pagamento preparado para orientar `1 serviço por ambiente` e envio do comprovante, deixando explícito que o link da POC é simulado e sem afirmar capacidade do player em `StatefulDomainToolService.java`
- [x] T018 [US2] Executar os testes focados de política, auditoria e ferramentas, incluindo regressões de termos, método inválido, comprovante e aprovação humana

**Checkpoint**: nenhuma contratação avança sem termos/aceite; nenhum pagamento
é confirmado automaticamente; a orientação de quantidade é somente textual.

## Phase 5: User Story 3 — continuidade e segurança da conversa (Priority: P2)

**Goal**: aproveitar mensagens consecutivas, evitar perguntas duplicadas e
preservar handoff, comprovante e briefing sob decisão humana.

**Independent Test**: cenários de concorrência e handoff passam sem duplicação,
sem chamada Hermes após transferência e sem liberação antes da aprovação.

### Tests and implementation for User Story 3

- [x] T019 [P] [US3] Criar testes de regressão para respostas de perfil consecutivas e publicação tardia sem repetir campo em `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationTest.java`, `AgentOutputReconcilerTest.java` e `ReceptionOrchestratorTest.java`
- [x] T020 [US3] Corrigir apenas a reconciliação necessária para consumir entradas já recebidas, instalar publication fence e manter uma pergunta de perfil por mensagem da Urba em `ReceptionOrchestrator.java`, `ReceptionTurnReconciliationService.java` e/ou `AgentOutputReconciler.java`
- [x] T021 [P] [US3] Adicionar/ajustar testes de comprovante, handoff exclusivo e liberação pós-aprovação em `CommercialPolicyServiceTest.java` e `StatefulDomainToolServiceTest.java`
- [ ] T022 [US3] Executar os cenários C07–C16 determinísticos e confirmar que falha, termos, pagamento, comprovante e handoff não usam tom celebratório nem emojis indevidos

**Checkpoint**: continuidade e segurança preservadas; qualquer falha deve ser
classificada como produto, teste ou ambiente antes de correção.

## Phase 6: Polish, regressão e handoff

**Purpose**: consolidar evidências e deixar a implementação pronta para QA e
PR em `hml`, sem fazer deploy.

- [x] T023 Executar suíte Gradle completa, JaCoCo e testes do plugin/profile; registrar comandos, resultados e falhas ambientais em `specs/010-refine-urba-sales-dialogue/quickstart.md`
- [x] T024 Executar `smoke-contract.sh`, `smoke-isolation.sh` e `verify-tool-surface.sh` quando o ambiente local estiver disponível e registrar limitações do player inexistente
- [x] T025 Revisar diff contra `spec.md`, `plan.md` e `data-model.md`, confirmar que não há links/provedor real, secrets, nova ferramenta ou arquivo fora do escopo
- [x] T026 Preparar relatório de validação C01–C16 e replay Yohanna conforme `specs/010-refine-urba-sales-dialogue/evidence/yohanna-baseline.md`, para execução pela QA independente

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1) deve preceder os testes.
- Foundational (Phase 2) bloqueia todas as user stories.
- US1 e US2 compartilham catálogo/profile e devem ser implementadas
  sequencialmente pelo mesmo escritor; US3 começa depois dos guardrails de US2.
- Polish depende de US1, US2 e US3 concluídas.
- QA independente acontece depois de T023 e da parada do Developer.

### Parallel Opportunities

- T003–T007 podem ser escritos em paralelo por arquivos distintos, mas T008 é
  serial e deve confirmar falha.
- T016 e T018 podem ser preparados em paralelo, sem alterar produção.
- Não há paralelismo de escritores em `SOUL.md`, `ServiceCatalogItem.java` ou
  `StatefulDomainToolService.java`.

## Implementation Strategy

1. Executar T001–T008 e validar o ciclo red → green esperado.
2. Entregar US1 (MVP de voz/catalogo) e testar isoladamente.
3. Entregar US2 (aceite/pagamento textual) e testar invariantes.
4. Entregar US3 (continuidade/handoff) e executar regressões.
5. Consolidar evidências, parar o Developer e acionar QA independente.
6. Corrigir no máximo duas vezes a mesma causa, reutilizando o Developer; depois
   escalar ou consultar Emanuel se o bloqueio persistir.

## Validation notes

- T008 não foi reexecutado como uma fase red isolada depois que a implementação
  já estava presente; os testes novos foram mantidos como contrato executável e
  estão verdes na rodada focada.
- T022 permanece pendente como execução literal do corpus C07–C16; a suíte e o
  E2E live cobrem os invariantes equivalentes, mas não substituem o corpus
  definido na spec.
- O replay literal de Yohanna, os 12 pares cegos e a avaliação qualitativa da
  PO também permanecem como passos de homologação, descritos no relatório.
- A publication fence fecha as corridas comuns e deixa o turno retryable, mas a
  janela entre a última leitura e o append normal só será eliminada com CAS ou
  transação no gateway de transcript.
