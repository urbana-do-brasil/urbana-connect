# Gerenciamento de Secrets Kubernetes

Este diretório contém os templates e instruções para gerenciar secrets da aplicação Urbana Connect.

## Estrutura de Diretórios

```
secrets/
├── README.md               # Este arquivo
├── templates/              # Templates para criar secrets
│   ├── registry-secret-template.yaml
│   ├── openai-secret-template.yaml
│   ├── whatsapp-secret-template.yaml
│   ├── mongodb-secret-template.yaml
│   └── secret-template.yaml
└── prod/                   # Diretório para armazenar os secrets reais (não versionado)
    └── .gitkeep
```

## Arquivos de Secrets

Por motivos de segurança, os arquivos contendo dados sensíveis não são versionados diretamente.
Em vez disso, são fornecidos templates para criar os arquivos reais:

- `templates/registry-secret-template.yaml` → `prod/registry-secret.yaml` (Credenciais do registro Docker)
- `templates/openai-secret-template.yaml` → `prod/openai-secret.yaml` (Chave API OpenAI)
- `templates/whatsapp-secret-template.yaml` → `prod/whatsapp-secret.yaml` (Credenciais WhatsApp)
- `templates/secret-template.yaml` → `prod/[seu-secret].yaml` (Template genérico para outros secrets)
- `templates/mongodb-secret-template.yaml` → `prod/mongodb-secret.yaml` (Credenciais do MongoDB)

## Como usar os templates

1. Copie o arquivo template para o diretório `prod`:
   ```bash
   cp templates/whatsapp-secret-template.yaml prod/whatsapp-secret.yaml
   ```

2. Edite o arquivo para incluir as credenciais reais:
   ```bash
   vim prod/whatsapp-secret.yaml
   ```

3. Aplique o arquivo usando kubectl:
   ```bash
   kubectl apply -f prod/whatsapp-secret.yaml
   ```

## Implantação dos Recursos

Para implantar a aplicação principal:

```bash
kubectl apply -k ../app
```

Para implantar o ClusterIssuer para certificados:

```bash
kubectl apply -k ../cert-manager
```

Para o MongoDB (após criar os arquivos a partir dos templates):

```bash
kubectl apply -f prod/mongodb-secret.yaml
kubectl apply -f ../mongodb/mongodb-simple.yaml
```

**Importante**: Lembre-se de que o diretório `prod` não é versionado. Faça backup dos seus arquivos de secret em um local seguro! 