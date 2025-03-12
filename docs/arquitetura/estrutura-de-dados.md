# Estrutura de Dados - Urbana Connect

Este documento descreve os principais modelos de dados utilizados no sistema Urbana Connect, suas relações e estratégias de armazenamento.

## Modelos de Dados Principais

### 1. Conversa (Conversation)

```json
{
  "_id": "ObjectId",
  "whatsappId": "String",  // ID do número no WhatsApp
  "customerId": "String",  // ID do cliente no sistema
  "status": "Enum(active, waiting_human, with_human, closed)",
  "startedAt": "Date",
  "lastMessageAt": "Date",
  "closedAt": "Date",
  "transferredAt": "Date",
  "metadata": {
    "customerName": "String",
    "language": "String",
    "source": "String",
    "tags": ["String"]
  },
  "context": {
    "lastIntent": "String",
    "lastSentiment": "String",
    "customData": "Object"
  },
  "humanAgent": {
    "agentId": "String",
    "name": "String",
    "assignedAt": "Date"
  },
  "metrics": {
    "responseTime": "Number",
    "messageCount": "Number",
    "aiTokensUsed": "Number",
    "transferAttempts": "Number"
  }
}
```

### 2. Mensagem (Message)

```json
{
  "_id": "ObjectId",
  "conversationId": "ObjectId",  // Referência à conversa
  "direction": "Enum(incoming, outgoing)",
  "type": "Enum(text, image, audio, video, document, location)",
  "content": {
    "text": "String",
    "mediaUrl": "String",
    "caption": "String",
    "latitude": "Number",
    "longitude": "Number"
  },
  "timestamp": "Date",
  "status": "Enum(received, processed, delivered, read)",
  "metadata": {
    "source": "Enum(customer, ai, human)",
    "aiModel": "String",
    "tokensUsed": "Number",
    "processingTime": "Number"
  },
  "analysis": {
    "intent": "String",
    "entities": ["Object"],
    "sentiment": "String",
    "keywords": ["String"]
  }
}
```

### 3. Cliente (Customer)

```json
{
  "_id": "ObjectId",
  "whatsappId": "String",  // Número de telefone com código do país
  "name": "String",
  "profile": {
    "email": "String",
    "language": "String",
    "timezone": "String",
    "registeredAt": "Date",
    "tags": ["String"]
  },
  "preferences": {
    "notifications": "Boolean",
    "contactTime": "Object"
  },
  "metrics": {
    "conversationCount": "Number",
    "lastInteraction": "Date",
    "averageResponseTime": "Number",
    "satisfactionScore": "Number"
  },
  "customData": "Object"  // Dados específicos do negócio
}
```

### 4. Atendente (Agent)

```json
{
  "_id": "ObjectId",
  "name": "String",
  "email": "String",
  "status": "Enum(online, busy, offline)",
  "role": "Enum(admin, supervisor, agent)",
  "skills": ["String"],
  "maxConcurrentChats": "Number",
  "activeChats": ["ObjectId"],  // Referências às conversas ativas
  "metrics": {
    "resolvedChats": "Number",
    "averageHandlingTime": "Number",
    "customerSatisfaction": "Number"
  },
  "schedule": {
    "workingHours": ["Object"],
    "timezone": "String"
  }
}
```

### 5. Resposta Pré-definida (Template)

```json
{
  "_id": "ObjectId",
  "name": "String",
  "category": "String",
  "content": "String",
  "variables": ["String"],  // Placeholders para personalização
  "tags": ["String"],
  "createdBy": "ObjectId",  // Referência ao atendente
  "createdAt": "Date",
  "updatedAt": "Date",
  "usage": {
    "count": "Number",
    "lastUsed": "Date"
  }
}
```

### 6. Evento (Event)

```json
{
  "_id": "ObjectId",
  "type": "String",  // message.received, handoff.requested, etc.
  "source": "String",  // Componente que gerou o evento
  "timestamp": "Date",
  "correlationId": "String",  // ID para rastreamento
  "data": "Object",  // Payload do evento
  "metadata": {
    "version": "String",
    "processingTime": "Number"
  }
}
```

## Estratégias de Armazenamento

### MongoDB

