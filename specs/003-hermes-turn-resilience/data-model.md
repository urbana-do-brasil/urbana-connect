# Data model: Resiliência de turnos Hermes

## Pending POC event

Registro durável criado antes do aceite HTTP.

| Campo | Regra |
|---|---|
| `eventId` | único; chave de idempotência recebida na borda |
| `contactId` | contato canônico, obrigatório |
| `type`, `text`, `transcript`, `mediaFixture`, `interactiveReplyId` | payload normalizado necessário para reconstruir o evento |
| `occurredAt` | timestamp do evento |
| `acceptedAt` | timestamp atribuído pela Urbana |
| `status` | `QUEUED`, `CLAIMED`, `COMPLETED`, `ABANDONED` |
| `claimToken`, `claimedAt` | fencing/recuperação do worker |
| `completedAt` | preenchido após o turno ser concluído ou terminalizado |

Índices: `eventId` único; busca por `contactId + status + occurredAt`; claim
condicional que não permite dois proprietários do mesmo evento.

## Reception turn

O turno é o trabalho conversacional lógico e sua auditoria.

| Campo | Regra |
|---|---|
| `id`, `correlationId`, `contactId`, `hermesSessionId` | identidades estáveis do trabalho |
| `inboundMessageIds` | uma ou mais entradas agrupadas pela janela real |
| `status` | `QUEUED → RUNNING → DELAYED → RECONCILING → COMPLETED` ou falha terminal |
| `acceptedAt`, `startedAt`, `finishedAt` | ciclo de vida durável |
| `attempt` | incrementa somente em retry seguro |
| `failureClass` | classificação técnica sanitizada |
| `retryAllowed` | só `true` após execução não iniciada ou encerramento remoto comprovado |
| `historyCheckpoint` | referência mínima para detectar resposta posterior |
| `version` | controle otimista/CAS |
| `output` e `usage` | preenchidos somente após saída canônica validada |

Uma transição terminal deve ser idempotente. `FAILED_SAFE_TO_RETRY` é o único
estado de falha que pode mostrar ação de retry; `FAILED_TERMINAL` e
`RECONCILING` não podem iniciar uma nova execução.

## Active turn lease

A lease autoriza ferramentas e também protege a sessão/conversa.

- Chave: `hermesSessionId`/`contactId`.
- Proprietário: `turnId + claimToken`.
- Estados: `RUNNING`, `RECONCILING`, `REVOKED`, `EXPIRED`.
- `RUNNING` precisa de heartbeat enquanto o worker aguarda Hermes.
- `RECONCILING` continua bloqueando novo turno.
- `REVOKED` só é permitido depois de conclusão ou encerramento remoto seguro.
- Expiração de autorização de ferramenta não deve, sozinha, liberar o gate da
  conversa; a transição exige fencing e evidência de término.

## Safe projection

O frontend recebe mensagens canônicas e um resumo do turno mais recente:

```json
{
  "status": "RUNNING",
  "correlationId": "opaque-correlation",
  "attempt": 1,
  "retryAllowed": false,
  "failureClass": null,
  "acceptedAt": "2026-08-07T12:00:00Z",
  "startedAt": "2026-08-07T12:00:04Z",
  "finishedAt": null
}
```

Nunca expor `hermesSessionId`, tokens, prompt, exceções brutas ou credenciais.

## State transitions

```text
QUEUED -> RUNNING       claim exclusivo do worker
RUNNING -> DELAYED      latência acima da janela usual, sem cancelar
DELAYED -> COMPLETED    resposta direta persistida
DELAYED -> RECONCILING  transporte perdeu resultado ou resposta ambígua
RECONCILING -> COMPLETED resposta encontrada no histórico
RECONCILING -> FAILED_SAFE_TO_RETRY somente com término remoto comprovado
RUNNING -> FAILED_TERMINAL falha não conversacional e não recuperável
qualquer terminal -> mesmo terminal (idempotente)
```
