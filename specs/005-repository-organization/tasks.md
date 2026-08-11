# Tasks: Organização do monorepo Urbana Connect

**Input**: Design documents de `specs/005-repository-organization/`
**Branch de execução**: `feat/pee-101`
**Status**: 39/39 tarefas concluídas; resultado `verified` na branch `feat/pee-101`

## Fases e dependências

- Fase 1 fixa a branch, o baseline e os contratos antes de qualquer movimento.
- Fase 2 cria validadores e a árvore-alvo sem mover código de aplicação.
- Fase 3 move as aplicações e atualiza seus próprios caminhos.
- Fase 4 separa integração Hermes, infraestrutura local e qualidade.
- Fase 5 atualiza referências transversais, documentação e CI.
- Fase 6 executa a matriz de validação, QA e registra exceções.

Não há paralelismo entre tarefas que movem ou referenciam o mesmo conjunto de
arquivos. Delegações devem manter um escritor por área: backend, chat e
integração/infra; documentação/CI permanece serial na thread principal.

## Phase 1: Baseline, contrato e preparação

- [x] T001 Registrar em `specs/005-repository-organization/` a branch real,
  estado do worktree, árvore atual, arquivos gerados/ambíguos e riscos sem
  imprimir `.env.poc`.
- [x] T002 [P] Confirmar que `.env.poc` está ignorado, com permissão local
  restrita, e que nenhum secret aparece no diff ou nos artefatos da spec.
- [x] T003 [P] Catalogar referências a `app/`, `poc-chat/`, `hermes/`,
  `corpus/` e `infra/k8s/` em scripts, Compose, Dockerfiles, workflows,
  Dependabot, CODEOWNERS, README e specs.
- [x] T004 Criar teste/validador estrutural que descreva a árvore alvo,
  ownership e ausência de dependências browser → Hermes/Mongo antes da
  migração, em `quality/system-e2e/` ou script de contrato equivalente.
- [x] T005 [P] Registrar o mapa de contratos e topologia preservados em
  `specs/005-repository-organization/contracts/repository-boundaries.md` e
  confrontá-lo com o Compose atual sem expor valores interpolados.

## Phase 2: Árvore-alvo e documentação-base

- [x] T006 Criar os diretórios canônicos `apps/`, `integrations/`,
  `infra/local-poc/`, `quality/` e `contracts/` somente onde necessários, sem
  mover ainda os arquivos de runtime.
- [x] T007 [P] Criar README de ownership em `apps/urbana-connect-api/README.md`
  e `apps/poc-chat/README.md` depois da movimentação de cada aplicação.
- [x] T008 [P] Criar `integrations/hermes-agent/README.md` e
  `infra/local-poc/README.md` explicando que Hermes upstream é externo e que
  `.env.poc` não é versionado.

## Phase 3: User Story 1 — Aplicações próprias explícitas (P1)

**Objetivo**: backend e chat ficam sob `apps/`, com seus comandos internos
funcionando nos paths novos.

### Testes antes da movimentação

- [x] T009 [P] [US1] Ajustar/criar teste de contrato dos manifests do backend
  para localizar Gradle, Dockerfiles, recursos e testes sob
  `apps/urbana-connect-api/`.
- [x] T010 [P] [US1] Ajustar/criar teste de contrato do chat para localizar
  `package.json`, Vite, Nginx, testes e Dockerfile sob `apps/poc-chat/`.

### Implementação

- [x] T011 [US1] Mover `app/` para `apps/urbana-connect-api/` preservando
  conteúdo, alterações não commitadas, wrapper Gradle, Dockerfiles, recursos e
  testes; não alterar comportamento Java.
- [x] T012 [US1] Corrigir apenas referências internas do backend que dependam do
  path raiz antigo, mantendo a separação Clean Architecture.
- [x] T013 [US1] Mover `poc-chat/` para `apps/poc-chat/` preservando código,
  testes rápidos, E2E local, Nginx, Dockerfile, lockfile e alterações existentes.
- [x] T014 [US1] Corrigir referências internas do chat, scripts de container e
  testes de contrato para funcionarem sob `apps/poc-chat/`.
- [x] T015 [US1] Atualizar os READMEs das duas aplicações com comandos de
  instalação, teste, typecheck/lint/build e limites de dependência.

**Checkpoint US1**: os manifests das duas aplicações são encontrados pelos
validadores e seus comandos locais não dependem de `app/` ou `poc-chat/` na
raiz.

## Phase 4: User Stories 2 e 3 — Hermes e runtime local separados (P1)

