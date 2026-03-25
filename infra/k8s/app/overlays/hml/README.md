# Homolog Container Runtime

Esta overlay define a estrutura mínima de execução em container da aplicação para homolog.

Inclui:
- `Deployment` da aplicação
- `Service` interno
- `ConfigMap` com perfil `hml`
- referência aos secrets e ao GHCR privado

Não inclui:
- `Ingress` público
- probes de health/readiness
- integração com OpenAI
- configuração de secrets reais

Esses pontos ficam para subtarefas seguintes da `PEE-30`.

## Aplicação

```bash
kubectl apply -k infra/k8s/app/overlays/hml
```

## Pré-requisitos

- secret `container-registry-credentials`
- secret `urbana-connect-mongodb-uri`
- secret `urbana-connect-whatsapp`
