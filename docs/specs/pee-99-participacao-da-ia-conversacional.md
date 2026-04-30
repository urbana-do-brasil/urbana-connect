# Spec SDD — PEE-99: Participação da IA na Condução Conversacional da Urba

## Metadados

- `Título da feature`: Participação da IA na Condução Conversacional da Urba
- `Ticket Jira`: PEE-99
- `Status`: Draft
- `Responsável pela spec`: Visão Codex
- `Branch`: `feature/PEE-99-participacao-ia`
- `Data`: 2026-04-30

---

## 1. Contexto

A PEE-23 entregou uma primeira versão funcional do fluxo de triagem e vendas da Urba no WhatsApp. Hoje o sistema já consegue:

- receber mensagens do cliente;
- persistir estado da conversa;
- persistir histórico de mensagens;
- consultar catálogo de serviços;
- conduzir a jornada até o envio do link de pagamento;
- usar IA para interpretar alguns textos livres.

O problema atual não é de funcionamento bruto, e sim de experiência. A Urba ainda transmite uma sensação de chatbot rigidamente roteirizado porque a IA atua principalmente como interpretadora de intenção, enquanto a state machine define quase toda a forma da conversa por mensagens fixas.

O script operacional da Urba pressupõe um comportamento diferente:

- descoberta por bate-papo;
- acolhimento de ambiguidade;
- capacidade de conversar com clientes que não sabem exatamente o que querem;
- uso de perguntas abertas antes de reduzir a conversa a opções estruturadas.

A direção aprovada é manter a state machine como autoridade do fluxo, mas mudar o papel da IA dentro dela:

- a state machine continua dizendo em que etapa a conversa está;
- a IA passa a conduzir a fala, a descoberta e a formulação da próxima interação dentro das regras daquela etapa.

Essa subtarefa existe para definir a arquitetura, o contrato comportamental e os guardrails dessa nova participação da IA.

---

## 2. Objetivo

Definir como a Urba deve evoluir de um fluxo menu-driven com IA auxiliar para um fluxo conversacional híbrido em que:

1. a state machine continue sendo a orquestradora da jornada;
2. a IA conduza verbalmente a interação dentro de etapas selecionadas;
3. o sistema passe a coletar dados progressivamente por conversa, e não apenas por clique;
4. botões e listas virem apoios operacionais e não o eixo principal da personalidade da Urba;
5. o modelo permaneça seguro, auditável e controlado comercialmente.

---

## 3. Escopo

### Dentro do escopo desta subtarefa

- definir o papel da IA em cada etapa conversacional da primeira iteração;
- definir quais etapas passam a ser conversacionais primeiro;
- definir os slots mínimos de coleta por conversa;
- definir onde a IA tem maior liberdade verbal e onde deve ser restrita;
- definir a relação entre conversa aberta, botões e listas;
- definir guardrails de prompt por etapa;
- definir os impactos necessários em persistência, contexto e orquestração;
- produzir uma base suficientemente clara para uma próxima implementação incremental.

### Fora do escopo imediato

- remover a state machine;
- permitir que a IA avance etapas sem validação objetiva;
- transformar a Urba em um agente totalmente aberto;
- implementar toda a jornada pós-pagamento;
- construir a interface gráfica humana;
- reescrever integralmente o fluxo atual nesta mesma entrega.

---

## 4. Decisões Validadas

### 4.1 Etapas conversacionais da primeira iteração

As primeiras etapas que devem ganhar condução verbal por IA são:

- `saudação + descoberta inicial`;
- `identificação de ICP`;
- `descoberta do serviço ideal`.

Justificativa:

- são as etapas em que a experiência humana mais pesa;
- são as etapas em que o script da Urba depende mais de bate-papo do que de seleção estruturada;
- permitem melhorar a conversa sem abrir cedo demais as partes mais sensíveis do fluxo.

### 4.2 Mapeamento para a state machine atual

A implementação não deve deixar esse refinamento solto em relação ao fluxo já existente.

Mapeamento recomendado da primeira iteração:

