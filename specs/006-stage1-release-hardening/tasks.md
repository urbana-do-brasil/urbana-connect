# Tasks: Fechamento da POC Hermes e higiene de release

**Input**: design documents de `specs/006-stage1-release-hardening/`
**Branch de execução**: `feat/pee-101`
**Status**: 31/31 tarefas concluídas; resultado `verified` na branch `feat/pee-101`

## Regras de execução

- Preservar alterações existentes; não usar reset, checkout destrutivo ou
  `git add -A`.
- Um escritor por conjunto: higiene/index, CI/Compose/health e documentação
  devem ser delimitados; QA só começa depois dos escritores.
- Testes/contratos novos devem falhar antes da implementação quando aplicável.
- Quarentena é reversível e ignorada; nenhum conteúdo ambíguo é apagado.
- O commit é responsabilidade da thread principal após QA e diff staged.

## Phase 1: Setup e contratos (bloqueante)

- [x] T001 Registrar branch, contagem de status, baseline de arquivos proibidos e
  estado dos serviços em `specs/006-stage1-release-hardening/`, sem imprimir
  `.env.poc`.
- [x] T002 [P] Criar `quality/system-e2e/release-boundary.contract.sh` com
  checks para secrets, `.codex`, duplicatas, destinos canônicos, workflow e
  Compose; executar contra o estado atual e registrar falhas esperadas.
- [x] T003 [P] Criar o inventário/classificação de artefatos em
  `specs/006-stage1-release-hardening/contracts/release-boundary.md` e
  `research.md`, incluindo planos pessoais e arquivos legados.
- [x] T004 Validar a spec, plano, contratos, checklist e quickstart contra os
  critérios da feature antes de delegar implementação.

## Phase 2: US1 — Conjunto seguro para versionar (P1)

**Objetivo**: deixar o source set canônico completo sem destruir alterações.

- [x] T005 [P] [US1] Escrever/ajustar testes de comparação e classificação de
  duplicatas em `quality/system-e2e/release-boundary.contract.sh` antes da
  quarentena.
- [x] T006 [US1] Comparar todas as cópias ` 2` e mover somente artefatos locais
  confirmados para `.codex/quarantine/006-stage1-release-hardening/`, mantendo
  um inventário reversível e sem versionar a quarentena.
- [x] T007 [US1] Corrigir `.gitignore` para excluir `.codex`, quarentena e
  artefatos locais sem mascarar fontes canônicas em `.gitignore`.
- [x] T008 [US1] Garantir que `apps/urbana-connect-api/`, `apps/poc-chat/`,
  `integrations/`, `infra/`, `quality/`, `contracts/`, docs e specs apareçam
  completos no conjunto de staging, sem deleções acidentais.
- [x] T009 [US1] Classificar `app/docker-compose.yml`, `app/dev-env.sh`,
  `ConversationFlowService`, Gemini e políticas conversacionais como legado
  fora da POC, documentando a fronteira em `README.md` e na spec 006.

## Phase 3: US2 — CI e runtime confiáveis (P1)

**Objetivo**: CI inicia e os sinais locais refletem dependências obrigatórias.

- [x] T010 [P] [US2] Criar teste/contrato que valide SHAs de Actions e
  caminhos do workflow em `quality/system-e2e/release-boundary.contract.sh`.
- [x] T011 [US2] Corrigir os SHAs inválidos do job frontend em
  `.github/workflows/build-test.yml` e validar cada ref remotamente.
- [x] T012 [P] [US2] Escrever teste de readiness/health em
  `apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/HealthControllerTest.java`
  para Mongo obrigatório e SMTP opcional.
- [x] T013 [US2] Ajustar `apps/urbana-connect-api/src/main/resources/application-poc.yml`
  ou configuração equivalente para que o healthcheck obrigatório da POC não
  dependa de SMTP ausente.
- [x] T014 [US2] Adicionar healthcheck da API e trocar a dependência do chat para
  `service_healthy` em `infra/local-poc/docker-compose.poc.yml`, preservando a
  topologia e evitando dependência circular com Hermes.
- [x] T015 [US2] Validar Compose sanitizado, readiness HTTP, health do chat e
  comportamento de inicialização/reconciliação com Hermes atrasado.

## Phase 4: US3 — Regressão Hermes-first (P1)

**Objetivo**: comprovar que o fechamento não muda o comportamento aprovado.

- [x] T016 [P] [US3] Executar testes Java/JaCoCo com o JDK 21 e registrar o
  tratamento da duplicata preexistente sem incluí-la no source set.
- [x] T017 [P] [US3] Executar testes, typecheck, lint e build Docker de
  `apps/poc-chat/`.
- [x] T018 [P] [US3] Executar instalação, contrato, isolamento, superfície,
  plugin e profile Hermes em `integrations/hermes-agent/`.
