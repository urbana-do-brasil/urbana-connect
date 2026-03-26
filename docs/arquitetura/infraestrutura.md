# Infraestrutura - Urbana Connect

Este documento descreve a infraestrutura implementada para o sistema Urbana Connect, com foco inicial em otimização de custos e implementação gradual.

## Visão Geral da Infraestrutura Atual

```mermaid
graph TD
    subgraph "Infraestrutura Inicial (Otimizada para Custos)"
        DOKS[Digital Ocean Kubernetes]
        NODE1[Node: s-2vcpu-2gb]
        AUTO[Auto-scaling: 1-3 nodes]
        
        DOKS --> NODE1
        DOKS --> AUTO
        
        subgraph "Recursos Planejados (Fase Posterior)"
            API[API Gateway]
            PROC[Message Processor]
            CONV[Conversation Manager]
            AI[AI Service]
            DB[(MongoDB)]
            CACHE[(Redis)]
        end
    end
```

## Implementação Atual

Atualmente, o Urbana Connect está na fase inicial de sua infraestrutura, com foco na criação e configuração do cluster Kubernetes. Esta abordagem gradual permite iniciar com custos mínimos e escalar conforme a demanda do negócio cresce.

### Cluster Kubernetes na Digital Ocean (DOKS)

O serviço foi implantado utilizando um cluster Kubernetes gerenciado na Digital Ocean com as seguintes características:

- **Região**: NYC1 (Nova York)
- **Versão do Kubernetes**: 1.32.2-do.0 (última versão estável)
- **Configuração de Nós**:
  - Tipo: s-2vcpu-2gb (2 vCPUs, 2GB RAM)
  - Quantidade inicial: 1 nó
  - Auto-scaling: Habilitado (min: 1, max: 3)
- **Custos estimados**: $18/mês (valor inicial com 1 nó)

### Infraestrutura como Código

Toda a infraestrutura é gerenciada através do Terraform, permitindo:

- Versionamento de configurações
- Reprodutibilidade dos ambientes
- Fácil atualização e expansão
- Documentação viva da infraestrutura

### Arquivo Terraform principais:

- `main.tf`: Definição do cluster DOKS
- `variables.tf`: Variáveis configuráveis
- `outputs.tf`: Saídas relevantes do cluster
- `providers.tf`: Configuração dos provedores
- `terraform.tfvars`: Valores das variáveis (não versionados)

## Ambiente de Desenvolvimento

### Configuração Local

Para um único desenvolvedor, o ambiente de desenvolvimento local deve ser simples e eficiente:

1. **Docker Compose:**
   - Todos os serviços em um único arquivo docker-compose.yml
   - Volumes para persistência de dados
   - Hot-reload para desenvolvimento rápido

2. **Serviços Locais:**
   - MongoDB single-node
   - Redis single-node
   - API Gateway
   - Message Processor
   - Conversation Manager
   - AI Service (com mock opcional)
   - Human Handoff UI

3. **Ferramentas de Desenvolvimento:**
   - Nodemon para auto-restart
   - MongoDB Compass para visualização de dados
   - Redis Commander para inspeção de cache
   - Postman/Insomnia para testes de API