| Etapa conversacional | Estratégia na state machine | Observação |
|---|---|---|
| `saudação + descoberta inicial` | continua em `GREETING` | `GREETING` deixa de ser apenas saudação fixa e passa a conduzir abertura + leitura inicial do grau de clareza do cliente |
| `identificação de ICP` | novo `ConversationStep`: `ICP_QUALIFICATION` | etapa intermediária logo após `GREETING`, dedicada a pronome, primeira experiência e ocupação |
| `descoberta do serviço ideal` | novo `ConversationStep`: `SERVICE_DISCOVERY` | substitui a separação rígida entre `TRIAGE_GUIDED` e `TRIAGE_DIRECT` como conceito principal de descoberta |
| confirmação do serviço | permanece em `AWAITING_CONFIRMATION` | continua mais estruturada |
| termos | permanece em `AWAITING_TERMS` | continua mais estruturada |
| pagamento | permanece em `AWAITING_PAYMENT_METHOD` | continua mais estruturada |
| link enviado | permanece em `PAYMENT_LINK_SENT` | sem mudança conceitual nesta etapa |

Diretriz adicional:

- `TRIAGE_GUIDED` e `TRIAGE_DIRECT` deixam de ser a forma principal de pensar a descoberta;
- se ainda precisarem existir por compatibilidade de implementação incremental, devem ser tratadas como detalhe transitório da migração e não como modelo-alvo da nova spec.

### 4.3 Slots por etapa e condição de avanço

Os slots não devem ser tratados como uma lista global de obrigatórios da primeira iteração. Eles precisam ser avaliados por etapa.

| Etapa | Slot | Nível mínimo | Obrigatório para avançar? | Condição de avanço |
|---|---|---|---|---|
| `GREETING` | `needsDiscoveryHelp` | `confirmed` | sim | avançar quando a Urba entender se o cliente precisa de ajuda para descobrir o serviço ou se já chega com clareza suficiente |
| `ICP_QUALIFICATION` | `pronounPreference` | `tentative` ou `confirmed` | não | a etapa pode avançar mesmo sem resposta conclusiva, desde que a Urba tenha tentado a coleta ou o cliente tenha sinalizado que prefere não responder |
| `ICP_QUALIFICATION` | `firstTimeHiringDesigner` | `tentative` ou `confirmed` | não | mesma regra acima |
| `ICP_QUALIFICATION` | `occupation` | `tentative` ou `confirmed` | não | mesma regra acima |
| `SERVICE_DISCOVERY` | `suggestedService` | `tentative` | sim | avançar para `AWAITING_CONFIRMATION` quando a orquestração tiver um serviço sugerido válido e apresentável ao cliente |
| `AWAITING_CONFIRMATION` | `confirmedService` | `confirmed` | sim | avançar apenas com confirmação explícita do cliente, por botão ou linguagem livre inequívoca |
| `AWAITING_TERMS` | `termsAccepted` | `confirmed` | sim | avançar apenas com botão ou texto livre inequivocamente positivo |
| `AWAITING_PAYMENT_METHOD` | `paymentMethod` | `confirmed` | sim | avançar apenas com escolha objetiva e válida da forma de pagamento |

Observação:

- `confirmedService` e `paymentMethod` fazem parte da primeira iteração da feature, mas pertencem a etapas posteriores do fluxo e não podem bloquear descoberta precoce;
- `suggestedService` é o slot mínimo para concluir a etapa conversacional de descoberta e levar a conversa para confirmação.

### 4.4 Liberdade verbal da IA

A IA deve ter maior liberdade verbal em:

- saudação;
- identificação de ICP;
- descoberta da necessidade;
- investigação do serviço ideal;
- explicação do serviço sugerido.

A IA deve ser mais restrita em:

- confirmação final do serviço;
- aceite de termos;
- escolha da forma de pagamento;
- envio do link de pagamento;
- acionamento de atendimento humano.

### 4.5 Interações que continuam estruturadas

Mesmo com a IA ganhando papel conversacional, as seguintes interações continuam estruturalmente controladas:

- confirmação final de serviço;
- aceite de termos;
- forma de pagamento;
- envio de link;
- handoff humano.

A IA pode introduzir essas interações com linguagem mais natural, mas a coleta final deve continuar apoiada por regras objetivas, botões, listas ou validações explícitas.

### 4.6 Regra para aceite de termos

O aceite de termos deve aceitar:

- botão;
- texto livre.

Exemplos de texto válidos:

- `aceito`
- `sim, aceito`
- `ok, aceito`

Exemplos que **não** contam como aceite:

- `não aceito`
- `não li ainda`
- `ok`
- `certo`
- `beleza`
- qualquer mensagem ambígua sem aceite explícito

