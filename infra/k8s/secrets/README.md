# Gerenciamento de Secrets Kubernetes

Este diretório contém os templates e instruções para gerenciar secrets da aplicação Urbana Connect.

## Estrutura de Diretórios

```
secrets/
├── README.md               # Este arquivo
├── apply-hml-runtime-secrets.sh
├── templates/              # Templates para criar secrets
│   ├── registry-secret-template.yaml
│   ├── openai-secret-template.yaml
│   ├── whatsapp-secret-template.yaml
│   ├── mongodb-secret-template.yaml
│   ├── mongodb-uri-secret-template.yaml
│   ├── grafana-admin-secret-template.yaml
│   └── secret-template.yaml
└── prod/                   # Diretório para armazenar os secrets reais (não versionado)
    └── .gitkeep
```

## Arquivos de Secrets

Por motivos de segurança, os arquivos contendo dados sensíveis não são versionados diretamente.
Em vez disso, são fornecidos templates para criar os arquivos reais:

- `templates/registry-secret-template.yaml` → `prod/registry-secret.yaml` (Credenciais do container registry)
- `templates/openai-secret-template.yaml` → `prod/openai-secret.yaml` (Chave API OpenAI)
- `templates/whatsapp-secret-template.yaml` → `prod/whatsapp-secret.yaml` (Credenciais WhatsApp)
- `templates/secret-template.yaml` → `prod/[seu-secret].yaml` (Template genérico para outros secrets)
- `templates/mongodb-secret-template.yaml` → `prod/mongodb-secret.yaml` (Credenciais base do MongoDB)
- `templates/mongodb-uri-secret-template.yaml` → `prod/mongodb-uri-secret.yaml` (URI consumida pela aplicação)
- `templates/grafana-admin-secret-template.yaml` → `prod/grafana-admin-secret.yaml` (Usuário e senha admin do Grafana)

## Secrets de Runtime para Homolog

Obrigatórios para a aplicação em `urbana-connect-hml`:

- `prod/registry-secret.yaml`
- `prod/mongodb-secret.yaml`
- `prod/mongodb-uri-secret.yaml`
- `prod/whatsapp-secret.yaml`

Opcionais neste estágio:

- `prod/openai-secret.yaml`
- `prod/grafana-admin-secret.yaml`

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

## Aplicação na VPS

Com o `kubectl` já apontando para o cluster de homolog, aplique os runtime secrets com:

```bash
./apply-hml-runtime-secrets.sh
```

Se quiser usar outro diretório de arquivos reais:

```bash
./apply-hml-runtime-secrets.sh /caminho/para/secrets
```

O script:

- valida a presença dos arquivos obrigatórios
- aplica os secrets em ordem segura
- verifica se os secrets essenciais existem em `urbana-connect-hml`
- valida o secret do Grafana em `monitoring` quando esse arquivo estiver presente

## Implantação dos Recursos

Para implantar a aplicação principal em homolog:

```bash
kubectl apply -k ../app/overlays/hml
```

Para implantar o ClusterIssuer para certificados:

```bash
kubectl apply -k ../cert-manager
```

Para o MongoDB (após criar os arquivos a partir dos templates):

```bash
kubectl apply -f prod/mongodb-secret.yaml
kubectl apply -f prod/mongodb-uri-secret.yaml
kubectl apply -k ../mongodb/overlays/hml
```

Para a observabilidade (após criar o secret do Grafana):

```bash
kubectl apply -f prod/grafana-admin-secret.yaml
```

## Verificação Manual

```bash
kubectl get secret -n urbana-connect-hml
kubectl get secret grafana-admin-credentials -n monitoring
```

**Importante**: Lembre-se de que o diretório `prod` não é versionado. Faça backup dos seus arquivos de secret em um local seguro! 
