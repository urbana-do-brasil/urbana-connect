---

description: "Tarefas de implementação do chat local para testes manuais da Urba"
---

# Tasks: Chat local para testes manuais da Urba

**Input**: Design documents from `specs/002-poc-manual-chat/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/chat-api.yaml` e `quickstart.md`

**Branch**: `feat/pee-101`

## Status de execução

Reconciliado em 2026-08-06 com base nos arquivos existentes, artefatos de
testes e validações registradas no ambiente. O tracker passa a registrar
**60/60 tarefas concluídas**. A implementação foi publicada em `feat/pee-101`,
o PR #60 foi aberto para `hml` e a subtask PEE-101 foi atualizada e movida para
`Awaiting approval`.

Situação atual: implementação principal, UI, estado, proxy e integração Compose
existem. O smoke live falhou inicialmente porque o tracker encerrava a espera
visual em 30s, embora o backend persistisse uma resposta real após 43s. O
limite foi ajustado para 120s, testado primeiro com teste determinístico e o
smoke live completo passou depois do rebuild do container.

Validações concluídas nesta rodada: `npm ci`, lint, typecheck, 42 testes
Vitest, cobertura, build, seis cenários E2E determinísticos, testes Java com
JDK 21, cinco testes Python, smoke de contrato, isolamento e superfície Hermes,
contrato do container e quickstart com os serviços ativos. O tracker também
foi ajustado de 30s para 120s após a evidência de uma resposta legítima do
backend persistida em aproximadamente 43s.

Inspeção final concluída: o bundle não contém padrões de token, `Authorization`,
`Bearer`, transcript ou chave OpenRouter; o contrato de privacidade E2E cobriu
armazenamento e tráfego; o webhook e escopos de produção não têm diff; `.env.poc`
permanece ignorado; e `git diff --check` passou.

Não há tarefas técnicas pendentes nesta spec. O próximo passo obrigatório é a
revisão humana e a validação em `hml`; nenhum deploy foi executado.

**Regra de execução**: tarefas de teste devem ser escritas e executadas antes da implementação correspondente. Os marcadores `[P]` indicam trabalho paralelo somente quando os arquivos não se sobrepõem e a dependência indicada já foi satisfeita.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: criar a aplicação frontend isolada e seus gates locais sem acoplar o runtime Java.

- [x] T001 [P] Criar a estrutura inicial da aplicação em `poc-chat/` conforme `specs/002-poc-manual-chat/plan.md`, sem mover arquivos do backend
- [x] T002 Inicializar dependências e scripts de build/teste em `poc-chat/package.json` e fixar o lockfile em `poc-chat/package-lock.json`
- [x] T003 [P] Configurar TypeScript estrito, Vite, Vitest, Testing Library e Playwright em `poc-chat/tsconfig.json`, `poc-chat/vite.config.ts`, `poc-chat/playwright.config.ts` e `poc-chat/src/test/setup.ts`
- [x] T004 [P] Configurar lint, formatação e limites de cobertura nos arquivos de configuração de qualidade de `poc-chat/`
- [x] T005 [P] Atualizar `.gitignore` e criar `poc-chat/.dockerignore` para excluir dependências, builds, cobertura, logs e qualquer `.env*` sem remover regras existentes

**Checkpoint**: o frontend instala dependências de forma reproduzível e os comandos base de typecheck, lint, teste e build estão definidos.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: estabelecer contratos, estado canônico e coordenação assíncrona que todas as histórias usam.

**⚠️ CRITICAL**: nenhuma história de usuário começa antes desta fase estar verde.

- [x] T006 [P] Criar o schema versionado do armazenamento visual em `specs/002-poc-manual-chat/contracts/browser-storage.schema.json`, proibindo transcripts, payloads, tokens e identificadores de turno
- [x] T007 [P] Escrever testes falhando para validação, migração segura e descarte individual de contatos em `poc-chat/src/state/contactStore.test.ts`
- [x] T008 Implementar `LocalContact`, `PersistedUiState`, geração de alias `manual-<UUID>` e persistência somente de metadados em `poc-chat/src/state/contactStore.ts`
- [x] T009 [P] Escrever testes de contrato HTTP para payload textual, alias, `202`, `409`, erros de transporte e ausência do header `Authorization` em `poc-chat/src/api/conversationClient.test.ts`
- [x] T010 Implementar os tipos e o cliente same-origin em `poc-chat/src/api/contracts.ts` e `poc-chat/src/api/conversationClient.ts`, sem aceitar token vindo da UI
- [x] T011 [P] Escrever testes falhando para validação de projeção, deduplicação por IDs, ordenação, reconciliação otimista e detecção de turno concluído em `poc-chat/src/state/conversationReducer.test.ts`
- [x] T012 Implementar reducer, modelos de estado e reconciliação canônica em `poc-chat/src/state/conversationReducer.ts`
- [x] T013 [P] Escrever testes falhando para polling somente de contatos pendentes, backoff, timeout, handoff e isolamento de ciclos em `poc-chat/src/state/conversationTracker.test.ts`
- [x] T014 Implementar o tracker assíncrono e o controle de retry com `eventId` estável em `poc-chat/src/state/conversationTracker.ts`
- [x] T015 [P] Criar fixtures determinísticas de projeção, recibos e falhas em `poc-chat/src/test/fixtures.ts` e helpers de `fetch` em `poc-chat/src/test/httpTestUtils.ts`