Regra segura:

- texto livre só deve contar como aceite quando a intenção positiva for inequívoca;
- validação por substring isolada é insuficiente;
- se houver dúvida, a Urba deve pedir confirmação novamente em vez de avançar.

---

## 5. Modelo Conceitual

### 5.1 Separação de responsabilidades

#### State machine

Responsável por:

- definir a etapa atual;
- definir quais transições são válidas;
- definir quais dados mínimos precisam existir para avançar;
- impedir saltos inválidos de fluxo;
- manter previsibilidade operacional.

#### IA conversacional

Responsável por:

- interpretar a mensagem do cliente no contexto da etapa atual;
- formular a resposta da Urba com tom natural;
- pedir esclarecimento quando a resposta do cliente for insuficiente;
- propor preenchimento de slots;
- sugerir se a etapa já tem informação suficiente para avançar.

#### Catálogo e regras comerciais

Continuam responsáveis por:

- preços;
- nome oficial dos serviços;
- descrição comercial dos serviços;
- links operacionais;
- disponibilidade dos serviços.

A IA não pode inventar esses dados.

### 5.2 Etapa conversacional

Cada etapa conversacional deve passar a ter, conceitualmente:

- `objetivo da etapa`
- `slots obrigatórios`
- `slots opcionais`
- `regras de avanço`
- `regras de contenção`
- `tom/persona desejados`
- `fallbacks`

Exemplo:

#### Etapa: descoberta inicial

- `objetivo`: entender se o cliente precisa de ajuda para descobrir o serviço ou se já chega com clareza
- `slot obrigatório`: `needsDiscoveryHelp`
- `IA pode`: acolher, perguntar, resumir parcialmente, reformular
- `IA não pode`: empurrar preço, prometer contratação, avançar sem sinal suficiente

---

## 6. Slots Conversacionais

O sistema deve deixar de pensar apenas em `currentStep` e passar a pensar também em dados coletados progressivamente por conversa.

### 6.1 Slots da primeira iteração

- `needsDiscoveryHelp`
- `pronounPreference`
- `firstTimeHiringDesigner`
- `occupation`
- `suggestedService`
- `confirmedService`
- `termsAccepted`
- `paymentMethod`

### 6.2 Slots previstos para evolução próxima

- `clarityLevel`
- `customerGoal`
- `discoveryMode`
- `handoffRequested`

### 6.3 Regras dos slots

- um slot pode ser `unknown`, `tentative` ou `confirmed`;
- a IA pode propor valor tentativo;
- a state machine só deve avançar quando os slots obrigatórios estiverem em nível suficiente para a etapa;
- o sistema deve manter rastreabilidade do que foi inferido e do que foi confirmado explicitamente.

---

## 7. Papel de Botões e Listas

Botões e listas continuam existindo, mas mudam de função.

### 7.1 Antes

- eram a linguagem principal da experiência.

### 7.2 Depois

- passam a ser atalhos operacionais;
- entram quando acelerarem a decisão;
- entram quando a ambiguidade estiver alta;
- entram quando a etapa exigir validação mais objetiva.

### 7.3 Diretriz

Regra geral:

1. conversar primeiro;
2. oferecer estrutura quando útil;
3. confirmar de forma objetiva quando necessário.

---

## 8. Guardrails por Etapa

Cada prompt de etapa deve receber contexto estruturado suficiente para evitar improvisação solta.

Campos mínimos do contexto enviado para a IA:

- etapa atual;
- objetivo da etapa;
- histórico recente da conversa;
- slots já preenchidos;
- slots ainda faltantes;
- catálogo real de serviços disponível;
- regras comerciais fixas;
- limites do que a IA pode afirmar;
- formato esperado de saída.

### 8.1 Formato esperado da saída da IA

O adapter de IA não deve devolver apenas texto. O contrato mínimo esperado nesta spec é estruturado e testável.

Formato-base esperado:

```json
{
  "replyText": "Entendi. Pelo que você me contou, parece que você quer renovar a fachada sem grandes obras. Faz sentido?",
  "action": "ASK_CLARIFYING_QUESTION",
  "slotUpdates": [
    {
      "slot": "suggestedService",
      "value": "DECOR_FACHADA",
      "level": "tentative",
      "confidence": 0.91,
      "source": "inferred"
    }
  ],
  "confidence": 0.91,
  "shouldAdvance": false,
  "suggestedNextStep": null,
  "shouldOfferStructuredOptions": false,
  "fallbackReason": null
}
```

