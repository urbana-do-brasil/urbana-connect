# Spec SDD — PEE-98: Fundação de Mensagens e Script Configurável da Urba

## Metadados

- `Título da feature`: Fundação de Mensagens e Script Configurável da Urba
- `Ticket Jira`: PEE-98
- `Status`: Draft
- `Responsável pela spec`: Visão Codex
- `Branch`: `feature/PEE-98-fundacao-mensagens-script`
- `Data`: 2026-04-28

---

## 1. Contexto

PEE-23 entregou a primeira versão funcional do fluxo de triagem e vendas da Urba dentro do WhatsApp. Hoje o sistema já consegue receber mensagens, conduzir a state machine principal, consultar o catálogo de serviços no MongoDB e enviar o link de pagamento.

O problema é que essa primeira versão ainda está presa a duas limitações estruturais:

1. a aplicação persiste apenas o estado resumido da conversa, sem guardar o histórico integral das mensagens de entrada e saída;
2. a copy operacional da Urba continua majoritariamente hardcoded no código, o que obriga ajuste em código e novo deploy para cada revisão de texto, tom ou script.

Essas limitações eram toleráveis no primeiro corte do fluxo, mas deixam de ser aceitáveis quando o destino planejado já está explícito:

- uma interface própria de atendimento, semelhante ao WhatsApp Web, capaz de mostrar o histórico integral da conversa;
- handoff humano com contexto real e, futuramente, envio de mensagens humanas pelo mesmo canal;
- edição simples de script, persona e mensagens operacionais sem depender de mudanças no código.

O diagrama de refinamento consolidado no Confluence deixa claro que o banco de dados precisa deixar de servir apenas como estado resumido e catálogo, passando a suportar também:

- histórico operacional de mensagens;
- configuração textual da Urba;
- evolução futura para uma inbox humana.

Esta subtarefa existe para preparar essa fundação agora, reduzindo retrabalho arquitetural depois.

---

## 2. Objetivo

Definir e implementar a base estrutural para que a Urba:

1. persista mensagens inbound e outbound em uma estrutura própria;
2. mantenha `conversations` como documento resumido de estado, sem misturar transcript completo no mesmo documento;
3. carregue mensagens operacionais, copy e persona a partir do banco em vez de hardcode;
4. prepare o handoff humano e o modelo de dados para consumo futuro por uma interface gráfica de atendimento;
5. preserve a state machine do fluxo em código nesta etapa.

---

## 3. Escopo

### Dentro do escopo

- criação de uma persistência própria para histórico de mensagens;
- persistência de mensagens de entrada do cliente e mensagens de saída da Urba;
- distinção explícita entre mensagens do usuário, do bot e, futuramente, do humano;
- criação de uma persistência própria para conteúdo configurável da Urba;
- externalização da copy principal usada no fluxo conversacional;
- adaptação do handoff humano para consumir contexto real vindo do histórico persistido;
- leitura do conteúdo textual da Urba via banco, com fallback controlado quando necessário;
- documentação do contrato de dados e do comportamento esperado antes da implementação final.

### Fora do escopo imediato

- construção da interface gráfica de atendimento;
- envio de mensagens humanas por interface própria;
- flow builder visual;
- state machine 100% configurável por banco;
- edição automática de whiteboards;
- analytics avançado ou relatórios operacionais complexos.

---

## 4. Modelo de Dados Proposto

### 4.1 `conversations`

`conversations` continua existindo como estado resumido da jornada.

Campos atuais continuam válidos:

- `phoneNumber`
- `status`
- `currentStep`
- `selectedService`
- `context`
- `createdAt`
- `updatedAt`
- `expiresAt`

Evoluções esperadas:

- permitir flags ou timestamps operacionais ligados a handoff humano quando fizer sentido;
- não usar este documento para armazenar o transcript integral da conversa.

### 4.2 `conversation_messages`

Nova collection para armazenar o histórico granular.

Estrutura mínima esperada:

