# PEE-102 — Retomada técnica HUMANO → URBA no Hermes

## Metadados

- **Ticket:** [PEE-102](https://urbanadobrasil.atlassian.net/browse/PEE-102)
- **Spec de negócio:** [Catálogo de serviços e contexto operacional da Urba](pee-102-catalogo-e-contexto-operacional-urba.md)
- **Status:** discovery técnica concluída; implementação não iniciada
- **Data:** 2026-08-16
- **Escopo:** contrato técnico da responsabilidade da conversa, sincronização de contexto e retomada proativa

## 1. Objetivo

Definir como uma conversa que estava sob responsabilidade da arquiteta volta
para a Urba sem pedir que o cliente repita decisões, sem perder mensagens e sem
permitir que o Hermes responda antes de receber o contexto humano completo.

Esta spec detalha o item 12 da spec de negócio. Ela não autoriza nem inicia a
implementação.

## 2. Evidências do estado atual

| Capacidade atual | Evidência | Lacuna para a retomada |
|---|---|---|
| Responsabilidade exclusiva | `ReceptionConversation.mode` possui `AI` e `HUMAN` | Não existe estado de sincronização nem transição de volta |
| Handoff para humano | `HumanHandoffService.enterHumanMode(...)` | O próprio serviço declara que não implementa `HUMAN -> AI` |
| Concorrência otimista | `ReceptionConversation.version` e save Mongo por compare-and-set | Falta aplicar a mesma proteção ao comando de retorno |
| Transcript canônico | `ReceptionMessage` distingue `CONTACT`, `URBA`, `HUMAN` e `SYSTEM` | A POC ainda não possui ingresso explícito para mensagem da arquiteta |
| Sessão persistente | `HermesSessionService` mantém uma sessão Hermes ativa por contato | Mensagens ocorridas em `HUMAN` não entram na memória do Hermes |
| Turnos resilientes | PEE-101 impede execução remota concorrente e trata timeout ambíguo | O evento de retomada precisa reutilizar essas garantias |
| Pass-through transparente | O turno normal envia ao Hermes apenas a mensagem atual do cliente | Um evento interno não pode ser disfarçado de mensagem do cliente |
| Interface de teste | A projeção e o frontend entendem `AI` e `HUMAN` | Faltam ação de devolução, progresso da sincronização e falha segura |
| Roteamento | O webhook pode escolher `reception/Hermes` ou o `ConversationFlowService` legado | Os dois caminhos possuem semânticas diferentes de handoff |

Conclusão: o transcript Mongo continua sendo a fonte canônica. A sessão Hermes
é uma projeção conversacional que precisa ser sincronizada antes de voltar a
responder.

## 3. Escopo

### 3.1 Incluído

- registro canônico das mensagens enviadas pela arquiteta;
- ação da plataforma “de responsabilidade da Urba agora”;
- transição concorrente e idempotente de responsabilidade;
- evento durável de retomada;
- sincronização do histórico humano com a sessão Hermes;
- decisão entre mensagem proativa, espera e novo handoff;
- proteção contra duplicidade, reordenação e timeout ambíguo;
- projeção do progresso para a plataforma;
- auditoria, métricas e testes do fluxo.

O contrato aplica-se ao bounded context `reception` e ao caminho Hermes. O
fluxo legado não se torna uma segunda fonte de responsabilidade.

### 3.2 Fora de escopo

- implementação desta spec;
- autenticação definitiva da plataforma de atendimento em produção;
- webhook real do WhatsApp e entrega real de mensagens;
- alteração de preços, termos, links ou regras do catálogo;
- resumo digitado manualmente pela arquiteta;
- reescrita dos turnos normais do Hermes;
- uso de memória global do Hermes como fonte de negócio.

## 4. Decisões arquiteturais

### D1 — Mongo é a fonte canônica; Hermes é projeção

O transcript, o estado operacional e os eventos de responsabilidade pertencem
à Urbana Connect. A memória da sessão Hermes não substitui esses registros e
deve poder ser reconstruída depois de perda ou rotação da sessão.

### D2 — Retomada usa um canal interno separado

O fluxo normal continua enviando exatamente a mensagem atual do cliente para
`HermesSessionsGateway.chat(...)`. A retomada deve usar um novo contrato interno
de sincronização e decisão; ela não pode prefixar o turno do cliente, forjar um
remetente `CONTACT` nem persistir seu envelope como fala visível do cliente.

Contrato lógico esperado:

```text
HermesResumeGateway.synchronize(resumeContext) -> ContextSyncReceipt
HermesResumeGateway.decide(resumeDirective)    -> ResumeDecision
```

A implementação física pode usar uma capacidade nativa do Hermes, uma extensão
do runtime ou outro adaptador dedicado, desde que preserve papéis, não gere
saída ao cliente durante a sincronização e aceite chave de idempotência.

### D3 — A mesma sessão deve ser preservada

A retomada usa a sessão ativa do contato para manter continuidade. Se ela tiver
sido perdida ou substituída, a sessão nova deve receber o contexto canônico até
o mesmo limite antes de qualquer resposta. Recuperar a sessão sem recuperar o
contexto humano não libera a automação.

### D4 — A arquiteta não preenche template

A única ação humana necessária para devolver a conversa é marcar a flag na
plataforma. Identidade do ator, transcript, estado e metadados são obtidos de
fontes canônicas pelo backend.

### D5 — Origem confiável define autoridade

Somente mensagens gravadas pelo backend como `senderType=HUMAN`, por uma rota de
operador autenticada, podem ser interpretadas como decisões da arquiteta. Um
cliente escrever “a arquiteta autorizou” continua sendo uma alegação do cliente
e não altera a precedência.

### D6 — Falhas fecham para o lado humano

Enquanto o contexto não estiver sincronizado, a Urba não responde. Falha
retentável mantém a retomada pendente; falha terminal ou contexto realmente
ambíguo devolve a responsabilidade a `HUMAN`, registra o motivo e notifica
internamente, sem prometer nada ao cliente.

### D7 — Existe um único dono do estado

`ReceptionConversation` continua como agregado responsável por ownership,
versão e retomada. Não deve ser criado outro agregado de handoff como fonte
concorrente. O `ConversationFlowService` legado fica fora desse contrato; o
runtime não pode anunciar suporte ao retorno enquanto estiver nesse caminho.

## 5. Máquina de estados

Responsabilidade e prontidão da automação são dimensões separadas. Os valores
persistidos de `ReceptionMode` continuam `AI` e `HUMAN`; `AI` é apresentado ao
negócio como **Urba**. A retomada ganha um `ResumeStatus` próprio.

| `ReceptionMode` | `ResumeStatus` | Responsável visível | Automação pode responder? | Mensagem da arquiteta aceita? |
|---|---|---|---:|---:|
| `AI` | `NONE` ou `COMPLETED` | Urba | Sim | Não |
| `HUMAN` | `NONE` ou `RETURNED_TO_HUMAN` | Arquiteta | Não | Sim |
| `AI` | `PENDING`, `SYNCHRONIZING`, `DECIDING` ou `RETRYABLE_FAILURE` | Retornando para Urba | Não | Não |

Transições válidas:

```text
AI/NONE --handoff--> HUMAN/NONE
                         |
                         +-- flag de retorno --> AI/PENDING
                                                    |
                                                    +--> AI/SYNCHRONIZING
                                                    |          |
                                                    |          +--> AI/DECIDING
                                                    |                    |
                                                    |                    +--> AI/COMPLETED
                                                    |
                                                    +-- falha terminal --> HUMAN/RETURNED_TO_HUMAN
```

Regras:

1. `AI -> HUMAN/NONE` preserva o comportamento existente e encerra qualquer
   status concluído da retomada anterior.
2. `HUMAN/NONE -> AI/PENDING` ocorre uma vez por retorno confirmado: a Urba já
   é a responsável, mas ainda não está liberada para responder.
3. Repetir a flag com a mesma chave retorna o mesmo resultado.
4. Marcar a flag em `AI` sem retomada ativa é no-op e não cria evento.
5. Duas tentativas concorrentes usam `expectedVersion`; somente uma vence.
6. Mensagens do cliente recebidas com retomada ativa são persistidas e aguardam.
7. Nenhum turno Hermes normal começa em `HUMAN` ou com `ResumeStatus` ativo;
   são ativos `PENDING`, `SYNCHRONIZING`, `DECIDING` e `RETRYABLE_FAILURE`.
8. `COMPLETED` só é gravado depois do recibo de sincronização e da persistência
   da decisão de retomada.

## 6. Comando da plataforma e auditoria

### 6.1 Retornar para a Urba

Contrato HTTP lógico:

```http
POST /api/operator/conversations/{conversationId}/return-to-urba
Idempotency-Key: <uuid>

{
  "expectedVersion": 17
}
```

O ator é obtido da sessão autenticada, nunca do corpo. A resposta é assíncrona e
informa `resumeId`, estado da retomada e nova versão da conversa.

- `202`: retorno criado ou ainda em processamento;
- `200`: repetição idempotente ou no-op porque a Urba já era responsável;
- `409`: versão desatualizada ou estado incompatível;
- `401/403`: operador não autenticado ou sem permissão.

### 6.2 Registrar mensagem humana

A plataforma precisa de um caso de uso próprio para acrescentar mensagens
`HUMAN` ao transcript. O backend define remetente, direção, conversa e ator; a
requisição fornece somente conteúdo e identidade idempotente. A escrita só é
aceita em `HUMAN` e participa da mesma serialização usada pelo retorno.

### 6.3 Registro atômico

O compare-and-set que grava `AI/PENDING` deve participar de uma transação
multi-documento que também grava a intenção/process manager e o outbox. A
transação é a fronteira que impede a conversa de mudar de responsável sem que o
evento recuperável exista.

Coleções previstas:

- `reception_conversations`: raiz de ownership, versão, epoch, status e limite;
- `reception_messages`: transcript imutável com sequência;
- `reception_resumes`: estado operacional da retomada;
- `reception_outbox`: evento imutável `ConversationOwnershipReturnedToUrba`;
- `reception_inbox`: idempotência de comandos e consumidores.

O Mongo de homologação precisa ser promovido a replica set, ainda que de um
único membro, antes de liberar esse fluxo. A implementação não deve trocar essa
garantia por uma sequência de escritas independentes ou por outbox embutida.

O evento imutável da transição preserva no mínimo:

- `eventId` determinístico, `schemaVersion`, `resumeId` e
  `idempotencyKeyHash`;
- `conversationId`, `contactId` e `conversationVersion`;
- `ownershipEpoch`, incrementado a cada mudança de responsável;
- `fromMode`, `toMode`, `reason` e `occurredAt`;
- `actorId` e `actorRole` provenientes do contexto autenticado;
- `causationId`, `correlationId` e `traceId`;
- limite canônico do transcript.

O registro operacional da retomada, separado do payload imutável do evento,
preserva estado, tentativas, classificação da última falha, recibo/checksum da
sincronização, decisão final e identificador da mensagem proativa.

O evento lógico emitido por essa intenção chama-se
`ConversationOwnershipReturnedToUrba`, versão 1. Consumidores mantêm inbox única
por `consumer + eventId` e ordenam trabalho por
`conversationId + ownershipEpoch`. Tentativas e falhas do consumidor pertencem
ao registro de dispatch/inbox, não alteram o payload imutável do evento.

## 7. Ordenação e limite do transcript

O transcript deve ganhar uma sequência monotônica por conversa. `createdAt`
sozinho não é suficiente para ordenar mensagens concorrentes nem para definir
com segurança até onde o Hermes foi sincronizado.

O comando de retorno captura `resumeBoundarySequence`. O pacote contém ou
disponibiliza todas as mensagens com `sequence <= resumeBoundarySequence`, em
ordem, incluindo `CONTACT`, `URBA`, `HUMAN` e `SYSTEM`.

Mensagens posteriores ao limite:

- mensagens humanas são rejeitadas porque a responsabilidade já voltou para
  `AI` e a retomada está ativa;
- mensagens do cliente são persistidas, mantidas em fila e não alteram o pacote
  já fechado;
- antes de enviar uma fala proativa, o processador verifica se apareceu entrada
  nova do cliente; se apareceu, suprime a fala proativa e libera essa entrada
  como o próximo turno após a sincronização.

Alocação da sequência, escrita da mensagem e mudança de responsabilidade devem
usar serialização por conversa ou transação equivalente. Lacunas numéricas são
aceitáveis; reordenação e reutilização não são.

## 8. Pacote de contexto da retomada

O evento durável não precisa carregar conteúdo conversacional sensível. Ele
carrega referências e integridade; o consumidor lê a fonte canônica.

```json
{
  "schemaVersion": 1,
  "resumeId": "uuid",
  "conversationId": "uuid",
  "contactId": "opaque-id",
  "hermesSessionId": "opaque-id",
  "ownershipEpoch": 4,
  "resumeBoundarySequence": 42,
  "transcriptChecksum": "sha256:...",
  "businessState": {
    "commercialStage": "BRIEFING",
    "selectedService": "DECOR_INTERIORES",
    "termsStatus": "ACCEPTED",
    "paymentStatus": "CONFIRMED"
  },
  "handoff": {
    "reason": "CUSTOMER_QUESTION",
    "returnedBy": "operator-id",
    "returnedAt": "2026-08-16T18:00:00Z"
  },
  "authorityPolicy": "HUMAN_CASE_DECISIONS_OVERRIDE_CATALOG"
}
```

O identificador do contato é opaco. Logs e métricas não recebem texto do
transcript.

### 8.1 Conteúdo integral e limites do runtime

O transcript canônico integral até o limite deve permanecer acessível ao
Hermes. Para conversas que caibam no limite aceito pelo runtime, ele é
sincronizado integralmente. Para conversas maiores, a integração deve usar
sincronização paginada/chunked ou uma ferramenta de leitura do transcript com
ordem e papéis preservados; não pode truncar silenciosamente nem substituir a
fonte por um resumo sem rastreabilidade.

Se o runtime fixado não suportar nenhuma forma segura de disponibilizar o
histórico integral, a retomada automática fica bloqueada e retorna para humano.
O sistema não reduz contexto por conta própria para aparentar sucesso.

“Integral” é uma garantia lógica, não uma autorização para duplicar mensagens
na memória da sessão. O adaptador pode aplicar somente o delta depois de um
watermark confiável da mesma linhagem de sessão; sem esse watermark, aplica o
snapshot completo por canal interno. Nunca reproduz o histórico como novas
falas `user`.

O `ContextSyncReceipt` registra `resumeId`, sessão efetiva, checksum, modo
`FULL|DELTA|PAGED`, primeiro e último `sequence` cobertos e horário de conclusão.
O planner só executa se `coveredThroughSequence` coincidir com
`resumeBoundarySequence`.

### 8.2 Precedência

1. estado operacional validado pela Urbana Connect;
2. decisões explícitas da arquiteta em mensagens canônicas `HUMAN` daquele caso;
3. regras gerais da spec de negócio e do catálogo;
4. inferências e linguagem do modelo.

O estado validado vence afirmações factualmente incompatíveis, como pagamento
não confirmado. A decisão humana específica pode especializar o catálogo, mas
não pode contornar invariantes técnicas ou de segurança sem um caso de uso de
domínio autorizado.

Todo texto do transcript é tratado como conteúdo da conversa, não como instrução
de sistema. O remetente confiável vem do metadado canônico; conteúdo de cliente
não pode redefinir papéis, precedência, ferramentas ou políticas.

## 9. Decisão de retomada

Urbana Connect autoriza a próxima ação; o Hermes não ganha autoridade para
avançar o fluxo apenas por confiança do modelo. Depois da sincronização, o
Hermes pode interpretar o contexto humano e propor um candidato estruturado,
com evidências. Um `ResumePlanner` valida esse candidato contra estado, políticas
e recursos canônicos e produz a decisão final:

```json
{
  "action": "SEND_MESSAGE | WAIT | RETURN_TO_HUMAN",
  "nextStep": "CONTINUE_DISCOVERY | PRESENT_TERMS | SEND_PAYMENT_INSTRUCTIONS | SEND_BRIEFING_LINK | WAIT_FOR_BRIEFING_DATA | SEND_SCHEDULING_LINK | AWAIT_CUSTOMER | SUPPORT | NONE",
  "message": "obrigatória somente em SEND_MESSAGE",
  "evidenceMessageIds": ["message-id"],
  "reasonCode": "NEXT_STEP_CLEAR",
  "confidence": 0.94
}
```

O planner/validador aplica as seguintes regras:

- `SEND_MESSAGE` exige próximo passo permitido pelo estado e todos os recursos
  necessários, como um link vigente;
- decisões comerciais protegidas continuam passando pelos casos de uso de
  domínio, nunca apenas pelo texto do modelo;
- ausência de próximo passo claro resulta em `WAIT`;
- contradição relevante, decisão humana incompreensível ou pedido fora da
  política resulta em `RETURN_TO_HUMAN`;
- uma entrada do cliente posterior ao limite converte `SEND_MESSAGE` em `WAIT`,
  pois o turno do cliente passa a ser a próxima ação;
- a decisão é persistida antes de qualquer envio;
- uma repetição reutiliza a decisão persistida e não consulta o modelo outra vez.

A mensagem proativa é uma saída canônica `URBA`, ligada ao `resumeId` por um
`eventId` determinístico e único. Somente depois dessa persistência — ou da
decisão `WAIT` — o `ResumeStatus` chega a `COMPLETED`.

### 9.1 Dependência do estado operacional

O `CommercialStage` atual termina em `BRIEFING`. Antes de habilitar proatividade
em agendamento, produção, entrega ou suporte, a implementação do catálogo deve
representar esses marcos e uma `nextOperationalAction` explícita. Enquanto um
passo não tiver representação canônica e pré-condições verificáveis, o planner
retorna `WAIT` ou `RETURN_TO_HUMAN`; não infere autorização apenas do texto.
Essa ação é mantida ou derivada pelo workflow da Urbana Connect; a arquiteta não
precisa selecioná-la nem preencher um template ao devolver a conversa.

## 10. Concorrência, retries e recuperação

1. O worker de retomada adquire exclusão por conversa e não concorre com turno
   normal do Hermes.
2. A chave `resumeId` acompanha sincronização, decisão e saída proativa.
3. Timeout ambíguo do Hermes entra em conciliação; não dispara segunda chamada.
4. Retry seguro reutiliza a mesma identidade e o mesmo limite do transcript.
5. Queda depois da transição é recuperada pela intenção durável pendente.
6. Queda depois da decisão, mas antes da saída, reutiliza a decisão persistida.
7. Saída proativa tem unicidade por `resumeId`; novo envio não cria nova fala.
8. Rotação/perda da sessão invalida o recibo anterior para a sessão velha e
   exige sincronização integral na sessão vencedora.
9. Depois do limite configurado de tentativas seguras, a conversa volta a
   `HUMAN` e gera alerta interno.

## 11. Projeção da plataforma

A projeção deve expor, sem conteúdo técnico sensível:

- responsável visível: `URBA`, `HUMAN` ou `RETURNING_TO_URBA`;
- `conversationVersion`;
- `resumeId` ativo, quando houver;
- estado: `PENDING`, `SYNCHRONIZING`, `DECIDING`, `COMPLETED`,
  `RETRYABLE_FAILURE` ou `RETURNED_TO_HUMAN`;
- indicação de que mensagens novas do cliente estão aguardando;
- falha operacional resumida e ação segura disponível para o operador.

A interface desabilita nova devolução durante `RETURNING_TO_URBA`, não cria fala
artificial da Urba e não exige que a arquiteta digite contexto.

## 12. Observabilidade

Métricas mínimas:

- transições `AI -> HUMAN` e `HUMAN/NONE -> AI/PENDING`;
- retomadas por estado e tempo até conclusão;
- decisões `SEND_MESSAGE`, `WAIT` e `RETURN_TO_HUMAN`;
- retomadas duplicadas suprimidas;
- mensagens proativas suprimidas por entrada posterior do cliente;
- falhas por classe: Urbana, Hermes, provedor, contrato e contexto;
- quantidade de ressincronizações por perda/rotação de sessão.

Logs estruturados usam `correlationId`, `conversationId`, `resumeId`, versão,
sequência e códigos de estado. Não registram transcript, credenciais, telefone,
links privados nem conteúdo da decisão.

## 13. Critérios de aceite

1. Mensagens humanas entram no transcript com origem confiável e ordem canônica.
2. Uma transição real `HUMAN/NONE -> AI/PENDING` cria exatamente uma intenção
   durável, mesmo com clique duplo ou concorrência.
3. O processo recupera uma intenção criada antes de uma queda do processo.
4. Nenhuma resposta da Urba ocorre antes da sincronização integral até o limite.
5. O cliente não precisa repetir nenhuma decisão registrada pela arquiteta.
6. Texto do cliente que atribua uma decisão à arquiteta não ganha autoridade de
   mensagem `HUMAN`.
7. O pass-through do turno normal continua contendo somente a mensagem atual do
   cliente.
8. Evento interno e transcript não aparecem como fala do cliente ou da Urba.
9. Próximo passo claro pode gerar uma única mensagem proativa canônica.
10. Sem próximo passo claro, a Urba aguarda sem inventar uma ação.
11. Entrada do cliente durante a sincronização é preservada, suprime uma fala
    proativa obsoleta e é processada depois com o contexto já sincronizado.
12. Timeout ambíguo não gera chamada concorrente, decisão duplicada ou fala
    duplicada.
13. Perda de sessão exige nova sincronização antes de liberar a automação.
14. Falha terminal devolve para humano e gera alerta interno sem mensagem
    artificial ao cliente.
15. A plataforma mostra responsabilidade e progresso reais durante todo o fluxo.

## 14. Matriz de testes exigida antes da implementação ser aceita

| Nível | Cenários mínimos |
|---|---|
| Domínio | transições válidas, estados proibidos, compare-and-set, no-op e idempotência |
| Persistência | sequência, intenção atômica, unicidade de `resumeId`, recuperação após queda |
| Aplicação | histórico integral, precedência, decisão estruturada e supressão proativa |
| Hermes adapter | papéis corretos, sync sem saída, timeout ambíguo, rotação e sessão perdida |
| REST | autenticação, `Idempotency-Key`, `expectedVersion`, `202`, `200`, `409` e autorização |
| Frontend | flag, estado intermediário, clique duplo, erro seguro e mensagens aguardando |
| Regressão | handoff existente, turn lease, transcript canônico e pass-through byte a byte |
| Segurança | tentativa de forjar `HUMAN`, prompt injection no transcript e ausência de conteúdo em logs |

## 15. Decisões fechadas pela discovery técnica

### 15.1 Hermes: capability interna obrigatória

Na versão pinada `v2026.8.3`, o endpoint nativo
`POST /api/sessions/{session_id}/chat` valida primeiro uma mensagem de usuário;
`system_message`/`instructions` são complementares e não iniciam uma execução
sozinhos. O endpoint `POST /v1/runs` também exige `input`, mesmo aceitando
`session_id`, `instructions` e `conversation_history`.

Evidências primárias:

- [session chat no Hermes v2026.8.3](https://github.com/NousResearch/hermes-agent/blob/v2026.8.3/gateway/platforms/api_server.py#L3211-L3325);
- [runs no Hermes v2026.8.3](https://github.com/NousResearch/hermes-agent/blob/v2026.8.3/gateway/platforms/api_server.py#L5833-L5913);
- [documentação da Runs API](https://github.com/NousResearch/hermes-agent/blob/v2026.8.3/website/docs/user-guide/features/api-server.md#runs-api-streaming-friendly-alternative).

Decisão:

- o `HermesSessionsGateway.chat(...)` continua responsável apenas pelo turno
  normal, com a mensagem real do cliente;
- a retomada exige uma capability interna separada, conceitualmente:

  ```text
  sync_context(resumeContext) -> ContextSyncReceipt
  resume_decide(resumeDirective) -> ResumeDecision
  ```

- essa capability pode ser uma extensão do runtime pinado ou um endpoint interno
  do adaptador, mas deve aceitar papéis, checksum, watermark e idempotência;
- o contrato mínimo exige append/import atômico com `sourceMessageId`,
  `senderType` e `role`, revisão/cursor monotônico e idempotência durável que
  sobreviva a restart;
- em perda/rotação de sessão, a nova sessão deve ser hidratada antes de liberar
  ferramentas ou decisão, e o lease deve ser transferido para a sessão efetiva;
- a sincronização não pode gerar uma fala, e a decisão proativa não pode ser
  persistida como `user`/`CONTACT`;
- usar `chat`/`runs` com texto artificial como “evento de retomada” está
  explicitamente proibido;
- enquanto a capability não estiver disponível e coberta por contract test, o
  retorno fica seguro, sem mensagem proativa automática. Isso é um blocker
  técnico do caminho proativo, não uma decisão pendente da Urbana.

### 15.2 Mongo: transação multi-documento como pré-requisito

O runtime de homologação atual usa Mongo 6.0.5 com StatefulSet de uma réplica e
sem configuração de replica set. Transações multi-documento, portanto, não são
uma premissa válida para a primeira implementação.

Decisão:

- `ReceptionConversation` continua sendo o agregado dono do ownership;
- o Mongo suportado deve ser promovido a replica set e a aplicação deve usar
  transação multi-documento para o fluxo;
- a escrita de uma mensagem canônica atualiza atomicamente o alocador
  `lastTranscriptSequence` e insere a mensagem em `reception_messages`;
- o retorno atualiza atomicamente `mode`, `resumeStatus`, `version`,
  `ownershipEpoch` e `resumeBoundarySequence`, além de inserir
  `reception_resumes` e `reception_outbox`;
- `reception_inbox` registra `Idempotency-Key` e consumo por
  `consumer + eventId`, com índices únicos explícitos;
- o dispatcher permanece at-least-once, mas todas as operações são idempotentes;
  entrega externa não é declarada exactly-once;
- o alocador de sequência aceita lacunas após rollback/retentativa, mas nunca
  reutiliza ou reordena uma sequência confirmada;
- a persistência canônica do inbound deve ocorrer no aceite do ingress, antes de
  o lote ser encaminhado ao orquestrador. Caso contrário, um retorno concorrente
  pode não enxergar uma mensagem já aceita pelo sistema.

Não será usado fallback silencioso para Mongo standalone. Um replica set de um
único membro habilita a garantia transacional, mas não fornece alta
disponibilidade; a topologia de produção deverá ser definida separadamente.

Esta é uma dependência de infraestrutura antes da implementação, não uma
decisão de negócio.

### 15.3 Operador: principal confiável, não identidade inventada

Hoje a POC possui apenas o token compartilhado `HERMES_POC_API_TOKEN`; a rota
fica permitida pelo Spring Security e o controller faz uma validação opcional.
Isso autentica a integração, mas não identifica individualmente a arquiteta.

Decisão:

- o caso de uso recebe um `TrustedOperatorPrincipal` fornecido pela plataforma
  ou por um adaptador interno autenticado;
- o corpo nunca informa `actorId`, `role` ou `senderType`;
- uma rota de retorno não será liberada se seu segredo estiver vazio;
- no ambiente POC, o principal pode ser explicitamente
  `poc-human-operator`, deixando claro que a auditoria é da integração, não da
  pessoa física;
- a auditoria individual da arquiteta fica condicionada à futura plataforma
  com sessão/identidade própria. Não será simulada com um nome enviado pelo
  navegador.

### 15.4 Próximo passo: escopo incremental e autorização determinística

O estado atual termina em `CommercialStage.BRIEFING`. O catálogo possui links de
pagamento e briefing, mas os valores semeados são fixtures/legados e os estados
de agendamento, produção, entrega e suporte ainda não existem no agregado.

Decisão para a primeira implementação:

- permitir proatividade somente quando houver `nextOperationalAction` canônica,
  pré-condições satisfeitas e recurso validado;
- o primeiro caso autorizado é `SEND_BRIEFING_LINK`, condicionado a pagamento
  confirmado e link vigente;
- `nextOperationalAction` deve ser um valor estruturado, no mínimo `{code,
  resourceKey}`. O `resourceKey` é resolvido pelo backend em uma fonte
  canônica; o modelo e a arquiteta não fornecem URL diretamente;
- ausência de estado/recurso confiável resulta em `WAIT` ou `RETURN_TO_HUMAN`,
  nunca em inferência livre do modelo;
- a arquiteta não precisa selecionar a ação nem preencher template: o workflow
  e o planner derivam um candidato do estado e do transcript, e o backend
  autoriza apenas ações permitidas;
- ações posteriores exigem uma evolução separada do estado operacional com, no
  mínimo, `ServiceLifecycleStage` (`BRIEFING`, `SCHEDULING`, `PRODUCTION`,
  `DELIVERY`, `SUPPORT`, `CLOSED`) e `nextOperationalAction` (`SEND_BRIEFING_LINK`,
  `WAIT_FOR_BRIEFING_DATA`, `SEND_SCHEDULING_LINK`, `WAIT_CUSTOMER`,
  `WAIT_HUMAN`, `SEND_DELIVERY`, `SUPPORT`, `NONE`).

Essa decisão permite entregar o caminho de contexto sem fingir que a aplicação
já conhece o fluxo pós-briefing completo.

## 16. Evidências verificadas dos spikes técnicos

Os spikes foram reexecutados em 2026-08-16 depois da disponibilização do
ambiente Docker local. O resultado abaixo separa capacidade comprovada de
capacidade ainda ausente; nenhum código de produção do fluxo foi alterado.

### 16.1 Hermes

- A POC local respondeu com sucesso em `/health` e `/api/v1/readiness`, usando a
  imagem integrada `urbana-hermes-agent:0.20.0`.
- A chamada autenticada de capabilities confirmou suporte a sessões, chat de
  sessão e submissão de runs, mas não expôs uma operação de sincronização ou
  importação de transcript.
- Criar e excluir uma sessão funcionou. O chat de sessão rejeitou tanto um
  corpo vazio quanto uma requisição contendo apenas `system_message`, com
  `400/missing_message`. Isso confirma no ambiente executável que os endpoints
  públicos não podem ser usados para injetar contexto silenciosamente.
- Os testes direcionados do runtime passaram: 19 testes em
  `test_session_api.py`, `test_internal_event_never_interrupts_busy_session.py`
  e `test_pre_gateway_dispatch.py`.
- O código local do Hermes possui eventos sintéticos `internal=True` para
  recuperação de sessões após restart. Eles são transformados em uma nota de
  sistema dentro do processamento interno e não constituem um canal tipado
  para importar mensagens `HUMAN`, preservar `sourceMessageId` ou confirmar
  checksum/watermark.

Conclusão do spike: o adaptador atual continua **não aprovado** para a
retomada. A extensão/fork do runtime é tecnicamente viável e deve aproveitar o
ponto interno de eventos somente como mecanismo de acionamento, adicionando a
capability explícita definida em D2. A implementação não pode simular a
sincronização com uma mensagem artificial do cliente, `chat` ou `runs`.

### 16.2 MongoDB

- A instância Mongo da POC executa como standalone (`mongo:8.0`): o comando
  `hello` não informa `setName`, e o driver Java registra modo `SINGLE` sem
  `requiredReplicaSetName`.
- Uma tentativa de transação na instância atual foi rejeitada pela topologia
  standalone. Portanto, o Mongo atualmente utilizado pela POC não sustenta a
  fronteira transacional exigida por 6.3.
- Um container temporário, isolado e sem volume, foi iniciado com um replica set
  de um membro (`rs0`). O smoke test confirmou commit e rollback de uma
  transação via URI com `replicaSet=rs0`. O container temporário foi removido
  após o teste; nenhum volume ou manifesto do projeto foi alterado.
- Com JDK 21, os testes focados do gateway Hermes, do domínio de recepção e o
  `MongoConnectivityVerifierIntegrationTest` passaram depois que o Docker foi
  disponibilizado. O Testcontainers já fornece a topologia de replica set
  necessária para testes de integração, mas o teste existente ainda não prova
  a transação do caso de uso.
- A inspeção dos manifestos de homologação continua indicando StatefulSet de
  uma réplica sem `replSet` configurado. A homologação ainda precisa da
  alteração de infraestrutura e da URI correspondente.

Conclusão do spike: a garantia transacional é **viável**, mas a topologia atual
é insuficiente. O próximo trabalho deve promover a infraestrutura para replica
set, validar a conexão com `replicaSet`/retry writes e adicionar teste da
fronteira transacional real entre conversa, sequência, mensagem, retomada e
outbox. Não há fallback aceitável para standalone.

### 16.3 Estado após a execução das PEE-103 e PEE-104

Os dois spikes foram executados com escopo delimitado e verificação local:

- **PEE-103:** capability interna versionada do Hermes implementada no runtime
  pinado (`v2026.8.3` / `0.20.0`), com `sync_context`, `resume_decide`,
  hidratação tipada fora do transcript visível, idempotência durável e
  contract tests. O contrato está pronto para a integração funcional da
  Urbana; essa integração continua fora destes spikes.
- **PEE-104:** replica set `rs0`, URI/retries, readiness, inicialização da POC,
  manifests de HML, `MongoTransactionManager` e teste de commit/rollback foram
  implementados e validados localmente. A aplicação em HML e o smoke test
  contra o cluster ainda são pendências operacionais; nenhum deploy foi feito.
  O replica set de um membro comprova transações, mas não alta disponibilidade.

Evidências locais adicionais: 27 testes direcionados do Hermes passaram, com
Ruff e Ty limpos; os testes focados e de regressão do backend Java passaram;
`docker compose config` e `kubectl kustomize` também passaram.

### 16.4 Gates derivados

Antes de iniciar a implementação funcional HUMANO → URBA, os dois trabalhos
devem entregar, no mínimo:

1. **HERMES-EXT:** contrato interno versionado para `sync_context` e
   `resume_decide`, com papéis confiáveis, `sourceMessageId`, sequência,
   checksum, watermark, idempotência durável, hidratação antes de tools e
   contract tests contra a versão pinada do runtime.
2. **MONGO-RS:** replica set reproduzível no ambiente-alvo, URI e healthcheck
   compatíveis, transação multi-documento exercitada no caso de uso e índices
   únicos para idempotência/inbox/outbox.

Até os dois gates estarem verdes, a única conduta suportada é manter a
conversa com a arquiteta ou falhar fechando para `HUMAN`, sem mensagem
proativa automática.

## 17. Pendências antes da implementação

Não restou pergunta de negócio bloqueante para esta etapa. Permanecem gates
objetivos de execução:

1. ~~especificar e testar a capability interna do Hermes~~ (**PEE-103 —
   concluído localmente; aguardando revisão**);
2. promover o Mongo de homologação para replica set e habilitar transações
   (**PEE-104 — implementação local concluída; aplicação e smoke test em HML
   pendentes**);
3. mover a persistência canônica do inbound para o aceite do ingress e criar o
   backfill/índice de sequência;
4. implementar o principal confiável do operador no adaptador da POC;
5. separar links fixtures/legados de recursos comercialmente válidos;
6. detalhar a evolução de `ServiceLifecycleStage` quando o fluxo pós-briefing
   entrar no escopo.

Até os cinco primeiros itens estarem cobertos por contrato/teste, a
implementação não deve declarar a retomada proativa como suportada.
