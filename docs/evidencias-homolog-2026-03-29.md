# Evidencias de homolog - 2026-03-29

Este documento registra a evidencia tecnica minima da homolog apos a configuracao do ambiente publico e a validacao do webhook.

## Ambiente validado

- Host publico: `api-hml.urbanadobrasil.com`
- Infraestrutura: `k3s` em `Contabo VPS`
- Cluster namespace da app: `urbana-connect-hml`
- Data da validacao: `2026-03-29`

## Estado do cluster

Validacao executada com `kubectl` usando o kubeconfig local do `k3s`.

Resultado observado:

- `urbana-connect` com pod `Running 1/1`
- `urbana-connect-mongodb-0` com pod `Running 1/1`
- `Service` da aplicacao com endpoint ativo
- `Ingress` publico resolvendo para `5.189.149.19`

## Validacao funcional

Validacoes executadas contra o endpoint publico:

- `GET /api/v1/readiness` retornando `200`
- `GET /api/webhook` com token invalido retornando `403`
- `POST /api/webhook` com payload valido retornando `200`
- `POST /api/webhook` com payload invalido retornando `400`

Adicionalmente, houve confirmacao operacional de recebimento de eventos reais da Meta/WhatsApp:

- `4` requisicoes `POST` com status `200`
- `1` requisicao `POST` com status `400` associada a payload invalido de teste

## Evidencia tecnica consolidada

Com base nos checks acima, a homolog atende ao minimo esperado para esta fase:

- DNS publico configurado e resolvendo corretamente
- TLS valido emitido para o host de homolog
- runtime da aplicacao em execucao
- conectividade da app com MongoDB funcional
- webhook publico acessivel e recebendo eventos do provider

## Limitacoes conhecidas

- Ainda nao ha log estruturado por requisicao do webhook no fluxo atual
- O `WebhookController` responde `200` de forma silenciosa, sem processamento de negocio
- O endurecimento de acesso ao `/actuator` segue em subtarefa separada
