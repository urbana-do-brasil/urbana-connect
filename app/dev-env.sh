#!/bin/bash

# Script para iniciar o ambiente de desenvolvimento do Urbana Connect

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$APP_ROOT")"
ENV_FILE="$SCRIPT_DIR/.env.dev"

# Função para ajuda
show_help() {
    echo "Uso: $0 [comando]"
    echo ""
    echo "Comandos:"
    echo "  start        Inicia o MongoDB e prepara o ambiente de desenvolvimento"
    echo "  stop         Para todos os contêineres Docker"
    echo "  env          Configura ou atualiza as variáveis de ambiente para desenvolvimento"
    echo "  load-env     Carrega as variáveis de ambiente definidas em .env.dev"
    echo "  help         Mostra esta mensagem de ajuda"
    echo ""
}

# Função para iniciar o ambiente
start_environment() {
    echo "===================================="
    echo "  Iniciando ambiente de desenvolvimento"
    echo "===================================="

    # Verifica se o Docker está instalado
    if ! command -v docker &> /dev/null; then
        echo "ERRO: Docker não está instalado. Instale o Docker e o Docker Compose primeiro."
        echo "Você pode baixar em: https://www.docker.com/products/docker-desktop/"
        exit 1
    fi

    # Verifica se o Docker está rodando
    if ! docker info &> /dev/null; then
        echo "ERRO: Docker não está em execução. Inicie o Docker e tente novamente."
        exit 1
    fi

    # Inicia os contêineres com Docker Compose
    echo "Iniciando MongoDB com Docker Compose..."
    cd "$SCRIPT_DIR" && docker compose up -d

    # Verifica se o MongoDB está pronto para uso
    echo "Aguardando MongoDB ficar pronto..."
    attempt=1
    max_attempts=10
    until docker exec urbana-connect-mongodb mongosh --eval "db.adminCommand('ping')" &> /dev/null || [ $attempt -gt $max_attempts ]; do
        echo "Tentativa $attempt de $max_attempts. Aguardando MongoDB iniciar..."
        sleep 3
        ((attempt++))
    done

    if [ $attempt -gt $max_attempts ]; then
        echo "ERRO: MongoDB não iniciou corretamente após várias tentativas."
        exit 1
    fi

    echo "===================================="
    echo "  MongoDB está pronto para uso!"
    echo "  URI de conexão: mongodb://localhost:27017/urbana-connect"
    echo "===================================="

    # Verifica se existe o arquivo .env.dev e sugere carregá-lo
    if [ -f "$ENV_FILE" ]; then
        echo "Arquivo .env.dev encontrado. Para carregar as variáveis de ambiente, execute:"
        echo "source $0 load-env"
    else
        echo "Nenhum arquivo .env.dev encontrado. Para configurar as variáveis de ambiente, execute:"
        echo "$0 env"
    fi

    echo ""
    echo "Para iniciar a aplicação, execute:"
    echo "cd $APP_ROOT && ./gradlew bootRun"
    echo ""
    echo "Para parar todos os contêineres:"
    echo "$0 stop"
}

# Função para parar os contêineres
stop_environment() {
    echo "Parando todos os contêineres..."
    cd "$SCRIPT_DIR" && docker compose down
    echo "Ambiente de desenvolvimento parado."
}

# Função para configurar variáveis de ambiente
setup_environment_vars() {
    echo "===================================="
    echo "  Configuração de variáveis de ambiente para desenvolvimento"
    echo "===================================="
    
    # Criar ou substituir o arquivo .env.dev
    cat > "$ENV_FILE" << EOF
# Arquivo de configuração para ambiente de desenvolvimento
# Gerado em $(date)

# OpenAI API
export OPENAI_API_KEY=""
export OPENAI_MODEL="gpt-4o-mini"
export OPENAI_MAX_TOKENS="500"
export OPENAI_TEMPERATURE="0.7"

# WhatsApp API
export WHATSAPP_API_URL="https://graph.facebook.com/v17.0"
export WHATSAPP_PHONE_NUMBER_ID=""
export WHATSAPP_ACCESS_TOKEN=""
export WHATSAPP_VERIFY_TOKEN="urbana-connect-webhook-token"

# MongoDB
export MONGODB_URI="mongodb://localhost:27017/urbana-connect"

# Admin credentials
export ADMIN_USER="admin"
export ADMIN_PASSWORD="admin"
EOF

    echo "Arquivo .env.dev criado em $ENV_FILE"
    echo "Por favor, edite este arquivo com seus valores reais de API keys."
    echo "Você pode usar seu editor preferido ou o comando:"
    echo "nano $ENV_FILE"
    echo ""
    echo "Após editar, carregue as variáveis com:"
    echo "source $0 load-env"
}

# Função para carregar variáveis de ambiente
load_environment_vars() {
    if [ -f "$ENV_FILE" ]; then
        echo "Carregando variáveis de ambiente de $ENV_FILE..."
        source "$ENV_FILE"
        echo "Variáveis carregadas com sucesso!"
        
        # Verifica se as APIs estão configuradas
        if [ -z "$OPENAI_API_KEY" ]; then
            echo "AVISO: OPENAI_API_KEY não está configurada."
        else
            echo "✅ OPENAI_API_KEY configurada"
        fi
        
        if [ -z "$WHATSAPP_ACCESS_TOKEN" ]; then
            echo "AVISO: WHATSAPP_ACCESS_TOKEN não está configurada."
        else
            echo "✅ WHATSAPP_ACCESS_TOKEN configurada"
        fi
        
        if [ -z "$WHATSAPP_PHONE_NUMBER_ID" ]; then
            echo "AVISO: WHATSAPP_PHONE_NUMBER_ID não está configurada."
        else
            echo "✅ WHATSAPP_PHONE_NUMBER_ID configurada"
        fi
    else
        echo "ERRO: Arquivo .env.dev não encontrado."
        echo "Execute primeiro: $0 env"
        return 1
    fi
}

# Processar comando
case "$1" in
    start)
        start_environment
        ;;
    stop)
        stop_environment
        ;;
    env)
        setup_environment_vars
        ;;
    load-env)
        load_environment_vars
        ;;
    help|"")
        show_help
        ;;
    *)
        echo "Comando desconhecido: $1"
        show_help
        exit 1
        ;;
esac 