- [x] T019 [P] [US3] Executar corpus Ruby e Playwright determinístico/live,
  preservando resultados fora do Git.
- [x] T020 [US3] Reexecutar prova de igualdade literal Hermes → Mongo → API → UI
  em sessão nova e registrar somente hashes/tamanhos sanitizados.

## Phase 5: US4 — Rastreabilidade (P2)

**Objetivo**: documentação não promete uma integração de produção inexistente.

- [x] T021 [P] [US4] Atualizar status/branch/evidências das specs 001–006 e
  seus quickstarts sem alterar o histórico do produto.
- [x] T022 [P] [US4] Atualizar `README.md`, `docs/` e `infra/local-poc/README.md`
  com a diferença entre `/api/poc/conversations` e `/api/webhook`.
- [x] T023 [US4] Atualizar checklist e tasks com evidências reais, riscos e o
  próximo ticket recomendado para WhatsApp → Hermes.

## Phase 6: QA, staging e commit

- [x] T024 Executar busca final por secrets, arquivos proibidos, paths antigos,
  duplicatas e artefatos gerados sem imprimir valores.
- [x] T025 Fazer QA independente somente leitura da árvore, diff, contratos,
  CI, Compose, saúde e regressões; não aceitar apenas arquivos existentes.
- [x] T026 Corrigir achados QA em até duas tentativas por causa, preservando o
  escritor original quando o escopo continuar delimitado.
- [x] T027 Preparar staging seletivo dos arquivos canônicos e executar
  `git diff --cached --check`, `git diff --cached --stat` e inspeção de nomes.
- [x] T028 Confirmar que nenhum secret, `.codex`, duplicata, resultado ou plano
  pessoal está staged.
- [x] T029 Marcar tasks/checklist somente após todas as evidências e registrar o
  status final como `verified`, `implemented_unverified` ou `blocked`.
- [x] T030 Criar o commit `chore(pee-101): fechar POC Hermes e higiene de release`
  na branch `feat/pee-101`, sem push automático.
- [x] T031 Verificar `git show --stat --check HEAD`, branch, commit e ausência de
  alterações acidentais no conjunto versionado; documentar qualquer resíduo
  local preservado fora do commit.

## Dependências

```text
T001–T004 -> T005–T009 -> T010–T015 -> T016–T020 -> T021–T023
                                                        -> T024–T031
```

Escritores independentes: T006–T009 (higiene/index), T011–T015
(CI/Compose/health) e T021–T023 (documentação). QA T025 só inicia depois de
todos os escritores.

## Estratégia de implementação

1. Fazer os contratos falharem no estado atual.
2. Corrigir primeiro a fronteira de versionamento e os sinais operacionais.
3. Reexecutar a matriz Hermes-first.
4. Fazer QA independente.
5. Staging seletivo, commit e verificação do commit.

## Evidências registradas

- Branch: `feat/pee-101`; o baseline inicial tinha renomes parciais staged,
  destinos canônicos untracked, artefatos locais e 15 cópias ` 2`.
- Higiene: 15 cópias comparadas; 11 idênticas e 4 divergentes preservadas em
  `.codex/quarantine/006-stage1-release-hardening/`, fora do Git. O contrato
  `release-boundary.contract.sh` e o contrato de estrutura passaram.
- CI/runtime: SHAs do checkout/setup-node validados remotamente; o teste de
  health passou; `install-local.sh`, `docker compose config --quiet`,
  readiness `READY`, Actuator `UP` e health do chat passaram.
- Java: `clean test jacocoTestReport bootJar` passou com JDK 21, 338 testes,
  zero falhas, zero erros e zero skips.
- Frontend: 66 testes Vitest, typecheck, lint, build Vite e build Docker Linux
  passaram. Playwright determinístico passou com 7 testes; o live smoke passou
  com uma sessão nova e três conversas isoladas.
- Hermes/qualidade: smoke de contrato, isolamento, superfície de ferramentas,
  plugin Python e profile passaram; corpus Ruby passou com 18 cenários, 92
  assertions, zero falhas/erros/skips.
- Round-trip literal: sessão nova concluída com `COMPLETED`; resposta de 96
  caracteres e SHA-256 `b6e1bf4e636885b74bb51840a0942c5c6acbccb0c3c3c457f3a16452f8bc00c2`
  iguais em API, Mongo, histórico Hermes e UI.
- Residual classificado: `/api/webhook` e `ConversationFlowService`/Gemini
  continuam legado fora da POC; a migração WhatsApp → Hermes é o próximo
  ticket. O volume Mongo antigo foi preservado; o E2E foi isolado em projeto
  Compose temporário para não misturar backlog local.
- Commit gate: QA independente aprovou o índice de 456 paths; T030 e T031 foram
  concluídas após a criação e verificação do commit final.
- Commit final verificado em `HEAD` na branch `feat/pee-101`, sem push automático.
