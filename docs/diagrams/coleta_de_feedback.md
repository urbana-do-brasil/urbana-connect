# Fluxograma de Mensagens - Coleta de Feedback

Este documento contém o fluxograma que mapeia o processo de coleta de feedback da **Urba**, desde o contato automatizado até o acionamento do Agente Humano em caso de notas baixas.

## Legenda
- **🟢 Verde**: Urba (Chatbot)
- **🟣 Lilás**: Agente Humano

## Diagrama

```mermaid
flowchart TD
    %% Definindo Classes Customizadas (Cores extraídas/baseadas na imagem original)
    classDef urba fill:#4cd58a,stroke:#3bbd75,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef humano fill:#be5df7,stroke:#a640e5,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef user fill:#ffffff,stroke:#cccccc,stroke-width:3px,color:#555,font-weight:bold

    U((👤 Usuário)):::user

    %% Camada 1: Fluxo Principal Automatizado (Urba)
    SP([Saudação e Pedido de Feedback 💬]):::urba
    AV([Avaliação no Google Meu negócio]):::urba
    AG1([Agradecimento]):::urba

    SP --> U
    SP --> AV
    AV --> AG1

    %% Camada 2: Monitoramento e Triagem Humana
    CR([Conferência de respostas]):::humano

    %% Conexão Condicional para Triagem
    AV -.-> CR

    %% Camada 3: Tratativa Humana para Retratores (Notas Baixas)
    SPE([Saudação e pergunta]):::humano
    OS([Oferta de solução]):::humano
    CO([Confirmação]):::humano
    AG2([Agradecimento]):::humano

    %% Acionamento do fluxo de retratores
    CR -.->|Contato com clientes que<br/>deram notas baixas| SPE

    %% Sequência de Resolução
    SPE --> OS --> CO --> AG2
```
