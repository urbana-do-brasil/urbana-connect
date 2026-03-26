# Infraestrutura Kubernetes - Urbana Connect

Este diretório contém os manifestos Kubernetes para a infraestrutura da aplicação Urbana Connect.

## Estrutura de Diretórios

```
k8s/
├── app/                      # Estrutura de execução da aplicação
│   ├── base/
│   ├── legacy/
│   ├── overlays/
│   │   └── hml/
│   └── kustomization.yaml
├── mongodb/                  # Estrutura de execução do MongoDB
│   ├── base/
│   ├── legacy/
│   ├── overlays/
│   │   └── hml/
│   └── kustomization.yaml
├── observability/            # ServiceMonitor, rules e namespace de monitoramento
├── prometheus/               # Values Helm do kube-prometheus-stack
├── loki/                     # Values Helm do Loki
├── promtail/                 # Values Helm do Promtail
├── cert-manager/
│   ├── cluster-issuer.yaml
│   └── kustomization.yaml
├── secrets/                  # Diretório para templates e secrets reais
│   ├── README.md
│   ├── templates/
│   ├── .gitignore
│   └── prod/
└── README.md                 # Este arquivo
```

## Componentes

### App

Contém a estrutura de execução da aplicação Urbana Connect.

Nova organização:
- `base/`: recursos comuns do runtime em container
- `overlays/hml/`: customizações de homolog
- `legacy/`: manifestos monolíticos antigos, mantidos apenas como referência

Para homolog:
```bash
kubectl apply -k app/overlays/hml
```

### Cert-Manager

Configuração do ClusterIssuer para obtenção automática de certificados TLS via Let's Encrypt.

Para implantar:
```bash
kubectl apply -k cert-manager
```

### MongoDB

Contém a estrutura de execução do MongoDB para homolog.

Nova organização:
- `base/`: recursos comuns do MongoDB
- `overlays/hml/`: customizações de homolog
- `legacy/`: manifestos antigos, mantidos apenas como referência

Para implantar:
```bash
kubectl apply -f secrets/prod/mongodb-secret.yaml
kubectl apply -f secrets/prod/mongodb-uri-secret.yaml
kubectl apply -k mongodb/overlays/hml
```

### Secrets

Contém templates para os secrets e instruções de gerenciamento. Para mais detalhes, consulte o [README.md](./secrets/README.md) no diretório de secrets.

### Observabilidade

Contém a configuração de observabilidade de homolog para `k3s/Contabo`.

Inclui:
- `kube-prometheus-stack` via Helm
- `Loki` e `Promtail` via Helm
- `ServiceMonitor` e `PrometheusRule` da aplicação

Para detalhes de instalação:

```bash
cat observability/README.md
```

## Fluxo de Implantação Completo

1. Crie os secrets a partir dos templates (veja as instruções em `secrets/README.md`)
2. Aplique os secrets
3. Aplique os componentes:
   ```bash
   kubectl apply -k cert-manager
   kubectl apply -k app/overlays/hml
   kubectl apply -k mongodb/overlays/hml
   ```

4. Instale a observabilidade de homolog seguindo `observability/README.md`
