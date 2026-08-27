# Contratos externos: Reception, Hermes e POC local

## 1. Ferramentas de domínio

O plugin Hermes continua usando somente as seis ferramentas allowlisted. O
backend deve responder JSON estável, sempre como objeto.

### Sucesso

```json
{
  "ok": true,
  "result": {
    "serviceType": "DECOR_PINTURA",
    "name": "Decor Pintura",
    "price": "250.00",
    "areaRule": "UNLIMITED_BY_CATALOG",
    "scope": "...",
    "deliverables": ["MANUAL_PDF", "VIRTUAL_TOUR", "THREE_OPTIONS", "TWO_ADJUSTMENT_ROUNDS"],
    "process": ["BRIEFING", "MEASUREMENTS_MEDIA", "ONLINE_MEETING", "PRODUCTION", "EMAIL_DELIVERY"],
    "support": "..."
  }
}
```

### Rejeição de negócio

```json
{
  "ok": false,
  "error": {
    "code": "TERMS_NOT_ACCEPTED",
    "nextAction": "ASK_FOR_CLEAR_ACCEPTANCE",
    "missingFields": [],
    "customerMessage": "Antes do pagamento, preciso do seu aceite claro dos termos."
  }
}
```

O plugin pode transportar o envelope, mas não pode concatenar exceções,
status HTTP, nomes de classes, URLs internas ou mensagens de stack trace. Se a
resposta não for segura, retorna uma rejeição genérica para o Hermes e registra
correlação somente no backend.

## 1.1 Enriquecimento de lead e observabilidade

O checkpoint conversacional de ICP é orientado pelo SOUL e não é um pré-
requisito de rejeição para `prepare_terms`. Os três campos são:

```json
{
  "leadEnrichment": {
    "PRONOUN_PREFERENCE": {"status": "ANSWERED", "value": "..."},
    "FIRST_TIME_HIRING": {"status": "ANSWERED", "value": "SIM"},
    "OCCUPATION": {"status": "NOT_INFORMED"}
  }
}
```

O valor textual só aparece no perfil/contexto autorizado e na mensagem do
cliente que o originou. Logs e métricas usam somente campo, status, origem,
conversa/turno e momento. Uma declaração explícita posterior substitui o valor
atual silenciosamente; o histórico permanece interno.

Quando termos forem preparados com algum campo ausente, o backend registra o
evento interno `ICP_SKIPPED_BEFORE_TERMS`. Esse evento não deve ser enviado ao
cliente, ao transcript visível, ao payload da ferramenta ou como erro para o
Hermes. A preparação dos termos mantém o mesmo resultado que teria sem o evento.

Correlação mínima interna:

```json
{
  "event": "ICP_SKIPPED_BEFORE_TERMS",
  "conversationId": "opaque-id",
  "turnId": "opaque-id",
  "serviceType": "DECOR_PINTURA",
  "missingFields": ["OCCUPATION"],
  "detectionPoint": "PREPARE_TERMS",
  "idempotencyKey": "opaque-id",
  "occurredAt": "2026-08-26T12:00:00Z"
}
```

Os identificadores são internos e os campos não carregam seus valores brutos.
Replay da mesma preparação produz no máximo um evento lógico. Não existe
`checkpointStatus`, `attemptsByField` ou máquina de estado de diálogo no
contrato do backend; pergunta e segunda oportunidade são interpretadas pelo
SOUL sobre a thread atual.

O teste de observabilidade usa injeção controlada diretamente nesse boundary.
Ele não compõe o fluxo conversacional normal, não instrui o cliente a burlar o
checkpoint e não reduz a exigência de que o E2E normal colete os campos antes
dos termos.

## 1.2 Contexto Hermes

Na thread atual, o Hermes recebe as mensagens canônicas integrais do cliente,
da Urba e da arquiteta, além do contexto associado ao cliente. O adaptador não
deve montar uma projeção que suprima mensagens por causa do ICP. Threads antigas
inteiras não são anexadas; o perfil corrente e o contexto pertinente são
reutilizados. A combinação entre thread, fatos correntes e SOUL é a única fonte
para decidir pergunta inicial, segunda oportunidade, pausa e retomada do ICP.

## 2. Handoff

`request_human_handoff` deve produzir um resultado determinístico de transição:

```json
{
  "ok": true,
  "result": {
    "status": "TRANSFERRED",
    "ownership": "HUMAN",
    "ackMessage": "Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.",
    "handoffId": "opaque-id"
  }
}
```

O backend grava `ackMessage` no transcript exatamente uma vez. O orquestrador
publica o ack antes de bloquear a saída do turno. A notificação da arquiteta
contém resumo interno, nunca aparece no transcript visível do cliente.

Repetição idempotente devolve o mesmo `handoffId` sem duplicar ack.

## 3. Retomada HUMANO → URBA

O comando interno deve usar uma chave idempotente e uma versão esperada da
conversa. O processamento deve:

1. registrar a transição real e o limite do transcript;
2. sincronizar mensagens `CONTACT`, `URBA`, `HUMAN` e `SYSTEM` até o limite;
3. aplicar decisões humanas como contexto de autoridade;
4. decidir entre uma mensagem proativa, espera ou retorno ao humano;
5. persistir o resultado antes de liberar novo turno Hermes.

Não é permitido simular a retomada como nova mensagem do cliente.

## 4. Projeção da POC

A projeção deve distinguir:

- mensagens canônicas, incluindo o ack de handoff;
- ownership humano/Urba;
- processamento pendente, reconciliação e falha segura;
- controles locais de arquiteta apenas no ambiente de teste.

Os controles de teste não devem ser exibidos como se fossem ações disponíveis
ao cliente final nem enviar WhatsApp, e-mail, cobrança ou recursos produtivos.
