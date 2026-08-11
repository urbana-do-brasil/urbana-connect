# Research: Organização do monorepo Urbana Connect

## Inventário encontrado

| Área atual | Evidência | Interpretação |
|---|---|---|
| `app/` | Gradle, Spring Boot, Java 21, Clean Architecture, Dockerfiles e testes | aplicação Urbana Connect; deve ir para `apps/urbana-connect-api/` |
| `poc-chat/` | React/Vite, Nginx, Vitest/RTL, Playwright e Dockerfile | aplicação web interna de teste; deve ir para `apps/poc-chat/` |
| `hermes/profile/` | `SOUL.md`, config e plugin local | artefato de integração mantido pela Urbana |
| `hermes/scripts/` | instalação, contrato, isolamento e superfície | operação/validação do runtime externo |
| `hermes/docker-compose.poc.yml` | Mongo, Hermes, API, chat e proxies | infraestrutura local, não código da integração |
| `corpus/` | cenários, fixtures, schema, runner Ruby e resultados ignorados | qualidade cross-system |
| `infra/k8s/` | Kustomize da API/Mongo/observabilidade/hml | infraestrutura de deploy; nome mais explícito será `infra/kubernetes/` |
| `contracts/` | inexistente como área raiz canônica | esta spec cria o primeiro mapa de fronteiras; contratos de produto continuam em suas specs |

## Acoplamentos críticos

1. O Compose atual usa contexts relativos `../app` e `../poc-chat`; ao mover as
   aplicações, esses contexts devem apontar para `../../apps/...` a partir de
   `infra/local-poc/`.
2. Scripts Hermes calculam paths a partir de `REPO_ROOT/hermes`; todos precisam
   resolver `integrations/hermes-agent` e `infra/local-poc` sem depender do
   diretório corrente.
3. O teste de contrato do Nginx e o teste de container do chat referenciam
   arquivos na raiz histórica; devem apontar para os novos limites ou receber
   uma variável de root única.
4. Workflows e Dependabot assumem `/app`; a aplicação Java passa a ser
   `/apps/urbana-connect-api`.
5. O workflow de deploy usa `infra/k8s`; a renomeação só é segura se todos os
   paths do workflow/manifests forem alterados em conjunto.
6. `app/docker-compose.yml` e `app/dev-env.sh` são uma composição legada
   concorrente do Compose completo da POC. Eles devem ser classificados e
   documentados, não misturados silenciosamente com o entrypoint local novo.
7. Há arquivos com sufixo ` 2` e cópias divergentes no worktree. Como o pedido é
   preservar alterações existentes, não são apagados por esta refatoração.

## Decisões

### D1 — Monorepo com três processos, duas aplicações próprias

Adotamos `apps/urbana-connect-api`, `apps/poc-chat` e a integração/runtime
Hermes como três processos operacionais. A estrutura de ownership, entretanto,
separa Hermes em `integrations/` e `infra/`, porque seu código upstream não é
propriedade deste repositório.

### D2 — `infra/local-poc` como único entrypoint da POC

O Compose completo que reproduz o fluxo manual deve ter um local canônico. O
arquivo legado de Mongo/Express do backend não será promovido a segundo
entrypoint; será mantido apenas se ainda for necessário e explicitamente
rotulado.

### D3 — E2E em duas camadas

Testes rápidos que validam apenas o chat continuam em `apps/poc-chat/e2e/`.
Jornadas que exigem Urbana, Mongo, Hermes e proxies ficam em
`quality/system-e2e/`. Isso preserva feedback rápido sem esconder dependências
cross-system.

### D4 — Compatibilidade transitória de secrets

O `.env.poc` real permanece na raiz durante esta mudança, porque mover ou
duplicar um arquivo secreto não oferece ganho estrutural e ele já é consumido
por scripts existentes. O exemplo pode ser referenciado pela infraestrutura,
mas nunca contém valores reais.

### D5 — Nenhuma cópia do upstream Hermes

O repositório fixa a imagem/versão/commit necessários e mantém profile/plugin e
scripts locais. O runtime upstream continua obtido pelo mecanismo já validado
de instalação.

## Não decisões

- não escolher novo framework ou banco;
- não mudar o contrato textual da spec 004;
- não excluir dados/volumes Docker ou arquivos do worktree;
- não prometer que uma ferramenta auxiliar legada aceitará o nome
  `feat/pee-101` sem configuração explícita.
