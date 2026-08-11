# Tasks: Hermes Transparent Pass-through

**Input**: Design documents from `/specs/004-hermes-transparent-pass-through/`
**Status**: 20/20 tarefas executadas; resultado `verified`

## Phase 1: Specification and discovery

- [x] T001 Registrar spec, plano, contrato, modelo, quickstart e checklist em `specs/004-hermes-transparent-pass-through/`
- [x] T002 Incorporar o inventário independente dos consumidores do contrato antigo e registrar riscos nesta pasta

## Phase 2: Test-first contract

- [x] T003 [P] Escrever teste backend que prove que a mensagem atual chega ao Hermes sem wrapper de continuidade
- [x] T004 [P] Escrever teste backend que prove igualdade literal Hermes → outbound persistido → projeção
- [x] T005 [P] Escrever regressão para ausência de prefixo, fallback e interceptação local no caminho normal
- [x] T006 [P] Escrever teste do profile/contrato Hermes para resposta textual sem `nextAction` obrigatório

## Phase 3: Implementation — single backend/profile writer

- [x] T007 Remover o wrapper `continuityInput` do request conversacional normal
- [x] T008 Remover `ensureFirstTurnIdentity`, `ReceptionResponsePolicy` e fallback textual do caminho normal
- [x] T009 Remover/bypassar `NonProspectPolicy` e respostas sintéticas antes do Hermes
- [x] T010 Adaptar `AgentOutput`/parser/reconciler para preservar o texto Hermes e separar controles operacionais
- [x] T011 Ajustar o orquestrador, gateway e modelos somente onde necessário para persistir a saída literal
- [x] T012 Simplificar `integrations/hermes-agent/profile/SOUL.md` e validações do profile para conversa textual natural
- [x] T013 Atualizar testes existentes incompatíveis e adicionar cobertura dos invariantes de entrega/resiliência

## Phase 4: Integration and verification

- [x] T014 Revisar diff e compilar backend sem tocar nas alterações preexistentes não relacionadas; compilação normal continua bloqueada pela duplicata preexistente e foi validada com init script temporário
- [x] T015 Executar suíte Java completa e testes focados do pass-through
- [x] T016 Executar testes, typecheck, lint e build do `poc-chat`
- [x] T017 Executar scripts Hermes (contrato, validação do profile e isolamento) e corpus
- [x] T018 Executar E2E local com Docker, comparar resposta Hermes, Mongo, HTTP e UI por igualdade literal; determinístico e live aprovados
- [x] T019 Executar QA independente dos critérios, regressões, concorrência e falha técnica; critérios funcionais e validação live confirmados
- [x] T020 Registrar evidências finais, atualizar esta lista/checklist e classificar riscos residuais

## Resultado final da execução

- Os 20 itens de implementação, testes e documentação foram executados.
- O smoke live do Hermes passou com uma sessão nova e o E2E live passou com os
  três contatos reais, alternância e reload.
- A validação integral de um mesmo contato comparou a última mensagem
  `assistant` do histórico Hermes, o outbound de `reception_messages`, a
  projeção HTTP e o conteúdo da bolha no DOM: 83 caracteres e SHA-256
  `a8607341f05b37e72b556241020cd2176f7665ee046ff42fb9c901cf61272eda` em
  todas as camadas.
- O build normal do worktree continua impedido pela alteração preexistente
  `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/PocReceptionWorker 2.java`,
  que declara a mesma classe pública. O arquivo foi preservado; a suíte foi
  executada em Java 21 com um init script temporário fora do repositório que
  exclui somente essa cópia da compilação.
- Sessões antigas persistidas com o alias de modelo `hermes-agent` continuam
  sujeitas à rejeição do OpenRouter; sessões novas usam o modelo configurado e
  foram validadas com sucesso. Nenhum dado persistido foi removido.
