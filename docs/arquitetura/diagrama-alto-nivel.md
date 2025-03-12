# Diagrama de Arquitetura de Alto Nível - Urbana Connect

```mermaid
graph TD
    Cliente[Cliente WhatsApp] --> WhatsAppAPI[WhatsApp Business API]
    WhatsAppAPI --> MessageGateway[Gateway de Mensagens]
    
    subgraph "Core Services"
        MessageGateway --> MessageProcessor[Processador de Mensagens]
        MessageProcessor --> ConversationManager[Gerenciador de Conversas]
        ConversationManager --> AIService[Serviço de IA/GPT-4]
        ConversationManager --> HumanHandoff[Transferência para Humano]
    end
    
    subgraph "Data Layer"
        ConversationManager --> EventBus[Barramento de Eventos]
        EventBus --> ConversationStore[(Armazenamento de Conversas)]
        EventBus --> AnalyticsService[Serviço de Analytics]
    end
    
    subgraph "Support Services"
        Monitoring[Monitoramento & Logging]
        ConfigService[Serviço de Configuração]
    end
    
    MessageProcessor --> Monitoring
    ConversationManager --> Monitoring
    AIService --> Monitoring
    HumanHandoff --> Monitoring
    
    ConfigService --> MessageProcessor
    ConfigService --> ConversationManager
    ConfigService --> AIService
```

## Componentes Principais

1. **Gateway de Mensagens**: Recebe e envia mensagens através da API do WhatsApp Business, atuando como ponto de entrada do sistema.

2. **Processador de Mensagens**: Analisa, filtra e encaminha mensagens recebidas para processamento adequado.

3. **Gerenciador de Conversas**: Mantém o estado das conversas, gerencia o contexto e orquestra o fluxo de comunicação.

4. **Serviço de IA/GPT-4**: Integra com a API da OpenAI para processamento de linguagem natural e geração de respostas.

5. **Transferência para Humano**: Gerencia a transição de conversas do bot para atendentes humanos quando necessário.

6. **Barramento de Eventos**: Facilita a comunicação assíncrona entre componentes através de eventos.

7. **Armazenamento de Conversas**: Persiste históricos de conversas e contextos no MongoDB.

8. **Serviço de Analytics**: Coleta e processa dados para análise de desempenho e insights.

9. **Monitoramento & Logging**: Fornece observabilidade através de logs estruturados e métricas.

10. **Serviço de Configuração**: Gerencia configurações centralizadas para todos os componentes. 