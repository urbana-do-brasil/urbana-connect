# Spec SDD — Urba: Fluxo de Triagem e Vendas

## Metadados

- `Título da feature`: Urba: Fluxo de Triagem e Vendas
- `Ticket Jira`: PEE-23
- `Status`: Draft
- `Responsável pela spec`: Visão Claude
- `Branch`: `feature/PEE-2-urba`
- `Data`: 2026-04-04

---

## 1. Contexto

PEE-23 é a primeira entrega funcional da Urba 1.0 (PEE-2). Hoje o sistema possui webhook funcionando (challenge + recebimento de POST), infraestrutura k8s em homolog e nenhuma lógica de negócio implementada. Esta feature transforma o webhook em um fluxo conversacional real: recebe mensagens do WhatsApp, conduz o cliente da saudação até o envio do link de pagamento e persiste o estado da conversa.

A decisão técnica central é o modelo **híbrido: state machine + IA**. A state machine é a orquestradora do fluxo (define etapas e transições). A IA atua dentro das etapas para interpretar linguagem natural, evitando que o cliente precise usar botões em 100% das interações. A IA é grounded no catálogo real de serviços — não inventa preços nem condições.

A integração com IA deve ser desacoplada via porta `AiGateway`, permitindo troca de provedor sem impacto no domínio.

**Dependências operacionais já satisfeitas em homolog:**
- `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_VERIFY_TOKEN` → k8s secrets configurados
- MongoDB → rodando em `urbana-connect-hml` namespace
- Endpoint público `api-hml.urbanadobrasil.com` → ativo com TLS

