# Inventário de artefatos locais e legado

Este inventário registra a classificação usada para fechar a POC na branch
`feat/pee-101`. Ele não contém credenciais nem copia conteúdo sensível.

## Quarentena reversível

As 15 cópias com sufixo ` 2` encontradas nos diretórios de código, integração,
qualidade e specs foram movidas, sem sobrescrever os arquivos canônicos, para:

`.codex/quarantine/006-stage1-release-hardening/`

Classificação:

- 11 cópias eram byte-a-byte idênticas ao arquivo canônico;
- 4 eram versões antigas divergentes: o teste Playwright, o worker Java, o
  profile `SOUL.md` e o smoke Hermes;
- a cópia Java divergente impedia a compilação por declarar uma classe pública
  no nome de arquivo errado;
- a quarentena é local, ignorada pelo Git e pode ser recuperada manualmente se
  uma comparação histórica for necessária.

## Arquivos locais preservados fora do Git

- `.env.poc`: credenciais/variáveis locais, ignorado e não exibido;
- `.codex/config.toml`: configuração específica da máquina;
- `.codex/quarantine/003-duplicate-java-sources/`: quarentena preexistente;
- resultados de Gradle, Node, Playwright, corpus e builds Docker: artefatos
  gerados e ignorados.

## Legado fora do caminho Hermes-first da POC

Os seguintes itens permanecem no código ou no histórico de trabalho por
compatibilidade e não foram removidos nesta feature:

- `WebhookController` e o caminho `/api/webhook`, ainda conectado ao fluxo
  legado de WhatsApp/Gemini;
- `ConversationFlowService`, `GeminiAiGateway` e políticas conversacionais do
  caminho legado;
- `app/docker-compose.yml` e `app/dev-env.sh`, substituídos como ponto de
  entrada local pelo `infra/local-poc/docker-compose.poc.yml`.

A integração WhatsApp → Hermes é um próximo ticket. A POC validada nesta branch
usa `/api/poc/conversations/{contactAlias}/messages`, que persiste a entrada,
encaminha o texto ao Hermes e projeta a resposta literal; não se deve tratar o
webhook legado como prova de integração produtiva com Hermes.

## Fora do conjunto versionável

Planos pessoais em `docs/plans/`, arquivos de IDE/sistema, segredos reais,
quarentenas `.codex` e resultados de execução não fazem parte do commit de
fechamento.
