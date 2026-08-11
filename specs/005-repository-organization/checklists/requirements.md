# Requirements checklist: Organização do monorepo Urbana Connect

**Feature**: [spec.md](../spec.md)
**Branch**: `feat/pee-101`

## Spec e rastreabilidade

- [x] CHK001 A spec registra objetivo, ownership, escopo e fora de escopo.
- [x] CHK002 Cada user story possui resultado observável e critério de teste.
- [x] CHK003 `tasks.md` mapeia cada requisito funcional para uma ação verificável.
- [x] CHK004 A exceção da convenção numérica do Speckit está documentada sem
  alterar a branch solicitada.

## Estrutura e ownership

- [x] CHK005 `apps/urbana-connect-api/` contém somente a aplicação backend e
  seus testes/configurações associadas.
- [x] CHK006 `apps/poc-chat/` contém somente a aplicação web local e seus testes
  rápidos/configuração de container.
- [x] CHK007 `integrations/hermes-agent/` contém profile/plugin/pin/scripts
  locais e não contém fonte upstream do Hermes.
- [x] CHK008 `infra/local-poc/` é o entrypoint documentado do stack completo.
- [x] CHK009 `infra/kubernetes/` e `quality/` têm ownership e comandos claros.
- [x] CHK010 Não restam referências canônicas quebradas aos diretórios antigos.

## Compatibilidade e segurança

- [x] CHK011 `.env.poc` continua ignorado, local e ausente de diffs/saídas.
- [x] CHK012 Serviços, portas, redes, volumes, healthchecks e contratos HTTP
  permanecem equivalentes.
- [x] CHK013 O fluxo Hermes → Mongo → API → UI preserva a string literal.
- [x] CHK014 Duplicatas/arquivos ambíguos foram classificados sem descarte
  irreversível.

## Validação

- [x] CHK015 `git diff --check` e validadores de paths passam.
- [x] CHK016 Suíte Java/JaCoCo passa ou a exceção preexistente é evidenciada.
- [x] CHK017 Testes, typecheck, lint e build do chat passam.
- [x] CHK018 Scripts Hermes, profile/isolamento e corpus passam.
- [x] CHK019 E2E determinístico passa e E2E live é executado quando o ambiente
  permite.
- [x] CHK020 QA independente revisa a árvore final e classifica riscos residuais.

## Evidência de encerramento

QA independente executou o contrato estrutural, `git diff --check`, verificou
`.env.poc` ignorado com modo `600`, buscou referências proibidas e confirmou as
raízes canônicas. Todos passaram. O build nativo macOS do Vite permanece uma
limitação ambiental documentada; o build Linux dentro do container passou.
