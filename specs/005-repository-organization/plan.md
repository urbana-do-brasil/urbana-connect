# Implementation Plan: Organização do monorepo Urbana Connect

**Branch**: `feat/pee-101`
**Date**: 2026-08-11
**Spec**: [spec.md](./spec.md)
**Ticket**: PEE-101

## Summary

Reorganizar o monorepo em limites explícitos de aplicações, integração,
infraestrutura e qualidade, preservando o comportamento e a topologia local já
validados. A migração será feita por movimentações reversíveis, atualização
centralizada de referências e validação estrutural/funcional após cada grupo.

O desenho distingue duas aplicações mantidas pela Urbana — backend Java e
`poc-chat` React — do material de integração/profile do Hermes e da composição
local que executa o runtime externo. O código upstream do Hermes não será
copiado para o repositório.

## Contexto técnico

**Linguagens/versões**: Java 21 LTS, TypeScript 5.x, React 19.2, Node 24 LTS,
Ruby para o runner do corpus
**Dependências principais**: Spring Boot 3.4.13, Gradle 8.x, Vite 8, Vitest,
React Testing Library, Playwright, Docker Compose, MongoDB, Hermes Sessions API
**Persistência**: MongoDB como transcript/projeção canônica da Urbana;
SQLite interno do runtime Hermes permanece externo
**Testes**: JUnit/Gradle, Vitest/RTL, typecheck/lint/build, Playwright,
scripts Hermes, validação do profile, corpus Ruby e smoke local
**Plataformas**: execução local via Docker Desktop/Compose; backend também
possui manifests Kubernetes de homologação
**Tipo de projeto**: monorepo com duas aplicações próprias, integração externa
e infraestrutura de desenvolvimento
**Restrições**: não alterar comportamento, contratos HTTP, credenciais, dados,
portas, redes ou volumes; preservar worktree e arquivos ambíguos; manter
quality gate Java existente
**Escopo**: reorganização física e de documentação/referências, sem feature de
produto

## Constitution Check

| Gate | Resultado | Evidência/ação |
|---|---|---|
| Stack oficial | PASS | Nenhuma troca de stack; paths e manifests serão apenas reposicionados. |
| Clean Architecture | PASS | O backend mantém `domain`, `application`, `infrastructure` e `interfaces` dentro da aplicação. |
| Specification-first / test-first | PASS | Esta spec e os testes/validadores de paths precedem a migração. |
| Quality gate | PASS WITH RISK | Os gates existentes serão executados; a duplicata `PocReceptionWorker 2.java` é risco preexistente e será preservada/classificada. |
| Homolog-first | NOT APPLICABLE TO THIS LOCAL REORG | Não há deploy nesta tarefa; qualquer validação de deploy continua fora do escopo. |
| Secrets fora do Git | PASS | `.env.poc` real permanece ignorado e nunca será impresso/movido como conteúdo rastreável. |

## Arquitetura e ownership alvo

```text
apps/urbana-connect-api/       Urbana Connect: Java, Spring, Gradle, testes
apps/poc-chat/                 ferramenta web local: React/Vite/Nginx/testes
integrations/hermes-agent/     profile, plugin, pin e scripts Hermes locais
infra/local-poc/               Compose, proxies, env example e operação local
infra/kubernetes/              Kustomize e deploy de homologação existente
quality/conversation-corpus/  cenários, fixtures, schema e runner Ruby
quality/system-e2e/            testes que exercitam mais de uma aplicação
contracts/                     contratos compartilhados e mapas de fronteira
docs/                          guias e decisões arquiteturais
specs/                         especificações e evidências de features
```

Ownership não implica acoplamento de runtime: `poc-chat` fala somente com a
API pública local da Urbana; a API fala com Hermes pela integração; o Compose
conecta os processos, mas não transforma a UI em cliente do Hermes.

## Estratégia de migração

1. Fixar a branch `feat/pee-101`, registrar o estado inicial e criar os
   artefatos desta spec sem tocar em alterações preexistentes.
2. Criar a árvore-alvo e mover o backend inteiro para
   `apps/urbana-connect-api/`.
3. Mover o chat inteiro para `apps/poc-chat/`, mantendo testes rápidos junto
   dele.
4. Separar os artefatos de Hermes: profile/plugin/scripts/pin sob
   `integrations/hermes-agent/`; Compose/proxies e operação local sob
   `infra/local-poc/`.
