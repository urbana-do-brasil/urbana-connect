# Baseline local reconciliado com lacunas

Este inventário impede que a execução da feature seja tratada como greenfield.
Ele registra o estado observado antes da correção dos artefatos em 2026-08-26;
não atribui autoria, não declara conclusão e não autoriza descartar arquivos.

## Snapshot observado

- branch no início do gate: `008-complete-urba-service-flow`; branch após o gate:
  `feature/008-complete-urba-service-flow`;
- Jira: `PEE-105`, subtarefa de PEE-23, status `Em andamento`;
- relação: `hml` é ancestral da branch atual;
- árvore: 53 arquivos rastreados modificados e 18 entradas não rastreadas;
- diff rastreado no início da auditoria: 3.631 inserções e 438 remoções;
- áreas afetadas: backend Reception/catálogo/persistência, plugin e SOUL Hermes,
  POC React, proxy/compose local, testes e documentação.

## Slices reconciliados

| Slice | Evidência observada | Estado de aceite |
|---|---|---|
| Catálogo rico | modelos, política comercial, seed Mongo e testes estão modificados; testes focados do catálogo passaram | Aproveitável com delta: regras ICP/política e cobertura live ainda faltam |
| Handoff e retomada | orquestrador, reconciliação, portas/adaptador Hermes e testes estão modificados; slice focado passou | Aproveitável com delta: aceite live e E2E ainda faltam |
| POC da arquiteta | contratos, reducer, componentes, proxy e testes estão modificados; Vitest, typecheck e build passaram | Aproveitável |
| Build local | Dockerfile, compose, script Hermes e README estão modificados; Docker daemon não estava disponível | Incompleto/bloqueado para validação de imagem e healthcheck |
| SOUL/plugin | perfil e ferramentas do plugin estão modificados; 8 testes unitários passaram | Aproveitável com delta: fluxo live e redação final ainda faltam |
| E2E de qualidade | `apps/poc-chat/e2e/quality-chat.spec.ts` existe e cobre descoberta/handoff | Incompleto para ICP/termos |
| Fatos de ICP | tipos, políticas e testes existentes referenciam os três campos; 3 dos 36 testes adicionais falharam | Incompleto/conflitante com o contrato aprovado |
| Evento de desvio | nenhuma ocorrência de `ICP_SKIPPED_BEFORE_TERMS` foi encontrada em `apps/` ou `integrations/` | Ausente |

## Evidência de validação do Gate 0

Os testes foram executados depois de separar falhas ambientais de falhas de
produto:

- Backend com JDK 21 Temurin temporário em `/tmp/urbana-connect-jdk21`:
  `./gradlew clean test` com os testes focados de catálogo, ferramentas,
  orquestração, handoff, reconciliação e controllers: **102 testes passaram**.
  O primeiro resultado inválido foi causado por Java 11 e, depois, por classes
  stale com sufixo ` 2` em `build`; `clean` removeu somente artefatos gerados.
- Backend adicional de fatos/retomada: **36 testes executados, 3 falharam**:
  `ReceptionResponsePolicyTest.keepsTheValidatedConversationalMessageAndNextActionAsSeparateFields`,
  `ReturningCustomerServiceTest.asksOnlyForIcpFieldsThatAreTentativeStaleOrAbsentAndAcceptsPreferNotToAnswer`
  e `ReturningCustomerServiceTest.keepsCorrectedFactHistoryAndNeverExposesTheSentinelContact`.
  A causa observável é que `CommercialPolicyService.missingIcpFields` e
  `isIcpComplete` ainda são placeholders (`[]`/`true`), além da política de
  preço rejeitar o caso aprovado esperado pelo primeiro teste.
- Plugin Hermes: `python3 -m unittest discover -s plugins/urbana-domain -p
  'test*.py'`: **8 testes passaram**.
- POC React: `npm test -- --run`: **19 arquivos/79 testes passaram**;
  `npm run typecheck`: passou; `npm run build`: passou após reidratar em
  `node_modules` os binários nativos do Rolldown e do Lightning CSS que estavam
  como `.icloud`. `package.json` e `package-lock.json` não foram alterados.
