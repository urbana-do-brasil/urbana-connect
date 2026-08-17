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

O mesmo fluxo pode ser iniciado por
`./integrations/hermes-agent/scripts/run-local.sh -d`. O script resolve a
imagem do serviço pelo Compose, constrói e valida a imagem PEE-103 padrão, ou
usa uma imagem explicitamente configurada sem reconstruí-la.

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

Esse replica set de um membro habilita a transação multi-documento usada na
prova técnica, mas não representa alta disponibilidade nem failover de
produção.

`.env.poc` não é movido, copiado ou exibido. Para validações Hermes, use os
scripts em `integrations/hermes-agent/scripts/` e registre apenas resultados
sanitizados.