#### Coleções Principais:
- `conversations`: Armazena todas as conversas
- `messages`: Armazena todas as mensagens
- `customers`: Armazena dados dos clientes
- `agents`: Armazena dados dos atendentes
- `templates`: Armazena respostas pré-definidas
- `events`: Armazena eventos para auditoria (opcional)

#### Índices Recomendados:

```javascript
// Conversas
db.conversations.createIndex({ "whatsappId": 1 });
db.conversations.createIndex({ "status": 1 });
db.conversations.createIndex({ "lastMessageAt": -1 });
db.conversations.createIndex({ "humanAgent.agentId": 1 });

// Mensagens
db.messages.createIndex({ "conversationId": 1, "timestamp": -1 });
db.messages.createIndex({ "content.text": "text" });  // Índice de texto para busca

// Clientes
db.customers.createIndex({ "whatsappId": 1 }, { unique: true });
db.customers.createIndex({ "profile.email": 1 });
db.customers.createIndex({ "metrics.lastInteraction": -1 });

// Atendentes
db.agents.createIndex({ "email": 1 }, { unique: true });
db.agents.createIndex({ "status": 1, "skills": 1 });  // Para alocação de atendentes
```

#### Estratégia de Sharding:

Para escala horizontal, considerar sharding por:
- `conversations`: Shard key `{ whatsappId: 1 }`
- `messages`: Shard key `{ conversationId: 1, timestamp: -1 }`

### Redis

#### Estruturas de Dados:

1. **Cache de Conversas Ativas:**
   - Chave: `conversation:{whatsappId}`
   - Valor: Objeto JSON com dados da conversa ativa
   - TTL: 24 horas de inatividade

2. **Cache de Respostas da IA:**
   - Chave: `ai:response:{hash_do_contexto}`
   - Valor: Resposta gerada pela IA
   - TTL: Configurável (1-24 horas)

3. **Estado de Atendentes:**
   - Chave: `agent:{agentId}:status`
   - Valor: Status atual (online, busy, offline)
   - TTL: Sem expiração (atualizado por heartbeat)

4. **Filas de Processamento:**
   - Lista: `queue:messages`
   - Lista: `queue:handoff`
   - Sem TTL (processamento remove itens)

5. **Rate Limiting:**
   - Chave: `ratelimit:whatsapp:{endpoint}:{minute}`
   - Valor: Contador de requisições
   - TTL: 60 segundos

6. **Distribuição de Locks:**
   - Chave: `lock:conversation:{conversationId}`
   - Valor: ID do processo que possui o lock
   - TTL: 30 segundos (com renovação)

## Estratégias de Evolução do Schema

1. **Versionamento de Documentos:**
   - Incluir campo `schemaVersion` em cada documento
   - Migrar documentos sob demanda ou em background

2. **Campos Opcionais:**
   - Novos campos são adicionados como opcionais
   - Código lida com ausência de campos novos

3. **Migração Gradual:**
   - Scripts de migração executados em janelas de baixo tráfego
   - Migração em lotes para minimizar impacto

4. **Compatibilidade Retroativa:**
   - Manter suporte a formatos antigos por período de transição
   - Deprecar gradualmente APIs e formatos antigos

## Considerações de Performance

1. **Tamanho de Documentos:**
   - Limitar tamanho de documentos MongoDB (< 16MB)
   - Para históricos muito longos, considerar particionamento por data

2. **Estratégia de Leitura/Escrita:**
   - Leituras frequentes: Índices otimizados + cache
   - Escritas frequentes: Batching de operações

3. **Time-To-Live (TTL):**
   - Índices TTL para expirar automaticamente dados antigos:
     ```javascript
     db.events.createIndex({ "timestamp": 1 }, { expireAfterSeconds: 2592000 });  // 30 dias
     ```

4. **Compressão:**
   - Habilitar compressão no MongoDB para economizar espaço
   - Comprimir conteúdo de mídia antes do armazenamento

## Estratégia de Backup e Recuperação

1. **Backup Regular:**
   - MongoDB: Snapshots diários completos
   - MongoDB: Oplog para point-in-time recovery
   - Redis: RDB snapshots + AOF para durabilidade

2. **Retenção:**
   - Backups diários: 7 dias
   - Backups semanais: 4 semanas
   - Backups mensais: 12 meses

3. **Testes de Recuperação:**
   - Validação mensal de procedimentos de restore
   - Ambiente de staging para testes de recuperação 