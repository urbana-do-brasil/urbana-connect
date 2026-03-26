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

Na overlay de homolog também fica o `Ingress` público da aplicação:
- host: `api-hml.urbanadobrasil.com`
- TLS: `letsencrypt-prod` via `cert-manager`
- ingress controller: `Traefik` (stack atual do `k3s/Contabo`)

Para homolog:
```bash
kubectl apply -k app/overlays/hml
```

### Cert-Manager

Configuração do ClusterIssuer para obtenção automática de certificados TLS via Let's Encrypt.

O solver HTTP01 está alinhado ao `Traefik`, que é o ingress controller esperado no ambiente atual de homolog.

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
3. Garanta o DNS público:
   - crie/atualize o registro `A` de `api-hml.urbanadobrasil.com`
   - aponte para o IP público da VPS de homolog
4. Aplique os componentes:
   ```bash
   kubectl apply -k cert-manager
   kubectl apply -k app/overlays/hml
   kubectl apply -k mongodb/overlays/hml
   ```

5. Instale a observabilidade de homolog seguindo `observability/README.md`
