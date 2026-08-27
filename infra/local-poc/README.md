# POC local

Este diretório é o ponto de entrada da operação local completa: MongoDB,
Hermes, Urbana Connect, `poc-chat` e os dois proxies. O runtime Hermes continua
externo; seus artefatos mantidos pela Urbana ficam em
`integrations/hermes-agent/`.

O Mongo local executa como replica set `rs0` de um membro. O serviço
`mongodb-rs-init` inicializa o membro `mongodb:27017` de forma idempotente, e o
healthcheck só fica verde quando `hello` confirma um primary gravável. A URI
consumida pela aplicação inclui `replicaSet=rs0`, retries de leitura/escrita e
`w=majority`.

Na raiz do repositório, com `.env.poc` local configurado:

```bash
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml config --quiet
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml up -d --build
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml ps
```

O backend é compilado no próprio build Docker com Gradle e JDK 21: a imagem
nunca copia `build/libs/*.jar` da máquina host. Assim, `up -d --build` usa
necessariamente o código-fonte presente em `apps/urbana-connect-api/` no
momento do build. O `poc-chat` também é reconstruído pelo Compose a partir do
seu contexto de fonte.

O mesmo fluxo pode ser iniciado por
`./integrations/hermes-agent/scripts/run-local.sh -d`. O script resolve a
imagem do serviço pelo Compose, constrói e valida a imagem PEE-103 padrão, ou
usa uma imagem explicitamente configurada sem reconstruí-la. Em ambos os
casos, ele constrói antes as imagens atuais de `urbana-connect` e `poc-chat`,
depois sobe o Compose com `--no-build` e aguarda os healthchecks dos dois
serviços. O argumento `--build` é aceito, mas é dispensado pelo script porque
os builds selecionados já ocorreram; isso impede que um `HERMES_IMAGE` externo
seja reconstruído acidentalmente.

O Compose usa por padrão a imagem local
`urbana-hermes-agent:pee-103-2f5472a15`. Ela é construída a partir do Hermes
pinado no commit `2f5472a15a026b6bd5847ad65058f1565d2b40ba`, sobre a imagem
`urbana-hermes-agent:0.20.0`, sem montar o código-fonte no container. O valor
pode ser substituído por `HERMES_IMAGE` para validar uma tag explicitamente
publicada em homologação; nesse caso, suba o stack sem `--build`.

```bash
HERMES_IMAGE=urbana-hermes-agent:<tag-de-homologacao> \
  docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml up -d --no-build
```

Para verificar a identidade da imagem antes de subir o stack:

```bash
docker image inspect urbana-hermes-agent:pee-103-2f5472a15 \
  --format 'revision={{index .Config.Labels "org.opencontainers.image.revision"}}'
```

Após a execução de `run-local.sh -d`, a saída também mostra o ID da imagem do
backend, a revisão Git usada pelo script (com o sufixo `-dirty` quando há
alterações locais em `apps/urbana-connect-api`) e a marca
`containerized-gradle-jdk21`. Para inspecionar manualmente a imagem em uma
subida direta do Compose:

```bash
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml images urbana-connect
docker image inspect $(docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml images -q urbana-connect) \
  --format 'revision={{index .Config.Labels "org.opencontainers.image.revision"}} build={{index .Config.Labels "br.com.urbana.connect.build"}}'
```

Os contexts apontam para `apps/urbana-connect-api/` e `apps/poc-chat/`; os
mounts do profile/plugin apontam para `integrations/hermes-agent/`. Nomes de
serviços, portas, redes, volumes, healthchecks e isolamento permanecem os da
POC anterior.

O caminho Hermes-first validado pelo stack é o ingresso sintético
`/api/poc/conversations/{contactAlias}/messages` da Urbana Connect. O
`poc-chat` fala somente com essa API; ele não acessa MongoDB nem Hermes
diretamente. `/api/webhook` é o ingresso legado de WhatsApp e não participa da
prova local desta POC.

Após a subida, valide a dependência obrigatória com
`curl -fsS http://127.0.0.1:8081/api/v1/readiness` e o chat com
`curl -fsS http://127.0.0.1:3000/health`. O chat depende de
`urbana-connect: service_healthy` no Compose.

Para confirmar também o estado dos containers e o JAR que está sendo executado:

```bash
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml ps
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml exec urbana-connect \
  sh -c 'sha256sum /app/app.jar && java -version'
```

O hash do JAR acima é produzido dentro do estágio de build da imagem atual;
ele não deve ser comparado a artefatos antigos do `build/libs` local.

Esse replica set de um membro habilita a transação multi-documento usada na
prova técnica, mas não representa alta disponibilidade nem failover de
produção.

`.env.poc` não é movido, copiado ou exibido. Para validações Hermes, use os
scripts em `integrations/hermes-agent/scripts/` e registre apenas resultados
sanitizados.

## Controles locais de validação humana

O `poc-chat` só mostra os controles da arquiteta quando a projeção canônica
declara a capability correspondente. Eles aparecem com a marca
`ação da arquiteta/teste`, fora do histórico do cliente, e não usam o ingresso
de mensagens do cliente.

As rotas expostas pelo proxy local são exclusivamente estas, sempre para um
alias opaco `manual-<uuid>` e com o token injetado no servidor:

```text
POST /api/poc/conversations/{contactAlias}/payment-proof/approve
POST /api/poc/conversations/{contactAlias}/human/messages
POST /api/poc/conversations/{contactAlias}/ownership/urba
```

A mensagem humana exige `Idempotency-Key` e `{text, occurredAt}`. A devolução
exige `Idempotency-Key` e `{expectedVersion}`; o backend sincroniza o histórico
canônico completo com o Hermes antes de decidir se a Urba pode retomar. Falha
de sincronização mantém a conversa com a arquiteta.

Não habilite esses controles com links, tokens ou dados de produção. Eles são
somente fixtures locais e não representam pagamento, contratação ou envio real.
