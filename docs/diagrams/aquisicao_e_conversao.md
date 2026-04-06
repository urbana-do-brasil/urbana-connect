# Fluxograma de Mensagens - Aquisição e Conversão

Este documento contém o fluxograma que mapeia as origens de clientes e a jornada automatizada da **Urba**, desde a captação até a marcação do bate-papo.

## Legenda
- **🟢 Verde**: Urba (Chatbot)
- **🟣 Lilás**: Agente Humano
- **🟣 Roxo Escuro**: Automação Google
- **🌐 Ciano**: Canal Web (Botão no Site)

## Diagrama

```mermaid
flowchart TD
    %% Definindo Classes Customizadas (Cores extraídas/baseadas na imagem original)
    classDef urba fill:#4cd58a,stroke:#3bbd75,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef humano fill:#be5df7,stroke:#a640e5,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef google fill:#6716c6,stroke:#4f0d9c,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef site fill:#56dbda,stroke:#41bcbb,stroke-width:2px,color:#fff,font-weight:bold,rx:20,ry:20
    classDef user fill:#ffffff,stroke:#cccccc,stroke-width:3px,color:#555,font-weight:bold

    U((👤 Usuário)):::user

    %% Camada 1: Ponto de entrada
    C([Conteúdo 📷]):::humano
    A([Anúncio 📷]):::humano
    MP([Mensagem Promocional 💬]):::urba
    BS([Botão no Site 🌐]):::site

    U --> C
    U --> A
    U --> MP
    U --> BS

    %% Camada 2: Direcionamento
    LB([Link na Bio 📷]):::humano

    C --> LB
    A --> LB

    %% Camada 3: Início de Atendimento Urba
    SA([Saudação 💬]):::urba

    BS --> SA
    LB --> SA
    MP --> SA

    %% Camada 4: Fluxo de Atendimento Linear
    CI([Coleta de informações]):::urba
    SS([Sugestão de serviço]):::urba
    CO([Confirmação]):::urba
    EP([Envio informações de pagamento]):::urba
    RC([Recebimento do comprovante]):::urba
    AG([Agradecimento]):::urba
    EB([Envio Briefing]):::urba
    EIA([Envio informações agendamento Bate-papo]):::urba
    ELB([Envio link do Bate-papo por e-mail]):::google

    SA --> CI --> SS --> CO --> EP --> RC --> AG --> EB --> EIA --> ELB
```
