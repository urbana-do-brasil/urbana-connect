# Data Model: Hermes Transparent Pass-through

Nenhuma coleção nova é necessária.

## Invariantes preservadas

- `ReceptionTurn` continua vinculando contato, sessão, correlação, entrada,
  saída e estado técnico.
- O transcript Mongo continua registrando inbound/outbound canônicos.
- A saída outbound deve guardar exatamente a string obtida do Hermes.
- `ActiveTurnLease`, pendências, tentativas e reconciliação continuam
  controlando entrega e concorrência.

## Contrato de conteúdo

```text
hermesResponseContent: String
persistedOutboundContent == hermesResponseContent
projectedOutboundContent == hermesResponseContent
```

O frontend não persiste o transcript em `localStorage`; apenas consome a
projeção canônica já existente.