5. Mover o corpus para `quality/conversation-corpus/` e manter em
   `quality/system-e2e/` os contratos estruturais que verificam mais de uma
   área. O E2E de navegador permanece em `apps/poc-chat/e2e/` por depender da
   configuração e das dependências do próprio chat; resultados continuam como
   artefatos ignorados e nenhum dado persistido é movido.
6. Atualizar Dockerfiles, Compose, scripts, workflows, dependabot, CODEOWNERS,
   `.gitignore`, README e referências documentais com um inventário de paths
   antigos.
7. Adicionar validadores baratos de estrutura/contrato e executar a matriz de
   testes. Só então marcar as tarefas correspondentes como concluídas.

Movimentações de diretório devem ser feitas como `git mv` quando o arquivo já
for rastreado. Arquivos não rastreados, duplicatas e conteúdo ambíguo só podem
ser isolados se o conteúdo permanecer recuperável e a decisão ficar registrada.

## Contratos preservados

- HTTP do chat: `POST /api/poc/conversations/{alias}/messages` e
  `GET /api/poc/conversations/{alias}`.
- Fluxo de conteúdo: inbound persistido → Hermes Sessions API → outbound
  persistido → projeção HTTP/UI.
- Serviços/portas/redes/volumes do Compose local, salvo ajuste mecânico de
  caminho.
- Profile/plugin Hermes e seu pin de runtime.
- API e manifests Kubernetes existentes, com paths de build corrigidos.

O contrato detalhado de ownership e o mapa de dependências estão em
[contracts/repository-boundaries.md](./contracts/repository-boundaries.md).

## Plano de testes e validação

### Antes da migração

- capturar branch/status e inventário de arquivos sem imprimir secrets;
- validar que `.env.poc` está ignorado e que os diretórios gerados não são fonte
  canônica;
- criar ou ajustar testes de path/contrato que falhem para referências antigas;
- executar uma baseline proporcional das suítes já verdes, registrando o
  bloqueio da duplicata preexistente se aplicável.

### Depois da migração

- `git diff --check` e busca por referências canônicas a `../app`, `../poc-chat`,
  `hermes/docker-compose` e `infra/k8s`;
- Gradle test/Jacoco no novo path, com a exceção preexistente explicitamente
  classificada;
- `npm run test -- --run`, typecheck, lint e build em `apps/poc-chat`;
- scripts Hermes de contrato, isolamento, superfície/profile e instalação;
- `ruby self-test.rb` e execução do corpus;
- Playwright determinístico e, com Docker/credenciais disponíveis, E2E live em
  `apps/poc-chat/`; o contrato estrutural cross-system fica em
  `quality/system-e2e/`;
- Compose config/health/smoke e comparação Hermes → Mongo → HTTP → UI quando
  o stack local estiver ativo;
- revisão QA independente da árvore, referências, secrets, ownership e
  compatibilidade.

## Riscos e mitigação

| Risco | Mitigação | Classificação de saída |
|---|---|---|
| worktree já sujo | branch no mesmo commit, sem reset/clean; movimentos reversíveis | bloqueio somente se houver conflito de path insolúvel |
| scripts dependem da raiz antiga | resolver root pelo próprio script e centralizar variáveis de path | referência quebrada não pode ser aceita |
| Docker indisponível | executar validações estáticas e classificar E2E live como bloqueado | nunca simular sucesso |
| duplicatas ` 2` | inventariar, não apagar; isolar apenas com evidência e registro | risco residual até revisão humana |
| mudança acidental da topologia | snapshot de nomes/portas/redes/volumes e `docker compose config` sem secrets | falha de aceite |
| upstream Hermes confundido com código local | separar integração de runtime externo e manter pin | falha de ownership |

## Complexidade e exceções

| Exceção | Justificativa | Alternativa rejeitada |
|---|---|---|
| Diretório de spec `005-...` em branch `feat/pee-101` | branch é contrato explícito do ticket e o Speckit legado exige prefixo numérico | renomear a branch quebraria o pedido; usar `SPECIFY_FEATURE` só para ferramentas |
| Compatibilidade temporária de `.env.poc` na raiz | evita mover/copiar secret e reduz risco operacional durante a primeira migração | duplicar o arquivo secreto em `infra/` seria inseguro |
| Manter E2E rápido junto do chat | feedback local fica próximo do componente | mover todo teste para `quality/` aumentaria acoplamento e tempo de execução |
