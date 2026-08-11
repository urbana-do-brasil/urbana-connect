# Contratos compartilhados

Esta área contém somente contratos que atravessam mais de uma unidade do
monorepo. Contratos internos permanecem junto da aplicação ou da integração
que os possui; contratos de uma feature continuam versionados em sua pasta de
`specs/`.

## Fronteiras atuais

- `apps/poc-chat/` acessa somente a API HTTP pública da POC Urbana.
- `apps/urbana-connect-api/` persiste o transcript/projeção no MongoDB e chama
  a Sessions API do Hermes.
- `integrations/hermes-agent/` configura e valida o runtime Hermes externo.
- `infra/local-poc/` compõe os processos, proxies, redes e volumes locais.
- `quality/` observa contratos existentes; não é dependência de produção.

O mapa verificável dessas dependências está em
`specs/005-repository-organization/contracts/repository-boundaries.md`.