**Checkpoint**: contratos de browser, estado local, cliente HTTP, reconciliação e tracker têm testes verdes sem depender do ambiente Hermes real.

---

## Phase 3: User Story 1 — Conversar manualmente com a Urba (Priority: P1) 🎯 MVP

**Goal**: permitir criar um contato, enviar texto, aguardar a janela real de agrupamento e visualizar a resposta canônica sem painel técnico.

**Independent Test**: iniciar um contato, enviar três fragmentos pela interface, aguardar o processamento e observar uma única resposta da Urba sem terminal ou banco.

### Tests for User Story 1

- [x] T016 [P] [US1] Escrever teste de interação para criação de contato, envio otimista e estado de espera em `poc-chat/src/components/ChatView.test.tsx`
- [x] T017 [P] [US1] Escrever teste de teclado para Enter enviar e Shift+Enter preservar quebra de linha em `poc-chat/src/components/MessageComposer.test.tsx`
- [x] T018 [P] [US1] Escrever teste de renderização de balões, quebras de linha e links `http/https` escapados em `poc-chat/src/components/MessageBubble.test.tsx`

### Implementation for User Story 1

- [x] T019 [US1] Implementar o shell da aplicação e composição do estado em `poc-chat/src/App.tsx` e `poc-chat/src/main.tsx`
- [x] T020 [US1] Implementar histórico e balões de cliente/Urba em `poc-chat/src/components/ChatView.tsx` e `poc-chat/src/components/MessageBubble.tsx`
- [x] T021 [US1] Implementar composer textual com limite, validação compreensível e regras de teclado em `poc-chat/src/components/MessageComposer.tsx`
- [x] T022 [US1] Implementar envio otimista, acompanhamento sem `flush` e estados de espera em `poc-chat/src/components/ChatView.tsx` e `poc-chat/src/state/conversationTracker.ts`
- [x] T023 [US1] Aplicar layout, acessibilidade básica e identidade Urbana com rótulo “Simulador local” em `poc-chat/src/styles.css`

**Checkpoint**: US1 funciona com mocks determinísticos e não chama webhook real, `flush`, Hermes diretamente ou endpoints técnicos.

---

## Phase 4: User Story 2 — Manter múltiplos contatos isolados (Priority: P1)

**Goal**: criar, selecionar, arquivar e conversar com vários contatos, mantendo aliases, memória e respostas isolados.

**Independent Test**: três contatos, incluindo dois com o mesmo nome, recebem mensagens intercaladas e nenhuma resposta aparece no histórico errado.

### Tests for User Story 2

- [x] T024 [P] [US2] Escrever testes de lista, criação, seleção, nomes duplicados, arquivamento e indicadores de não lido em `poc-chat/src/components/ConversationList.test.tsx`
- [x] T025 [P] [US2] Escrever teste negativo que inspeciona URL, headers, corpo e armazenamento e prova que `displayName` nunca é enviado em `poc-chat/src/api/privacyContract.test.ts`
- [x] T026 [P] [US2] Escrever teste de isolamento de três contatos e respostas fora de ordem em `poc-chat/src/App.multi-contact.test.tsx`

### Implementation for User Story 2

- [x] T027 [US2] Implementar lista, criação, seleção, arquivamento e indicador de não lido em `poc-chat/src/components/ConversationList.tsx`
- [x] T028 [US2] Integrar o contato ativo, cursores de leitura e múltiplos estados de conversa em `poc-chat/src/App.tsx` e `poc-chat/src/state/conversationReducer.ts`
- [x] T029 [US2] Garantir a fronteira de alias opaco no cliente e impedir `displayName` em path, payload, logs e headers em `poc-chat/src/api/conversationClient.ts`
- [x] T030 [US2] Implementar acompanhamento concorrente por alias sem cruzar respostas em `poc-chat/src/state/conversationTracker.ts`

**Checkpoint**: US1 continua verde e US2 cobre identidade técnica independente, nomes iguais, não lidos e isolamento observável.

---

## Phase 5: User Story 3 — Retomar testes após recarregar a interface (Priority: P1)

**Goal**: restaurar a lista visual e recuperar transcripts exclusivamente da projeção canônica após reload.

