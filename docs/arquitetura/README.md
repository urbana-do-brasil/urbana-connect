# Documentação de Arquitetura - Urbana Connect

Este diretório contém a documentação de arquitetura para o sistema Urbana Connect, um chatbot para WhatsApp que utiliza GPT-4 para atendimento ao cliente da Urbana do Brasil. Atualmente, o projeto está em fase inicial de implementação, com foco na infraestrutura base.

## Status Atual do Projeto

**[MARÇO 2024]**
- ✅ Cluster Kubernetes na Digital Ocean (DOKS) implementado
- ✅ Configuração de infraestrutura como código (Terraform)
- ✅ Otimização de custos iniciais (1 nó com auto-scaling)
- 🔄 Próximos passos: Implementação dos componentes básicos da aplicação

## Índice

1. [Diagrama de Alto Nível](diagrama-alto-nivel.md) - Visão geral da arquitetura e seus principais componentes
2. [Arquitetura Detalhada](arquitetura-detalhada.md) - Design detalhado, padrões e decisões arquiteturais
3. [Fluxo de Dados](fluxo-de-dados.md) - Diagramas e descrições dos fluxos de processamento de mensagens
4. [Estrutura de Dados](estrutura-de-dados.md) - Modelos de dados e estratégias de armazenamento
5. [Infraestrutura](infraestrutura.md) - Configuração de ambientes, implantação e escalabilidade *(Implementado)*
6. [Conclusão](conclusao.md) - Resumo da proposta, benefícios e roteiro de implementação

## Sobre a Arquitetura

A arquitetura do Urbana Connect foi projetada para atender aos critérios da História de Usuário HU04, que solicita uma implementação escalável que suporte crescimento futuro sem necessidade de grandes refatorações. No entanto, iniciamos com uma implementação otimizada para custos, que permitirá crescimento gradual.

### Principais Características

- **Implementação Gradual**: Começando com infraestrutura básica, expandindo conforme a necessidade
- **Otimização de Custos**: Infraestrutura mínima viável com capacidade de expansão
- **Componentes Desacoplados**: Design preparado para implementação futura
- **Infraestrutura como Código**: Toda configuração gerenciada via Terraform

### Como Usar Esta Documentação

Esta documentação serve como guia para o desenvolvimento e evolução do sistema Urbana Connect. Recomenda-se a leitura na ordem apresentada no índice para uma compreensão completa da arquitetura proposta.

Para a fase atual do projeto, o documento de [Infraestrutura](infraestrutura.md) é o mais relevante, pois contém detalhes sobre o que já foi implementado.

A documentação restante serve como blueprint para a implementação futura dos demais componentes do sistema. 