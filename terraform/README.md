# Infraestrutura como Código - Urbana Connect

Este repositório contém a configuração de infraestrutura como código (IaC) usando Terraform para o projeto Urbana Connect. A infraestrutura é provisionada no Google Cloud Platform (GCP) e utiliza recursos do nível gratuito do GKE quando possível.

## Estrutura do Projeto

```
terraform/
├── modules/                  # Módulos reutilizáveis
│   ├── network/              # Módulo de rede (VPC, subnets)
│   ├── compute/              # Módulo de computação (GKE)
│   ├── database/             # Módulo de banco de dados (Cloud SQL)
│   ├── storage/              # Módulo de armazenamento (Cloud Storage)
│   └── security/             # Módulo de segurança (IAM, firewall)
├── environments/             # Configurações específicas de ambiente
│   ├── dev/                  # Ambiente de desenvolvimento
│   ├── staging/              # Ambiente de homologação
│   └── prod/                 # Ambiente de produção
├── scripts/                  # Scripts auxiliares
└── README.md                 # Esta documentação
```

## Pré-requisitos

- [Terraform](https://www.terraform.io/downloads.html) v1.0.0 ou superior
- [Google Cloud SDK](https://cloud.google.com/sdk/docs/install)
- Conta no Google Cloud Platform com permissões para criar recursos
- Projeto GCP criado para cada ambiente (dev, staging, prod)
- Service Account com permissões adequadas para provisionar recursos

## Configuração Inicial

### 1. Autenticação no Google Cloud

```bash
gcloud auth login
gcloud config set project [PROJECT_ID]
```

### 2. Criar Service Account para o Terraform

```bash
# Criar Service Account
gcloud iam service-accounts create terraform-sa --display-name="Terraform Service Account"

# Atribuir permissões necessárias
gcloud projects add-iam-policy-binding [PROJECT_ID] \
  --member="serviceAccount:terraform-sa@[PROJECT_ID].iam.gserviceaccount.com" \
  --role="roles/editor"

# Criar e baixar a chave
gcloud iam service-accounts keys create terraform-sa-key.json \
  --iam-account=terraform-sa@[PROJECT_ID].iam.gserviceaccount.com
```

### 3. Configurar Variáveis de Ambiente

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/terraform-sa-key.json"
```

### 4. Inicializar o Terraform

Use o script de inicialização para criar o bucket de estado e inicializar o Terraform:

```bash
# Tornar o script executável
chmod +x scripts/init.sh

# Inicializar o ambiente de desenvolvimento
./scripts/init.sh dev [PROJECT_ID]
```

## Uso

### Gerenciamento de Ambientes

Cada ambiente (dev, staging, prod) tem sua própria configuração e estado do Terraform. Os scripts na pasta `scripts/` facilitam o gerenciamento dos ambientes.

#### Inicializar um Ambiente

```bash
./scripts/init.sh [ambiente] [projeto-id]
```

#### Aplicar Mudanças

```bash
./scripts/apply.sh [ambiente] [auto-approve]
```

#### Destruir Infraestrutura

```bash
./scripts/destroy.sh [ambiente] [auto-approve]
```

### Gerenciamento Manual

Se preferir gerenciar manualmente, navegue até o diretório do ambiente desejado e execute os comandos do Terraform:

```bash
cd environments/dev

# Inicializar
terraform init

# Planejar mudanças
terraform plan

# Aplicar mudanças
terraform apply

# Destruir infraestrutura
terraform destroy
```

## Módulos

### Módulo de Rede

Cria uma VPC e subnet para o projeto, além de regras de firewall básicas.

**Recursos principais:**
- VPC
- Subnet
- Regras de firewall para comunicação interna
- Regras de firewall para acesso SSH

### Módulo de Computação (GKE)

Cria um cluster Kubernetes gerenciado (GKE) para hospedar a aplicação.

**Recursos principais:**
- Cluster GKE
- Node Pool
- Service Account para o GKE

### Módulo de Banco de Dados

Cria uma instância Cloud SQL para PostgreSQL.

**Recursos principais:**
- Instância Cloud SQL
- Banco de dados
- Usuário do banco de dados

### Módulo de Armazenamento

Cria buckets do Cloud Storage para armazenamento de arquivos.

**Recursos principais:**
- Bucket principal
- Pastas para uploads e backups
- Configurações de ciclo de vida

### Módulo de Segurança

Implementa recursos de segurança para a infraestrutura.

**Recursos principais:**
- Service Account para a aplicação
- Regras de firewall para HTTP/HTTPS
- Secret Manager para armazenar secrets

## CI/CD

O arquivo `scripts/ci-cd-pipeline.yml` contém um exemplo de pipeline CI/CD para GitHub Actions que automatiza o processo de validação, planejamento e aplicação das mudanças de infraestrutura.

### Fluxo de Trabalho

1. **Pull Request**: Valida e planeja as mudanças, comentando no PR com o resultado.
2. **Merge para develop**: Aplica automaticamente as mudanças no ambiente de staging.
3. **Merge para main**: Aplica automaticamente as mudanças no ambiente de produção.
4. **Manual**: Permite executar ações específicas (plan, apply, destroy) em qualquer ambiente.

## Gerenciamento de Secrets

Os secrets são gerenciados usando o Secret Manager do Google Cloud. As credenciais sensíveis, como senhas de banco de dados, não são armazenadas no código.

Para adicionar um novo secret:

```bash
# Criar o secret
gcloud secrets create [NOME_DO_SECRET] --project=[PROJECT_ID]

# Adicionar uma versão do secret
echo -n "valor-do-secret" | gcloud secrets versions add [NOME_DO_SECRET] --data-file=-
```

## Boas Práticas

1. **Versionamento**: Todo o código de infraestrutura é versionado no Git.
2. **Modularização**: Recursos relacionados são agrupados em módulos reutilizáveis.
3. **Separação de Ambientes**: Cada ambiente tem sua própria configuração e estado.
4. **Automação**: O processo de deploy é automatizado através de CI/CD.
5. **Segurança**: Secrets são gerenciados de forma segura, fora do código.
6. **Documentação**: Todos os módulos e recursos são documentados.

## Troubleshooting

### Problemas Comuns

#### Erro de Autenticação

```
Error: google: could not find default credentials
```

**Solução**: Verifique se a variável de ambiente `GOOGLE_APPLICATION_CREDENTIALS` está configurada corretamente.

#### Erro de Permissão

```
Error: Error creating Network: googleapi: Error 403: Required 'compute.networks.create' permission
```

**Solução**: Verifique se o Service Account tem as permissões necessárias para criar os recursos.

#### Erro de Quota

```
Error: Error creating Instance: googleapi: Error 403: Quota 'CPUS' exceeded
```

**Solução**: Solicite um aumento de quota no console do GCP ou reduza o tamanho dos recursos.

## Contribuição

1. Crie um branch a partir de `develop`
2. Faça suas alterações
3. Abra um Pull Request para `develop`
4. Após revisão e testes, o PR será mesclado

## Licença

Este projeto é licenciado sob a licença MIT - veja o arquivo LICENSE para detalhes. 