**Independent Test**: conversar com dois contatos, recarregar, recuperar ambos os históricos e criar um terceiro sem memória herdada.

### Tests for User Story 3

- [x] T031 [P] [US3] Escrever testes de hidratação, seleção ativa, schema desconhecido e armazenamento sem transcript em `poc-chat/src/state/contactStore.reload.test.ts`
- [x] T032 [P] [US3] Escrever teste de recuperação da projeção ordenada sem duplicação e de arquivamento sem DELETE remoto em `poc-chat/src/App.reload.test.tsx`

### Implementation for User Story 3

- [x] T033 [US3] Implementar hidratação segura do estado visual e restauração do contato ativo em `poc-chat/src/state/contactStore.ts` e `poc-chat/src/App.tsx`
- [x] T034 [US3] Implementar carga sob demanda, ordenação canônica e reconciliação após reload em `poc-chat/src/api/conversationClient.ts` e `poc-chat/src/state/conversationReducer.ts`
- [x] T035 [US3] Implementar arquivamento exclusivamente local e criação de identidade sem herança em `poc-chat/src/components/ConversationList.tsx`

**Checkpoint**: reload recupera contatos e projeções; localStorage contém somente metadados permitidos; nenhum transcript é apagado ou copiado para o browser.

---

## Phase 6: User Story 4 — Continuar usando o chat durante processamentos em segundo plano (Priority: P2)

**Goal**: manter vários turnos em processamento enquanto a pessoa troca de conversa e entregar respostas fora de ordem ao contato correto.

**Independent Test**: enviar mensagens para três contatos antes das respostas e navegar entre eles enquanto cada resposta chega no destino correto.

### Tests for User Story 4

- [x] T036 [P] [US4] Escrever testes de ciclo de vida do polling, cancelamento apenas de contatos ociosos e respostas fora de ordem em `poc-chat/src/state/conversationTracker.concurrent.test.ts`
- [x] T037 [P] [US4] Escrever teste de não lido para conversa inativa e ausência de falso não lido na conversa aberta em `poc-chat/src/App.concurrent.test.tsx`

### Implementation for User Story 4

- [x] T038 [US4] Implementar registry de trackers por contato e limpeza segura no unmount em `poc-chat/src/state/conversationTracker.ts`
- [x] T039 [US4] Integrar estados de processamento independentes à lista e ao cabeçalho do chat em `poc-chat/src/App.tsx` e `poc-chat/src/components/ConversationList.tsx`

**Checkpoint**: nenhum contato pendente bloqueia a interface e nenhuma resposta fora de ordem altera outro histórico.

---

## Phase 7: User Story 5 — Compreender e recuperar falhas transitórias (Priority: P2)

**Goal**: diferenciar erro técnico de fala da Urba e permitir recuperação/retry idempotente.

**Independent Test**: provocar indisponibilidade antes/depois da aceitação, recuperar ou tentar novamente com o mesmo `eventId`, sem duplicar entrada ou saída.

### Tests for User Story 5

- [x] T040 [P] [US5] Escrever testes de `202`, `409`, timeout, `502`, resposta inválida, retry único e `eventId` estável em `poc-chat/src/state/conversationTracker.failure.test.ts`
- [x] T041 [P] [US5] Escrever teste de estados técnicos, botão de retry e ausência de mensagem inventada em `poc-chat/src/components/FailureState.test.tsx`
- [x] T042 [P] [US5] Escrever teste de encerramento do indicador em modo `HUMAN` e preservação de resposta canônica de contingência em `poc-chat/src/state/conversationReducer.handoff.test.ts`

### Implementation for User Story 5

- [x] T043 [US5] Implementar estados de erro técnico sanitizado, timeout e tentativa manual em `poc-chat/src/components/FailureState.tsx` e `poc-chat/src/components/ChatView.tsx`
- [x] T044 [US5] Finalizar retry idempotente, backoff e encerramento por `HUMAN` em `poc-chat/src/state/conversationTracker.ts` e `poc-chat/src/api/conversationClient.ts`
- [x] T045 [US5] Garantir que erros de transporte nunca sejam convertidos em mensagens `URBA` em `poc-chat/src/state/conversationReducer.ts` e `poc-chat/src/components/MessageBubble.tsx`

**Checkpoint**: falhas não inventam conteúdo conversacional, entradas originais permanecem visíveis e retries não geram efeitos duplicados.

---

## Phase 8: Container, proxy e integração Compose

**Purpose**: servir a SPA localmente com proxy mínimo, token somente no container e hardening operacional.

