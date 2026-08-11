# Tasks: WhatsApp Webhook Hermes Routing

## Phase 1: Contract and tests first

- [x] T001 Registrar spec e plano da troca do webhook para Hermes.
- [x] T002 Escrever teste do handler para texto literal Hermes → WhatsApp.
- [x] T003 Escrever regressões de duplicata, falha e turno inconclusivo sem
  envio ao WhatsApp.
- [x] T004 Atualizar testes MVC para provar seleção do handler Hermes no perfil
  habilitado, mantendo challenge e parsing.

## Phase 2: Implementation

- [x] T005 Criar handler Hermes-first usando o contrato canônico da recepção.
- [x] T006 Ajustar o mapper para títulos de respostas interativas serem texto
  conversacional, preservando ids como metadado.
- [x] T007 Trocar o controller para rotear o `POST /api/webhook` pelo handler
  Hermes quando habilitado e manter fallback legado fora do perfil.
- [x] T008 Atualizar wiring do perfil POC e documentação da configuração.

## Phase 3: Verification and handoff

- [x] T009 Executar testes focados e revisar o diff sem descartar alterações
  existentes.
- [x] T010 Executar suíte Java, profile Hermes e validações locais relevantes.
- [x] T011 Registrar evidências, riscos de timeout/Graph API e concluir o
  versionamento com commit e push.

## Evidence collected

- Test-first focused run: 11 tests passed with Java 21 explicit, covering the
  Hermes handler, literal WhatsApp delivery, duplicate/failure suppression,
  MVC routing, challenge/provider parsing, mapper and POC wiring.
- Full backend run: `./gradlew clean test jacocoTestReport bootJar` passed in
  3m31s with Java 21; no test failures, errors or skips were reported.
- `git diff --check` passed.
- Real Meta Graph delivery and credentials were not exercised; they remain an
  external validation boundary.

## Result

- Status: `verified` for the Hermes-first routing change in the local POC
  profile.
- The branch remains `feat/pee-101`; personal untracked `docs/plans/` files were
  intentionally preserved outside the commit.
