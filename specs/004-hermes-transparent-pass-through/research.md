# Research: Hermes Transparent Pass-through

## Evidências que motivaram a mudança

- `ReceptionOrchestrator` atualmente injeta estado/fatos em
  `continuityInput` antes do Hermes.
- O mesmo orquestrador aplica reconciliação semântica, `ReceptionResponsePolicy`
  e `ensureFirstTurnIdentity` depois do Hermes.
- `NonProspectPolicy` pode produzir uma resposta local sem chamada ao Hermes.
- O fallback observado no transcript foi:
  `Não consigo confirmar essa informação com segurança. Posso esclarecer sua
  necessidade com base nas opções aprovadas?`
- A comparação de um mesmo turno mostrou texto raw diferente do outbound
  persistido, incluindo o prefixo `Olá! Sou a Urba...`.

## Decisões

1. O contrato de conteúdo é a string textual do Hermes, não uma resposta
   reinterpretada pelo backend.
2. A persistência canônica continua sendo a fonte de entrega ao chat.
3. Segurança de ferramenta, pagamento, handoff e idempotência não é removida;
   ela deve ser aplicada por autorização técnica e não por edição da fala.
4. O nome do contato visual permanece fora do payload Hermes.

## Inventário de consumidores revisado

- `AgentOutput` continua sendo o resultado técnico de um turno e é usado por
  `ReceptionTurn`, `TurnReceipt`, documentos Mongo e o controller REST.
- `HermesAgentOutputParser.parse` agora atende a conversa textual; a validação
  estrita de envelope legado ficou em `parseOperationalEnvelope` para usos
  operacionais explícitos.
- `AgentOutputReconciler` foi mantido para a fronteira operacional legada, mas
  não participa do caminho conversacional normal.
- `ReceptionTurnReconciliationService` usa o mesmo parser textual ao recuperar
  uma resposta pelo histórico Hermes, preservando cursor, lease e idempotência.
- `ReceptionResponsePolicy`, `NonProspectPolicy`, `continuityInput` e o prefixo
  de identidade não são mais chamados durante um turno conversacional normal.
- `MessageBubble` e a projeção React não transformam o texto; o CSS usa
  `white-space: pre-wrap` para não colapsar whitespace.

O arquivo `PocReceptionWorker 2.java` é uma duplicata preexistente no worktree.
Ele não foi removido; os testes Java foram executados com um init script
temporário fora do repositório que o exclui somente da compilação.

## Não decisões

- Não foi autorizado habilitar o dashboard Hermes nem alterar credenciais.
- Não se presume que o webhook real de WhatsApp seja necessário para a POC.
- Não se altera o comportamento de anexos nesta fase textual.
