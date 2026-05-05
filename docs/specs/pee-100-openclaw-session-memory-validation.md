# Spec SDD - PEE-100: Validacao de sessao e memoria do OpenClaw

## Metadados

- `Titulo da feature`: Validacao de sessao e memoria do OpenClaw
- `Ticket Jira`: PEE-100
- `Status`: Draft
- `Responsavel pela spec`: Visao Codex
- `Branch`: `feature/PEE-100-openclaw-poc-spec`
- `Contexto de branch`: `feature/* -> hml -> main`
- `Data`: 2026-05-05
- `Spec relacionada`: `docs/specs/pee-100-urbana-connect-urbana-claw-contract.md`

---

## 1. Contexto

A spec do contrato Urbana Connect -> Urbana Claw validou que o endpoint
OpenAI-compatible do Gateway responde texto via:

```text
POST /v1/chat/completions
```

O smoke em homologacao tambem mostrou uma limitacao importante: duas chamadas
HTTP com a mesma `x-openclaw-session-key` e o mesmo `user` retornam `200`, mas o
Gateway nao recuperou o turno anterior de forma confiavel.

Como contingencia, a Urbana Connect passou a montar um prompt com historico
textual recente da conversa. Isso prova continuidade por contexto enviado pela
aplicacao, mas nao valida o diferencial esperado do OpenClaw: sessao
persistente, memoria ativa, arquivos de memoria e capacidade de recordar
informacoes sem reenviar todos os turnos a cada interacao.

Esta spec define um spike de validacao para descobrir qual superficie do
OpenClaw deve ser usada para preservar contexto e memoria na POC.

---

## 2. Objetivo

Determinar se a POC deve continuar com HTTP OpenAI-compatible ou migrar para um
contrato mais nativo do OpenClaw para preservar:

- continuidade de sessao entre turnos;
- elegibilidade de `active-memory`;
- consulta a `MEMORY.md` e `memory/*.md`;
- aprendizado/memoria de fatos relevantes sem replay completo da conversa pela
  Urbana Connect.

O resultado esperado e uma decisao tecnica documentada:

1. manter `/v1/chat/completions`, se a correcao de session key/config for
   suficiente;
2. usar `/v1/responses` com `previous_response_id`, se resolver continuidade em
   HTTP;
3. usar Gateway RPC `sessions.create` + `sessions.send`, diretamente em Java ou
   por bridge minimo;
4. reconhecer que memoria ativa exige configuracao adicional do `urbana-claw`
   antes de qualquer conclusao.

---

## 3. Hipoteses a validar

### 3.1 Session key com tipo de chat explicito

A session key atual da POC segue o formato:

```text
agent:urba:whatsapp:wa_<hash>
```

O OpenClaw deriva o tipo de conversa pela propria chave. Para memoria ativa, a
sessao deve parecer uma conversa interativa persistente, normalmente contendo
`direct`, `dm`, `group` ou `channel`.

Hipotese:

```text
agent:urba:whatsapp:direct:wa_<hash>
```

ou:

```text
agent:urba:whatsapp:dm:wa_<hash>
```

pode tornar a sessao elegivel para memoria ativa e melhorar continuidade.

### 3.2 `/v1/responses` com `previous_response_id`

O endpoint `/v1/responses` possui suporte explicito a
`previous_response_id`, reusando a sessao anterior quando o escopo de
autenticacao/agente/sessao bate.

Hipotese: `/v1/responses` pode manter continuidade por HTTP com menos atrito que
WebSocket/RPC.

### 3.3 Gateway RPC `sessions.create` + `sessions.send`

O fluxo nativo de sessoes persistentes do Gateway usa RPC:

```text
sessions.create
sessions.send
```

Hipotese: esse caminho preserva transcript, eventos de chat, memoria ativa e
comportamento mais proximo do ecossistema real do OpenClaw.

### 3.4 Active Memory configurado para o agente `urba`

