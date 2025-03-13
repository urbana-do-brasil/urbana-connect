# Urbana Connect - Chatbot WhatsApp com IA

Este repositório contém a infraestrutura e documentação para o Urbana Connect, um chatbot para WhatsApp que utiliza GPT-4 para atendimento ao cliente da Urbana do Brasil.

## Status do Projeto

**[MARÇO 2024]** - Fase Inicial
- ✅ Infraestrutura básica implementada
- ✅ Documentação arquitetural
- 🔄 Implementação gradual em andamento

## Estrutura do Repositório

```
urbana-connect/
├── docs/                    # Documentação do projeto
│   └── arquitetura/         # Especificações da arquitetura
├── urbana-connect-infra/    # Código de infraestrutura
│   └── terraform/           # Configuração do Terraform para DOKS
```

## Infraestrutura

O projeto está atualmente hospedado em um cluster Kubernetes gerenciado na Digital Ocean (DOKS) com as seguintes características:

- Região: NYC1 (Nova York)
- Nós: 1 nó do tipo s-2vcpu-2gb (2 vCPUs, 2GB RAM)
- Auto-scaling: Configurado para 1-3 nós
- Custo mensal estimado: $18 (com 1 nó)

A infraestrutura é gerenciada como código utilizando Terraform, o que permite fácil reprodução, versionamento e expansão do ambiente.

## Documentação

A pasta `docs/arquitetura/` contém a documentação completa da arquitetura do sistema, incluindo:

- Diagrama de alto nível
- Especificações detalhadas dos componentes
- Fluxos de dados
- Estruturas de dados
- Configurações de infraestrutura

## Próximos Passos

1. Implementação dos componentes básicos da aplicação
2. Configuração do sistema de mensageria
3. Integração com WhatsApp Business API
4. Integração com GPT-4
5. Testes e implantação incremental

## Contato

Para mais informações, entre em contato com a equipe de desenvolvimento. 