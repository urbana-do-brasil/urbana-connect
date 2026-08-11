# Feature Specification: Organização do monorepo Urbana Connect

**Feature Branch**: `feat/pee-101`
**Spec Directory**: `005-repository-organization`
**Created**: 2026-08-11
**Status**: Verified — reorganização validada e consolidada em `feat/pee-101`
**Ticket Jira**: PEE-101
**Input**: separar claramente as aplicações Urbana Connect, Hermes e poc-chat,
organizando responsabilidades e pontos de execução local.

## 1. Contexto

O repositório funciona hoje como um monorepo, mas os limites entre seus
componentes estão implícitos na raiz. O backend Java está em `app/`, o chat
manual React está em `poc-chat/`, o material de integração e runtime local do
Hermes está em `hermes/`, e os testes de corpus estão em `corpus/`. O Compose
que sobe a POC, scripts locais, CI e documentação ainda apontam diretamente
para essa organização histórica.

Essa estrutura dificulta responder rapidamente:

- qual diretório é uma aplicação de propriedade da Urbana;
- qual diretório é apenas um adaptador/configuração para o runtime externo
  Hermes;
- onde fica o único ponto de entrada da infraestrutura local;
- quais arquivos são contratos, testes de sistema, documentação ou artefatos
  gerados;
- quais referências precisam ser atualizadas quando um componente muda de
  lugar.

A solução deve reorganizar o monorepo sem mudar o comportamento conversacional
já validado: a Urbana Connect persiste a mensagem, chama o Hermes, persiste a
resposta textual e a devolve ao chat sem reescrita. O runtime upstream do
Hermes não está versionado neste repositório e continuará sendo uma dependência
externa fixada por versão/commit.

### Decisões de contexto

- A unidade de propriedade é composta por duas aplicações: `urbana-connect-api`
  e `poc-chat`.
- Hermes é uma integração/runtime externo; os arquivos locais pertencem à
  integração, ao profile da POC e à infraestrutura de desenvolvimento, não a
  uma cópia do produto upstream.
- MongoDB é dependência local compartilhada do ecossistema e continua sendo a
  fonte canônica do transcript da POC.
- A branch operacional desta entrega é exatamente `feat/pee-101`.
- O worktree já contém alterações de specs 001–004 e alterações de código não
  commitadas. Todas devem ser preservadas; a reorganização deve usar migrações
  reversíveis e não apagar conteúdo ambíguo.

## 2. Resultado estrutural esperado

Ao final, a raiz deverá comunicar estes limites:

```text
urbana-connect/
├── apps/
│   ├── urbana-connect-api/       # aplicação Java/Spring da Urbana
│   └── poc-chat/                 # aplicação React para teste manual local
├── integrations/
│   └── hermes-agent/             # profile, plugin e adaptadores locais
├── infra/
│   ├── local-poc/                # Compose, proxies e operação local
│   └── kubernetes/               # manifests de ambientes Kubernetes
├── quality/
│   ├── conversation-corpus/      # corpus/eval cross-system
│   └── system-e2e/               # jornadas E2E do ecossistema
├── contracts/                    # contratos compartilhados quando houver
├── docs/
├── specs/
└── README.md
```

O nome e a disposição finais podem conservar subdiretórios internos já
estáveis, desde que a responsabilidade de cada área seja inequívoca e os
comandos documentados continuem executáveis.

## 3. Histórias de usuário

### US1 — Encontrar e alterar a aplicação correta (P1)

Como pessoa desenvolvedora, quero localizar backend e chat em diretórios de
aplicações explícitos, para alterar um componente sem confundi-lo com
infraestrutura ou runtime externo.

1. Dado o checkout do repositório, quando a pessoa listar `apps/`, então os
   dois produtos mantidos pela Urbana estarão identificáveis por nome e cada
   um terá seu manifesto, código e testes locais.
2. Dado um arquivo do backend ou do chat, quando a pessoa seguir o README da
   aplicação, então encontrará comandos de teste/build sem depender de paths
   históricos inexistentes.

### US2 — Operar a POC local com um ponto de entrada claro (P1)

Como pessoa testadora, quero iniciar e validar o ecossistema local a partir de
`infra/local-poc/`, para simular mensagens sem conhecer detalhes internos de
cada aplicação.

1. Dado Docker acessível e `.env.poc` configurado, quando a pessoa seguir o
   quickstart, então poderá subir MongoDB, Hermes, Urbana Connect, proxies e
   `poc-chat` usando a composição local documentada.
2. Dado um serviço iniciado, quando os smoke tests locais forem executados,
   então os scripts encontrarão os manifests e endpoints por caminhos
   documentados, sem depender de `hermes/` como diretório-raiz implícito.
3. A reorganização não deverá trocar nomes de serviços, portas, redes, volumes,
   credenciais ou dados persistidos sem decisão explícita própria.

### US3 — Distinguir integração Hermes de produto upstream (P1)

Como mantenedor da integração, quero separar profile, plugin, scripts e pin do
runtime Hermes dos arquivos de Compose, para deixar claro o que é código local
e o que é dependência externa.

