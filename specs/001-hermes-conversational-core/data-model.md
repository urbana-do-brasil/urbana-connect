# Data Model: Recepção conversacional persistente

## Contact

Representa uma identidade interna opaca.

| Field | Type | Rules |
| --- | --- | --- |
| `id` | UUID/string | Gerado pela Urbana Connect; nunca escolhido pelo modelo. |
| `channelAddresses` | collection | Número normalizado e futuro identificador de outros canais; protegido na borda. |
| `createdAt` | instant | Imutável. |
| `updatedAt` | instant | Atualizado em alterações cadastrais. |

## AgentSessionLink

Vínculo canônico entre contato e sessão Hermes.

| Field | Type | Rules |
| --- | --- | --- |
| `contactId` | string / `_id` | Um único documento Mongo por contato. |
| `hermesSessionId` | string | Sessão atualmente ativa no documento. |
| `status` | `ACTIVE` | A projeção corrente só permanece ativa; estados anteriores ficam na lineage. |
| `createdAt`, `lastUsedAt` | instant | Auditoria da sessão corrente. |
| `lineage` | embedded collection | Sessões anteriores com status, timestamps, versão e `replacedBySessionId`; lookup histórico é permitido. |
| `version` | number | CAS atômico da projeção corrente. |

Rotação e recuperação usam um único `findAndModify` condicional por
`contactId + hermesSessionId esperado + ACTIVE`, promovendo a nova sessão e
anexando a anterior à `lineage` na mesma mutação. Assim, uma falha não deixa o
contato sem uma projeção `ACTIVE`.

A criação usa insert-if-absent e adota o vínculo vencedor em caso de corrida.
A atualização de `lastUsedAt` também é condicional à sessão ativa esperada;
nenhum `save` genérico pode sobrescrever uma rotação concorrente.

## ReceptionConversation

Estado operacional que continua sob autoridade da Urbana Connect.

| Field | Type | Rules |
| --- | --- | --- |
| `id` | string | Identificador auditável. |
| `contactId` | string | Obrigatório. |
| `mode` | `AI`, `HUMAN` | Em `HUMAN`, nenhuma chamada ao Hermes é permitida. |
| `commercialStage` | enum | Checkpoints: descoberta, ICP, termos, pagamento, briefing. Não dita a ordem literal da fala. |
| `selectedService` | enum? | Deve existir no catálogo aprovado. |
| `termsStatus` | `NOT_PRESENTED`, `PRESENTED`, `ACCEPTED`, `DECLINED` | Pagamento exige `ACCEPTED`. |
| `paymentStatus` | `NOT_STARTED`, `PREPARED`, `PROOF_RECEIVED`, `CONFIRMED`, `REJECTED` | Somente pessoa/webhook financeiro pode produzir `CONFIRMED`. |
| `handoffReason` | string? | Obrigatório ao entrar em `HUMAN`. |
| `createdAt`, `updatedAt` | instant | Auditoria. |
| `version` | number | Concorrência otimista. |

### State transitions

```text
AI --request_human_handoff--> HUMAN
HUMAN --new_inbound_or_late_tool--> HUMAN

NOT_STARTED --prepare_payment--> PREPARED
PREPARED --receive_proof--> PROOF_RECEIVED
PROOF_RECEIVED --human_approve--> CONFIRMED
PROOF_RECEIVED --human_reject--> REJECTED
```

`prepare_payment` só é válido com ICP completo, serviço confirmado e termos aceitos. `release_briefing` só é válido com pagamento confirmado.

## ConversationMessage

Transcript imutável espelhado pela Urbana Connect.

| Field | Type | Rules |
| --- | --- | --- |
| `id` | string | Gerado internamente. |
| `eventId` | string | Único para mensagens inbound; base de deduplicação. |
| `correlationId` | string | Liga ingresso, turno, ferramentas e resposta. |
| `conversationId`, `contactId` | string | Obrigatórios. |
| `direction` | `INBOUND`, `OUTBOUND` | Obrigatório. |
| `senderType` | `CONTACT`, `URBA`, `HUMAN`, `SYSTEM` | Obrigatório. |
| `type` | `TEXT`, `AUDIO`, `IMAGE`, `DOCUMENT`, `INTERACTIVE` | Obrigatório. |
| `text` | string? | Texto original ou transcrição quando aplicável. |
| `mediaRef` | string? | Referência ao binário persistido, nunca conteúdo bruto em log. |
| `providerMessageId` | string? | Futuro identificador do WhatsApp. |
| `createdAt` | instant | Imutável. |

