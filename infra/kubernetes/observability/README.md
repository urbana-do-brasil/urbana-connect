# Observabilidade em Homolog

Esta estrutura prepara a observabilidade de homolog no `k3s/Contabo` sem depender do Terraform legado.

Escopo:
- `kube-prometheus-stack` para Prometheus, Alertmanager e Grafana
- `Loki` para armazenamento de logs
- `Promtail` para coleta de logs dos pods
- `ServiceMonitor` e `PrometheusRule` da aplicação

Não inclui:
- exposição pública do Grafana
- alertas externos (Slack, email, PagerDuty)
- dashboards customizados além dos dashboards padrão da stack

## Pré-requisitos

- cluster `k3s` com `StorageClass` padrão `local-path`
- Helm 3 instalado
- aplicação já implantada em `urbana-connect-hml`
- secret `grafana-admin-credentials` criado a partir do template em `../secrets/templates/grafana-admin-secret-template.yaml`

## Instalação

Adicionar os repositórios Helm:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

Instalar a stack de métricas:

```bash
helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  -f infra/kubernetes/prometheus/values-hml.yaml
```

Instalar o Loki:

```bash
helm upgrade --install loki grafana/loki \
  --namespace monitoring \
  --create-namespace \
  -f infra/kubernetes/loki/values-hml.yaml
```

Instalar o Promtail:

```bash
helm upgrade --install promtail grafana/promtail \
  --namespace monitoring \
  --create-namespace \
  -f infra/kubernetes/promtail/values-hml.yaml
```

Aplicar os manifests complementares da app:

```bash
kubectl apply -k infra/kubernetes/observability
```

## Validação rápida

Verificar pods:

```bash
kubectl get pods -n monitoring
```

Verificar descoberta do `ServiceMonitor`:

```bash
kubectl get servicemonitor -n monitoring
kubectl describe servicemonitor urbana-connect-sm -n monitoring
```

Verificar coleta de métricas da aplicação:

```bash
kubectl get svc -n urbana-connect-hml
kubectl port-forward -n urbana-connect-hml svc/urbana-connect 8080:80
curl http://127.0.0.1:8080/actuator/prometheus
```

Verificar datasource do Loki no Grafana:

```bash
kubectl get secret -n monitoring grafana-admin-credentials
kubectl port-forward -n monitoring svc/prometheus-stack-grafana 3000:80
```

## Rollback

```bash
helm uninstall promtail -n monitoring
helm uninstall loki -n monitoring
helm uninstall prometheus-stack -n monitoring
kubectl delete -k infra/kubernetes/observability
```