- Validação Docker/Playwright live não foi executada: o Docker daemon local não
  estava disponível. O E2E existente permanece limitado à descoberta,
  explicação inicial e handoff; ainda não cobre ICP, aceite, pagamento,
  briefing, retorno humano → Urba ou `ICP_SKIPPED_BEFORE_TERMS`.

## Delta congelado para os escritores

- Writer A deve tratar a parte de catálogo/fatos de T007, T011, T013–T016,
  T019–T020, T025 e T029, começando pelos três testes vermelhos e sem editar
  os boundaries de ferramentas/orquestração.
- Writer B deve tratar a parte de ferramentas de T007, T008–T010, T012,
  T017, T021–T023, T026–T028 e T030–T044, incluindo
  `ICP_SKIPPED_BEFORE_TERMS`, contexto integral e handoff visível.
- Writer C deve tratar T024, T042 e T045–T048, ampliando o E2E sem assumir que
  o backend está disponível no ambiente do writer.
- Nenhum writer deve reverter o snapshot, reintroduzir o seeder duplicado ou
  criar `ICPCheckpointState`/`attemptsByField`.

## Alertas de preservação

- O arquivo duplicado legado `ServiceCatalogSeeder 2.java` foi comparado com o
  seeder canônico, retirado do source set para não declarar uma segunda classe
  pública `ServiceCatalogSeeder` e preservado em
  `docs/plans/legacy-review/ServiceCatalogSeeder 2.java`.
- A existência de testes modificados não prova que foram escritos antes do
  código nem que passam no estado atual.
- Nenhum arquivo do baseline pode ser revertido, substituído em massa ou
  marcado como concluído sem inspeção e evidência correspondente.
- O backend não contém uma classe `ICPCheckpointState` no snapshot observado;
  ela também não deve ser introduzida como autoridade conversacional.

## Gate obrigatório antes dos escritores

- [x] Inventário inicial e relação com `hml` registrados.
- [x] Lacunas centrais conhecidas registradas nos artefatos.
- [x] Associar cada slice existente às tarefas revisadas e executar os testes
  focados correspondentes.
- [x] Classificar o diff por slice como aproveitável, incompleto, conflitante ou
  fora de escopo; registrar evidência neste arquivo.
- [x] Resolver o arquivo de seeder suspeito sem perda de conteúdo; a versão
  legada está preservada para consulta/revisão.
- [x] Vincular `PEE-105` e movê-la para `Em andamento` no início efetivo da
  execução.
- [x] Migrar/renomear para `feature/008-complete-urba-service-flow`, preservando
  a árvore e mantendo `hml` como base.

Somente após esses itens o Tech Lead distribui ownership de escrita. O estado
dos checkboxes de `tasks.md` deve refletir evidência, não aparência do diff.

## Estado pós-escritores e QA local — 2026-08-26

O delta foi implementado na branch `feature/008-complete-urba-service-flow`.
Os itens T007–T047 foram concluídos localmente e os gates T049, T050, T052 e
T054 foram executados. O ticket `PEE-105` continua em `Em andamento`; o
resultado e os bloqueios foram registrados no comentário Jira `12111`.

### Correções orientadas por QA

- O SOUL agora conduz o ICP somente depois de serviço confirmado e intenção
  explícita, pergunta apenas campos faltantes, oferece no máximo uma segunda
  oportunidade, registra `NÃO INFORMADO` apenas nos campos ainda faltantes e
  avança automaticamente para os termos.
- `PRONOUN_PREFERENCE` e `OCCUPATION` preservam o texto explícito; somente
  `FIRST_TIME_HIRING` normaliza para `SIM`, `NÃO` ou `NÃO INFORMADO`.
- A substituição de fatos cria o novo fato antes da supersessão e grava o ID
  real em `supersededBy`; o teste de regressão confirma que a leitura corrente
  retorna apenas a versão nova.
- A busca de arquitetura não encontrou `ICPCheckpointState` ou
  `attemptsByField`; `prepare_terms` permanece sem hard gate de ICP e o desvio
  observacional `ICP_SKIPPED_BEFORE_TERMS` não altera o resultado comercial.

### Evidências locais pós-correção

