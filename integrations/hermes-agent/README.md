# Integração local do Hermes

Este diretório contém somente artefatos mantidos pela Urbana para configurar e
validar o runtime externo Hermes: profile, plugin `urbana-domain` e scripts
locais. O código upstream Hermes não é versionado aqui.

O runtime upstream de referência continua fixado pelos scripts em `scripts/`:
versão `v2026.8.3`, commit `3c27eb6234bf91b8ceee9e9071591b31e9b148cb` e
imagem base `urbana-hermes-agent:0.20.0`. Para validar a capability PEE-103 no
POC atual, o Compose constrói a imagem local
`urbana-hermes-agent:pee-103-2f5472a15` a partir do checkout irmão do Hermes,
usando o commit `2f5472a15a026b6bd5847ad65058f1565d2b40ba` e sem bind mount do
código-fonte. O valor pode ser substituído pela variável `HERMES_IMAGE`.

Os scripts resolvem a raiz do repositório a partir de sua própria localização,
portanto podem ser chamados da raiz ou de outro diretório:

```bash
./integrations/hermes-agent/scripts/install-local.sh
./integrations/hermes-agent/scripts/run-local.sh -d
./integrations/hermes-agent/scripts/smoke-contract.sh
./integrations/hermes-agent/scripts/smoke-isolation.sh
./integrations/hermes-agent/scripts/verify-tool-surface.sh
```

`install-local.sh` mantém a imagem upstream base pinada. O `run-local.sh`
constrói e valida a sobreposição PEE-103 usada pelo Compose; uma variável
`HERMES_IMAGE` explícita é tratada como imagem já publicada e não é
reconstruída.

O arquivo `.env.poc` real permanece na raiz, fora do Git, e nunca deve ser
copiado para este diretório nem impresso em logs.