```json
{
  "_id": "ObjectId",
  "conversationId": "ObjectId",
  "phoneNumber": "+5583999999999",
  "channel": "WHATSAPP",
  "direction": "INBOUND",
  "senderType": "USER",
  "messageType": "TEXT",
  "rawText": "quero falar com alguém",
  "interactiveReplyId": null,
  "providerMessageId": "wamid.xpto",
  "createdAt": "2026-04-28T12:00:00Z",
  "metadata": {
    "stepAtReception": "AWAITING_CONFIRMATION"
  }
}
```

Campos mínimos:

- `conversationId`
- `phoneNumber`
- `channel`
- `direction`
- `senderType`
- `messageType`
- `rawText`
- `interactiveReplyId`
- `providerMessageId`
- `createdAt`
- `metadata`

Enums esperados:

- `direction`: `INBOUND` | `OUTBOUND`
- `senderType`: `USER` | `URBA_BOT` | `HUMAN_AGENT`
- `messageType`: `TEXT` | `INTERACTIVE_BUTTON` | `INTERACTIVE_LIST` | `SYSTEM`

Observações:

- `rawText` deve guardar o conteúdo textual exibível da mensagem;
- `interactiveReplyId` deve ser persistido quando a entrada vier de botão/lista;
- `metadata` pode guardar contexto técnico mínimo útil sem virar dump bruto descontrolado do payload;
- o objetivo principal é suportar leitura operacional futura, não arquivar payloads arbitrários sem critério.

### 4.3 `conversation_content`

Nova collection para conteúdo configurável da Urba.

Estrutura sugerida:

```json
{
  "_id": "ObjectId",
  "key": "GREETING_TEXT",
  "channel": "WHATSAPP",
  "scope": "FLOW",
  "value": "Olá! Tudo bem? ...",
  "active": true,
  "updatedAt": "2026-04-28T12:00:00Z"
}
```

Exemplos de chaves esperadas:

- `GREETING_TEXT`
- `DIRECT_TRIAGE_TEXT`
- `GUIDED_TRIAGE_PROMPT`
- `TERMS_TEXT`
- `PAYMENT_METHOD_TEXT`
- `CLOSING_TEXT`
- `HUMAN_HANDOFF_ACK`
- `FALLBACK_UNKNOWN_INPUT`

Objetivo desta collection:

- permitir ajuste de copy, tom e persona sem deploy;
- manter a orquestração em código, externalizando somente o conteúdo configurável;
- viabilizar futura interface administrativa simples.

### 4.4 `services`

Permanece como catálogo comercial já configurável.

Não deve absorver:

- histórico conversacional;
- copy operacional genérica da Urba;
- conteúdo de persona fora do escopo comercial do serviço.

---

## 5. Comportamentos Esperados

1. Dado que uma mensagem inbound do cliente chega ao sistema, quando ela for recebida pelo webhook, então ela deve ser persistida em `conversation_messages` antes ou junto do processamento do fluxo, sem depender de a mensagem causar transição de etapa.
2. Dado que a Urba envia uma resposta ao cliente, quando a mensagem outbound for enviada, então ela também deve ser persistida em `conversation_messages` com `senderType = URBA_BOT`.
3. Dado que a entrada do cliente vier por botão ou lista, quando a mensagem for persistida, então o sistema deve guardar `interactiveReplyId` para preservar o contexto operacional da escolha.
4. Dado que uma conversa esteja em andamento, quando mensagens forem persistidas, então `conversations` deve continuar representando apenas o estado resumido da jornada, sem incorporar o transcript completo.
5. Dado que o handoff humano seja solicitado, quando o sistema notificar a equipe, então ele deve usar o histórico recente persistido para compor contexto melhor do que apenas a última mensagem recebida.
6. Dado que a Urba precise enviar copy operacional do fluxo, quando o conteúdo correspondente existir em `conversation_content`, então o sistema deve lê-lo do banco em vez de usar constante hardcoded.
7. Dado que uma chave de conteúdo configurável esteja ausente ou inválida, quando o sistema precisar responder ao cliente, então ele deve aplicar fallback controlado para uma copy segura e observável, sem quebrar o fluxo.
8. Dado que novos ajustes de script/persona sejam necessários, quando os textos forem alterados na persistência de conteúdo, então a Urba deve conseguir refletir essas alterações sem necessidade de novo deploy.
9. Dado o plano futuro de inbox humana, quando o modelo for implementado, então ele deve permitir distinguir claramente mensagens do usuário, do bot e de humano futuro usando `senderType`.
10. Dado que a interface gráfica não faz parte desta etapa, quando esta subtarefa for concluída, então o sistema ainda não precisa ter UI própria, mas a base de dados deve estar preparada para ela.

