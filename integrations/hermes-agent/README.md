# Integração local do Hermes

Este diretório contém somente artefatos mantidos pela Urbana para configurar e
validar o runtime externo Hermes: profile, plugin `urbana-domain` e scripts
locais. O código upstream Hermes não é versionado aqui.

O runtime é fixado pelos scripts em `scripts/`: versão `v2026.8.3`, commit
`3c27eb6234bf91b8ceee9e9071591b31e9b148cb` e imagem local
`urbana-hermes-agent:0.20.0`. O Compose e os proxies ficam em
`infra/local-poc/docker-compose.poc.yml`.

Os scripts resolvem a raiz do repositório a partir de sua própria localização,
portanto podem ser chamados da raiz ou de outro diretório:

```bash
./integrations/hermes-agent/scripts/install-local.sh
./integrations/hermes-agent/scripts/smoke-contract.sh
./integrations/hermes-agent/scripts/smoke-isolation.sh
./integrations/hermes-agent/scripts/verify-tool-surface.sh
```

O arquivo `.env.poc` real permanece na raiz, fora do Git, e nunca deve ser
copiado para este diretório nem impresso em logs.