Campos obrigatórios:

- `replyText`: texto a ser enviado ao cliente, quando a resposta for válida;
- `action`: ação pretendida pela IA dentro do conjunto permitido;
- `slotUpdates`: lista de alterações propostas de slot;
- `confidence`: confiança geral da interpretação atual, em faixa `0.0` a `1.0`;
- `shouldAdvance`: sinalização da IA de que entende haver dados suficientes para transição;
- `suggestedNextStep`: próximo passo sugerido, quando aplicável;
- `shouldOfferStructuredOptions`: se a orquestração deve preferir botões/listas na resposta;
- `fallbackReason`: justificativa estruturada quando a IA não conseguiu devolver uma saída confiável.

Enums mínimos:

### `action`

- `ASK_CLARIFYING_QUESTION`
- `CONFIRM_UNDERSTANDING`
- `PROPOSE_SERVICE`
- `OFFER_STRUCTURED_OPTIONS`
- `ACKNOWLEDGE_AND_ADVANCE`
- `REPEAT_WITH_REFRAME`
- `REQUEST_HUMAN_HANDOFF`

### `slotUpdates[].level`

- `tentative`
- `confirmed`

### `slotUpdates[].source`

- `inferred`
- `explicit`

### `suggestedNextStep`

- `GREETING`
- `ICP_QUALIFICATION`
- `SERVICE_DISCOVERY`
- `AWAITING_CONFIRMATION`
- `AWAITING_TERMS`
- `AWAITING_PAYMENT_METHOD`
- `PAYMENT_LINK_SENT`
- `null`

Regras de interpretação pela orquestração:

- `shouldAdvance` é apenas sugestão, nunca autoridade final;
- a orquestração só pode honrar `shouldAdvance = true` quando:
  - `confidence >= 0.85`;
  - os slots mínimos da etapa estiverem no nível exigido;
  - a ação proposta for permitida na etapa atual;
  - não houver conflito com regra operacional rígida.

Regras adicionais de segurança:

- `confirmedService`, `termsAccepted` e `paymentMethod` não podem avançar apenas por confiança alta; exigem confirmação explícita ou regra determinística aprovada;
- se a IA devolver JSON inválido, campo obrigatório ausente, enum fora do contrato ou slot impossível, a orquestração deve descartar a resposta estruturada, logar o evento e aplicar fallback seguro;
- o sistema não deve depender de texto puro da IA sem estrutura adicional.

---

## 9. Arquitetura Proposta

### 9.1 Orquestração

A aplicação precisa de uma camada orquestradora que:

- leia estado e slots da conversa;
- determine a etapa ativa;
- monte o contexto da IA;
- interprete a resposta estruturada da IA;
- aplique regras de avanço;
- decida quando enviar conversa aberta e quando enviar componente estruturado.

### 9.2 Persistência

A persistência já criada na PEE-98 deve evoluir para suportar:

- histórico integral de mensagens;
- conteúdo configurável;
- contexto progressivo da conversa;
- eventual distinção entre dado inferido e dado confirmado.

Essa evolução pode acontecer:

- expandindo `context` em `conversations`; ou
- criando estrutura separada de atributos conversacionais.

### 9.3 Conteúdo configurável

O conteúdo configurável deve deixar de ser apenas copy fixa e passar a incluir, no futuro:

- instruções auxiliares por etapa;
- tom esperado;
- frases-base;
- fallback textual.

Sem transformar a solução num CMS arbitrário logo de saída.

---

## 10. Comportamentos Esperados