A infra atual do `urbana-claw` foi mantida minimalista, com plugins extras
restritos. Para validar memoria, e necessario testar `active-memory` de forma
controlada.

Hipotese: com `active-memory` habilitado para `urba`, session key de DM e
`MEMORY.md` semeado, o agente deve recordar fatos relevantes sem a Urbana
Connect reenviar historico.

---

## 4. Plano de validacao

### Fase 1 - Baseline do estado atual

Reexecutar smoke em homologacao com o contrato atual:

1. `GET /v1/models` retorna `openclaw/urba`.
2. `POST /v1/chat/completions` retorna texto.
3. Duas chamadas com a mesma session key atual nao recuperam o turno anterior.
4. Chamada com session key diferente nao compartilha contexto.

Resultado esperado: confirmar o comportamento observado antes de mudar a
configuracao.

### Fase 2 - Session key com `direct` ou `dm`

Executar o mesmo smoke usando:

```text
agent:urba:whatsapp:direct:wa_smoke_<id>
```

e, se necessario:

```text
agent:urba:whatsapp:dm:wa_smoke_<id>
```

Testes:

1. primeira chamada informa um fato simples, como servico de interesse;
2. segunda chamada, com a mesma session key, pergunta pelo fato;
3. terceira chamada, com outra session key, pergunta pelo mesmo fato.

Criterio:

- se a segunda chamada recuperar e a terceira nao recuperar, a falha era
  formato de session key;
- se a segunda chamada nao recuperar, seguir para `/v1/responses` e RPC.

### Fase 3 - `/v1/responses` com `previous_response_id`

Habilitar temporariamente o endpoint, se ainda estiver desligado:

```json
{
  "gateway": {
    "http": {
      "endpoints": {
        "responses": { "enabled": true }
      }
    }
  }
}
```

Testes:

1. primeira chamada em `/v1/responses` informa o fato;
2. salvar o `id` da resposta;
3. segunda chamada envia `previous_response_id` e pergunta pelo fato;
4. repetir com outro `user` e/ou outra session key para verificar isolamento.

Criterio:

- se funcionar, considerar `/v1/responses` como contrato HTTP preferencial;
- se falhar, seguir para RPC nativo.

### Fase 4 - RPC nativo `sessions.create` + `sessions.send`

Criar uma sessao persistente para o usuario de teste:

```text
sessions.create { key: "agent:urba:whatsapp:direct:wa_smoke_<id>", agentId: "urba" }
```

Enviar turnos:

```text
sessions.send { key, message, idempotencyKey }
```

Testes:

1. criar ou garantir sessao;
2. enviar fato simples;
3. enviar pergunta sobre o fato usando a mesma sessao;
4. enviar pergunta usando outra sessao;
5. observar transcript/eventos da sessao.

Criterio:

- se preservar contexto, a POC deve migrar para Gateway RPC;
- se Java WebSocket for grande demais, especificar bridge HTTP minimo como
  proxima etapa.

### Fase 5 - Active Memory controlado

Habilitar `active-memory` somente para o agente `urba` em homologacao:

```json
{
  "plugins": {
    "entries": {
      "active-memory": {
        "enabled": true,
        "config": {
          "enabled": true,
          "agents": ["urba"],
          "allowedChatTypes": ["direct"],
          "queryMode": "recent",
          "promptStyle": "balanced",
          "timeoutMs": 15000,
          "maxSummaryChars": 220,
          "logging": true,
          "persistTranscripts": true
        }
      }
    }
  }
}
```

Preparar memoria estatica de teste no workspace do agente:

```text
MEMORY.md
```

com um fato controlado e nao sensivel, por exemplo:

```text
Cliente smoke de memoria prefere Decor Pintura para renovar um quarto.
```

Testes:

1. enviar uma pergunta que dependa do fato sem inclui-lo na mensagem atual;
2. confirmar se o agente recupera o fato;
3. confirmar nos logs se `active-memory` executou;
4. desligar `active-memory` para a mesma pergunta e confirmar diferenca.

Criterio:

- se recuperar apenas com `active-memory` ligado, memoria ativa esta validada;
- se nao recuperar, revisar provider de embeddings, indexacao e elegibilidade
  da sessao antes de concluir que o recurso nao atende.

---

## 5. Configuracoes candidatas

### 5.1 Session key da Urbana Connect

Se a Fase 2 passar, atualizar o resolver para:

```text
agent:urba:whatsapp:direct:wa_<hash>
```

O hash continua obrigatorio para nao expor telefone puro em logs.

### 5.2 Endpoint Responses

Se a Fase 3 passar, adicionar configuracoes:

```text
OPENCLAW_POC_RESPONSES_PATH=/v1/responses
OPENCLAW_POC_CONTRACT=responses
```

O `OPENCLAW_POC_CONTRACT` deve permitir voltar para `chat-completions` durante
diagnostico.

### 5.3 Bridge minimo

Se a Fase 4 passar e Java WebSocket for considerado grande para a POC, criar
nova spec para:

```text
urbana-connect -> urbana-claw-bridge -> urbana-claw Gateway RPC
```

O bridge deve expor apenas um endpoint interno, autenticado e sem acesso
publico.

---

## 6. Criterios de aceite

1. Baseline atual documentado com data, payloads resumidos e resultado.
2. Smoke com session key contendo `direct` ou `dm` executado.
3. Smoke de `/v1/responses` com `previous_response_id` executado ou bloqueio
   documentado.
4. Smoke de RPC `sessions.create` + `sessions.send` executado ou bloqueio
   documentado.
5. Active Memory testado com memoria semeada ou bloqueio documentado.
6. Resultado final aponta explicitamente um contrato recomendado.
7. Nenhum teste envia mensagem real para WhatsApp.
8. Nenhum teste expoe token, telefone puro ou dado real de cliente.
9. Nenhuma alteracao de producao e feita nesta spec.
10. Qualquer mudanca de config em homologacao deve ser reversivel por manifesto.

---

## 7. Observabilidade

Registrar, sem payload sensivel:

- data/hora do teste;
- contrato usado: `chat-completions`, `responses` ou `gateway-rpc`;
- session key mascarada ou hash;
- formato da session key usado;
- HTTP status ou status RPC;
- latencia aproximada;
- se houve recuperacao de contexto;
- se houve isolamento entre sessoes;
- se `active-memory` executou;
- decisao tecnica resultante.

Nao registrar:

- token do Gateway;
- telefone puro;
- payload completo do usuario;
- conteudo de memoria com dado real de cliente.

---

## 8. Fora de escopo

- Implementar o contrato final na Urbana Connect.
- Ativar OpenClaw no webhook real.
- Criar bridge Node sem nova aprovacao.
- Criar plugin customizado.
- Processar audio, imagem ou documento.
- Persistir memorias reais de clientes.
- Expor o Gateway ou bridge por Ingress publico.

---

## 9. Riscos

1. `/v1/chat/completions` pode continuar sendo bom para compatibilidade, mas
   insuficiente para experiencia nativa de memoria.
2. `active-memory` pode exigir provider de embeddings e indexacao que ainda nao
   estao configurados no `urbana-claw`.
3. RPC nativo pode aumentar complexidade em Java por exigir WebSocket/eventos.
4. Bridge minimo reduz complexidade da Urbana Connect, mas adiciona um
   componente operacional.
5. Memoria ativa em atendimento real precisa de politica clara para evitar
   recordar informacao sensivel ou incorreta.

---

## 10. Decisao esperada apos os testes

Preencher depois da execucao:

```text
Contrato recomendado:
Motivo:
Testes que passaram:
Testes que falharam:
Impacto na Urbana Connect:
Proxima spec necessaria:
```