**Objetivo**: profile/plugin/scripts ficam em `integrations/`; Compose/proxies
ficam em `infra/local-poc/`, mantendo o runtime externo e a topologia.

### Testes antes da movimentação

- [x] T016 [P] [US2] Criar teste/checagem de resolução de paths para scripts
  Hermes executados da raiz, de `integrations/hermes-agent/` e de um diretório
  corrente arbitrário.
- [x] T017 [P] [US2] Criar teste de contrato do Compose que verifique contexts,
  mounts, nomes de serviços, redes, volumes, portas e healthchecks sem comparar
  secrets.
- [x] T018 [P] [US3] Criar teste de ownership que falhe se fonte do upstream
  Hermes for adicionada em `integrations/hermes-agent/`.

### Implementação

- [x] T019 [US3] Mover `hermes/profile/`, `hermes/plugins/`, pin/documentação
  local e `hermes/scripts/` para `integrations/hermes-agent/`, preservando
  scripts e arquivos ambíguos sem apagar cópias.
- [x] T020 [US3] Atualizar scripts Hermes para resolver a raiz do repositório e
  os novos paths, mantendo pin, imagem, isolamento de rede e comportamento de
  instalação/validação.
- [x] T021 [US2] Mover Compose da POC, proxies e configuração operacional de
  `hermes/` para `infra/local-poc/`, sem mover `.env.poc` real nem volumes.
- [x] T022 [US2] Atualizar contexts e mounts do Compose para as aplicações e a
  integração nos novos locais, mantendo serviços, redes, portas, volumes,
  healthchecks e dependências.
- [x] T023 [US2] Atualizar `infra/local-poc/README.md` e scripts de operação com
  o único quickstart local e comandos sanitizados.

**Checkpoint US2/US3**: Compose e scripts encontram os componentes pelo novo
mapa e a separação local/upstream do Hermes é explícita.

## Phase 5: User Story 4 — Qualidade, contratos e referências transversais (P2)

**Objetivo**: corpus, E2E cross-system, CI e documentação refletem a estrutura
alvo sem criar uma segunda fonte de verdade.

- [x] T024 [US4] Mover `corpus/` para `quality/conversation-corpus/`, preservando
  cenários, fixtures, schema, runner e resultados ignorados; corrigir paths
  internos e comandos Ruby.
- [x] T025 [US4] Classificar `apps/poc-chat/e2e/live-chat.spec.ts` como E2E
  pertencente à aplicação por depender do `playwright.config.ts` e do pacote
  Playwright do chat; manter o validador estrutural cross-system em
  `quality/system-e2e/` e documentar a decisão, sem duplicar o cenário.
- [x] T026 [US4] Criar/atualizar contratos em `contracts/` somente para
  fronteiras compartilhadas e apontar specs 001–004 para suas fontes
  canônicas, sem reescrever contratos de produto.
- [x] T027 [US4] Atualizar `.gitignore` para os novos paths de artefatos e
  confirmar que caches, build, coverage, resultados E2E e `.env.poc` continuam
  fora do Git.
- [x] T028 [US4] Atualizar README raiz, `docs/`, quickstarts e referências de
  specs para explicar ownership, comandos e árvore nova.
- [x] T029 [US4] Atualizar `.github/workflows/`, Dependabot e CODEOWNERS para
  backend, chat, quality e infra nos paths novos, sem mudar gatilhos de deploy
  além do necessário para localizar arquivos.
- [x] T030 [US4] Classificar `app/docker-compose.yml`, `app/dev-env.sh`, cópias
  com sufixo ` 2`, `.codex/quarantine` e demais artefatos ambíguos; preservar
  conteúdo e registrar o que não pode ser removido nesta entrega.

**Checkpoint US4**: busca de referências antigas retorna somente histórico,
compatibilidade documentada ou a própria spec; nenhum comando canônico aponta
para uma fonte inexistente.

## Phase 6: Verificação, QA e encerramento

- [x] T031 Executar `git diff --check`, validação da árvore/ownership e busca
  sanitizada de paths antigos; corrigir falhas em até duas tentativas por causa.
- [x] T032 Executar a suíte Java/JaCoCo a partir de
  `apps/urbana-connect-api/`, registrando separadamente o efeito da duplicata
  preexistente `PocReceptionWorker 2.java`, se ainda existir.
- [x] T033 Executar testes, typecheck, lint e build de `apps/poc-chat/`.
- [x] T034 Executar scripts de instalação/contrato/isolamento/superfície e
  validação do profile a partir de `integrations/hermes-agent/`.
