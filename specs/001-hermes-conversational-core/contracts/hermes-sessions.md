# Hermes Sessions API contract

Base URL local padrão: `http://127.0.0.1:8642`. Todos os requests usam `Authorization: Bearer <API_SERVER_KEY>`.

## Capability and health gates

Antes de aceitar tráfego, o adapter verifica:

- `GET /health` retorna liveness saudável;
- `GET /v1/capabilities` anuncia criação, leitura e chat de sessões;
- o profile ativo expõe somente o toolset de domínio esperado.

Falha em qualquer gate mantém o ingresso persistido, mas não inicia o turno.

## Create session

`POST /api/sessions`

O adapter cria uma sessão vazia e persiste o ID retornado em `AgentSessionLink`. O título usa somente o `contactId` opaco, nunca telefone ou nome.

## Execute turn

`POST /api/sessions/{sessionId}/chat`

Request lógico do adapter:

```json
{
  "input": "mensagem ou transcrição agrupada",
  "model": "openai/gpt-5.6-luna",
  "provider": "openrouter",
  "model_options": {
    "reasoning_effort": "max"
  },
  "images": []
}
```

O serializer deve seguir o formato exato anunciado pela versão instalada do Hermes. Imagens usam somente o caminho inline multimodal documentado. Áudio nunca é enviado cru por esse contrato.

Resposta real esperada da versão pinada:

```json
{
  "object": "hermes.session.chat.completion",
  "session_id": "20260804_120000_abcd1234",
  "message": {
    "role": "assistant",
    "content": "texto livre não confiável"
  },
  "usage": {
    "input_tokens": 0,
    "output_tokens": 0,
    "total_tokens": 0
  }
}
```

A Sessions API não garante JSON estruturado no `message.content`. A porta normaliza somente depois de parse estrito e reconciliação com o ledger de ferramentas:

```json
{
  "sessionId": "20260804_120000_abcd1234",
  "output": {
    "message": "string",
    "nextAction": "NONE",
    "handoffReason": null
  },
  "usage": {
    "inputTokens": 0,
    "outputTokens": 0
  }
}
```

## Inspect history

`GET /api/sessions/{sessionId}/messages`

Usado para diagnóstico, auditoria comparativa e recuperação. Não é chamado em todo turno.

## Failure mapping

| Condition | Application result |
| --- | --- |
| `404` session | Marcar vínculo `LOST`, criar sessão substituta e executar recuperação uma vez. |
| `401/403` | `AUTHENTICATION_FAILED`; nenhuma tentativa automática. |
| `429` | `CAPACITY_EXCEEDED`; retry com backoff fora do lock ativo. |
| timeout/5xx antes de ferramenta | Turno `FAILED_RETRYABLE`; nenhuma resposta enviada. |
| falha após ferramenta mutável | Consultar `idempotencyKey` antes de qualquer retry. |
| saída inválida | `INVALID_AGENT_OUTPUT`; fallback seguro ou handoff. |

## Isolation invariant

O `sessionId` é sempre obtido pelo `contactId` da mensagem já autenticada. Antes da chamada, a aplicação adquire `ActiveTurnLease(sessionId, turnId, contactId, sourceMessageId)`. Ferramentas só são aceitas com lease `RUNNING`; nenhuma informação fornecida pelo modelo pode sobrescrever esse vínculo.

## Session rotation

Se `session_id` retornado diferir do solicitado, o adapter trata o resultado como rotação por compressão: cria o novo vínculo ativo, marca o anterior como `REPLACED` e transfere a autorização somente depois do término do turno atual.
