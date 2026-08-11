# Quickstart: validação da resiliência Hermes

## Pré-requisitos

- Docker Desktop ou outro Docker Engine acessível.
- Chaves locais do Hermes/OpenRouter configuradas somente no ambiente local.
- JDK 21. Nesta máquina, o binário Temurin usado nas validações fica fora do
  repositório em `/Users/emanueljoseguimaraesbrito/.sdkman/candidates/java/21.0.12-tem`.

## Baseline validada em 2026-08-07

- `docker context`: `desktop-linux`; `docker info` respondeu com Docker Desktop
  28.0.1.
- Os seis serviços do compose estavam ativos; MongoDB e `poc-chat` saudáveis.
- O compose precisa receber explicitamente `--env-file .env.poc` quando executado
  a partir da raiz; o arquivo permanece fora do Git.
- O Gradle falhou com o Java de sistema 11 porque Spring Boot exige Java 17+.
  A validação passou a usar o Temurin 21 binário instalado fora do repositório,
  sem Xcode/Command Line Tools.
- A baseline focada (`PocReceptionIngressTest`, `ReceptionFailureRecoveryTest` e
  `ReceptionOrchestratorTest`) passou com Java 21 antes das mudanças da feature.

## Histórico da validação intermediária da spec 003 em 2026-08-07

Antes da restrição atual do ambiente, o compose foi reconstruído para
`urbana-connect` e `poc-chat`, os containers foram iniciados e
`/api/v1/readiness` respondeu `READY`; a saúde funcional da Urbana respondeu
`OK`. Isso comprova o boot daquele artefato local, não o E2E final da spec.

Resultados automatizados registrados:

- Backend: 102 classes sem Testcontainers, 292 testes aprovados; `bootJar`
  aprovado. A asserção adicional dos defaults de lease/claim foi escrita, mas
  ainda precisa ser executada. A suíte completa com `jacocoTestReport` permanece aberta porque o
  Testcontainers parou em `MongoDBContainer.initReplicaSet` (`mongo:6.0.5`).
- Frontend: 63 testes Vitest aprovados, typecheck/lint/build aprovados.
- Playwright: 6 aprovados e 1 cenário live ignorado explicitamente com o Chrome
  do sistema. Uma repetição posterior não conseguiu iniciar o Vite por
  `listen EPERM` do sandbox; repetir em um ambiente que permita bind local.
- Corpus: 18 execuções, 92 assertions, zero falhas/erros/skips.
- `./integrations/hermes-agent/scripts/smoke-contract.sh`: aprovado. O smoke live Hermes → provedor
  excedeu 60s sem resposta (`curl (28)`), portanto está classificado como
  indisponibilidade externa.

O fluxo E2E final chat → Urbana → Hermes → Mongo ainda não tem uma execução válida:
a tentativa final encontrou a porta 3000 inacessível antes de enviar o POST.
Não há evidência Mongo para esse cenário e ela não deve ser inferida da readiness.

Naquele momento, o runtime também não estava operacional: o contexto era
`desktop-linux`, mas `docker info` falha com `permission denied ... operation not
permitted` ao conectar em `~/.docker/run/docker.sock`; 8081, 3000 e 8652 recusam
conexão. `open /Applications/Docker.app` também falhou com
`NSOSStatusErrorDomain Code -10822`. O bloqueio deve ser resolvido antes de
reexecutar T048/T050.

Não havia runtime local substituto funcional: o `mongod` encontrado era
3.6.3 e aborta por `libssl.1.0.0.dylib` ausente; Colima v0.10.3 está presente,
mas falha por ausência do executável `lima`. Não foi instalado Xcode nem outra
dependência pesada para mascarar o bloqueio.

## Revalidação final após retomada do Docker Desktop

Esta é a evidência vigente da feature; a seção anterior permanece apenas como
histórico do bloqueio ambiental que foi resolvido.

- `docker context show` retornou `desktop-linux`; `docker info` respondeu com
  Docker Desktop 28.0.1. MongoDB, Urbana Connect, Hermes, proxies e `poc-chat`
  ficaram ativos; MongoDB e `poc-chat` saudáveis. Readiness da Urbana: `READY`.
- Os valores efetivos do container da Urbana foram timeout Hermes `180000ms`,
  lease `240s` e claim `240s`. `.env.poc` permaneceu local, ignorado pelo Git e
  não foi copiado para a imagem.
- Backend: `./gradlew --no-daemon --max-workers=1 test jacocoTestReport` e
  `./gradlew --no-daemon bootJar` passaram. Foram registrados 58 suítes/327
  testes backend sem falhas; JaCoCo ficou em 81.74% instruções, 60.91% branches
  e 82.94% linhas.
