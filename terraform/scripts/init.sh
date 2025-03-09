#!/bin/bash
# Script para inicializar o Terraform em um ambiente específico

# Verificar se o ambiente foi especificado
if [ -z "$1" ]; then
  echo "Uso: $0 <ambiente> [projeto-id]"
  echo "Ambientes disponíveis: dev, staging, prod"
  exit 1
fi

# Definir variáveis
ENV=$1
PROJECT_ID=$2
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"

# Verificar se o diretório do ambiente existe
if [ ! -d "$ENV_DIR" ]; then
  echo "Erro: Ambiente '$ENV' não encontrado em $ENV_DIR"
  exit 1
fi

# Navegar para o diretório do ambiente
cd "$ENV_DIR" || exit 1
echo "Inicializando Terraform no ambiente $ENV..."

# Criar bucket para armazenar o estado se não existir
if [ ! -z "$PROJECT_ID" ]; then
  BUCKET_NAME="urbana-connect-terraform-state-$ENV"
  
  echo "Verificando se o bucket $BUCKET_NAME existe..."
  if ! gsutil ls -p "$PROJECT_ID" "gs://$BUCKET_NAME" &>/dev/null; then
    echo "Criando bucket para armazenar o estado do Terraform..."
    gsutil mb -p "$PROJECT_ID" -l us-central1 "gs://$BUCKET_NAME"
    gsutil versioning set on "gs://$BUCKET_NAME"
  else
    echo "Bucket $BUCKET_NAME já existe."
  fi
fi

# Inicializar o Terraform
terraform init

echo "Terraform inicializado com sucesso no ambiente $ENV!" 