### Exemplo de Docker Compose para Desenvolvimento

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:5.0
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    environment:
      - MONGO_INITDB_ROOT_USERNAME=urbana
      - MONGO_INITDB_ROOT_PASSWORD=devpassword

  redis:
    image: redis:6.2
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  api-gateway:
    build:
      context: ./api-gateway
      dockerfile: Dockerfile.dev
    ports:
      - "3000:3000"
    volumes:
      - ./api-gateway:/app
      - /app/node_modules
    environment:
      - NODE_ENV=development
      - MONGO_URI=mongodb://urbana:devpassword@mongodb:27017/urbana-connect
      - REDIS_URI=redis://redis:6379
    depends_on:
      - mongodb
      - redis

  message-processor:
    build:
      context: ./message-processor
      dockerfile: Dockerfile.dev
    volumes:
      - ./message-processor:/app
      - /app/node_modules
    environment:
      - NODE_ENV=development
      - MONGO_URI=mongodb://urbana:devpassword@mongodb:27017/urbana-connect
      - REDIS_URI=redis://redis:6379
    depends_on:
      - mongodb
      - redis
      - api-gateway

  conversation-manager:
    build:
      context: ./conversation-manager
      dockerfile: Dockerfile.dev
    volumes:
      - ./conversation-manager:/app
      - /app/node_modules
    environment:
      - NODE_ENV=development
      - MONGO_URI=mongodb://urbana:devpassword@mongodb:27017/urbana-connect
      - REDIS_URI=redis://redis:6379
    depends_on:
      - mongodb
      - redis
      - message-processor

  ai-service:
    build:
      context: ./ai-service
      dockerfile: Dockerfile.dev
    volumes:
      - ./ai-service:/app
      - /app/node_modules
    environment:
      - NODE_ENV=development
      - OPENAI_API_KEY=your-api-key-here
      - REDIS_URI=redis://redis:6379
    depends_on:
      - redis

  human-handoff:
    build:
      context: ./human-handoff
      dockerfile: Dockerfile.dev
    ports:
      - "3001:3001"
    volumes:
      - ./human-handoff:/app
      - /app/node_modules
    environment:
      - NODE_ENV=development
      - MONGO_URI=mongodb://urbana:devpassword@mongodb:27017/urbana-connect
      - REDIS_URI=redis://redis:6379
    depends_on:
      - mongodb
      - redis

  redis-commander:
    image: rediscommander/redis-commander
    ports:
      - "8081:8081"
    environment:
      - REDIS_HOSTS=local:redis:6379
    depends_on:
      - redis

  mongo-express:
    image: mongo-express
    ports:
      - "8082:8081"
    environment:
      - ME_CONFIG_MONGODB_ADMINUSERNAME=urbana
      - ME_CONFIG_MONGODB_ADMINPASSWORD=devpassword
      - ME_CONFIG_MONGODB_URL=mongodb://urbana:devpassword@mongodb:27017/
    depends_on:
      - mongodb

volumes:
  mongodb_data:
  redis_data:
```

## Ambiente de Staging

O ambiente de staging deve ser uma versão reduzida, mas funcionalmente idêntica ao ambiente de produção:

1. **Infraestrutura:**
   - Kubernetes cluster pequeno (1-3 nodes)
   - MongoDB ReplicaSet (3 nodes)
   - Redis Sentinel (3 nodes)
   - Elasticsearch single-node
   - Prometheus + Grafana

2. **Configuração:**
   - Recursos limitados (CPU/RAM)
   - Dados de teste ou anonimizados
   - Integração com sandbox de APIs externas

3. **Propósito:**
   - Testes de integração
   - Testes de performance
   - Validação de deploys
   - Treinamento

## Ambiente de Produção

### Infraestrutura em Nuvem

A infraestrutura de produção é projetada para ser escalável e resiliente:

1. **Kubernetes Cluster:**
   - Múltiplos nodes distribuídos em zonas
   - Auto-scaling baseado em métricas
   - Liveness e readiness probes
   - Afinidade de pods para otimização

2. **Bancos de Dados:**
   - MongoDB Atlas (gerenciado) ou MongoDB Cluster auto-hospedado
   - Redis Cluster com persistência
   - Elasticsearch Cluster para logs e busca

3. **Rede e Segurança:**
   - Load balancer com SSL termination
   - VPC com subnets privadas
   - Network policies restritivas
   - Secrets management (Kubernetes Secrets ou Vault)

### Exemplo de Configuração Kubernetes

#### API Gateway Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: urbana-connect
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  strategy:
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
    type: RollingUpdate
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: api-gateway
        image: urbana-connect/api-gateway:latest
        ports:
        - containerPort: 3000
        resources:
          limits:
            cpu: "1"
            memory: "1Gi"
          requests:
            cpu: "500m"
            memory: "512Mi"
        env:
        - name: NODE_ENV
          value: "production"
        - name: MONGO_URI
          valueFrom:
            secretKeyRef:
              name: mongodb-credentials
              key: uri
        - name: REDIS_URI
          valueFrom:
            secretKeyRef:
              name: redis-credentials
              key: uri
        livenessProbe:
          httpGet:
            path: /health
            port: 3000
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 3000
          initialDelaySeconds: 5
          periodSeconds: 5
```

#### Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway-hpa
  namespace: urbana-connect
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

#### Service e Ingress

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-service
  namespace: urbana-connect
spec:
  selector:
    app: api-gateway
  ports:
  - port: 80
    targetPort: 3000
  type: ClusterIP