## CustomerFact

| Field | Type | Rules |
| --- | --- | --- |
| `id`, `contactId` | string | Obrigatórios. |
| `type` | enum/string aprovada | Inclui preferência de tratamento, primeira contratação, ocupação e fatos comerciais permitidos. |
| `value` | typed JSON/string | Validado por tipo. |
| `confidence` | `CONFIRMED`, `TENTATIVE` | Declaração explícita é confirmada; inferência é tentativa. |
| `sourceMessageId` | string | Procedência obrigatória. |
| `validFrom`, `validUntil` | instant | Vigência temporal. |
| `supersededBy` | string? | Correções não apagam o histórico. |

ICP está completo quando os três tipos obrigatórios possuem valor confirmado vigente; `PREFER_NOT_TO_ANSWER` é valor válido.

## ReceptionTurn

| Field | Type | Rules |
| --- | --- | --- |
| `id`, `correlationId` | string | Únicos. |
| `contactId`, `hermesSessionId` | string | Obrigatórios. |
| `inboundMessageIds` | collection | Um ou mais fragmentos agrupados. |
| `status` | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `BLOCKED_BY_HUMAN` | Transições monotônicas. |
| `startedAt`, `finishedAt` | instant? | Métricas. |
| `usage` | object? | Tokens/custo quando fornecidos. |
| `failureCode` | string? | Sem conteúdo sensível. |

## ActiveTurnLease

Autorização efêmera que transforma o identificador de sessão fornecido ao plugin em um turno canônico.

| Field | Type | Rules |
| --- | --- | --- |
| `hermesSessionId` | string | Chave única enquanto o turno estiver ativo. |
| `turnId`, `contactId`, `sourceMessageId` | string | Derivados pela Urbana Connect antes da chamada. |
| `status` | `RUNNING`, `REVOKED`, `EXPIRED` | Ferramentas aceitam somente `RUNNING`; `EXPIRED` é tombstone permanente. |
| `acquiredAt`, `expiresAt`, `revokedAt` | instant | Expiração lógica e revogação em `finally`; não usar TTL destrutivo. |
| `version` | number | Incrementado nas transições; o CAS usa sessão, turno, status e validade. |

Uma sessão só pode ser reacquirida após `REVOKED` limpo. Um lease `EXPIRED` ou
em estado desconhecido bloqueia a reutilização da sessão e exige recuperação
com nova sessão Hermes; o plugin fornece apenas `sessionId`, portanto o
backend não pode distinguir uma chamada tardia de uma chamada de um novo
turno sem essa barreira.

## DomainToolInvocation

| Field | Type | Rules |
| --- | --- | --- |
| `id`, `idempotencyKey` | string | Chave derivada pelo backend a partir do turno, ferramenta e argumentos normalizados. |
| `turnId`, `hermesSessionId`, `contactId` | string | Contato é resolvido pelo backend, não por argumento do modelo. |
| `toolName` | enum | Somente allowlist. |
| `argumentsHash` | string | Auditoria sem expor valores desnecessários. |
| `status` | `STARTED`, `SUCCEEDED`, `REJECTED`, `FAILED` | Obrigatório. |
| `resultCode` | string | Resultado tipado. |
| `resultPayload` | JSON? | Snapshot exato do resultado para replay idempotente; nunca reexecutar uma ferramenta `SUCCEEDED`. |
| `createdAt`, `finishedAt` | instant? | Auditoria. |

## SyntheticScenarioRun

| Field | Type | Rules |
| --- | --- | --- |
| `scenarioId`, `runNumber` | string/number | Cada persona executa ao menos três vezes. |
| `contactAlias` | string | Mapeado para contato sintético isolado. |
| `expectedOutcome` | object | Barreiras, fatos e ação final esperados. |
| `observedOutcome` | object | Resultado coletado. |
| `criticalViolations` | collection | Qualquer item reprova a execução. |
| `qualityScores` | object | Naturalidade, clareza, utilidade de 1 a 5. |
| `duration`, `usage`, `estimatedCost` | metrics | Apenas medidos na primeira POC. |
