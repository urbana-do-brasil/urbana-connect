# Fluxograma de Mensagens - Decor e Coleta de Prova Social

Este documento contém o fluxograma que mapeia o processo de execução do serviço Decor, englobando as etapas de atendimento humano, aprovação de entregáveis e, por fim, a coleta de prova social e entrega final.

## Legenda
- **🟢 Verde**: Urba (Chatbot) - *Sem ação neste macro-fluxo*
- **🟣 Lilás**: Agente Humano

## Diagrama

```mermaid
flowchart TD
    %% Definindo Classes Customizadas
    classDef urba fill:#4cd58a,stroke:#3bbd75,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef humano fill:#be5df7,stroke:#a640e5,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef user fill:#ffffff,stroke:#cccccc,stroke-width:3px,color:#555,font-weight:bold

    U((👤 Usuário)):::user

    %% Camada 1: Entrada
    BV([Boas Vindas ao processo de Decor 💬]):::humano
    U --> BV

    %% Camada 2: Levantamento e Dúvidas
    ASL([Atualização de Status - Levantamento 3D]):::humano
    DSE([Dúvidas sobre o espaço]):::humano
    
    BV --> ASL
    ASL <-.->|Loop de esclarecimentos| DSE

    %% Camada 3: Execução
    BP([Bate-Papo]):::humano
    ASP([Atualização de Status - Produção do espaço]):::humano
    EOC([Envio de opções e coleta de escolhas]):::humano
    CE([Criação do espaço]):::humano

    ASL --> BP
    BP --> ASP
    ASP --> EOC
    EOC <-.->|Iterações do projeto| CE

    %% Camada 4: Aprovação e Entrega (Fluxo de retorno)
    AP([Aprovação]):::humano
    ASPM([Atualização de Status - Produção Manual e Tour]):::humano
    AE([Agendamento Entrega]):::humano
    ECPS([Entrega / Coleta de Prova social]):::humano

    EOC --> AP
    AP --> ASPM
    ASPM --> AE
    AE --> ECPS
```
