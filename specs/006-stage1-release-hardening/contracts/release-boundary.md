# Contract: fronteira do release local

## Deve entrar no commit

- `apps/urbana-connect-api/` sem fontes duplicadas ou artefatos gerados;
- `apps/poc-chat/` sem dependências instaladas, coverage ou reports;
- `integrations/hermes-agent/` com profile, plugin, scripts e documentação
  canônicos;
- `infra/local-poc/`, `infra/kubernetes/`, `quality/`, `contracts/`, docs,
  specs, workflows e regras de ignore relacionadas;
- `.env.poc.example` apenas quando contiver placeholders, nunca `.env.poc`.

## Deve ficar fora do commit

- `.env.poc` e qualquer token/chave real;
- `.codex/`, `.idea/`, `.DS_Store`, caches, `node_modules`, `build`, `dist`,
  `coverage`, `test-results`, `results` e volumes Docker;
- qualquer arquivo com sufixo ` 2` que não seja uma fonte canônica deliberada;
- planos pessoais ou documentos de outra iniciativa sem relação com PEE-101;
- código upstream do Hermes.

## Invariantes operacionais

- Compose continua com os mesmos serviços, portas, redes e volumes;
- frontend não recebe token em bundle, localStorage ou headers próprios;
- API POC continua usando `HERMES_BASE_URL` interno e persistência Mongo;
- webhook WhatsApp continua identificado como fluxo legado fora desta feature;
- o commit não deve depender de arquivos ignorados para compilar ou iniciar,
  exceto `.env.poc` explicitamente fornecido pelo operador local.
