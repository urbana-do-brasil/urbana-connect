# Feature Specification: WhatsApp Webhook Hermes Routing

**Feature Branch**: `feat/pee-101`
**Created**: 2026-08-11
**Status**: In implementation
**Input**: Trocar o motor do fluxo principal `/api/webhook/` para o Hermes,
reutilizando o caminho transparente já validado na POC.

## 1. Objetivo

Quando a aplicação receber uma mensagem textual pelo webhook oficial do
WhatsApp, o caminho ativo no perfil POC deve ser:

```text
WhatsApp webhook
  -> normalização do evento
  -> persistência inbound canônica
  -> sessão persistente do Hermes
  -> resposta textual do Hermes
  -> persistência outbound canônica
  -> envio do mesmo texto pela API do WhatsApp
```

O webhook continua responsável por validar o provedor e interpretar o
envelope. A Urbana Connect não deve montar contexto comercial, prefixar
identidade, escolher fallback conversacional ou reescrever o retorno do
Hermes.

## 2. Requisitos funcionais

- **FR-001**: manter o `GET /api/webhook` e a validação do objeto WhatsApp sem
  alteração de contrato.
- **FR-002**: no perfil Hermes/Poc, o `POST /api/webhook` deve encaminhar
  mensagens ao `ReceptionOrchestrator`, e não ao `ConversationFlowService`.
- **FR-003**: persistir o inbound antes do dispatch remoto e preservar o
  identificador do provedor para idempotência.
- **FR-004**: reutilizar a sessão persistente do Hermes por contato e enviar ao
  Hermes apenas o texto da pessoa. Respostas de botão/lista devem usar o título
  visível como texto conversacional; o id permanece metadado técnico.
- **FR-005**: persistir a resposta textual retornada pelo Hermes sem mutação
  observável e enviá-la, exatamente, para o número de origem pelo gateway
  WhatsApp.
- **FR-006**: não enviar resposta automática quando o turno for duplicado,
  inconclusivo, falhar tecnicamente ou for bloqueado por atendimento humano.
- **FR-007**: manter o caminho legado disponível fora do perfil Hermes/Poc,
  permitindo rollback de configuração sem apagar sua implementação nesta etapa.
- **FR-008**: preservar challenge, rejeição de provedor desconhecido e parsing
  dos metadados WhatsApp existentes.

## 3. Fora de escopo

- deploy, alteração de credenciais ou mudança de configuração da Meta;
- anexos, download de mídia e transcrição multimodal;
- streaming, filas externas ou alteração do contrato da Sessions API;
- remoção definitiva do `ConversationFlowService` e do cliente Gemini;
- mudanças em regras comerciais, pagamento ou handoff além do bloqueio técnico
  já existente no caminho de recepção.

## 4. Critérios de aceite

1. Teste de unidade prova que uma mensagem textual chega ao Hermes pelo
   `ReceptionOrchestrator`, não ao fluxo Gemini/local.
2. Teste prova igualdade literal entre `AgentOutput.message`, o texto enviado
   ao gateway WhatsApp e a mensagem outbound canônica persistida pelo
   orquestrador.
3. Teste prova que duplicata, falha e turno inconclusivo não disparam envio
   adicional ao WhatsApp.
4. Testes do webhook continuam cobrindo challenge, provedor inválido e
   parsing de texto/interativo.
5. A suíte Java completa e a validação focada do perfil POC passam. Qualquer
   validação externa da API Meta fica classificada separadamente, pois requer
   credenciais e um ambiente externo.

## 5. Riscos conhecidos

- O webhook permanece síncrono durante esta troca, como o fluxo legado; o
  timeout do Hermes pode aumentar o tempo até o `200` da Meta. A evolução para
  ack imediato com outbox de entrega é uma etapa posterior, não deve ser
  simulada nesta implementação.
- O envio real depende de `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`
  e conectividade com a Graph API; os testes locais usam gateway mockado.
