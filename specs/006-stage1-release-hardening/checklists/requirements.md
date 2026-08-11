# Release Readiness Checklist: Fechamento da POC Hermes

**Purpose**: verificar que a etapa local está funcional, versionável e sem
afirmações falsas sobre o webhook WhatsApp.
**Created**: 2026-08-11
**Feature**: [spec.md](../spec.md)
**Branch**: `feat/pee-101`

## Escopo e rastreabilidade

- [x] CHK001 A spec delimita POC local e webhook WhatsApp legado.
- [x] CHK002 Plano, contratos, quickstart e tasks apontam para a mesma branch.
- [x] CHK003 Nenhum critério depende de decisão de produto não confirmada.

## Higiene e segurança

- [x] CHK004 `.env.poc` permanece ignorado, local e sem conteúdo exposto.
- [x] CHK005 `.codex`, IDE, caches, resultados e artefatos gerados não entram no commit.
- [x] CHK006 Cópias ` 2` foram comparadas, classificadas e preservadas de forma reversível.
- [x] CHK007 A duplicata `PocReceptionWorker 2.java` não participa do source set canônico.
- [x] CHK008 Scanner sanitizado não encontra padrão de secret em arquivos versionáveis.

## CI e runtime

- [x] CHK009 Todos os SHAs de Actions usados pelo workflow frontend existem e têm 40 caracteres.
- [x] CHK010 Backend e frontend continuam apontando para `apps/`.
- [x] CHK011 A readiness da API responde `READY` quando Mongo está disponível.
- [x] CHK012 SMTP opcional não derruba o sinal obrigatório da POC.
- [x] CHK013 Compose mantém serviços, portas, redes, volumes e healthchecks exigidos.
- [x] CHK014 Chat depende da API saudável, não somente do processo iniciado.

## Regressão Hermes-first

- [x] CHK015 Suíte Java e cobertura passam com a exceção preexistente classificada.
- [x] CHK016 Testes, typecheck, lint e build Docker do chat passam.
- [x] CHK017 Smokes Hermes, isolamento, superfície, plugin e profile passam.
- [x] CHK018 Corpus e Playwright passam.
- [x] CHK019 Round-trip literal Hermes → Mongo → API → UI passa.

## Staging e commit

- [x] CHK020 `git diff --cached --check` passa.
- [x] CHK021 Diff staged contém destinos canônicos completos e nenhuma deleção acidental.
- [x] CHK022 QA independente aprova a árvore e os riscos residuais.
- [x] CHK023 Commit é criado em `feat/pee-101` somente após todos os itens anteriores.

## Resultado

`verified`: 23/23 itens atendidos. A migração do webhook WhatsApp para Hermes
permanece explicitamente fora do escopo e é o próximo ticket.

## Notas

- O webhook real WhatsApp/Gemini continua fora desta feature e deve virar uma
  próxima especificação.
- O build nativo macOS do Vite pode permanecer limitado pelo binding opcional
  `lightningcss`; o build Docker Linux é a evidência suportada.