---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-gateway-ingress
  namespace: urbana-connect
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - api.urbana-connect.com
    secretName: urbana-connect-tls
  rules:
  - host: api.urbana-connect.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-gateway-service
            port:
              number: 80
```

## Estratégia de Implantação

### CI/CD Pipeline

O repositório utiliza GitHub Actions para executar a esteira mínima de integração contínua.

Pipeline atual:

- Trigger em `pull_request` para `main`
- Trigger em `push` para `main`
- Trigger manual via `workflow_dispatch`
- Build Gradle da aplicação em `app/`
- Execução de testes automatizados
- Geração do relatório JaCoCo
- Análise SonarCloud quando `SONAR_TOKEN` estiver configurado
- Publicação dos artefatos de teste e cobertura mesmo em caso de falha

Objetivo da CI atual:

- proteger o merge para `main`
- validar build, testes e quality gate básico
- fornecer evidência operacional por meio dos artefatos publicados
- manter a pipeline independente do fluxo de deploy, que fica para subtarefas específicas de CD

### Workflow Atual de CI

Arquivo:

```text
.github/workflows/build-test.yml
```

Resumo do fluxo:

1. Checkout com histórico completo para suportar SonarCloud.
2. Configuração do JDK 21.
3. Setup de cache e ambiente Gradle.
4. Execução de `./gradlew build jacocoTestReport`.
5. Detecção condicional do `SONAR_TOKEN`.
6. Análise SonarCloud apenas quando o secret existir.
7. Upload dos relatórios de teste e cobertura como artifacts do workflow.

Exemplo simplificado:

```yaml
name: Build, Test e Análise de Qualidade

on:
  workflow_dispatch:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build_test_analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - run: chmod +x ./gradlew
        working-directory: ./app
      - run: ./gradlew build jacocoTestReport
        working-directory: ./app
```

## Estratégia de Escalabilidade

### Escalabilidade Horizontal

1. **Componentes Stateless:**
   - API Gateway, Message Processor, AI Service
   - Escalam horizontalmente via HPA
   - Baseado em métricas de CPU, memória e custom metrics

2. **Componentes Stateful:**
   - MongoDB: Sharding por whatsappId
   - Redis: Cluster mode com sharding
   - Elasticsearch: Cluster com shards distribuídos

### Escalabilidade Vertical

1. **Otimização de Recursos:**
   - Monitoramento de uso para identificar necessidades
   - Ajuste gradual de limites de recursos
   - Priorização de componentes críticos

2. **Estratégia de Upgrade:**
   - Testes de carga para validar necessidades
   - Upgrades em janelas de baixo tráfego
   - Rollback automatizado em caso de problemas

## Monitoramento e Observabilidade

### Stack de Monitoramento

1. **Logging:**
   - Fluentd/Fluent Bit para coleta
   - Elasticsearch para armazenamento
   - Kibana para visualização

2. **Métricas:**
   - Prometheus para coleta e armazenamento
   - Grafana para dashboards
   - Alertmanager para alertas

3. **Tracing:**
   - Jaeger para tracing distribuído
   - OpenTelemetry para instrumentação

### Dashboards Essenciais

1. **Dashboard Operacional:**
   - Status de todos os serviços
   - Latência de resposta
   - Taxa de erros
   - Uso de recursos

2. **Dashboard de Negócio:**
   - Volume de mensagens
   - Tempo médio de resposta
   - Taxa de transferência para humanos
   - Satisfação do cliente

3. **Dashboard de Custos:**
   - Uso de tokens GPT-4
   - Uso de recursos de infraestrutura
   - Projeções de crescimento

## Disaster Recovery

### Estratégia de Backup

1. **Backups Regulares:**
   - MongoDB: Snapshots diários
   - Redis: RDB snapshots + AOF
   - Configurações: Git + backup de secrets

2. **Retenção:**
   - Política de retenção baseada em importância
   - Armazenamento em múltiplas regiões

### Plano de Recuperação

1. **RTO (Recovery Time Objective):**
   - Tier 1 (crítico): < 1 hora
   - Tier 2 (importante): < 4 horas
   - Tier 3 (não-crítico): < 24 horas

2. **RPO (Recovery Point Objective):**
   - Tier 1: < 5 minutos
   - Tier 2: < 1 hora
   - Tier 3: < 24 horas

3. **Procedimentos Documentados:**
   - Runbooks para cenários comuns
   - Checklist de verificação pós-recuperação
   - Testes regulares de recuperação
