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
app/src/main/java/br/com/urbana/connect/
├── domain/          # Entidades e contratos (sem dependências externas)
├── application/     # Casos de uso, serviços e configurações
├── infrastructure/  # Implementações: MongoDB, WhatsApp API, etc.
└── interfaces/      # Entrada: REST controllers, webhooks
```

## Estrutura do Repositório

```
urbana-connect/
├── app/                     # Código da aplicação Spring Boot
│   ├── src/                 # Código-fonte Java e testes
│   ├── build.gradle         # Dependências e configuração de build
│   └── Dockerfile           # Imagem da aplicação
├── infra/
│   └── k8s/                 # Manifestos Kubernetes (Kustomize)
│       ├── app/             # Deployment, Service, ConfigMap
│       ├── mongodb/         # StatefulSet do MongoDB
│       ├── observability/   # ServiceMonitor e PrometheusRules
│       ├── prometheus/      # Helm values do kube-prometheus-stack
│       ├── loki/            # Helm values do Loki
│       ├── promtail/        # Helm values do Promtail
│       └── secrets/         # Templates de Secrets (não commitados)
└── .github/workflows/
    └── build-test.yml       # CI: build + testes + quality gate
```

## Pré-requisitos de Desenvolvimento

- JDK 21
- Docker (para Testcontainers)

## Executando os testes

```bash
cd app
./gradlew test
```

O quality gate exige **60% de cobertura de linha** (JaCoCo). O build falha se o threshold não for atingido.

## Endpoints

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/v1/health` | Health check |
| `GET` | `/api/v1/readiness` | Readiness check (verifica MongoDB) |
| `GET` | `/api/webhook` | Verificação de webhook Meta (challenge) |
| `POST` | `/api/webhook` | Recebimento de eventos WhatsApp |
| `GET` | `/actuator/prometheus` | Métricas Prometheus |

## Testando o Webhook Localmente

```bash
# Verificação de challenge (simulando Meta)
curl "http://localhost:8080/api/webhook?hub.mode=subscribe&hub.verify_token=SEU_TOKEN&hub.challenge=12345"

# Evento de mensagem recebida
curl -X POST http://localhost:8080/api/webhook \
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

Os manifestos em `infra/k8s/` seguem o padrão Kustomize com `base/` + `overlays/hml/`. Para aplicar:

```bash
kubectl apply -k infra/k8s/app/overlays/hml/
kubectl apply -k infra/k8s/mongodb/overlays/hml/
kubectl apply -k infra/k8s/observability/
```

## Contato

Dúvidas: equipe de engenharia da Urbana do Brasil.