1. Dado `integrations/hermes-agent/`, quando a pessoa o inspecionar, então
   reconhecerá somente artefatos mantidos pela Urbana para configurar ou
   validar o Hermes.
2. O repositório não deverá passar a conter uma cópia do código upstream do
   Hermes para realizar a reorganização.
3. O pin de versão/commit e os limites de rede do runtime permanecerão
   verificáveis por script e documentação.

### US4 — Validar o ecossistema por camadas (P2)

Como pessoa responsável por qualidade, quero encontrar corpus, E2E e contratos
em áreas próprias, para executar validações unitárias, de integração e
cross-system sem misturar seus artefatos com código de produção.

1. O corpus de conversação terá uma localização estável sob `quality/` e
   manterá cenários, fixtures, schema, runner e resultados ignorados pelo Git.
2. Os testes E2E específicos do navegador permanecem junto da aplicação
   `apps/poc-chat/`, porque dependem do `playwright.config.ts` e do pacote
   Playwright do chat; contratos estruturais que verificam o ecossistema ficam
   em `quality/system-e2e/`. Ambos terão localização e comando documentados e
   não farão parte do bundle de produção.
3. Contratos de fronteira e documentação arquitetural indicarão a direção das
   dependências: UI → API Urbana → Hermes; Mongo como persistência canônica da
   Urbana; runtime Hermes sem dependência de implementação interna da UI.

### US5 — Migrar sem perder o estado atual (P1)

Como responsável pelo repositório, quero que a reorganização preserve todas as
alterações existentes e seja auditável, para não perder o trabalho já validado
nas specs anteriores.

1. Arquivos movidos deverão conservar conteúdo e histórico sempre que o Git
   conseguir reconhecer a movimentação.
2. `.env.poc` e qualquer credencial local permanecerão fora do Git e não serão
   exibidos em logs, diffs ou documentação.
3. Duplicatas ou arquivos com finalidade incerta não serão apagados durante
   esta entrega; serão classificados e, quando necessário, isolados de forma
   reversível com registro da decisão.
4. O comportamento observável do fluxo textual Hermes → Mongo → API → UI deverá
   continuar igual antes e depois da reorganização.

## 4. Requisitos funcionais

- **FR-001**: O repositório MUST possuir limites físicos para `apps/`,
  `integrations/`, `infra/`, `quality/`, `contracts/`, `docs/` e `specs/`,
  conforme aplicabilidade documentada.
- **FR-002**: O backend Java MUST residir sob `apps/urbana-connect-api/` com
  seu Gradle wrapper, código, recursos, testes e Dockerfiles associados.
- **FR-003**: O frontend React MUST residir sob `apps/poc-chat/` com seus
  manifests, código, testes unitários/componentes, E2E local e configuração de
  container.
- **FR-004**: Artefatos locais mantidos para o Hermes MUST residir sob
  `integrations/hermes-agent/`; o runtime upstream MUST continuar externo e
  fixado por referência verificável.
- **FR-005**: Compose, proxies, scripts de subida/health/smoke e configuração
  operacional da POC MUST possuir um ponto de entrada sob `infra/local-poc/`.
- **FR-006**: Corpus e E2E cross-system MUST ser separados de código de
  produção em `quality/`, preservando seus comandos e fixtures.
- **FR-007**: Todas as referências relativas, scripts, Dockerfiles, Compose,
  workflows, Dependabot, CODEOWNERS, README e quickstarts MUST apontar para os
  novos caminhos ou declarar explicitamente compatibilidade transitória.
- **FR-008**: A reorganização MUST preservar nomes de serviço, contratos HTTP,
  portas, redes, volumes e variáveis de ambiente da POC, salvo mudança
  documentada e validada como necessária para resolver um path.
- **FR-009**: A reorganização MUST preservar o fluxo textual transparente já
  validado e não poderá introduzir tratamento conversacional, wrapper ou
  transformação de conteúdo.
- **FR-010**: Arquivos gerados, caches, relatórios e segredos MUST continuar
  fora do conjunto versionado e não poderão ser usados como fonte canônica da
  estrutura.
- **FR-011**: O README raiz MUST explicar ownership, dependências e comandos
  mínimos para backend, chat, runtime Hermes, POC local e qualidade.
- **FR-012**: A validação final MUST incluir build/testes das duas aplicações,
  validação dos scripts/profile/isolamento Hermes, corpus, contratos e E2E
  relevante; qualquer limitação deverá ser classificada com evidência.

## 5. Requisitos não funcionais

- **NFR-001 — Segurança**: nenhum secret de `.env.poc` ou token de serviço pode
  aparecer em arquivos rastreados, patches, saída de validação ou artefatos da
  spec.
- **NFR-002 — Reversibilidade**: cada grupo de movimentações deverá ser
  rastreável por diff e poder ser revertido sem apagar dados persistidos.
- **NFR-003 — Compatibilidade local**: o operador não deverá precisar instalar
  um runtime novo além das dependências já documentadas para executar a POC.
- **NFR-004 — Isolamento**: o Hermes não deverá ganhar acesso implícito à rede
  de dados Mongo apenas por causa da reorganização.