- JUnit focado da classe alterada: **20 testes, 0 falhas**.
- Suíte determinística integrada de catálogo, fatos, ferramentas, handoff,
  reconciliação, orquestração, controllers e boundaries Mongo: **BUILD
  SUCCESSFUL**; QA independente confirmou 59 testes focados verdes em seu
  recorte.
- Testes direcionados de sessões Hermes, retomada e worker POC: **BUILD
  SUCCESSFUL**.
- Plugin Urbana: **8/8** testes Python.
- POC React: **19 arquivos / 80 testes**, typecheck, build e lint verdes; a
  lista Playwright contém **9 testes em 4 arquivos**.
- Compose local: `docker compose ... config --quiet` passou com variáveis de
  homologação fictícias; `git diff --check` passou.

### Aceite ainda pendente

O resultado do checkpoint QA anterior foi `implemented_unverified`, não
`verified` para o produto. O estado operacional foi atualizado na seção abaixo
após a disponibilização do Docker:

- o binário Chromium do Playwright não está disponível, portanto os cenários
  live e os cinco roteiros manuais ainda não foram executados;
- a suíte backend completa e o gate JaCoCo de 60% continuam sem evidência
  final neste ambiente;
- ainda não há PR para `hml`; esse passo depende do aceite local de Emanuel.

## Estado operacional após o smoke local — 2026-08-26

O daemon Docker foi disponibilizado no ambiente local e o stack foi reconstruído
com o fonte atual:

- `docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml up -d --build`
  concluiu o build do backend dentro da imagem com JDK 21/Gradle; a imagem
  recebeu os labels `revision=workspace-source` e
  `build=containerized-gradle-jdk21`.
- MongoDB, Urbana Connect, Hermes e `poc-chat` ficaram saudáveis; os endpoints
  locais de readiness/health responderam `READY`, `ok` e Hermes `0.20.0`.
- `verify-tool-surface.sh`, `smoke-contract.sh` e os testes do plugin passaram;
  o `smoke-isolation.sh` foi corrigido para refletir o desenho efetivo de
  `hermes-profile-init` + volume nomeado, e passou com
  `filesystem_isolation=ok`, `profile_config_staging=read-only` e
  `network_isolation=ok`.

### Smoke conversacional observado

Em uma conversa limpa via ingresso sintético, usando apenas o `.env.poc` local:

1. A abertura passou a ser curta, identificando a Urba, citando os quatro
   serviços e perguntando qual opção/necessidade o cliente queria resolver.
2. Após `Decor Pintura` + intenção explícita de contratação, a Urba perguntou
   os três campos do perfil antes dos termos.
3. Uma única resposta com os três dados persistiu `Emanuel`, `SIM` e
   `Desenvolvedor`; a conversa avançou para `TERMS=PRESENTED` sem repetir o
   perfil.
4. Após `Aceito os termos`, avançou para pagamento; após `PIX`, marcou o
   pagamento como preparado e manteve a confirmação dependente da arquiteta.
5. Nenhuma mensagem do smoke expôs erro técnico, identificador interno ou
   falha do sistema ao cliente.

A primeira execução após a subida foi feita com uma chave sintética curta e
falhou antes do despacho por validação do próprio Hermes. Isso foi corrigido
usando as credenciais já presentes no `.env.poc`, sem alteração de código ou
exposição de segredo.

### Limitações que continuam abertas

- A abertura curta foi verificada após uma correção final no `SOUL.md`, mas os
  cinco roteiros manuais completos, incluindo recusa, segunda ausência,
  captura incidental, handoff e retomada, ainda não foram executados e
  registrados como transcripts em `quickstart.md`.
- A suíte backend completa executou 387 testes e terminou com 12 falhas no
  `ConversationFlowServiceIntegrationTest` legado (arquivos sem diff desta
  feature); o relatório JaCoCo gerado ficou em 18,49%, abaixo do mínimo de
  60%. Os testes focados da feature continuam verdes.
- O Playwright live não foi executado porque o binário Chromium não está
  disponível neste ambiente; a validação realizada foi por API/Compose.
- Não há PR para `hml`, promoção ou deploy nesta etapa.
