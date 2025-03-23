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
├── app/                     # Código da aplicação Spring Boot
│   ├── src/                 # Código-fonte Java
│   ├── dev-env.sh           # Script para ambiente de desenvolvimento
│   └── docker-compose.yml   # Configuração do Docker Compose
├── infra/                   # Código de infraestrutura
│   └── terraform/           # Configuração do Terraform para DOKS
```

## Configuração do Ambiente de Desenvolvimento

### Pré-requisitos

- JDK 17 ou superior
- Docker e Docker Compose
- MongoDB (rodando via Docker)
- Chaves de API (OpenAI e WhatsApp Business API)

### Inicialização do Ambiente

O projeto inclui um script automatizado que configura o ambiente de desenvolvimento. Para utilizá-lo:

1. Navegue até a pasta da aplicação:
   ```bash
   cd app
   ```

2. Configure as permissões e execute o script de ambiente:
   ```bash
   chmod +x dev-env.sh
   ./dev-env.sh start
   ```

3. Configure as variáveis de ambiente:
   ```bash
   ./dev-env.sh env
   ```
   
   Isso criará um arquivo `.env.dev` com as variáveis necessárias.

4. Edite o arquivo `.env.dev` e adicione suas chaves de API:
   ```bash
   nano .env.dev
   ```

5. Carregue as variáveis de ambiente:
   ```bash
   source ./dev-env.sh load-env
   ```

6. Execute a aplicação:
   ```bash
   ./gradlew bootRun
   ```

### Variáveis de Ambiente

As seguintes variáveis de ambiente são necessárias:

| Variável | Descrição | Padrão |
|----------|-----------|--------|
| `OPENAI_API_KEY` | Chave da API da OpenAI | (obrigatório) |
| `OPENAI_MODEL` | Modelo GPT a ser utilizado | gpt-4o-mini |
| `WHATSAPP_PHONE_NUMBER_ID` | ID do número de telefone no WhatsApp Business API | (obrigatório) |
| `WHATSAPP_ACCESS_TOKEN` | Token de acesso à API do WhatsApp | (obrigatório) |
| `MONGODB_URI` | URI de conexão com o MongoDB | mongodb://localhost:27017/urbana-connect |

### Comandos do Script de Ambiente

O script `dev-env.sh` oferece vários comandos:

- `./dev-env.sh start` - Inicia o MongoDB e configura o ambiente
- `./dev-env.sh stop` - Para todos os contêineres Docker
- `./dev-env.sh env` - Configura o arquivo de variáveis de ambiente
- `./dev-env.sh load-env` - Carrega as variáveis de ambiente (deve ser usado com `source`)
- `./dev-env.sh help` - Exibe ajuda sobre os comandos disponíveis

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