# Pipeline de CI/CD Automatizado para Java + Spring

Este repositório contém a configuração completa de um pipeline de CI/CD automatizado para uma aplicação Java + Spring, utilizando GitHub Actions, Docker e Kubernetes.

## Visão Geral

O pipeline implementa um fluxo completo de integração e entrega contínua, permitindo que desenvolvedores foquem no desenvolvimento de features enquanto testes, build e deploy são gerenciados automaticamente.

### Fluxo do Pipeline

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│  Pull Request   │────▶│    CI Build     │────▶│  Homologação    │
│                 │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│   Produção      │◀────│   Aprovação     │◀────│  Testes de      │
│                 │     │    Manual       │     │  Integração     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## Estrutura do Projeto

```
.
├── .github/
│   └── workflows/
│       ├── ci.yml                # Workflow de Integração Contínua
│       ├── cd-staging.yml        # Workflow de Deploy para Homologação
│       └── cd-production.yml     # Workflow de Deploy para Produção
├── k8s/
│   ├── staging/                  # Manifestos Kubernetes para Homologação
│   │   ├── namespace.yaml
│   │   ├── configmap.yaml
│   │   ├── secret.yaml
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── ingress.yaml
│   └── production/               # Manifestos Kubernetes para Produção
│       ├── namespace.yaml
│       ├── configmap.yaml
│       ├── secret.yaml
│       ├── deployment.yaml
│       ├── service.yaml
│       └── ingress.yaml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/seudominio/app/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── model/
│   │   │       └── Application.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-staging.properties
│   │       └── application-production.properties
│   └── test/
│       ├── java/
│       │   └── com/seudominio/app/
│       │       ├── controller/
│       │       ├── service/
│       │       ├── integration/
│       │       └── ApplicationTests.java
│       └── resources/
│           └── application-test.properties
├── Dockerfile                    # Configuração para build da imagem Docker
├── .dockerignore                 # Arquivos a serem ignorados no build Docker
├── pom.xml                       # Configuração do Maven
└── README.md                     # Documentação do projeto
```

## Workflows do GitHub Actions

### 1. Integração Contínua (CI)

O workflow de CI é executado em cada push para as branches `main` e `develop`, bem como em pull requests para essas branches.

**Etapas:**
- Verificação de código (Checkstyle, SpotBugs)
- Execução de testes unitários com JaCoCo para cobertura
- Análise de segurança (SAST com CodeQL)
- Verificação de dependências com OWASP
- Build com Maven e publicação do JAR
- Build e publicação de imagens Docker
- Scan de vulnerabilidades nas imagens

Para executar manualmente:
```bash
# Não é necessário, pois é acionado automaticamente em pushes e PRs
```

### 2. Deploy para Homologação (CD-Staging)

O workflow de CD para homologação é executado automaticamente após o sucesso do workflow de CI na branch `develop`.

**Etapas:**
- Deploy automático para o ambiente de homologação
- Execução de testes de integração
- Notificações sobre o status do deploy

Para executar manualmente:
```bash
# Não é necessário, pois é acionado automaticamente após o CI
```

### 3. Deploy para Produção (CD-Production)

O workflow de CD para produção requer aprovação manual e é iniciado através da interface do GitHub Actions.

**Etapas:**
- Validação da aprovação manual
- Deploy para o ambiente de produção
- Monitoramento pós-deploy
- Rollback automático em caso de falha
- Notificações sobre o status do deploy

Para executar manualmente:
```bash
# Acesse a aba "Actions" no GitHub
# Selecione o workflow "Deploy para Produção"
# Clique em "Run workflow"
# Informe a versão para deploy e confirme com "yes"
```

## Configuração do Ambiente

### Secrets Necessários

Configure os seguintes secrets no seu repositório GitHub:

**Docker Hub:**
- `DOCKERHUB_USERNAME`: Seu nome de usuário no Docker Hub
- `DOCKERHUB_TOKEN`: Token de acesso ao Docker Hub

**Google Cloud Platform:**
- `GCP_SA_KEY`: Chave de conta de serviço do GCP (JSON)
- `GCP_PROJECT_ID`: ID do projeto no GCP
- `GKE_CLUSTER_NAME`: Nome do cluster GKE
- `GKE_CLUSTER_ZONE`: Zona do cluster GKE

**Notificações:**
- `SLACK_WEBHOOK`: URL do webhook do Slack
- `SMTP_SERVER`: Servidor SMTP para e-mails
- `SMTP_PORT`: Porta do servidor SMTP
- `SMTP_USERNAME`: Usuário SMTP
- `SMTP_PASSWORD`: Senha SMTP

### Configuração do Kubernetes

Os manifestos Kubernetes estão organizados em dois ambientes:

1. **Homologação (`k8s/staging/`):**
   - Ambiente para testes e validação
   - Configurado com 2 réplicas
   - Recursos limitados

2. **Produção (`k8s/production/`):**
   - Ambiente de produção
   - Configurado com 3 réplicas
   - Recursos otimizados para produção

## Guia de Implementação

### 1. Configuração Inicial

1. Clone este repositório
2. Configure os secrets necessários no GitHub
3. Personalize os manifestos Kubernetes conforme necessário
4. Ajuste o Dockerfile de acordo com sua aplicação

### 2. Desenvolvimento

1. Crie uma branch a partir de `develop`
2. Desenvolva suas features
3. Crie um Pull Request para `develop`
4. O pipeline de CI será executado automaticamente
5. Após aprovação e merge, o deploy para homologação será automático

### 3. Deploy para Produção

1. Verifique se a aplicação está funcionando corretamente em homologação
2. Acesse a aba "Actions" no GitHub
3. Selecione o workflow "Deploy para Produção"
4. Clique em "Run workflow"
5. Informe a versão para deploy e confirme com "yes"
6. Monitore o status do deploy

### 4. Troubleshooting

**Falha nos testes:**
- Verifique os logs de CI para identificar os testes que falharam
- Corrija os problemas e faça push novamente

**Falha no deploy para homologação:**
- Verifique os logs do workflow de CD-Staging
- Verifique os logs do Kubernetes: `kubectl logs -n staging deployment/app-name`

**Falha no deploy para produção:**
- Verifique se o rollback automático foi executado com sucesso
- Verifique os logs do workflow de CD-Production
- Verifique os logs do Kubernetes: `kubectl logs -n production deployment/app-name`

## Considerações Adicionais

### Escalabilidade

O pipeline foi projetado para ser escalável:
- Suporte a múltiplos ambientes
- Configuração modular dos workflows
- Estratégia de deploy com zero downtime

### Melhorias Futuras

- Implementação de testes de carga automatizados
- Integração com ferramentas de observabilidade (Prometheus, Grafana)
- Implementação de estratégias de deploy mais avançadas (Canary, Blue/Green)
- Automação de backups de banco de dados antes de deploys

### Alternativas

- **Jenkins:** Alternativa self-hosted com mais flexibilidade
- **GitLab CI/CD:** Solução integrada se estiver usando GitLab
- **CircleCI:** Alternativa cloud com foco em velocidade
- **Azure DevOps:** Solução integrada para ecossistema Microsoft

## Suporte

Para dúvidas ou problemas, abra uma issue neste repositório. 