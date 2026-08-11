# Quickstart: fechar e validar a etapa 1

Executar a partir de `/Users/emanueljoseguimaraesbrito/Documents/Desenvolvimento/workspace/urbana-connect`.

## 1. Conferir branch e segredos sem imprimi-los

```bash
git branch --show-current
git diff --check
git check-ignore -q .env.poc
test "$(stat -f '%Lp' .env.poc)" = 600
```

O branch deve ser `feat/pee-101`; `.env.poc` deve estar ignorado e com modo
`600`. Nunca execute `git add -A` antes de revisar a fronteira.

## 2. Contratos estáticos

```bash
./quality/system-e2e/repository-structure.contract.sh
./quality/system-e2e/release-boundary.contract.sh
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml config --quiet
```

Não imprimir a configuração renderizada do Compose.

## 3. Aplicações

```bash
cd apps/urbana-connect-api
JAVA_HOME=/Users/emanueljoseguimaraesbrito/.sdkman/candidates/java/21.0.12-tem \
  ./gradlew --no-daemon --max-workers=1 test jacocoTestReport

cd ../poc-chat
npm run test -- --run
npm run typecheck
npm run lint
docker compose --env-file ../../.env.poc \
  -f ../../infra/local-poc/docker-compose.poc.yml build poc-chat
```

O build nativo macOS pode depender do binding opcional local do `lightningcss`;
o build Docker Linux é a validação suportada quando o placeholder iCloud
persistir.

## 4. Runtime e round-trip

```bash
cd ../../
./integrations/hermes-agent/scripts/smoke-contract.sh
./integrations/hermes-agent/scripts/smoke-isolation.sh
./integrations/hermes-agent/scripts/verify-tool-surface.sh
ruby quality/conversation-corpus/self-test.rb
```

Com Docker acessível:

```bash
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml ps
curl -fsS http://127.0.0.1:8081/api/v1/readiness
curl -fsS http://127.0.0.1:3000/health
```

Depois execute os cenários Playwright de `apps/poc-chat/e2e/` e a prova literal
Hermes → Mongo → API → UI descrita em `specs/004-hermes-transparent-pass-through`.

## 5. Revisar e versionar

```bash
git status --short
git diff --cached --check
git diff --cached --name-status
git diff --cached --stat
```

Somente após confirmar a fronteira e o QA independente:

```bash
git commit -m "chore(pee-101): fechar POC Hermes e higiene de release"
```

Push não faz parte deste quickstart; ele depende da próxima decisão operacional.
