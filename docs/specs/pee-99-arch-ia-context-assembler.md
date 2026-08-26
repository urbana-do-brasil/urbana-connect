# Spec SDD — PEE-99: Arquitetura IA Conversacional — Context Assembler

## Metadados

- `Título da feature`: Arquitetura IA Conversacional — Context Assembler
- `Ticket Jira`: PEE-99
- `Status`: Draft
- `Responsável pela spec`: Review cruzado (Visão Claude + Visão Codex)
- `Branch`: `feature/PEE-99-arch-ia`
- `Contexto de branch`: `feature/* -> hml -> main`
- `Data`: 2026-05-01
- `Fonte principal`: [Refinamento Participação da IA v7](https://urbanadobrasil.atlassian.net/wiki/spaces/pro/pages/374865922)

> **Contrato de negócio relacionado — PEE-102 (2026-08-16):** o Context
> Assembler também deve suportar a retomada da conversa após responsabilidade
> humana. A semântica de negócio do evento `HUMANO -> URBA`, da precedência das
> decisões da arquiteta, do histórico completo e da retomada proativa está
> definida em [`pee-102-catalogo-e-contexto-operacional-urba.md`](pee-102-catalogo-e-contexto-operacional-urba.md).
> O contrato técnico que preserva o pass-through dos turnos normais e cria um
> canal interno separado está em
> [`pee-102-retomada-tecnica-humano-urba-hermes.md`](pee-102-retomada-tecnica-humano-urba-hermes.md).

---

## 1. Contexto

### O que existe hoje

A PEE-23 e PEE-98 entregaram um fluxo funcional de triagem e vendas no WhatsApp com:

- State machine controlando etapas (`GREETING` → `ICP_QUALIFICATION` → `SERVICE_DISCOVERY` → `AWAITING_CONFIRMATION` → `AWAITING_TERMS` → `AWAITING_PAYMENT_METHOD` → `PAYMENT_LINK_SENT`)
- Persistência de histórico de mensagens e slots conversacionais
- Catálogo de serviços consultável
- Conteúdo configurável via banco (`ConversationContentGateway`)
- IA via Gemini com dois métodos: `converse()` e `interpret()`
- Contrato de resposta estruturada (`ConversationalAiReply`) com slots, confiança e sugestão de avanço

### O que está faltando

A primeira tentativa de dar liberdade verbal à IA (homolog PEE-99) expôs problemas arquiteturais:

1. **Prompt monolítico**: o `buildConversationalPrompt` no `GeminiAiGateway` mistura identidade, regras, etapa, slots e catálogo numa única string sem hierarquia.
2. **Sem regras de turno**: não existem limites de tamanho de resposta, quantidade de perguntas por turno, nem restrição de paráfrase. O modelo improvisa o comportamento de turno.
3. **Sem ancoragem no script real**: o prompt por etapa usa descrições genéricas (ex: "coletar contexto pessoal leve") em vez do playbook real da Urba.
4. **Dupla chamada LLM**: em alguns fluxos, o sistema chama `converse()` e, se falhar, chama `interpret()` — duas chamadas ao modelo no mesmo turno.
5. **Sem escape de estagnação**: quando a conversa fica em loop numa etapa sem progredir, não há mecanismo automático de escape.
6. **Validação apenas estrutural**: o `isStructurallyValid()` verifica JSON, mas não detecta alucinação de serviço, preço incorreto, ou resposta excessivamente longa.

### Necessidade

Reorganizar a participação da IA como um **Assembler de Contexto Conversacional** com camadas hierarquizadas, separação explícita entre decisão e execução, e contratos formais por etapa.

---

## 2. Comportamentos esperados

### 2.1 Montagem de contexto

1. Dado que uma mensagem do cliente chegou, quando o orquestrador processar o turno, então o Context Assembler deve montar um contexto hierarquizado com as seguintes camadas, em ordem de prioridade: Core Identity, Operational Policy, Conversation Playbook, Business Knowledge, Session Memory e Current Turn.

2. Dado que o contexto montado exceda o limite de tokens do modelo, quando o Assembler aplicar a política de corte, então ele deve descartar camadas nesta ordem: Session Memory (reduz janela de histórico), Business Knowledge (mantém apenas serviço relevante), Conversation Playbook (mantém apenas etapa atual). Operational Policy e Core Identity nunca são descartadas.

3. Dado que uma etapa tenha playbook configurável no banco, quando o Assembler montar o contexto dessa etapa, então deve carregar o playbook da etapa ativa e incluir os anti-patterns associados.

### 2.2 Policy Engine e StepContract

4. Dado que a etapa ativa tenha um `StepContract` definido, quando o Policy Engine avaliar a resposta da IA, então ele deve verificar que o `action` está na lista `allowedActions` do contrato e rejeitar `action` presente em `forbiddenActions`.

5. Dado que a IA retorne `shouldAdvance = true`, quando o Policy Engine avaliar, então só deve autorizar o avanço se: (a) `confidence >= 0.85`, (b) todos os `requiredSlots` do `StepContract` estão preenchidos no nível mínimo exigido, (c) `suggestedNextStep` é válido para a etapa atual, e (d) não há conflito com regra operacional rígida.

6. Dado que a resposta da IA tenha sido rejeitada pelo Response Validator, quando o Policy Engine decidir o próximo passo, então ele deve aplicar o `fallbackBehavior` definido no `StepContract` da etapa ativa.

### 2.3 Circuit breaker de estagnação

7. Dado que N turnos consecutivos não tenham produzido progresso (nenhum slot novo preenchido E `shouldAdvance = false`), quando N atingir o `maxTurnsWithoutProgress` do `StepContract`, então o Policy Engine deve acionar o escape e o Action Executor deve enviar as `structuredEscapeOptions` definidas no contrato.

8. Dado que um novo slot tenha sido preenchido ou a etapa tenha avançado, quando o Policy Engine atualizar o estado, então o contador de turnos sem progresso deve ser zerado.

### 2.4 Resposta estruturada e validação

9. Dado que o Agent Adapter receba resposta do modelo LLM, quando a resposta for parseada, então deve seguir o contrato `ConversationalAiReply` com todos os campos obrigatórios (`replyText`, `action`, `slotUpdates`, `shouldAdvance`, `confidence`, `shouldOfferStructuredOptions`) presentes. Os campos opcionais (`suggestedNextStep`, `fallbackReason`) podem ser `null`.

10. Dado que o Response Validator avalie a resposta, quando `replyText` mencionar um serviço que não existe no catálogo ativo, ou contiver preço divergente do catálogo, então a resposta deve ser descartada e o motivo logado.

11. Dado que o Response Validator avalie a resposta, quando `replyText` exceder o limite de caracteres da política de turno, então a resposta deve ser descartada e o motivo logado.

12. Dado que `shouldAdvance = true` e `slotUpdates` esteja vazio, quando o Policy Engine avaliar, então ele deve verificar se os `requiredSlots` do `StepContract` já estavam preenchidos em turnos anteriores ou se o avanço veio de confirmação determinística. Se sim, o avanço é válido. Se não, o Policy Engine deve tratar como avanço indevido e aplicar fallback.

### 2.5 Unificação converse/interpret

13. Dado que o `AiGateway` receba uma requisição de interação com a IA, quando o Agent Adapter for chamado, então deve existir apenas o método `converse()`. O método `interpret()` deixa de existir como chamada separada ao modelo.

14. Dado que o fluxo anterior usasse `interpret()` como fallback após `converse()` falhar, quando essa situação ocorrer na nova arquitetura, então o Policy Engine deve aplicar o `fallbackBehavior` do `StepContract` em vez de refazer a chamada ao modelo.

### 2.6 Regras de turno (Operational Policy)

15. Dado que a IA gere uma resposta válida, quando o `replyText` for avaliado, então deve conter no máximo 3 frases curtas, exceto em resposta a pergunta complexa do cliente.

16. Dado que a IA gere uma resposta válida, quando o `replyText` for avaliado, então ele não deve repetir ou parafrasear o que o cliente acabou de dizer, exceto quando for confirmar para avançar de etapa.

17. Dado que a IA gere uma resposta válida, quando o `replyText` contiver perguntas, então deve conter no máximo 1 pergunta por turno.

18. Dado que a IA gere uma resposta, quando o `replyText` contiver metafala sobre processo ("Agora vou coletar...", "O próximo passo é..."), então a resposta deve ser considerada inválida pelo Response Validator.

### 2.7 Etapas que continuam determinísticas

19. Dado que a etapa ativa seja `AWAITING_CONFIRMATION`, `AWAITING_TERMS` ou `AWAITING_PAYMENT_METHOD`, quando o cliente enviar resposta, então o sistema deve continuar usando confirmação explícita (botão ou texto livre inequívoco) sem depender de confiança probabilística para avançar.

---

## 3. Contratos formais

### 3.1 StepContract

Cada etapa da state machine possui um contrato formal consumido pelo Policy Engine:

```
StepContract:
  step:                     # enum ConversationStep
  goal:                     # objetivo em linguagem natural
  requiredSlots:            # slots obrigatórios com nível mínimo
  optionalSlots:            # slots desejáveis, não bloqueiam avanço
  allowedActions:           # ações permitidas para a IA nesta etapa
  forbiddenActions:         # ações que a IA nunca pode propor
  advanceCriteria:          # condições objetivas para transição
  fallbackBehavior:         # ação ao receber resposta inválida
  maxTurnsWithoutProgress:  # limite de turnos sem slot novo
  structuredEscapeOptions:  # opções enviadas quando circuit breaker aciona
```

#### GREETING

```
step: GREETING
goal: entender se o cliente precisa de ajuda para descobrir o serviço ou se já sabe o que quer
requiredSlots:
  - needsDiscoveryHelp (confirmed)
optionalSlots: []
allowedActions:
  - ASK_CLARIFYING_QUESTION
  - CONFIRM_UNDERSTANDING
  - ACKNOWLEDGE_AND_ADVANCE
  - OFFER_STRUCTURED_OPTIONS
  - REPEAT_WITH_REFRAME
forbiddenActions:
  - PROPOSE_SERVICE
advanceCriteria:
  - needsDiscoveryHelp em nível CONFIRMED
  - confidence >= 0.85
  - suggestedNextStep == ICP_QUALIFICATION
fallbackBehavior: reenviar saudação com botões YES_HELP / NO_HELP
maxTurnsWithoutProgress: 2
structuredEscapeOptions: botões YES_HELP / NO_HELP
```

#### ICP_QUALIFICATION

```
step: ICP_QUALIFICATION
goal: coletar contexto pessoal leve (pronome, primeira experiência, ocupação)
requiredSlots: []
optionalSlots:
  - pronounPreference (tentative ou confirmed)
  - firstTimeHiringDesigner (tentative ou confirmed)
  - occupation (tentative ou confirmed)
allowedActions:
  - ASK_CLARIFYING_QUESTION
  - CONFIRM_UNDERSTANDING
  - ACKNOWLEDGE_AND_ADVANCE
  - REPEAT_WITH_REFRAME
forbiddenActions:
  - PROPOSE_SERVICE
  - OFFER_STRUCTURED_OPTIONS (exceto via circuit breaker)
advanceCriteria:
  - a IA tentou coletar pelo menos 1 slot OU o cliente sinalizou que não quer responder
  - confidence >= 0.85
  - suggestedNextStep == SERVICE_DISCOVERY
fallbackBehavior: repetir pergunta de ICP de forma diferente
maxTurnsWithoutProgress: 4
structuredEscapeOptions: mensagem direta avançando para SERVICE_DISCOVERY
```

#### SERVICE_DISCOVERY

```
step: SERVICE_DISCOVERY
goal: descobrir qual serviço do catálogo melhor atende o cliente
requiredSlots:
  - suggestedService (tentative)
optionalSlots: []
allowedActions:
  - ASK_CLARIFYING_QUESTION
  - CONFIRM_UNDERSTANDING
  - PROPOSE_SERVICE
  - OFFER_STRUCTURED_OPTIONS
  - ACKNOWLEDGE_AND_ADVANCE
  - REPEAT_WITH_REFRAME
forbiddenActions:
  - inventar serviço fora do catálogo
  - afirmar preço sem consultar catálogo
advanceCriteria:
  - suggestedService em nível TENTATIVE com valor válido do catálogo
  - confidence >= 0.85
  - suggestedNextStep == AWAITING_CONFIRMATION
fallbackBehavior: enviar lista interativa de serviços com cenários
maxTurnsWithoutProgress: 3
structuredEscapeOptions: lista interativa com serviços disponíveis e cenários
```

#### AWAITING_CONFIRMATION

```
step: AWAITING_CONFIRMATION
goal: confirmar com o cliente que o serviço sugerido é o correto
requiredSlots:
  - confirmedService (confirmed)
optionalSlots: []
allowedActions:
  - CONFIRM_UNDERSTANDING
  - OFFER_STRUCTURED_OPTIONS
  - REPEAT_WITH_REFRAME
  - ACKNOWLEDGE_AND_ADVANCE
forbiddenActions:
  - PROPOSE_SERVICE
  - ASK_CLARIFYING_QUESTION
advanceCriteria:
  - confirmedService em nível CONFIRMED por confirmação explícita (botão ou texto inequívoco)
  - NÃO depende de confidence probabilística — avanço é determinístico
fallbackBehavior: reapresentar o serviço com botões SIM / NÃO
maxTurnsWithoutProgress: 2
structuredEscapeOptions: botões SIM / NÃO / VER OUTROS SERVIÇOS
deterministic: true
```

#### AWAITING_TERMS

```
step: AWAITING_TERMS
goal: obter aceite explícito dos termos de uso
requiredSlots:
  - termsAccepted (confirmed)
optionalSlots: []
allowedActions:
  - CONFIRM_UNDERSTANDING
  - REPEAT_WITH_REFRAME
  - ACKNOWLEDGE_AND_ADVANCE
forbiddenActions:
  - PROPOSE_SERVICE
  - ASK_CLARIFYING_QUESTION
  - OFFER_STRUCTURED_OPTIONS
advanceCriteria:
  - termsAccepted em nível CONFIRMED por aceite explícito ("aceito", "sim, aceito") ou botão
  - NÃO depende de confidence probabilística — avanço é determinístico
  - texto ambíguo ("ok", "certo", "beleza") NÃO conta como aceite
fallbackBehavior: reenviar link dos termos e pedir aceite explícito
maxTurnsWithoutProgress: 3
structuredEscapeOptions: reenviar link + botão ACEITO
deterministic: true
```

#### AWAITING_PAYMENT_METHOD

```
step: AWAITING_PAYMENT_METHOD
goal: coletar a forma de pagamento escolhida pelo cliente
requiredSlots:
  - paymentMethod (confirmed)
optionalSlots: []
allowedActions:
  - CONFIRM_UNDERSTANDING
  - OFFER_STRUCTURED_OPTIONS
  - REPEAT_WITH_REFRAME
  - ACKNOWLEDGE_AND_ADVANCE
forbiddenActions:
  - PROPOSE_SERVICE
  - ASK_CLARIFYING_QUESTION
advanceCriteria:
  - paymentMethod em nível CONFIRMED com valor válido (PIX ou CARTAO)
  - NÃO depende de confidence probabilística — avanço é determinístico
fallbackBehavior: reenviar opções de pagamento com botões PIX / CARTÃO
maxTurnsWithoutProgress: 2
structuredEscapeOptions: botões PIX / CARTÃO DE CRÉDITO
deterministic: true
```

**Nota sobre etapas determinísticas:** os StepContracts com `deterministic: true` indicam que o Policy Engine não deve usar `confidence` para decidir avanço. O avanço depende exclusivamente de confirmação explícita validada por regra determinística (match de texto ou botão). O campo `confidence` da IA pode ser usado para logging, mas não influencia a decisão.

### 3.2 ConversationalAiReply

Contrato formal da resposta estruturada que o Agent Adapter devolve:

```
ConversationalAiReply:

  # Campos obrigatórios — sempre presentes
  replyText: string           # texto a enviar ao cliente (máx 3 frases curtas)
  action: enum                # ação pretendida (validada contra allowedActions do StepContract)
  slotUpdates: list           # alterações propostas de slot (pode ser lista vazia)
    - slot: enum              #   nome do slot (ConversationSlotName)
      value: string           #   valor proposto
      level: TENTATIVE | CONFIRMED
      confidence: float       #   0.0–1.0
      source: INFERRED | EXPLICIT
  shouldAdvance: boolean      # sugestão de avanço (Policy Engine valida)
  confidence: float           # confiança geral (0.0–1.0)
  shouldOfferStructuredOptions: boolean  # se deve acompanhar botões/lista

  # Campos opcionais — podem ser null
  suggestedNextStep: enum?    # próximo passo sugerido (null quando shouldAdvance=false)
  fallbackReason: string?     # presente apenas quando a IA não conseguiu saída confiável
```

Enums de `action`:

- `ASK_CLARIFYING_QUESTION` — pedir esclarecimento
- `CONFIRM_UNDERSTANDING` — confirmar entendimento parcial
- `PROPOSE_SERVICE` — sugerir serviço específico do catálogo
- `OFFER_STRUCTURED_OPTIONS` — solicitar que o orquestrador envie botões/lista
- `ACKNOWLEDGE_AND_ADVANCE` — reconhecer e avançar
- `REPEAT_WITH_REFRAME` — reformular a pergunta anterior
- `REQUEST_HUMAN_HANDOFF` — solicitar handoff humano

---

## 4. Separação de responsabilidades

### Princípio: o Policy Engine decide; o Action Executor executa; o Response Validator informa; o Context Assembler estrutura.

| Componente | Responsabilidade | Não faz |
|---|---|---|
| **Policy Engine** | Decide avanço, bloqueio, fallback e circuit breaker. Consome StepContracts. | Não executa ações de envio. |
| **Context Assembler** | Monta contexto hierarquizado a partir das 6 camadas. Aplica política de corte. | Não decide. Apenas estrutura. |
| **Agent Adapter** | Chama o modelo LLM. Exige resposta no contrato `ConversationalAiReply`. | Não decide. Apenas traduz. |
| **Response Validator** | Valida estrutura e conteúdo da resposta. Reporta ao Policy Engine. | Não decide o que fazer com a falha. |
| **Action Executor** | Envia texto, botão, lista, handoff. Atualiza slots e transiciona etapa. | Não contém lógica de negócio. |
| **Conversation State** | Mantém estado da jornada e slots. | Não toma decisões. |

### Fluxo de um turno

1. State Machine identifica a etapa atual.
2. Policy Engine carrega o StepContract e verifica o circuit breaker.
3. Se circuit breaker não disparou: Context Assembler monta o contexto → Agent Adapter chama LLM → Response Validator valida.
4. Se circuit breaker disparou: Policy Engine determina escape estruturado.
5. Policy Engine decide: aceitar resposta, aplicar fallback, ou acionar escape.
6. Action Executor executa a decisão.

### Context Assembler — contrato

**Entrada:**
- `ConversationStep` (etapa atual)
- `Conversation` (estado completo com slots e histórico)
- `List<ServiceCatalogItem>` (catálogo disponível)
- `StepContract` (contrato formal da etapa ativa)

**Saída:**
- `AssembledContext` — objeto hierarquizado com as 6 camadas

**Política de corte por limite de tokens (ordem de descarte):**
1. Session Memory: reduz janela de histórico
2. Business Knowledge: mantém apenas serviço relevante
3. Conversation Playbook: mantém apenas seção da etapa atual
4. Operational Policy: nunca descarta
5. Core Identity: nunca descarta

### Response Validator — regras

**Validação estrutural:**
- `action` presente e dentro do enum
- `confidence` na faixa 0.0–1.0
- `slotUpdates` com slots válidos do domínio e valores legítimos

**Validação de conteúdo:**
- `replyText` não excede limite de caracteres da política de turno
- `replyText` não menciona serviços fora do catálogo ativo
- `replyText` não contém preços divergentes do catálogo
- `shouldAdvance = true` com `slotUpdates` vazio → reportar ao Policy Engine para avaliação contextual (pode ser válido se requiredSlots já estavam preenchidos ou se avanço é determinístico)
- `suggestedNextStep` consistente com fluxo permitido da etapa
- `action` está na `allowedActions` do StepContract ativo

**Validação de política de turno:**
- `replyText` com mais de 3 frases → rejeitar
- `replyText` com mais de 1 pergunta → rejeitar
- `replyText` com metafala sobre processo → rejeitar

---

## 5. Camadas do contexto

### Core Identity

Define quem é a Urba. Conteúdo estável, raramente muda:

- "A Urba fala como uma atendente humana e próxima."
- "Prefere mensagens curtas e claras."
- "Usa emojis quando ajudam a dar calor e leveza, sem exagero."
- "Evita respostas longas demais ou resumos repetitivos."
- "Nunca deve parecer: menu robótico, assistente frio, atendente prolixo."

### Operational Policy

Regras duras com prioridade acima da persona:

- Brevidade: máximo 3 frases por resposta.
- Anti-paráfrase: não repetir o que o cliente disse, exceto para confirmar avanço.
- Limite de perguntas: máximo 1 por turno.
- Proibição de metafala: não anunciar o que vai fazer. Fazer.
- Anti-loop: se não coletou informação nova, fazer pergunta diferente ou oferecer opções.
- Nunca inventar serviço, preço ou link.
- Nunca prometer prazo de entrega.
- Nunca avançar etapa sem os slots mínimos preenchidos.

### Conversation Playbook

Adaptação do script operacional por etapa. Deve conter, para cada etapa:

- Exemplos de falas boas (extraídos do script real)
- Perguntas preferidas
- Emojis esperados
- Anti-patterns obrigatórios:
  - ❌ Repetir contexto que o cliente acabou de fornecer
  - ❌ Resumir antes de avançar quando o avanço já está claro
  - ❌ Anunciar processo em vez de executar
  - ❌ Fazer múltiplas perguntas num mesmo turno

Configurável no banco via `ConversationContentGateway` e versionável.

### Business Knowledge

Catálogo de serviços (via `ServiceCatalogGateway`): nome, tipo, preço, descrição, cenário, links de pagamento, disponibilidade. Factual.

### Session Memory

- Últimas N mensagens (janela controlada)
- Slots coletados com nível e confiança
- Contador de turnos sem progresso na etapa atual

### Current Turn

- Mensagem atual do cliente
- Etapa atual
- Objetivo da etapa (do StepContract)

---

## 6. Critérios de aceite

1. Existe um `StepContract` formal e completo para cada etapa da state machine: GREETING, ICP_QUALIFICATION e SERVICE_DISCOVERY (conversacionais) e AWAITING_CONFIRMATION, AWAITING_TERMS e AWAITING_PAYMENT_METHOD (determinísticas).
2. O `AiGateway` expõe apenas `converse()`. O método `interpret()` foi removido e sua lógica absorvida pelo contrato `ConversationalAiReply`.
3. O Context Assembler monta contexto hierarquizado com as 6 camadas, com política de corte documentada.
4. O Policy Engine consome StepContracts para decidir avanço, bloqueio e fallback.
5. O Policy Engine implementa circuit breaker de estagnação com `maxTurnsWithoutProgress` configurável por etapa.
6. O Response Validator valida estrutura, conteúdo e política de turno.
7. O Action Executor executa ações sem conter lógica de negócio.
8. As respostas da Urba nas etapas conversacionais têm no máximo 3 frases e 1 pergunta por turno.
9. A Urba não repete/parafraseia o que o cliente disse, exceto para confirmar avanço.
10. As etapas AWAITING_CONFIRMATION, AWAITING_TERMS e AWAITING_PAYMENT_METHOD continuam com lógica determinística.
11. Existe playbook com anti-patterns para GREETING, ICP_QUALIFICATION e SERVICE_DISCOVERY.
12. O sistema não faz mais de 1 chamada LLM por turno.

---

## 7. Edge cases

- IA retorna JSON inválido ou campo obrigatório ausente → Policy Engine aplica `fallbackBehavior` do StepContract.
- IA sugere serviço que não existe no catálogo → Response Validator rejeita, Policy Engine aplica fallback.
- IA retorna `shouldAdvance = true` mas slots mínimos não estão preenchidos → Policy Engine bloqueia avanço.
- IA propõe `action` que está em `forbiddenActions` da etapa → Response Validator rejeita.
- Cliente envia mensagem em etapa que não tem StepContract configurado (ex: `PAYMENT_LINK_SENT` ou etapa futura) → sistema usa fallback genérico seguro (mensagem de desculpas + botão de ajuda).
- Modelo LLM retorna `REPEAT_WITH_REFRAME` como ação de fallback própria → o Policy Engine aceita essa ação se ela constar em `allowedActions` do StepContract. Se não constar, aplica `fallbackBehavior` do contrato em vez de rejeitar silenciosamente.
- Catálogo muda enquanto conversa está em andamento → Assembler sempre consulta catálogo atualizado a cada turno.
- Circuit breaker aciona mas as `structuredEscapeOptions` falham ao enviar → logar erro, não travar a conversa.
- Cliente responde de forma ambígua e contraditória na mesma mensagem → IA deve pedir esclarecimento (`ASK_CLARIFYING_QUESTION`) em vez de escolher uma interpretação.
- `replyText` excede limite de caracteres → Response Validator rejeita, Policy Engine aplica fallback.
- Modelo LLM indisponível ou timeout → Agent Adapter retorna fallback, Policy Engine usa `fallbackBehavior`.
- Slot preenchido com valor que não pertence ao enum do domínio (ex: serviço inexistente) → validação de slot descarta o update, loga e continua.
- Cliente pede handoff humano durante qualquer etapa → detectar antes da chamada LLM, acionar handoff imediatamente.

---

## 8. Observabilidade e validação

### Logs esperados

- Etapa ativa e StepContract em uso a cada turno.
- Quais camadas entraram no contexto montado pelo Assembler.
- Quando a resposta foi rejeitada pelo Response Validator (com motivo específico: estrutura, conteúdo ou política de turno).
- Decisão do Policy Engine a cada turno (aceitar / fallback / circuit breaker).
- Contador de turnos sem progresso por etapa.
- Quando o circuit breaker aciona e quais `structuredEscapeOptions` foram enviadas.
- Quando slot update foi descartado por validação (com valor rejeitado e motivo).

### Testes unitários

- Montagem de contexto por etapa com todas as 6 camadas.
- Política de corte: quando excede limite, camadas são descartadas na ordem correta.
- Policy Engine respeita `allowedActions` e `forbiddenActions` do StepContract.
- Policy Engine bloqueia avanço quando `requiredSlots` não estão preenchidos.
- Circuit breaker aciona após `maxTurnsWithoutProgress` turnos sem progresso.
- Circuit breaker zera contador quando há progresso.
- Response Validator rejeita resposta com serviço fora do catálogo.
- Response Validator rejeita resposta com mais de 3 frases.
- Response Validator rejeita resposta com mais de 1 pergunta.
- Response Validator reporta `shouldAdvance=true` + sem slots novos → Policy Engine valida contextualmente.
- Policy Engine aceita avanço com slotUpdates vazio quando requiredSlots já estavam preenchidos.
- Policy Engine rejeita avanço com slotUpdates vazio quando requiredSlots não estão preenchidos.
- Fallback do StepContract é aplicado corretamente quando resposta é rejeitada.
- `REPEAT_WITH_REFRAME` é aceito como action válida em todas as etapas.

### Testes de integração

- Fluxo GREETING → ICP_QUALIFICATION → SERVICE_DISCOVERY → AWAITING_CONFIRMATION com cliente conversacional.
- Fluxo com cliente objetivo que avança rápido.
- Fluxo que aciona circuit breaker e recebe opções estruturadas.
- Fluxo com rejeição de serviço e retorno para SERVICE_DISCOVERY.
- Fluxo com handoff humano.

### Validação manual em homolog

- Cliente confuso consegue ser conduzido sem sentir fluxo robótico.
- Cliente objetivo avança rápido sem ser retardado por perguntas desnecessárias.
- Respostas da Urba são curtas, naturais e alinhadas ao script.
- A Urba não repete o que o cliente disse.
- A Urba faz no máximo 1 pergunta por turno.
- Emojis aparecem nos momentos esperados do script.
- Termos, pagamento e confirmação permanecem seguros.

---

## 9. Fora de escopo

- Agente totalmente autônomo sem state machine.
- Remoção da state machine.
- Pós-pagamento completo.
- Inbox humana / interface gráfica.
- Editor visual de prompts ou playbooks.
- Múltiplos canais além de WhatsApp.
- Versionamento e rollback de Playbook (Fase 2).
- Mover identity, policy e playbook para configuração versionável no banco (Fase 2).
- Memória de sessão avançada e resumo para handoff (Fase 3).
- A/B testing de Playbooks.
- Flow builder configurável via banco.

---

## 10. Dúvidas em aberto

1. **Estratégia de coleta de ICP**: a IA deve tentar coletar `pronounPreference`, `firstTimeHiringDesigner` e `occupation` numa ordem fixa ou livre? O script operacional sugere uma ordem (pronome → experiência → ocupação), mas a conversa natural pode não seguir essa sequência.
   - **Sugestão**: deixar a IA livre para coletar na ordem que a conversa fluir, desde que tente todas antes de avançar ou respeite o sinal do cliente de não querer responder.

2. **Localização dos StepContracts**: os StepContracts devem ficar em código (enums/records) ou em configuração no banco?
   - **Sugestão para Fase 1**: em código (records ou enums Java), para simplicidade e testabilidade. Migrar para banco na Fase 2.

3. **Migração de `TRIAGE_GUIDED` e `TRIAGE_DIRECT`**: as conversas existentes que ainda estão nesses steps legados devem ser migradas automaticamente na Fase 1?
   - **Sugestão**: sim, manter o `migrateLegacyDiscoveryStepIfNeeded` existente no `ConversationFlowService` e adaptá-lo para a nova arquitetura.
