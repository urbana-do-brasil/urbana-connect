# Spec SDD — PEE-100: Contrato Urbana Connect -> Urbana Claw

## Metadados

- `Título da feature`: Spike do contrato Urbana Connect -> Urbana Claw
- `Ticket Jira`: PEE-100
- `Status`: Draft
- `Responsável pela spec`: Visão Codex
- `Branch`: `feature/PEE-100-openclaw-poc-spec`
- `Contexto de branch`: `feature/* -> hml -> main`
- `Data`: 2026-05-04
- `Fonte principal`: `docs/specs/pee-100-urbana-claw-infra.md`

---

## 1. Contexto

O `urbana-claw` já foi instalado em homologação como um serviço Kubernetes
separado, no namespace `urbana-connect-hml`.

Estado validado:

- `deployment/urbana-claw` está `Ready`;
- `service/urbana-claw` responde internamente na porta `18789`;
- `NetworkPolicy` permite entrada apenas de pods com label
  `app=urbana-connect`;
- o Gateway usa token em `urbana-claw-secrets`;
- o OpenClaw sobe com agente `urba`, modelo `google/gemini-2.5-flash-lite`,
  cron desabilitado e sem plugins extras carregados.

A próxima dúvida da POC não é mais infraestrutura. A dúvida agora é o contrato
de comunicação:

```text
urbana-connect -> urbana-claw -> resposta textual
```

Esta spec descreve um spike pequeno para provar esse contrato antes de conectar
qualquer mensagem real do WhatsApp.

---

## 2. Objetivo

Provar que a Urbana Connect consegue enviar um turno textual para o OpenClaw e
receber uma resposta textual usando um contrato direto, simples e suportável em
Java/Spring.

O resultado esperado do spike é uma decisão técnica:

- seguir com chamada direta para o OpenClaw Gateway; ou
- reconhecer que o contrato direto ficou complexo demais e propor um bridge
  HTTP mínimo como próxima etapa.

---

## 3. Decisão técnica inicial

A hipótese primária deve ser usar o endpoint HTTP OpenAI-compatible do
OpenClaw:

```text
POST http://urbana-claw:18789/v1/chat/completions
```

Motivos:

- usa HTTP simples, fácil de encapsular com `WebClient`;
- não exige SDK Node dentro da Urbana Connect;
- aceita autenticação via `Authorization: Bearer <token>`;
- suporta resposta não-streaming;
- permite escolher o agente com `model: "openclaw/urba"`;
- permite preservar continuidade com header `x-openclaw-session-key`;
- permite informar origem com header `x-openclaw-message-channel`.

O caminho WebSocket/RPC do Gateway deve ser tratado como fallback técnico, não
como primeira tentativa.

---

## 4. Contrato HTTP proposto

### 4.1 Request

Endpoint:

```text
POST /v1/chat/completions
```

Headers:

```text
Authorization: Bearer <OPENCLAW_GATEWAY_TOKEN>
Content-Type: application/json
x-openclaw-session-key: agent:urba:whatsapp:<conversation-key>
x-openclaw-message-channel: whatsapp
```

Body:

```json
{
  "model": "openclaw/urba",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "Mensagem recebida do usuario"
    }
  ]
}
```

Observações:

- `conversation-key` deve ser derivado de um identificador estável da conversa,
  sem expor telefone puro em logs.
- A primeira implementação deve enviar apenas a mensagem atual; a continuidade
  deve ser responsabilidade da sessão do OpenClaw.
- Histórico manual no array `messages` só deve ser considerado se o teste
  mostrar que o OpenClaw não preserva contexto suficiente por sessão.

### 4.2 Response esperada