**Nova dependência — e-mail (handoff humano):**
- Quando o cliente solicitar atendimento humano, a Urba envia e-mail de alerta para `comunicacao@urbanadobrasil.com`
- Requer configuração de SMTP (Spring Mail) com credenciais em k8s secret (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`)
- Credenciais a serem provisionadas antes da implementação desta funcionalidade

---

## 2. State Machine

### Estados

| Estado | Descrição |
|--------|-----------|
| `GREETING` | Saudação inicial enviada; aguardando resposta Sim/Não |
| `TRIAGE_GUIDED` | Cliente pediu ajuda; aguardando seleção de cenário (4 opções) |
| `TRIAGE_DIRECT` | Cliente já sabe o que quer; aguardando seleção de serviço (4 opções) |
| `AWAITING_CONFIRMATION` | Serviço apresentado com preço; aguardando Sim/Não |
| `AWAITING_TERMS` | Termo de uso enviado; aguardando "Aceito" |
| `AWAITING_PAYMENT_METHOD` | Aguardando escolha PIX ou Cartão |
| `PAYMENT_LINK_SENT` | Link de pagamento enviado; fluxo de vendas encerrado |
| `COMPLETED` | Conversa concluída |
| `EXPIRED` | Janela de 24h expirada |

### Transições

```
[número novo ou conversa expirada]
        │
        ▼
    GREETING ──── "Sim, estou precisando" ────▶ TRIAGE_GUIDED
        │                                              │
        └──── "Não, já sei o que quero" ───▶ TRIAGE_DIRECT
                                                       │
                         ┌─────────────────────────────┘
                         │ (serviço selecionado/sugerido)
                         ▼
               AWAITING_CONFIRMATION
                   │           │
                "Sim"        "Não"
                   │           │
                   │           └──────────▶ TRIAGE_DIRECT (volta ao menu)
                   ▼
            AWAITING_TERMS
                   │
           mensagem com "aceito"
                   │
                   ▼
        AWAITING_PAYMENT_METHOD
                   │
          PIX ou Cartão selecionado
                   │
                   ▼
         PAYMENT_LINK_SENT ──▶ COMPLETED
```

---

## 3. Catálogo de Serviços

Os dados de serviço **não devem ser hardcoded**. Devem ser carregados de uma collection `services` no MongoDB.

### Estrutura do documento `services`

```json
{
  "type": "DECOR",
  "name": "Decor",
  "emoji": "🛋️",
  "scenarioText": "Quero renovar meu espaço interno sem gastar muito, nada de quebra-quebra.",
  "presentationText": "Para espaços de até 20m², temos a Decor 🛋️\n\nCriamos uma solução de espaço, de acordo com seu estilo e orçamento.",
  "price": 400.00,
  "paymentLink": "https://mpago.la/1TbJFYx",
  "briefingLink": "https://forms.gle/W4zBPwusPZeJ2cnD7"
}
```

### Valores iniciais do catálogo

| Tipo | Nome | Preço | Link Pagamento | Disponível |
|------|------|-------|----------------|------------|
| `DECOR` | Decor 🛋️ | R$400 | `https://mpago.la/1TbJFYx` | ✅ |
| `DECOR_PINTURA` | Decor Pintura 🎨 | R$250 | `https://mpago.la/32aNZUw` | ✅ |
| `DECOR_FACHADA` | Decor Fachada 🏡 | R$350 | `https://mpago.la/1Qeg34y` | ✅ |
| `DECOR_REFORMA` | Decor Reforma 🧱 | R$450 | *(a criar)* | ❌ |

> **Decor Reforma sem link de pagamento:** serviço recém-criado. O campo `available: false` no catálogo mantém o serviço fora do fluxo até o link ser configurado — sem impacto no desenvolvimento dos demais serviços. Quando o link estiver pronto, basta atualizar o documento no MongoDB e mudar para `available: true`.

---

## 4. Modelo de Dados — Conversa

Collection: `conversations`

```json
{
  "_id": "ObjectId",
  "phoneNumber": "+5583999999999",
  "status": "ACTIVE",
  "currentStep": "AWAITING_TERMS",
  "selectedService": "DECOR",
  "context": {
    "paymentMethod": null
  },
  "createdAt": "2026-04-04T10:00:00Z",
  "updatedAt": "2026-04-04T10:05:00Z",
  "expiresAt": "2026-04-05T10:00:00Z"
}
```

**Regras:**
- `expiresAt = createdAt + 24h`
- Ao receber mensagem com `expiresAt` no passado → status `EXPIRED` → reinicia fluxo do zero
- Índice em `phoneNumber` (único por conversa ativa)
- Índice em `expiresAt` (para limpeza futura)

---

## 5. Porta AiGateway

```java
// domain/port/out/AiGateway.java
public interface AiGateway {
    AiInterpretation interpret(AiContext context);
}

public record AiContext(
    ConversationStep currentStep,
    String userMessage,
    List<ServiceSummary> availableServices,
    String conversationHistory   // últimas N trocas, formatadas como texto
) {}

public record AiInterpretation(
    IntentType intent,           // SERVICE_SELECTION | AFFIRMATION | NEGATION | TERMS_ACCEPTANCE | UNKNOWN
    ServiceType selectedService, // preenchido quando intent = SERVICE_SELECTION
    String suggestedResponse     // texto a enviar ao cliente quando a IA precisar responder diretamente
) {}
```

**Regras de uso da IA:**
- A IA só é acionada quando o cliente envia **texto livre** (não clicou em botão/lista)
- Se `intent = UNKNOWN` e a IA não conseguiu interpretar → mensagem de reorientação padrão + repete passo atual
- A IA nunca inventa preços ou condições — o contexto enviado inclui o catálogo completo
- Adapter inicial: Claude (Anthropic). Interface permite troca sem impacto no domínio.

---

## 6. Integração WhatsApp Cloud API

### Recebimento (já implementado)

`POST /api/webhook` — payload da Meta já tratado pelo `WebhookController`.

A nova camada deve extrair de `entry[].changes[].value.messages[]`:
- `from` → phoneNumber do cliente
- `type` → `text` | `interactive`
- `text.body` → conteúdo quando type = text
- `interactive.button_reply.id` → ID do botão clicado
- `interactive.list_reply.id` → ID do item de lista selecionado

### Envio

`POST https://graph.facebook.com/v18.0/{WHATSAPP_PHONE_NUMBER_ID}/messages`

**Tipos de mensagem usados:**

**Texto simples:**
```json
{ "messaging_product": "whatsapp", "to": "{phoneNumber}", "type": "text",
  "text": { "body": "mensagem aqui" } }
```

**Botões interativos (máx. 3):**
```json
{ "messaging_product": "whatsapp", "to": "{phoneNumber}", "type": "interactive",
  "interactive": {
    "type": "button",
    "body": { "text": "Precisando de ajuda para encontrar o serviço perfeito?" },
    "action": { "buttons": [
      { "type": "reply", "reply": { "id": "YES_HELP", "title": "✅ Sim, estou precisando" } },
      { "type": "reply", "reply": { "id": "NO_HELP", "title": "🚫 Não, já sei o que quero" } }
    ]}
  }
}
```

**Lista interativa (até 10 itens — usada para 4 serviços/cenários):**
```json
{ "messaging_product": "whatsapp", "to": "{phoneNumber}", "type": "interactive",
  "interactive": {
    "type": "list",
    "body": { "text": "Das opções abaixo, qual você se identifica mais?" },
    "action": {
      "button": "Ver opções",
      "sections": [{ "rows": [
        { "id": "DECOR", "title": "🛋️ Decor", "description": "Renovar espaço interno sem quebra-quebra" },
        { "id": "DECOR_PINTURA", "title": "🎨 Decor Pintura", "description": "Renovar com tintas e estilo" },
        { "id": "DECOR_FACHADA", "title": "🏡 Decor Fachada", "description": "Renovar fachada ou muro externo" },
        { "id": "DECOR_REFORMA", "title": "🧱 Decor Reforma", "description": "Reforma completa com quebra-quebra" }
      ]}]
    }
  }
}
```

---

## 7. Comportamentos esperados

1. Dado um número sem conversa ativa, quando chegar qualquer mensagem, então o sistema deve persistir nova conversa em `GREETING` e enviar a saudação com botões interativos.
2. Dado conversa em `GREETING`, quando o cliente clicar "Sim, estou precisando", então o sistema deve transicionar para `TRIAGE_GUIDED` e enviar a lista de 4 cenários.
3. Dado conversa em `GREETING`, quando o cliente clicar "Não, já sei o que quero", então o sistema deve transicionar para `TRIAGE_DIRECT` e enviar a lista de 4 serviços com nome e preço.
4. Dado conversa em `TRIAGE_GUIDED`, quando o cliente selecionar um cenário, então o sistema deve mapear o cenário para o serviço correspondente, transicionar para `AWAITING_CONFIRMATION` e enviar a apresentação do serviço com preço.
5. Dado conversa em `TRIAGE_GUIDED` ou `TRIAGE_DIRECT`, quando o cliente enviar texto livre com intenção identificável, então a IA deve interpretar e agir como se o cliente tivesse clicado no botão correspondente.
6. Dado conversa em `AWAITING_CONFIRMATION`, quando o cliente confirmar, então o sistema deve transicionar para `AWAITING_TERMS` e enviar o link do Termo de Uso.
7. Dado conversa em `AWAITING_CONFIRMATION`, quando o cliente negar, então o sistema deve transicionar para `TRIAGE_DIRECT` e retornar ao menu de serviços.
8. Dado conversa em `AWAITING_TERMS`, quando a mensagem do cliente contiver "aceito" (case-insensitive), então o sistema deve transicionar para `AWAITING_PAYMENT_METHOD` e perguntar forma de pagamento.
9. Dado conversa em `AWAITING_PAYMENT_METHOD`, quando o cliente selecionar a forma de pagamento, então o sistema deve transicionar para `PAYMENT_LINK_SENT`, enviar o link de pagamento do serviço escolhido e encerrar com mensagem de próximo passo.
10. Dado qualquer etapa, quando o cliente enviar texto fora do fluxo que a IA não consiga interpretar, então o sistema deve responder "Não entendi 😊 Por favor, use as opções abaixo:" e repetir o passo atual.
11. Dado conversa com `expiresAt` no passado, quando chegar nova mensagem, então o sistema deve marcar a conversa como `EXPIRED`, criar nova conversa e reiniciar do zero.
12. Dado conversa ativa e incompleta, quando chegar nova mensagem dentro da janela de 24h, então o sistema deve retomar do passo atual sem reiniciar.
13. Dado qualquer etapa, quando o cliente expressar intenção de falar com humano ("HUMANO", "quero falar com alguém" ou equivalente), então o sistema deve: (a) responder *"Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais"*; (b) enviar e-mail para `comunicacao@urbanadobrasil.com` com número do cliente, etapa atual e últimas mensagens como contexto; (c) manter o estado da conversa inalterado.

---

## 8. Critérios de Aceite

- Ao receber a primeira mensagem de um número sem contexto ativo, a Urba envia saudação e inicia o fluxo.
- O cliente consegue percorrer o fluxo completo guiado por botões/listas até receber o link de pagamento.
- Preços e links exibidos vêm do catálogo no MongoDB, não de constantes no código.
- Se o cliente digitar texto livre com intenção de serviço clara, a IA interpreta corretamente sem quebrar o estado.
- "Não, foi quase" na confirmação retorna ao menu de serviços.
- O sistema aceita "aceito", "Aceito", "ok aceito", "sim aceito" como aceite de termos.
- Após o link de pagamento, a Urba envia mensagem de encerramento.
- Conversa incompleta é retomada na etapa correta quando o cliente volta dentro de 24h.
- Conversa expirada (> 24h) reinicia do zero.
- Fluxo funciona em homolog com evidência de ponta a ponta via WhatsApp real.
- Cobertura de testes coerente com a complexidade: state machine, parsing de payload, regras de expiração.

---

## 9. Edge Cases

- Mensagem chegando para número com conversa `EXPIRED` → reinicia, não retoma
- Cliente envia texto livre em qualquer etapa → IA tenta interpretar; se `UNKNOWN` → reorienta
- Cliente responde "HUMANO", "falar com alguém" ou intenção equivalente → Urba responde *"Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais"* + envia e-mail de alerta para `comunicacao@urbanadobrasil.com` com número do cliente e resumo do contexto da conversa + mantém estado atual (conversa não é encerrada)
- WhatsApp Cloud API retorna erro no envio → logar com número e etapa, não travar o fluxo de recebimento
- Serviço não encontrado no catálogo para o `ServiceType` selecionado → logar erro e retornar ao menu
- Payload de webhook com `messages` vazio ou `type` desconhecido → ignorar silenciosamente (já coberto pelo WebhookController)
- Cliente envia mídia (imagem, áudio) em vez de texto → tratar como texto livre desconhecido

---

## 10. Observabilidade e Validação

- **Testes unitários:** state machine (todas as transições), parsing de payload WhatsApp, regra de expiração de conversa, matching de aceite de termos
- **Testes de integração:** fluxo ponta a ponta com MongoDB real (Testcontainers), simulando payload da Meta
- **Logs obrigatórios:** recebimento de mensagem (número, tipo, etapa atual), transição de estado, envio de mensagem (tipo, destino), erro de API WhatsApp
- **Logs proibidos:** conteúdo completo de mensagem do cliente, tokens, links de pagamento
- **Smoke test em homolog:** percorrer fluxo completo via WhatsApp real, confirmar persistência no MongoDB e resposta da Urba em cada etapa

---

## 11. Fora de Escopo

- Recebimento e validação de comprovante de pagamento
- Envio de briefing (Google Forms)
- Agendamento de bate-papo (Google Calendar)
- Envio de link por e-mail
- Onboarding pós-pagamento
- Handoff formal para humano (apenas mensagem de fallback nesta versão)
- Notificação ou lembrete por timeout (janela de 24h encerra silenciosamente)

---

## 12. Dúvidas em Aberto

- [x] **Link de pagamento do Decor Reforma** — serviço recém-criado, link ainda não existe. Tratado com `available: false` no catálogo; habilitado quando o link for criado.
- [x] **Provedor de IA inicial** — decisão adiada intencionalmente. A porta `AiGateway` está definida; o adapter começa como stub. Uma subtask dedicada fará a pesquisa de modelos (consolidados, chineses e open source) antes da integração real.
- [x] **Mensagem de encerramento** — confirmada: *"Perfeito! Assim que o pagamento for confirmado, daremos os próximos passos 😊"*
- [x] **Fallback "HUMANO"** — Urba responde *"Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais"* e envia e-mail de alerta para `comunicacao@urbanadobrasil.com` com contexto da conversa. Conversa permanece ativa. Requer configuração de SMTP via k8s secret.