- Hermes: `./integrations/hermes-agent/scripts/smoke-contract.sh`, o mesmo smoke com
  `HERMES_LIVE_MODEL_SMOKE=1` e `./integrations/hermes-agent/scripts/smoke-isolation.sh` passaram.
  A rota live Hermes → OpenRouter foi exercitada com as chaves locais; nenhum
  smoke externo foi inferido a partir de readiness.
- E2E real pela superfície do chat: POST `202/QUEUED`, projeção posterior
  `COMPLETED`, uma chamada Hermes e uma saída canônica. Mongo confirmou entrada,
  turno concluído, pendência concluída, lease revogada e nenhuma saída duplicada.
- Playwright determinístico com Chrome local: 6 testes passaram. Playwright live
  contra `http://127.0.0.1:3000`: 1 teste passou em 38.7s com três contatos,
  alternância e reload; Mongo confirmou um turno concluído e uma saída outbound
  em cada uma das três conversas mais recentes.
- Correção frontend sequencial: a regra de polling foi ajustada para manter uma
  mensagem otimista ativa mesmo quando o GET ainda informa o turno anterior como
  `COMPLETED`. A regressão unitária passou com 14 testes focados; a suíte frontend
  passou com 19 arquivos/65 testes; Playwright passou com 7 casos e 1 live skip,
  incluindo o cenário US6 de duas mensagens sequenciais sem reload e sem POST
  duplicado.
- O container `poc-chat` foi reconstruído e recriado com o bundle corrigido. Um
  smoke live adicional manteve os GETs ativos, mas a primeira chamada real excedeu
  180s e entrou em `RECONCILING` por `HERMES_TIMEOUT_AFTER_DISPATCH`; isso foi
  classificado como latência/indisponibilidade externa.

## Testes automatizados na branch consolidada

```bash
export JAVA_HOME=/Users/emanueljoseguimaraesbrito/.local/share/urbana-connect/jdks/temurin-21/Contents/Home
cd apps/urbana-connect-api
./gradlew test jacocoTestReport

cd ../poc-chat
npm ci
npm run test -- --run
npm run build
npx playwright test
```

O teste backend deve incluir os cenários de claim exclusivo, timeout ambíguo,
reconciliação, retry seguro e isolamento entre contatos. O frontend deve cobrir
polling além de 120s, erro temporário de GET e retomada após reload.

## Serviços locais

Inicie o compose da POC já existente e valide saúde de MongoDB, Urbana Connect,
Hermes e chat. Não use o webhook real.

```bash
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml up -d
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml ps
```

Ao executar a partir da raiz, use `--env-file .env.poc`; o arquivo contém chaves
locais, permanece fora do Git e não deve ser copiado para imagens ou commitado.

## Smoke direto da dependência

```bash
./integrations/hermes-agent/scripts/smoke-contract.sh
```

Se o smoke live falhar, registre o resultado como indisponibilidade Hermes/
provedor. Não transforme a falha em aprovação do E2E.

## Cenário controlado de aceite

1. Configure um gateway Hermes fake/controlado para responder após 35s e depois
   após mais de 120s.
2. Envie uma mensagem pelo chat local e confirme `202/QUEUED` imediato.
3. Verifique no Mongo que a entrada e o trabalho existem antes da chamada remota.
4. Confirme `RUNNING/DELAYED`, uma única chamada ao fake e polling contínuo.
5. Derrube apenas a resposta HTTP, mantendo a execução fake; confirme
   `RECONCILING`, nenhuma nova chamada e uma única saída após reconciliação.
6. Envie mensagens para três contatos; confirme execução independente.
7. Recarregue o navegador durante `RECONCILING`; confirme retomada do estado.
8. Provoque falha comprovadamente pré-dispatch; confirme `FAILED_SAFE_TO_RETRY` e
   apenas então use o botão de retry.

## Evidências mínimas

- logs correlacionados do turno e da tentativa;
- documento Mongo com estado, tentativa, timestamps e `retryAllowed`;
- contador Hermes igual a uma chamada no cenário ambíguo;
- uma única mensagem outbound canônica;
- resultado separado do smoke Hermes → provedor;
- relatório Gradle/JaCoCo, Vitest e Playwright sem falhas relevantes.

A correção frontend e o handoff independente estão encerrados em `tasks.md` como
T055. Smoke externo, readiness e testes determinísticos continuam sendo
classificados separadamente; a falha live acima não foi transformada em aprovação
do provedor.