- [x] T046 [P] Escrever testes/fixtures de allowlist, substituição de `Authorization`, bloqueio de `flush`, métricas, ferramentas e pagamento em `poc-chat/nginx/nginx.contract.test.ts`
- [x] T047 [P] Criar template de configuração com proxy same-origin e tmpfs de segredo em `poc-chat/nginx/default.conf.template`
- [x] T048 Implementar configuração estática, headers, healthcheck e rotas bloqueadas em `poc-chat/nginx/nginx.conf`
- [x] T049 Implementar build multi-stage Node 24 + Nginx sem privilégios em `poc-chat/Dockerfile` e `poc-chat/docker-entrypoint.d/10-poc-token.sh`
- [x] T050 Integrar serviço `poc-chat` somente ao profile/Compose local em `hermes/docker-compose.poc.yml`, com rede mínima, bind `127.0.0.1:${POC_CHAT_HOST_PORT:-3000}:8080`, read-only filesystem, capabilities removidas e `no-new-privileges`
- [x] T051 [P] Criar healthcheck e validações de container em `poc-chat/container.test.sh`, incluindo `nginx -t`, ausência de CORS e bloqueio das rotas fora da allowlist

**Checkpoint**: `docker compose ... config --quiet`, build, healthcheck, `nginx -t` e rotas permitidas/bloqueadas passam sem expor o token.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: validar a entrega completa contra a spec, quickstart e regressões do core Hermes-first.

- [x] T052 [P] Escrever os cinco cenários determinísticos de navegador US1–US5 em `poc-chat/e2e/manual-chat.spec.ts`
- [x] T053 [P] Adicionar teste de segurança que inspeciona bundle, localStorage e requisições do browser em `poc-chat/e2e/privacy.spec.ts`
- [x] T054 Atualizar instruções de inicialização, smoke manual, falhas e troubleshooting em `specs/002-poc-manual-chat/quickstart.md`
- [x] T055 [P] Executar e corrigir `npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run coverage`, `npm run build` e `npm run test:e2e` dentro de `poc-chat/`
- [x] T056 Executar validação do Compose e do serviço local conforme `specs/002-poc-manual-chat/quickstart.md`, sem imprimir `.env.poc` ou segredos
- [x] T057 Executar os gates de regressão `./gradlew check --offline --no-daemon --console=plain`, `python3 -m unittest discover -s hermes/plugins/urbana-domain -p 'test*.py'`, `./hermes/scripts/smoke-contract.sh`, `./hermes/scripts/smoke-isolation.sh` e `./hermes/scripts/verify-tool-surface.sh`
- [x] T058 Executar smoke live Browser → poc-chat → Urbana Connect → Hermes → MongoDB → browser com pelo menos três contatos, fragmentação, troca, reload e isolamento
- [x] T059 Inspecionar diff final, bundle, armazenamento e tráfego para confirmar FR-001–FR-022, ausência de alteração no webhook/produção e ausência de credenciais
- [x] T060 Atualizar a subtask PEE-101 com branch, resumo, arquivos, testes, critérios cobertos e riscos; preparar PR para `hml` sem fazer deploy

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: pode iniciar imediatamente; T001–T005 são pré-requisitos locais.
- **Foundational (Phase 2)**: depende de Setup e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende de T006–T015 e é o MVP funcional.
- **US2 e US3 (Phases 4–5)**: dependem de US1; podem compartilhar o mesmo estado, mas tarefas com arquivos sobrepostos são sequenciais.
- **US4 e US5 (Phases 6–7)**: dependem do tracker e da UI de US1–US3.
- **Container (Phase 8)**: pode preparar proxy em paralelo após os contratos, mas o compose final depende do bundle estável.
- **Polish (Phase 9)**: depende de todas as histórias e do container.

### Parallel execution examples

- T003–T005 podem rodar em paralelo após T001–T002.
- T006–T007, T009, T011, T013 e T015 são leituras/testes em arquivos distintos e podem ser preparados em paralelo.
- T016–T018 são testes independentes de componentes.
- T024–T026, T031–T032, T036–T037, T040–T042 e T046–T047 podem ser escritos em paralelo dentro de suas fases.
- T052–T053 são testes de navegador independentes.

### Agent ownership

- **Explorer**: somente leitura dos contratos existentes, Compose, scripts e riscos; não escreve código.
- **Developer**: único escritor da implementação em `poc-chat/`, `hermes/docker-compose.poc.yml`, scripts e documentação da spec, executando T001–T060 em ordem e preservando alterações preexistentes.
- **QA Tester**: após o Developer parar, executa T055–T059 de forma independente, registra falhas e não aceita a própria implementação.
- **Tech Lead Orchestrator**: mantém Jira, branch, tasks.md, decisões de escopo, integração, correções e aceitação final.

## Implementation Strategy

1. Fechar Setup + Foundational com testes determinísticos.
2. Entregar US1 como MVP verificável.
3. Adicionar isolamento/múltiplos contatos e reload.
4. Adicionar processamento concorrente e falhas.
5. Integrar Nginx/Compose e executar todos os gates.
6. Só classificar como `verified` depois do smoke live completo e da revisão independente do QA.