- **NFR-005 — Clareza**: um novo colaborador deverá identificar em até cinco
  minutos onde editar backend, chat, integração Hermes, infra e qualidade a
  partir do README raiz.
- **NFR-006 — Qualidade**: a reorganização não reduz os gates existentes de
  testes, cobertura ou validação operacional.

## 6. Critérios de aceite

1. `feat/pee-101` contém a spec, plano, tarefas e evidências desta
   reorganização; o trabalho não é realizado em outra branch.
2. A árvore-alvo está materializada para backend, chat, integração Hermes,
   infraestrutura local, Kubernetes e qualidade, sem conteúdo de produção
   duplicado por acidente.
3. Os comandos documentados de build/teste do backend e do chat passam com os
   paths novos, respeitando o bloqueio já conhecido da duplicata preexistente
   caso ela não possa ser isolada sem autorização adicional.
4. O Compose local sobe ou valida a mesma topologia funcional, sem alteração
   silenciosa de portas, serviços, redes, volumes ou secrets.
5. Os scripts Hermes de instalação/contrato/isolamento/superfície e a validação
   do profile passam a operar a partir do novo local.
6. O corpus e o E2E local são encontrados e executados pelos comandos
   documentados, com resultados gerados fora do Git.
7. Uma prova de round-trip textual confirma que a resposta produzida pelo
   Hermes continua idêntica no Mongo, na projeção HTTP e na UI, sem prefixo ou
   fallback local.
8. `git diff --check`, validações de paths e revisão independente não encontram
   referências canônicas quebradas, secret exposto ou serviço sem dono.
9. Toda exceção — duplicata, arquivo histórico, ambiente indisponível ou
   compatibilidade transitória — está registrada em `tasks.md`, checklist ou
   seção de risco com próximo passo explícito.

## 7. Edge cases e riscos

- Worktree com alterações não commitadas não pode ser limpo ou resetado para
  viabilizar a migração.
- O branch name `feat/pee-101` não atende à validação numérica legada do
  Speckit; isso é uma exceção de ferramenta, não uma mudança no branch pedido.
- O Docker Desktop pode estar indisponível; a validação estrutural e estática
  deve continuar, mas o E2E live deve ser classificado como bloqueado, não
  simulado.
- `hermes/docker-compose.poc.yml` atualmente constrói os serviços a partir de
  `../app` e `../poc-chat`; esses contextos precisam acompanhar as
  movimentações.
- Existem cópias com sufixo ` 2` e arquivos gerados/caches no worktree. O
  conteúdo não deve ser apagado sem identificação e decisão reversível.
- Scripts executados de fora da raiz, de dentro de um container ou via
  symlink não devem resolver paths diferentes do contrato documentado.
- O `.env.poc` real pode permanecer na raiz por compatibilidade durante a
  migração; apenas o exemplo/documentação pode mudar de lugar, sem copiar o
  secret.

## 8. Observabilidade e validação

Além de testes de path e conteúdo, a execução deverá registrar:

- árvore final e ownership por diretório;
- referências antigas encontradas e corrigidas;
- comandos executados e resultado sem imprimir valores sensíveis;
- nomes/quantidades de containers e redes apenas quando a validação Docker
  estiver disponível;
- comparação literal Hermes → Mongo → API → UI em uma sessão de teste nova,
  quando o ecossistema local estiver operacional.

Logs devem mostrar identificadores não sensíveis, status e caminhos relativos,
nunca `.env.poc`, headers de autorização ou payloads com credenciais.

## 9. Fora de escopo

- alterar o domínio ou os casos de uso da Urbana Connect;
- reescrever o fluxo transparente Hermes-first ou melhorar o prompt do agente;
- adicionar anexos, streaming, WebSocket ou novas funcionalidades de chat;
- copiar, modificar ou fazer deploy do código upstream do Hermes;
- alterar modelo, provedor, credenciais, volumes persistidos ou dados Mongo;
- reorganizar o deploy de produção além de atualizar paths necessários para a
  validação do repositório;
- apagar duplicatas/artefatos ambíguos sem classificação, preservação e
  evidência de que não são fonte ativa.

## 10. Dúvidas em aberto e decisões assumidas

- **Assumido**: a primeira migração mantém o `.env.poc` real na raiz para não
  quebrar ferramentas existentes; um alias/documentação sob `infra/local-poc/`
  pode ser criado sem duplicar o conteúdo secreto.
- **Assumido**: a infraestrutura Kubernetes será renomeada de `infra/k8s/`
  para `infra/kubernetes/` somente se todos os workflows e manifests forem
  atualizados na mesma mudança; não haverá mudança de comportamento de deploy.
- **Assumido**: E2E específico do chat pode permanecer junto da aplicação para
  testes rápidos, enquanto jornadas que exigem o stack completo serão
  centralizadas em `quality/system-e2e/`.
- **Pendente de decisão posterior**: remoção definitiva dos aliases históricos
  e duplicatas após uma branch limpa e uma revisão humana. Nesta entrega eles
  não serão tratados como lixo descartável.