- [x] T035 Executar self-test/corpus e as jornadas E2E determinísticas a partir
  de `quality/`.
- [x] T036 Validar `docker compose config` de forma sanitizada e, com runtime
  disponível, subir o stack local e verificar healthchecks sem remover volumes
  ou imagens.
- [x] T037 Executar prova E2E do fluxo Hermes → Mongo → API → UI, comparando a
  resposta textual por igualdade literal e confirmando mais de uma conversa.
- [x] T038 [P] Fazer QA independente da estrutura final, ownership, contratos,
  secrets, regressões e riscos residuais; não aceitar apenas a existência dos
  arquivos.
- [x] T039 Atualizar `tasks.md`, checklist e quickstart somente com evidências
  obtidas; classificar o resultado final como `verified`,
  `implemented_unverified` ou `blocked` conforme os gates efetivamente
  executados.

## Estratégia de execução e delegação

1. A thread principal mantém a spec, branch, baseline, referências transversais
   e decisão de aceite.
2. Um Developer escreve somente no conjunto do backend (`app/` →
   `apps/urbana-connect-api/`).
3. Um Developer escreve somente no conjunto do chat (`poc-chat/` →
   `apps/poc-chat/`).
4. Um Developer/Staff escreve somente no conjunto Hermes + infra local, depois
   que os paths das aplicações estiverem estáveis.
5. A thread principal atualiza raiz, CI, docs, quality e tasks serialmente.
6. QA só inicia após os escritores pararem; qualquer correção retorna ao
   escritor original quando o contexto for útil.

## Dependências principais

```text
T001–T005 -> T006–T010 -> T011–T015 -> T016–T023
                                   └──> T024–T030 -> T031–T039
```

As tarefas marcadas `[P]` podem ser paralelas somente quando seus arquivos não
se sobrepõem e não dependem de uma movimentação ainda em curso.

## Evidências registradas

- Branch confirmada: `feat/pee-101`; alterações preexistentes preservadas e
  `.env.poc` mantido na raiz, ignorado e sem conteúdo exposto.
- A validação estrutural falhou antes da migração com referências antigas e
  passou depois com `quality/system-e2e/repository-structure.contract.sh`.
- A árvore final contém `apps/urbana-connect-api/`, `apps/poc-chat/`,
  `integrations/hermes-agent/`, `infra/local-poc/`, `infra/kubernetes/`,
  `quality/conversation-corpus/`, `quality/system-e2e/` e `contracts/`; os
  diretórios legados de topo não permanecem como fontes canônicas.
- `git diff --check`, `docker compose ... -f infra/local-poc/docker-compose.poc.yml
  config --quiet`, sintaxe Bash e a resolução do smoke Hermes a partir de
  `/tmp` passaram.
- Backend: Gradle com JDK 21 explícito passou; XML registrou 337 testes, 0
  falhas, 0 erros e 0 skips. A duplicata preexistente
  `PocReceptionWorker 2.java` foi preservada e excluída apenas por init script
  temporário fora do repositório.
- Chat: Vitest passou com 19 arquivos/66 testes; typecheck e lint passaram;
  build Linux dentro do container Docker passou. O build nativo macOS ficou
  limitado pelo binding opcional `lightningcss` local materializado como
  placeholder iCloud; não houve alteração de código ou lockfile para mascarar
  esse problema.
- Hermes/qualidade: instalação local, smoke de contrato, isolamento,
  superfície de ferramentas, testes do plugin, corpus Ruby e Playwright
  determinístico passaram; o cenário live Playwright passou com três contatos
  e persistência/reload.
- A prova literal Hermes → Mongo → API → UI passou em uma sessão nova: a
  resposta outbound observada na história Hermes, no Mongo e na projeção HTTP
  teve o mesmo comprimento e SHA-256; a UI live também concluiu o fluxo.
- QA independente concluiu `verified`: contrato estrutural, `git diff --check`,
  `.env.poc`, referências proibidas e raízes canônicas passaram sem alterar
  arquivos. O aviso `bad substitution` do SDKMAN foi ambiental e não afetou os
  códigos de saída.
- Resultado desta entrega: `verified` para a reorganização estrutural e os
  fluxos suportados pelo runtime. Resta apenas o risco ambiental classificado
  de o build nativo macOS do Vite depender de um binding opcional
  `lightningcss` materializado como placeholder iCloud; o build Linux da
  imagem Docker passou e não houve alteração de código/lockfile para ocultar a
  limitação.
