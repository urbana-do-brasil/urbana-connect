# Quickstart: POC local Hermes-first

Os comandos abaixo foram validados na branch consolidada `feat/pee-101`
com Docker Desktop ativo, JDK 21 e o perfil local isolado.

## Prerequisites

- JDK 21 disponível para o Gradle toolchain (o Gradle não aceita o JDK 11 do
  sistema).
- Docker Desktop ativo para Mongo, Hermes e a aplicação POC.
- Hermes Agent `v2026.8.3` instalado pelo script isolado e verificado contra o contrato nativo.
- Conta OpenRouter com créditos.
- `OPENROUTER_API_KEY` inserida localmente no arquivo de segredo do profile Hermes.

Nunca cole a chave em código, fixture, log, Jira ou conversa.

## 1. Prepare local secrets

```bash
docker info
cp .env.poc.example .env.poc
chmod 600 .env.poc
```

Preencher localmente os valores indicados. `.env.poc` deve permanecer ignorado pelo Git.

## 2. Install the isolated Hermes profile

```bash
./integrations/hermes-agent/scripts/install-local.sh
```

O script deve criar/copiar apenas o profile `urba-receptionist`, habilitar o plugin `urbana-domain` e validar que nenhuma ferramenta ampla está exposta. Ele não deve alterar o profile Hermes pessoal existente.

O setup não executa um turno LLM por padrão. O `smoke-contract.sh` valida
health, capabilities (incluindo `endpoints.toolsets`), tool surface, criação e
histórico de sessão, removendo a sessão de contrato ao final. O smoke de modelo
real é opt-in e exige uma chave OpenRouter local.

## 3. Build and start the isolated POC stack

```bash
./integrations/hermes-agent/scripts/install-local.sh
cd apps/urbana-connect-api
export JAVA_HOME=/Users/emanueljoseguimaraesbrito/.sdkman/candidates/java/21.0.12-tem
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootJar --offline --no-daemon --console=plain
cd ../..
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml build urbana-connect
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml up -d
./integrations/hermes-agent/scripts/smoke-contract.sh
./integrations/hermes-agent/scripts/verify-tool-surface.sh
curl -fsS http://127.0.0.1:8652/health
curl -fsS http://127.0.0.1:8081/api/v1/health
```

Para verificar o isolamento de rede e filesystem:

```bash
./integrations/hermes-agent/scripts/smoke-isolation.sh
```

## 4. Run deterministic tests first

```bash
cd apps/urbana-connect-api
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test --offline --no-daemon --console=plain
./gradlew check --offline --no-daemon --console=plain
cd ../..
python3 -m unittest discover -s integrations/hermes-agent/plugins/urbana-domain -p 'test*.py'
```

Se o host já tiver a porta `58003` ocupada por outro processo, o Testcontainers
pode não conseguir iniciar o Ryuk. Nesse caso, repita os comandos Gradle com
`TESTCONTAINERS_RYUK_DISABLED=true`; a execução validada neste ambiente usou
essa alternativa sem alterar o código da aplicação.

## 5. Run the synthetic corpus

```bash
./quality/conversation-corpus/run-local.sh \
  --base-url http://127.0.0.1:8081 \
  --repetitions 3 \
  --memory-seed-mode setup-events \
  --output quality/conversation-corpus/results
```

O `run-local.sh` lê de forma restrita o `HERMES_INTERNAL_TOOL_TOKEN` do
`.env.poc` para autenticar a API sintética; não é necessário copiar a chave
para outro arquivo nem expô-la no terminal.

O relatório deve conter:

- três execuções de cada persona;
- barreiras críticas e vazamentos;
- conclusão esperada e observada;
- memória recuperada no cliente recorrente;
- naturalidade, clareza e utilidade;
- duração, tokens e custo estimado.

## 6. Mandatory failure drills

Executar ao menos:

- evento duplicado;
- duas mensagens concorrentes do mesmo contato;
- contatos diferentes em paralelo;
- Hermes indisponível antes da resposta;
- sessão Hermes removida e reconstruída;
- ferramenta mutável repetida;
- resposta fora do schema;
- handoff seguido de nova mensagem;
- comprovante interpretado como imagem, mas ainda não aprovado.
- ferramenta chamada sem lease, com lease expirado e depois de sua revogação;
  um lease `EXPIRED` não pode ser reutilizado e exige sessão Hermes nova.
- conteúdo final JSON válido, porém divergente do ledger de ferramentas.

## 7. Promotion gate

Somente após o relatório local passar:

1. implantar em `hml`;
2. repetir o corpus sintético implantado;
3. revisar segurança, retenção e segredos;
4. promover para produção;
5. executar smoke test com contato controlado na API oficial do WhatsApp;
6. liberar gradualmente.
