# Implementation Plan: Fechamento da POC Hermes e higiene de release

**Branch**: `feat/pee-101` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)
**Input**: fechar a primeira etapa local e preparar um commit seguro e auditável.

## Summary

Separar o conjunto versionável do estado local, corrigir a inicialização do
workflow frontend e tornar os sinais de prontidão da POC coerentes com as
dependências obrigatórias. A implementação deve ser incremental, com contratos
falhando antes das correções e com quarentena reversível para cópias ambíguas.

O escopo mantém a POC Hermes-first intacta. O caminho legado do webhook real
continua explicitamente fora desta feature e será tratado em uma migração
posterior.

## Technical Context

**Language/Version**: Java 21 LTS, TypeScript/Node 24, Bash, Ruby
**Primary Dependencies**: Spring Boot 3.4.13, Gradle 8.x, Docker Compose,
GitHub Actions, Vitest, Playwright, Hermes Sessions API
**Storage**: MongoDB para a POC; volumes locais não são alterados
**Testing**: JUnit/Gradle/JaCoCo, Vitest/RTL, typecheck, lint, build Docker,
scripts Hermes, corpus Ruby, Playwright e contratos shell
**Target Platform**: macOS local com Docker Desktop e CI Ubuntu; sem deploy
**Project Type**: monorepo com duas aplicações, integração externa e infra local
**Performance Goals**: não aumentar o tempo de resposta do chat; readiness deve
  responder em até 5 segundos quando Mongo estiver saudável
**Constraints**: preservar worktree, não expor secrets, não mudar contratos,
  não apagar conteúdo ambiguamente, manter branch `feat/pee-101`
**Scale/Scope**: fechamento de POC local e preparação de um commit; não é a
  migração do webhook WhatsApp.

## Constitution Check

| Gate | Resultado | Evidência/ação |
|---|---|---|
| Preserve user changes | PASS WITH CONTROL | Cópias divergentes serão comparadas e quarentenadas reversivelmente; staging será seletivo. |
| Secrets outside Git | PASS | `.env.poc` continua ignorado; scanner por nomes e templates será executado sem imprimir valores. |
| Test-first | PASS | Contrato de release e testes de readiness/CI serão criados ou ajustados antes da implementação. |
| Clean Architecture | PASS | Health/readiness permanece em interface/configuração apropriada; não há nova regra de negócio. |
| No production change | PASS | Webhook, Gemini, WhatsApp, manifests de produção e credenciais ficam fora do escopo. |
| Quality gates | PASS WITH RISK | O build nativo macOS do Vite pode continuar limitado pelo binding iCloud; build Docker/CI é a evidência suportada. |

## Project Structure

```text
specs/006-stage1-release-hardening/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/release-boundary.md
├── checklists/requirements.md
└── tasks.md

quality/system-e2e/release-boundary.contract.sh
.github/workflows/build-test.yml
infra/local-poc/docker-compose.poc.yml
apps/urbana-connect-api/src/main/resources/application-poc.yml
apps/urbana-connect-api/src/test/java/br/com/urbana/connect/interfaces/rest/HealthControllerTest.java
```

## Migration Strategy

1. Registrar o estado do worktree e criar o contrato de release em modo
   test-first, sem tocar em secrets ou dados Docker.
2. Comparar duplicatas e mover somente artefatos locais confirmados para uma
   quarentena ignorada; preservar cópias divergentes até sua classificação.
3. Corrigir `.gitignore`, documentação e status das specs sem esconder código
   canônico.
4. Corrigir SHAs do frontend, contrato de readiness e dependências de saúde do
   Compose.
5. Executar a matriz de testes e repetir o contrato de pass-through.
6. Fazer QA independente do índice e do worktree.
7. Preparar staging seletivo, revisar o diff staged, criar commit em
   `feat/pee-101` e confirmar que o commit contém o conjunto completo da
   etapa, sem fazer push automaticamente.

## Design Decisions

- A prontidão operacional canônica da POC será `/api/v1/readiness`, pois já
  valida o estado de aceitação da aplicação e o ping Mongo. O Actuator não
  deve falhar por SMTP opcional; a configuração POC desabilitará o indicador de
  mail ou separará esse indicador do grupo obrigatório, conforme o teste
  primeiro demonstrar.
- O chat dependerá de `urbana-connect` saudável no Compose, sem criar uma
  dependência circular de Hermes: a API deve iniciar antes e recuperar turnos
  enquanto Hermes sobe.
- Cópias ` 2` não serão automaticamente assumidas como fonte. Arquivos
  idênticos serão removidos do conjunto staged; divergentes irão para
  `.codex/quarantine/006-stage1-release-hardening/`, que permanecerá fora do
  Git.
- O caminho webhook/Gemini permanece compilável e documentado como legado;
  removê-lo agora aumentaria o escopo e poderia quebrar a entrega real já
  existente.

## Test Plan

### Before implementation

- contrato de release deve falhar com os SHAs inválidos, duplicatas e ausência
  de readiness no Compose;
- `git diff --check` e baseline sem expor `.env.poc`;
- testes atuais do backend/frontend servem como baseline.

### After implementation

- contrato shell de fronteira e `git diff --check`;
- validação dos SHAs via refs remotas sem alterar o repositório;
- Gradle/Java e HealthController tests;
- `docker compose config`, `up` já disponível e readiness HTTP;
- Vitest/typecheck/lint/build Docker/Playwright;
- scripts Hermes, plugin/profile, corpus e round-trip literal;
- revisão staged independente e inspeção final do commit.

## Risks and Mitigations

| Risk | Mitigation | Exit classification |
|---|---|---|
| Duplicata divergente contém trabalho útil | comparar, registrar e quarentenar sem apagar | residual documentado |
| Staging parcial omite backend | contrato verifica destino canônico e revisão `git diff --cached` | bloqueia commit |
| SHA inválido quebra CI | validar refs e corrigir antes do commit | bloqueia aceite |
| SMTP opcional torna Actuator DOWN | separar indicador opcional da readiness POC | bloqueia health gate |
| Docker indisponível | executar estático e classificar live como unverified | não simular |
| Confundir webhook com POC | documentação e teste de fronteira explicitam os dois fluxos | fora de escopo |

## Commit Gate

O commit só pode ser criado quando:

- todas as tarefas estiverem marcadas com evidência;
- QA independente aprovar;
- `git diff --cached --check` passar;
- nenhum arquivo proibido estiver staged;
- o diff staged contiver os destinos canônicos completos;
- os testes relevantes estiverem registrados em `tasks.md`.
