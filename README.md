# Urbana Connect

Agente de atendimento via WhatsApp com IA para a Urbana do Brasil (Urba).

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 LTS |
| Framework | Spring Boot 3.4.x |
| Build | Gradle 8.x |
| Banco de dados | MongoDB |
| Testes | JUnit 5 + Testcontainers |
| Infra | k3s (Contabo VPS) |
| Manifestos | Kustomize (base + overlays) |
| Observabilidade | Prometheus + Grafana + Loki + Promtail |

## Arquitetura

Clean Architecture com 4 camadas:

```
apps/urbana-connect-api/src/main/java/br/com/urbana/connect/
├── domain/          # Entidades e contratos (sem dependências externas)
├── application/     # Casos de uso, serviços e configurações
├── infrastructure/  # Implementações: MongoDB, WhatsApp API, etc.
└── interfaces/      # Entrada: REST controllers, webhooks
```

## Estrutura do Repositório

```
urbana-connect/
├── apps/
│   ├── urbana-connect-api/  # Backend Spring Boot, Mongo e adapter Hermes
│   └── poc-chat/            # Chat React para teste manual local
├── integrations/
│   └── hermes-agent/        # Profile, plugin, pin e scripts locais Hermes
├── infra/
│   ├── local-poc/           # Compose completo, proxies e operação local
│   └── kubernetes/          # Manifestos Kubernetes (Kustomize)
├── quality/
│   ├── conversation-corpus/ # Cenários, fixtures e runner Ruby
│   └── system-e2e/          # Validações cross-system e contratos estruturais
├── contracts/               # Fronteiras compartilhadas do monorepo
├── specs/                   # Specs, planos, tarefas e evidências
├── docs/                    # Documentação operacional e arquitetural
├── infra/kubernetes/        # Kustomize de homologação
│   ├── app/                 # Deployment, Service, ConfigMap
│   ├── mongodb/             # StatefulSet do MongoDB
│   ├── observability/       # ServiceMonitor e PrometheusRules
│   ├── prometheus/          # Helm values do kube-prometheus-stack
│   ├── loki/                # Helm values do Loki
│   ├── promtail/            # Helm values do Promtail
│   └── secrets/             # Templates; secrets reais não são versionados
└── .github/workflows/
    ├── build-test.yml       # CI: backend + validações aplicáveis
    └── deploy-hml.yml       # CD: build/push/deploy via branch hml
```

O runtime upstream do Hermes não é versionado aqui. A Urbana mantém somente
os artefatos da integração em `integrations/hermes-agent/`; o Compose completo
para a POC fica em `infra/local-poc/`.

## Fronteira da POC Hermes-first

O fluxo validado localmente usa o ingresso sintético da API:
`/api/poc/conversations/{contactAlias}/messages`. Ele persiste a mensagem,
encaminha o texto ao Hermes Sessions API, persiste a resposta literal e a
projeta para o `poc-chat`.

O endpoint `/api/webhook` continua sendo o ingresso legado de WhatsApp da
aplicação e não é a prova da integração WhatsApp → Hermes. Esse caminho ainda
usa o fluxo conversacional legado/Gemini e sua migração para Hermes é um
próximo ticket, fora desta etapa local.

## Pré-requisitos de Desenvolvimento

- JDK 21
- Docker (para Testcontainers)

## Executando os testes

```bash
cd apps/urbana-connect-api
./gradlew test
```

O quality gate exige **60% de cobertura de linha** (JaCoCo). O build falha se o threshold não for atingido.

## Chat manual local

Com Docker e `.env.poc` configurados, o stack completo é operado pelo Compose
em `infra/local-poc/`. A aplicação React pode ser validada isoladamente:

```bash
cd apps/poc-chat
npm install
npm run test -- --run
npm run typecheck
npm run lint
npm run build
```

No cenário completo, o browser fala somente com a API da Urbana. Ele não acessa
MongoDB nem a Sessions API do Hermes diretamente.

## Endpoints

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/v1/health` | Health check |
| `GET` | `/api/v1/readiness` | Readiness check (verifica MongoDB) |
| `GET` | `/api/webhook` | Verificação de webhook Meta (challenge) |
| `POST` | `/api/webhook` | Recebimento de eventos WhatsApp |
| `POST` | `/api/poc/conversations/{contactAlias}/messages` | Ingresso sintético Hermes-first da POC |
| `GET` | `/api/poc/conversations/{contactAlias}` | Projeção do transcript e do turno da POC |
| `GET` | `/actuator/prometheus` | Métricas Prometheus |

## Testando o webhook legado localmente

Este roteiro valida somente o contrato legado do webhook. Para testar a Urba
com Hermes, use o `poc-chat` ou o quickstart da spec 006.

```bash
# Verificação de challenge (simulando Meta)
curl "http://localhost:8081/api/webhook?hub.mode=subscribe&hub.verify_token=SEU_TOKEN&hub.challenge=12345"

# Evento de mensagem recebida
curl -X POST http://localhost:8081/api/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "object": "whatsapp_business_account",
    "entry": [{
      "id": "123456789",
      "changes": [{
        "value": {
          "messaging_product": "whatsapp",
          "metadata": {"display_phone_number": "5511111111111", "phone_number_id": "123456789"},
          "contacts": [{"profile": {"name": "Usuário Teste"}, "wa_id": "5511999999999"}],
          "messages": [{
            "from": "5511999999999",
            "id": "wamid.123456789",
            "timestamp": "1677587365",
            "text": {"body": "Olá!"},
            "type": "text"
          }]
        },
        "field": "messages"
      }]
    }]
  }'
```

## Infraestrutura

Ambiente de homologação (`api-hml.urbanadobrasil.com`) rodando em **k3s no Contabo VPS** (Ubuntu 24.04 LTS).

Os manifestos em `infra/kubernetes/` seguem o padrão Kustomize com `base/` + `overlays/hml/`. Para aplicar:

```bash
kubectl apply -k infra/kubernetes/cert-manager/
kubectl apply -k infra/kubernetes/app/overlays/hml/
kubectl apply -k infra/kubernetes/mongodb/overlays/hml/
kubectl apply -k infra/kubernetes/observability/
```

Para exposição pública do webhook em homolog:
- host: `api-hml.urbanadobrasil.com`
- `Ingress`: `infra/kubernetes/app/overlays/hml/ingress.yaml`
- TLS automático via `cert-manager`
- requisito externo: DNS `A` do subdomínio apontando para a VPS
- apenas os paths públicos mínimos ficam expostos; `/actuator` deixa de ser publicado externamente
- validação operacional: `docs/validacao-webhook-publico-hml.md`

Pipeline de deploy para homolog:

- branch de deploy: `hml`
- workflow: `.github/workflows/deploy-hml.yml`
- estratégia: build da imagem no GHCR + apply da overlay `infra/kubernetes/app/overlays/hml`
- secrets do GitHub Actions: `docs/github-actions-secrets-hml.md`

## Contato

Dúvidas: equipe de engenharia da Urbana do Brasil.
