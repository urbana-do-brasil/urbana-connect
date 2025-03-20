#!/bin/bash

# Script para iniciar o ambiente de desenvolvimento do Urbana Connect

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$APP_ROOT")"

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

echo "Para iniciar a aplicação, execute:"
echo "cd $APP_ROOT && ./gradlew bootRun"

echo "Para parar todos os contêineres:"
echo "cd $SCRIPT_DIR && docker compose down" 