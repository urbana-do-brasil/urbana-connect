# Feature Specification: Hermes Transparent Pass-through

**Feature Branch**: `004-hermes-transparent-pass-through`
**Consolidated branch**: `feat/pee-101`
**Created**: 2026-08-07
**Status**: Implemented and verified
**Input**: Remover o tratamento conversacional local da Urbana Connect e manter o fluxo textual Hermes-first transparente.

## 1. Objetivo

A POC deve ter um caminho conversacional simples e observável:

```text
mensagem do usuário
  -> persistência canônica inbound
  -> Hermes Sessions API
  -> resposta textual do Hermes
  -> persistência canônica outbound
  -> projeção para o chat local
```

A Urbana Connect continua responsável por entrega confiável, correlação,
idempotência, ordenação, sessão, reconciliação e estados técnicos. Ela não deve
reescrever a mensagem do usuário nem a resposta do Hermes para conduzir a
conversa.

O nome exibido na ferramenta local é apenas metadado visual e não deve ser
injetado na conversa. O Hermes continua responsável por descobrir o nome e as
necessidades da pessoa durante a conversa.

## 2. Histórias de usuário

### US1 — Receber exatamente a resposta do Hermes (P1)

Como pessoa testadora, quero que a resposta exibida no chat seja a mesma string
retornada pelo Hermes, para avaliar o comportamento real do agente sem
contaminação do backend.

1. Dado um texto inbound aceito, quando a Urbana chamar o Hermes, então o corpo
   enviado deve conter a mensagem atual sem prefixo de estado, instrução local,
   resumo comercial ou wrapper de continuidade.
2. Dado um retorno textual do Hermes, quando a Urbana o persistir, então o campo
   outbound deve preservar conteúdo, espaços, pontuação e acentuação exatamente.
3. Dado o retorno persistido, quando o chat o consultar, então deve exibir o
   mesmo texto, sem prefixo de identidade, fallback ou segunda interpretação.

### US2 — Manter o Hermes como autoridade conversacional (P1)

Como responsável pela POC, quero que o perfil e o contrato do Hermes sejam
simples, para que as respostas sejam produzidas pelo próprio agente.

1. O perfil pode apresentar a Urba uma vez e orientar segurança básica, mas não
   deve exigir JSON conversacional, `nextAction`, frases fixas ou respostas
   artificiais para cobrir incerteza.
2. A Urbana não deve classificar mensagens comuns como non-prospect nem responder
   localmente antes do Hermes.
3. A Urbana não deve validar, reconciliar semanticamente, completar ou substituir
   a resposta textual do Hermes no caminho normal.

### US3 — Preservar garantias operacionais (P1)

Como pessoa testadora, quero remover apenas a lógica de conteúdo, para continuar
seguro contra duplicidade, concorrência e perda de mensagens.

1. A entrada deve estar persistida antes do dispatch remoto.
2. Deve existir no máximo um turno remoto ativo ou inconclusivo por sessão/contato.
3. Timeout ambíguo deve permanecer em processamento/conciliação e não gerar
   reenvio concorrente.
4. Idempotência, ordenação, polling, reconciliação, correlação e persistência
   devem continuar funcionando.
5. Falha técnica não deve criar uma mensagem conversacional sintética atribuída
   à Urba; deve permanecer no estado técnico já definido pela POC.

### US4 — Manter controles operacionais independentes (P2)

Ferramentas, handoff humano, pagamento e permissões continuam protegidos por
controles técnicos próprios. Esses controles podem autorizar ou bloquear uma
ação operacional, mas não podem reescrever a fala comum do Hermes.

## 3. Requisitos funcionais

- **FR-001**: Persistir cada inbound antes do primeiro dispatch para o Hermes.
- **FR-002**: Enviar ao Hermes a mensagem atual como conteúdo conversacional,
  sem wrapper de estado canônico, fatos, política comercial ou instrução local
  gerada por `ReceptionOrchestrator`.
- **FR-003**: Persistir o conteúdo textual retornado pelo Hermes sem alteração
  observável, incluindo whitespace, acentuação e pontuação.
- **FR-004**: Devolver ao cliente a mesma mensagem outbound canônica que foi
  persistida; nenhuma camada HTTP ou frontend pode prefixar, resumir ou substituir
  o conteúdo.
- **FR-005**: Remover do caminho conversacional normal o prefixo automático de
  identidade, `ReceptionResponsePolicy`, fallback seguro textual,
  `NonProspectPolicy` e reconciliação semântica de resposta.
- **FR-006**: A interpretação de um envelope Hermes legado, se ainda necessária
  para ferramentas/telemetria, não pode alterar o texto `message` apresentado.
  O fluxo textual normal não exige `nextAction`.
- **FR-007**: Preservar leases, estados de turno, idempotência, ordenação,
  sessão Hermes, timeout, reconciliação e projeção canônica.
- **FR-008**: Falha ou ausência de resposta remota não deve ser convertida em
  uma fala da Urba; deve usar o estado técnico da POC.
- **FR-009**: Não alterar o webhook de produção, credenciais, deploy ou escopo
  de anexos nesta feature.

## 4. Requisitos não funcionais

- **NFR-001**: O comportamento deve ser verificável por testes unitários,
  integração e um cenário E2E local com evidência de igualdade Hermes → Mongo →
  projeção/UI.
- **NFR-002**: Os controles operacionais devem permanecer independentes do texto
  produzido pelo agente.
- **NFR-003**: Logs e testes não devem expor credenciais, tokens ou segredos de
  `.env.poc`.
- **NFR-004**: A alteração deve ser reversível por commit/PR e preservar as
  alterações preexistentes do worktree.

## 5. Fora de escopo

- anexos, áudio, imagens ou multimodalidade no chat local;
- streaming/WebSocket/SSE;
- edição do modelo, provedor ou credenciais Hermes/OpenRouter;
- dashboard de produção ou deploy;
- remoção da resiliência de turnos implementada pela spec 003;
- alteração de regras operacionais de pagamento ou handoff.

## 6. Critérios de aceite

1. Um teste envia uma mensagem com prefixos/fatos que seriam diferentes sob a
   implementação antiga e comprova que o Hermes recebe apenas o texto atual.
2. Um retorno Hermes com espaços e pontuação distintivos é comparado por
   igualdade exata no gateway, no documento Mongo, na resposta HTTP e na
   projeção consumida pelo frontend.
3. Uma sequência de pelo menos três turnos não repete automaticamente
   `Olá! Sou a Urba...` e não produz o fallback
   `Não consigo confirmar essa informação...` quando o Hermes retornou texto.
4. Uma falha/timeout controlado não cria bubble outbound sintético e não remove
   o lease de um turno inconclusivo.
5. As suítes Java, frontend, scripts Hermes/corpus, validação do profile e E2E
   local relevante passam, ou cada limitação fica classificada com evidência.