---

## 6. Critérios de Aceite

- Existe uma nova persistência própria para mensagens, separada de `conversations`.
- O sistema persiste mensagens inbound e outbound com campos mínimos suficientes para leitura operacional futura.
- O contrato de dados diferencia `direction`, `senderType` e `messageType`.
- O handoff humano passa a ter caminho claro para usar histórico real da conversa.
- A copy operacional principal da Urba deixa de depender exclusivamente de constantes hardcoded.
- A aplicação mantém a state machine em código nesta etapa.
- O desenho final evita acoplamento indevido entre estado da conversa, transcript e conteúdo configurável.
- A spec deixa explícito o que é fundação desta etapa e o que permanece fora do escopo.

---

## 7. Edge Cases

- mensagem inbound sem texto e sem `interactiveReplyId`;
- erro ao persistir mensagem recebida;
- erro ao persistir mensagem outbound depois do envio;
- conteúdo configurável ausente para uma chave obrigatória;
- conteúdo configurável presente, mas vazio;
- histórico muito grande para ser enviado integralmente em handoff humano;
- duplicidade de mensagem recebida pelo webhook;
- mensagens técnicas ou automáticas da plataforma que não devam entrar como mensagem exibível para operador humano;
- migração de conversas antigas que ainda não possuem histórico persistido.

---

## 8. Observabilidade e Validação

### Logs esperados

- registro de persistência de mensagem inbound;
- registro de persistência de mensagem outbound;
- registro quando houver fallback para conteúdo hardcoded por ausência de chave configurável;
- registro de composição de contexto para handoff humano;
- logs sem vazamento excessivo de conteúdo sensível.

### Testes esperados

- testes unitários para mapeamento e persistência de `conversation_messages`;
- testes unitários para leitura de `conversation_content`;
- testes de integração com Mongo para novas collections;
- testes de fluxo garantindo que persistência de mensagens não quebra a state machine;
- testes de fallback para conteúdo ausente;
- testes de handoff usando histórico recente persistido.

### Smoke/manual validation

- enviar mensagem real em homolog e confirmar registro em `conversation_messages`;
- validar que mensagens outbound da Urba também aparecem no histórico;
- alterar uma copy configurável em homolog e confirmar reflexo sem rebuild;
- solicitar handoff humano e verificar contexto enriquecido.

---

## 9. Estratégia Incremental de Implementação

Sequência preferencial:

1. criar modelo e gateway de `conversation_messages`;
2. persistir inbound e outbound sem alterar a lógica funcional do fluxo;
3. criar modelo e gateway de `conversation_content`;
4. externalizar a copy prioritária da Urba;
5. adaptar handoff para consumir histórico real;
6. consolidar testes e smoke test em homolog.

Objetivo da sequência:

- reduzir risco de regressão no fluxo atual;
- isolar a fundação estrutural antes de qualquer preocupação com interface gráfica;
- permitir validação parcial por camada.

---

## 10. Dúvidas em Aberto

- quantas mensagens recentes devem compor o contexto padrão de handoff humano?
- `metadata` deve guardar somente contexto normalizado ou parte controlada do payload original da Meta?
- a edição de conteúdo configurável será feita inicialmente por acesso direto ao banco ou já precisamos prever endpoint administrativo simples?
- o carregamento de conteúdo configurável deve ser consultado a cada uso ou pode ter cache curto em memória?
- quais textos entram já nesta primeira extração para banco e quais podem permanecer temporariamente em fallback?
