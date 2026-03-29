# Infraestrutura - Urbana Connect

Este documento descreve a infraestrutura atual do Urbana Connect, alinhada ao ambiente de homologacao efetivamente em uso.

## Visao geral

O projeto roda hoje com uma aplicacao Spring Boot empacotada em container e implantada em um cluster `k3s` hospedado em VPS da Contabo.

O foco desta fase e suportar a integracao inicial com WhatsApp Business em homolog, com observabilidade basica, pipeline de CI/CD e um runtime enxuto.

## Stack atual

| Camada | Tecnologia |
|--------|------------|
| Aplicacao | Java 21 + Spring Boot 3.4.x |
| Build | Gradle 8.x |
| Runtime | Docker + Kubernetes |
| Cluster | `k3s` em Contabo VPS |
| Manifestos | Kustomize (`base` + `overlays`) |
| Banco | MongoDB em `StatefulSet` |
| Registro de imagens | GHCR (`ghcr.io`) |
| Observabilidade | Prometheus + Grafana + Loki + Promtail |
| Certificados | cert-manager + Let's Encrypt |
| CI | GitHub Actions |
| CD | GitHub Actions via branch `hml` |

## Ambientes

### Desenvolvimento local

O ambiente local e mantido simples:

- aplicacao Spring Boot executada localmente
- MongoDB local via `docker-compose`
- testes de integracao com `Testcontainers`

Esse fluxo existe para acelerar desenvolvimento e validacao funcional sem depender do cluster.

### Homologacao

O ambiente de homologacao usa:

- host publico: `api-hml.urbanadobrasil.com`
- cluster `k3s`
- namespace principal da app: `urbana-connect-hml`
- deploy da aplicacao via `Deployment`
- MongoDB dedicado via `StatefulSet`

## Componentes principais

### Aplicacao

Recursos Kubernetes da app:

- `Deployment`
- `Service` `ClusterIP`
- `ConfigMap`
- `Ingress`

Organizacao dos manifests:

- `infra/k8s/app/base`
- `infra/k8s/app/overlays/hml`

No ambiente publico, o `Ingress` expoe apenas os endpoints necessarios:

- `/api/webhook`
- `/api/v1/health`
- `/api/v1/readiness`

O path `/actuator` nao e roteado externamente.

### MongoDB

O MongoDB de homolog e executado dentro do cluster com:

- `StatefulSet`
- `Service` headless
- PVC
- `ConfigMap`

Organizacao dos manifests:

- `infra/k8s/mongodb/base`
- `infra/k8s/mongodb/overlays/hml`

### Observabilidade

A observabilidade de homolog usa:

- `kube-prometheus-stack`
- `Loki`
- `Promtail`
- `ServiceMonitor`
- `PrometheusRule`

O scrape de metricas da aplicacao acontece internamente pelo cluster, sem depender do `Ingress` publico.

Arquivos principais:

- `infra/k8s/observability/`
- `infra/k8s/prometheus/values-hml.yaml`
- `infra/k8s/loki/values-hml.yaml`
- `infra/k8s/promtail/values-hml.yaml`

### Certificados e DNS

O host publico de homolog usa:

- DNS apontando para o IP publico da VPS
- `Traefik` como ingress controller
- `cert-manager` com `ClusterIssuer`
- emissao automatica de certificado TLS via Let's Encrypt

Arquivos principais:

- `infra/k8s/cert-manager/cluster-issuer.yaml`
- `infra/k8s/app/overlays/hml/ingress.yaml`

## Segredos e configuracao

Os valores sensiveis nao ficam versionados no repositorio.

O projeto usa:

- templates em `infra/k8s/secrets/templates`
- secrets reais aplicados diretamente no cluster
- secrets do GitHub Actions para etapas de deploy

Segredos principais de homolog:

- credenciais de pull do GHCR
- string de conexao do MongoDB
- credenciais do webhook/WhatsApp
- `KUBE_CONFIG_HML` no GitHub Actions

## Pipeline

### CI

A integracao continua valida:

- build da aplicacao
- execucao de testes
- cobertura JaCoCo
- SonarCloud quando configurado

Workflow principal:

- `.github/workflows/build-test.yml`

### CD de homolog

O deploy de homolog ocorre via branch `hml`.

Fluxo:

1. build da imagem Docker da aplicacao
2. push da imagem para o GHCR
3. configuracao de acesso ao cluster com `KUBE_CONFIG_HML`
4. apply da overlay `infra/k8s/app/overlays/hml`
5. acompanhamento do rollout

Workflow principal:

- `.github/workflows/deploy-hml.yml`

## Layout do repositorio relacionado a infra

```text
infra/
  k8s/
    app/
      base/
      overlays/hml/
    mongodb/
      base/
      overlays/hml/
    observability/
    prometheus/
    loki/
    promtail/
    cert-manager/
    secrets/
```

## Estado atual validado

No estado atual de homolog, ja houve validacao de:

- DNS publico funcional
- TLS valido
- app em execucao no cluster
- MongoDB em execucao no cluster
- `readiness` retornando `200`
- webhook publico recebendo requisicoes da Meta/WhatsApp

## Limites atuais e proximos passos

Alguns pontos ainda estao fora deste documento ou em subtarefas especificas:

- probes de `liveness` e `readiness` no `Deployment`
- evolucao do processamento do webhook alem do recebimento basico
- limpeza de outros documentos de arquitetura ainda marcados pelo legado

## Referencias relacionadas

- `README.md`
- `infra/k8s/README.md`
- `docs/github-actions-secrets-hml.md`
- `docs/validacao-webhook-publico-hml.md`