1. Dado que a conversa esteja numa etapa conversacional, quando o cliente enviar texto livre, então a IA deve produzir a próxima fala da Urba dentro das regras da etapa atual.
2. Dado que a IA identifique parcialmente a necessidade do cliente, quando ainda faltarem dados obrigatórios, então a Urba deve continuar a conversa pedindo esclarecimento, sem avançar prematuramente.
3. Dado que a IA preencha slots suficientes para a etapa, quando a confiança e as regras forem compatíveis, então a state machine pode autorizar a transição.
4. Dado que a etapa permita apoio estruturado, quando a IA ou a orquestração entenderem que opções objetivas aceleram a conversa, então o sistema pode enviar botão ou lista como apoio.
5. Dado que a conversa esteja na fase de descoberta, quando o cliente não souber explicar claramente o que quer, então a Urba deve conseguir conduzir por bate-papo antes de reduzir para opções.
6. Dado que a conversa esteja numa etapa com restrição operacional, quando o sistema precisar confirmar serviço, termos ou pagamento, então a Urba pode usar tom natural, mas a confirmação final deve continuar objetiva.
7. Dado que o cliente aceite os termos, quando isso ocorrer por botão ou por texto livre válido, então a state machine deve reconhecer ambos os caminhos.
8. Dado que a IA proponha uma interpretação inconsistente com catálogo, preço ou regra operacional, quando a orquestração validar a saída, então o sistema deve bloquear essa interpretação e aplicar fallback seguro.
9. Dado que a IA esteja conversando dentro de uma etapa, quando não houver confiança suficiente para decidir, então a Urba deve pedir esclarecimento em vez de inventar entendimento.
10. Dado que a primeira iteração da feature seja implantada, quando o cliente interagir nas etapas de abertura, ICP e descoberta, então a conversa deve soar menos menu-driven sem perder controle de fluxo.

---

## 11. Critérios de Aceite

- Existe um modelo conceitual claro separando responsabilidade da state machine e da IA.
- A primeira iteração de etapas conversacionais está explicitamente delimitada.
- Os slots obrigatórios e opcionais da primeira iteração estão definidos.
- Está claro onde a IA tem mais liberdade verbal e onde ela deve ser mais restrita.
- Está explícito que confirmação de serviço, termos, pagamento e handoff continuam com maior controle operacional.
- O aceite de termos contempla botão e texto livre.
- A spec deixa claro como botões e listas passam a funcionar como apoio e não como eixo principal da experiência.
- A spec descreve os guardrails mínimos para prompt, contexto e resposta estruturada da IA.
- A spec prepara a implementação sem abrir espaço para transformar a Urba num agente sem trilhos.

---

## 12. Edge Cases

- cliente responde de forma ambígua e contraditória dentro da mesma etapa;
- IA sugere serviço com baixa confiança;
- cliente pula assunto e pergunta preço cedo demais;
- cliente muda de ideia no meio da descoberta;
- cliente fornece ICP parcial e depois corrige;
- IA tenta avançar sem slots mínimos preenchidos;
- cliente usa linguagem livre para aceite de termos com redação incompleta;
- cliente insiste em fluxo totalmente humano antes da conclusão da descoberta;
- o catálogo muda enquanto a conversa está em andamento;
- a IA devolve resposta estruturalmente inválida.

---

## 13. Observabilidade e Validação

### Logs esperados

- logs da etapa ativa no momento da decisão;
- logs de slots atualizados;
- logs quando a IA pedir esclarecimento;
- logs quando a orquestração bloquear interpretação da IA;
- logs quando o sistema optar por resposta estruturada em vez de conversa aberta.

### Testes esperados

- testes unitários de orquestração por etapa;
- testes de decisão de avanço baseados em slots;
- testes de aceite híbrido de termos;
- testes de fallback quando a IA vier com resposta inválida;
- testes do uso combinado entre conversa aberta e componentes estruturados.

### Validação manual esperada

- cliente confuso consegue ser conduzido sem sentir fluxo excessivamente robótico;
- cliente objetivo continua conseguindo avançar rápido;
- termos, pagamento e confirmação permanecem seguros;
- a Urba parece assistente humana guiando conversa e não apenas menu encadeado.

---

## 14. Fora de Escopo

- agente totalmente autônomo;
- remoção da state machine;
- pós-pagamento completo;
- inbox humana;
- editor visual de prompts;
- múltiplos canais além de WhatsApp nesta mesma frente.

---

## 15. Dúvidas em Aberto

As decisões principais foram validadas no refinamento. Antes da implementação, ainda pode ser necessário fechar:

- se `pronounPreference`, `firstTimeHiringDesigner` e `occupation` entram já na primeira entrega de código ou numa segunda iteração imediata;
- se os slots conversacionais ficam dentro de `conversations.context` ou em estrutura separada;
- se `SERVICE_DISCOVERY` já substitui de imediato `TRIAGE_GUIDED` e `TRIAGE_DIRECT` no código ou se haverá uma etapa de migração intermediária.
