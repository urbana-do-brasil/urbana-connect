# Quickstart: validar a organização do monorepo

## Pré-requisitos

- checkout na branch `feat/pee-101`;
- Java 21, Node 24, Ruby e Docker Compose disponíveis conforme a validação
  aplicável;
- Docker Engine acessível para a etapa live;
- `.env.poc` criado localmente, com permissões restritas e ignorado pelo Git;
- nenhuma credencial deve ser colada em terminal, issue, log ou relatório.

## Navegação rápida

```text
apps/urbana-connect-api/  backend Java/Spring
apps/poc-chat/            chat React para teste manual
integrations/hermes-agent/ profile/plugin/scripts Hermes locais
infra/local-poc/          Compose e operação local completa
infra/kubernetes/         manifests Kubernetes
quality/conversation-corpus/ corpus Ruby
quality/system-e2e/       E2E cross-system
```

## Validação estrutural

Executar na raiz:

```bash
git branch --show-current
git diff --check
find apps integrations infra quality -maxdepth 2 -type d | sort
```

Conferir que a branch é `feat/pee-101`, que não há whitespace inválido e que os
diretórios alvo existem.

O contrato estrutural cross-system pode ser executado diretamente:

```bash
./quality/system-e2e/repository-structure.contract.sh
```

## Validação das aplicações

```bash
cd apps/urbana-connect-api
./gradlew --no-daemon --max-workers=1 test jacocoTestReport

cd ../poc-chat
npm run test -- --run
npm run typecheck
npm run lint
npm run build
```

Se a duplicata preexistente `PocReceptionWorker 2.java` continuar no worktree,
o build deve usar somente o init script temporário já registrado nas specs
anteriores, sem remover o arquivo. A limitação deve ser anotada como risco, não
mascarada.

## Validação Hermes e corpus

Os comandos devem ser executados a partir da raiz e usar paths novos:

```bash
./integrations/hermes-agent/scripts/smoke-contract.sh
./integrations/hermes-agent/scripts/smoke-isolation.sh
./integrations/hermes-agent/scripts/verify-tool-surface.sh
ruby quality/conversation-corpus/self-test.rb
```

O script de instalação do runtime deve ser usado somente quando necessário e
continuará obtendo o Hermes externo pelo pin documentado.

## Validação do stack local

```bash
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml config --quiet
docker compose --env-file .env.poc \
  -f infra/local-poc/docker-compose.poc.yml up -d --build
```

Não registrar a saída completa de `docker compose config`, porque ela pode
conter valores interpolados de secret. Usar os smoke tests e healthchecks para
evidência sanitizada.

Os cenários de navegador ficam em `apps/poc-chat/e2e/` porque usam a
configuração Playwright e as dependências do chat; execute-os a partir desse
diretório:

```bash
cd apps/poc-chat
npx playwright test
```

## E2E manual principal

1. Abrir o `poc-chat` pelo endereço documentado no Compose.
2. Criar uma conversa nova e enviar texto.
3. Confirmar que a mensagem aparece como inbound.
4. Aguardar a resposta do Hermes pelo polling já existente.
5. Confirmar a igualdade literal entre a resposta persistida/projetada e a
   bolha exibida na UI, usando somente identificadores não sensíveis.
6. Repetir em uma segunda conversa para comprovar isolamento de sessões.

## Critério de encerramento

A reorganização só é `verified` quando os paths novos forem usados pelos
comandos, as duas aplicações e os validadores passarem, o Compose preservar a
topologia e o round-trip Hermes → Mongo → HTTP → UI continuar literal. Se
Docker, credencial ou a duplicata impedir uma etapa, o status deve ser
`implemented_unverified` ou `blocked`, com evidência precisa.
