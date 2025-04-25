# Infraestrutura Kubernetes - Urbana Connect

Este diretório contém os manifestos Kubernetes para a infraestrutura da aplicação Urbana Connect.

## Estrutura de Diretórios

```
k8s/
├── app/                      # Aplicação principal
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   └── kustomization.yaml
├── cert-manager/
│   ├── cluster-issuer.yaml
│   └── kustomization.yaml
├── mongodb/                  # Componente MongoDB
│   ├── mongodb-simple-template.yaml
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

Contém os recursos principais da aplicação Urbana Connect:
- Deployment
- Service
- Ingress
- ConfigMap

Para implantar:
```bash
kubectl apply -k app
```

### Cert-Manager

Configuração do ClusterIssuer para obtenção automática de certificados TLS via Let's Encrypt.

Para implantar:
```bash
kubectl apply -k cert-manager
```

### MongoDB

Recursos para implantação do MongoDB. Observe que a implantação completa requer a criação prévia dos secrets.

Para implantar (após configurar o secret):
```bash
kubectl apply -f secrets/prod/mongodb-secret.yaml
kubectl apply -k mongodb
```

### Secrets

Contém templates para os secrets e instruções de gerenciamento. Para mais detalhes, consulte o [README.md](./secrets/README.md) no diretório de secrets.

## Fluxo de Implantação Completo

1. Crie os secrets a partir dos templates (veja as instruções em `secrets/README.md`)
2. Aplique os secrets
3. Aplique os componentes:
   ```bash
   kubectl apply -k cert-manager
   kubectl apply -k app
   kubectl apply -k mongodb
   ``` 