Formato compatível com OpenAI Chat Completions:

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "Resposta da Urba"
      }
    }
  ]
}
```

A Urbana Connect deve extrair apenas:

```text
choices[0].message.content
```

Qualquer resposta sem esse campo textual deve ser tratada como resposta
inválida.

---

## 5. Configuração da Urbana Connect

Novas configurações esperadas:

```text
OPENCLAW_POC_ENABLED=false
OPENCLAW_GATEWAY_BASE_URL=http://urbana-claw:18789
OPENCLAW_GATEWAY_TOKEN=<secret>
OPENCLAW_GATEWAY_MODEL=openclaw/urba
OPENCLAW_GATEWAY_TIMEOUT_MS=45000
OPENCLAW_GATEWAY_CONNECT_TIMEOUT_MS=3000
OPENCLAW_GATEWAY_MAX_RESPONSE_CHARS=3500
```

Regras:

- `OPENCLAW_POC_ENABLED` deve continuar `false` por padrão.
- O token não deve ser commitado.
- Em Kubernetes, a Urbana Connect deve receber o token via `Secret`, não via
  `ConfigMap`.
- A aplicação não deve depender de `localhost`; o endpoint interno correto em
  homologação é `http://urbana-claw:18789`.

---

## 6. Design de código esperado

Criar um contrato interno pequeno, desacoplado de OpenClaw:

```java
interface ConversationalRuntimeClient {
    ConversationalRuntimeResponse sendTurn(ConversationalRuntimeRequest request);
}
```

DTO conceitual:

```java
record ConversationalRuntimeRequest(
    String conversationKey,
    String senderKey,
    String text,
    String correlationId
) {}

record ConversationalRuntimeResponse(
    String text,
    String provider,
    Duration latency
) {}
```

Implementação inicial:

```text
OpenClawGatewayClient implements ConversationalRuntimeClient
```

Responsabilidades do `OpenClawGatewayClient`:

- montar `x-openclaw-session-key`;
- montar payload OpenAI-compatible;
- enviar `Authorization: Bearer`;
- aplicar timeout;
- extrair `choices[0].message.content`;
- mapear erro HTTP/timeout/resposta inválida para exceção ou resultado
  controlado;
- não conhecer regra de WhatsApp;
- não enviar mensagem para o usuário.

O fluxo de WhatsApp real não deve ser alterado nesta entrega.

---

## 7. Session key

Formato proposto:

```text
agent:urba:whatsapp:<conversation-key>
```

Regras:

- deve ser estável para a mesma conversa;
- deve ser diferente para usuários/conversas diferentes;
- não deve conter telefone puro se a chave aparecer em log;
- pode usar hash determinístico do identificador do WhatsApp;
- deve ser curta o suficiente para aparecer em logs e troubleshooting.

Exemplo:

```text
agent:urba:whatsapp:wa_8f14e45fce
```

---

## 8. Plano de implementação

### Fase 1 — Smoke manual do Gateway em homologação

Antes de implementar código Java, validar manualmente o contrato HTTP a partir
de um pod temporário com label `app=urbana-connect`.

Testes:

1. `GET /v1/models` retorna `openclaw/default` ou `openclaw/urba`.
2. `POST /v1/chat/completions` retorna texto para uma mensagem simples.
3. Duas chamadas com a mesma `x-openclaw-session-key` preservam contexto.
4. Chamada com outra `x-openclaw-session-key` não compartilha contexto.

Critério:

- se esses quatro testes passarem, seguir para client Java direto;
- se falhar apenas por configuração do `urbana-claw`, ajustar infra/config;
- se o contrato HTTP não suportar sessão do jeito necessário, avaliar RPC.

### Fase 2 — Client Java isolado

Implementar `OpenClawGatewayClient` sem conectar ao webhook real.

Entregáveis:

- propriedades de configuração;
- DTOs internos;
- client HTTP;
- parser de resposta;
- tratamento de erro;
- testes unitários com mock HTTP/WireMock ou equivalente usado no projeto.

Critério:

- dado um payload HTTP válido, o client retorna texto;
- dado timeout, HTTP 401/500, body inválido ou resposta vazia, o client falha de
  forma controlada;
- nenhum fluxo real de WhatsApp é alterado.

### Fase 3 — Smoke de integração controlado

Validar o client contra o `urbana-claw` em homologação sem ativar o webhook
real.

Opções aceitáveis:

1. teste manual a partir de pod/Job temporário que execute a aplicação ou um
   runner de integração; ou
2. endpoint diagnóstico interno, desligado por padrão, protegido por token
   próprio e removível após a POC; ou
3. teste de integração executado no pipeline contra endpoint configurável,
   quando houver ambiente disponível.

Não criar endpoint público sem autenticação apenas para smoke.

---

## 9. Fallback técnico

Se o endpoint `/v1/chat/completions` não atender ao fluxo, testar o RPC do
Gateway.

Contrato RPC observado no OpenClaw:

```text
method: agent
payload:
  sessionKey: agent:urba:whatsapp:<conversation-key>
  idempotencyKey: <uuid>
  message: <texto>
  deliver: false
  timeout: <segundos>
```

Esse caminho provavelmente exige WebSocket e protocolo do Gateway. Só deve ser
adotado se o HTTP OpenAI-compatible não preservar sessão ou não devolver um
resultado final confiável.

Se implementar RPC direto em Java ficar grande demais para a POC, a alternativa
é criar um `urbana-claw-bridge` mínimo em Node usando SDK/cliente oficial do
OpenClaw. Essa decisão deve virar outra spec antes de implementação.

---

## 10. Segurança

Regras mínimas:

1. Não expor `urbana-claw` por Ingress.
2. Não logar `OPENCLAW_GATEWAY_TOKEN`.
3. Não logar telefone puro no `sessionKey`.
4. Não habilitar WhatsApp dentro do OpenClaw.
5. Não permitir que OpenClaw envie mensagens diretamente.
6. Não habilitar stream na primeira versão.
7. Não enviar mídia, áudio, imagem ou documento nesta etapa.
8. Não alterar o comportamento atual do webhook enquanto a feature flag estiver
   desligada.

---

## 11. Observabilidade

Cada chamada do client deve registrar:

- `correlationId`;
- `conversationKey` ou hash equivalente;
- `sessionKey` mascarada ou derivável sem dado sensível;
- latência;
- status (`success`, `timeout`, `http_error`, `invalid_response`);
- HTTP status quando houver;
- tamanho da resposta;
- nunca token, payload completo do usuário ou telefone puro em log estruturado.

---

## 12. Critérios de aceite

1. Spec revisada e aprovada antes da implementação.
2. Smoke manual confirma que `GET /v1/models` funciona a partir de pod com
   label `app=urbana-connect`.
3. Smoke manual confirma que `POST /v1/chat/completions` retorna texto.
4. Smoke manual confirma que a mesma `x-openclaw-session-key` preserva contexto
   entre dois turnos.
5. Smoke manual confirma que session keys diferentes não compartilham contexto.
6. `OpenClawGatewayClient` existe atrás de interface interna.
7. Client possui testes para sucesso, timeout, HTTP error e resposta inválida.
8. Token vem de `Secret`.
9. Nenhuma mensagem real do WhatsApp é delegada ao OpenClaw nesta entrega.
10. O resultado deixa uma decisão documentada: HTTP direto aprovado, RPC direto
    necessário ou bridge mínimo necessário.

---

## 13. Fora do escopo

- Ativar POC no webhook real.
- Enviar respostas para WhatsApp usando OpenClaw.
- Criar bridge Node.
- Criar UI de atendimento.
- Incluir catálogo dinâmico de serviços no prompt.
- Implementar handoff humano.
- Implementar action schema.
- Processar mídia.

---

## 14. Perguntas em aberto

1. O `model` deve ser `openclaw/urba` ou `openclaw/default` com header
   `x-openclaw-agent-id: urba`?
2. O HTTP OpenAI-compatible preserva contexto com
   `x-openclaw-session-key: agent:urba:whatsapp:<key>` de forma suficiente para
   nossa conversa?
3. Qual timeout real é aceitável para a experiência WhatsApp sem parecer que o
   atendimento travou?
4. Vamos precisar enriquecer a mensagem com contexto mínimo da Urbana Connect já
   neste spike, ou a primeira validação deve usar somente texto cru do usuário?

