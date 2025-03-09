#!/bin/bash
# Script para destruir a infraestrutura em um ambiente específico

# Verificar se o ambiente foi especificado
if [ -z "$1" ]; then
  echo "Uso: $0 <ambiente> [auto-approve]"
  echo "Ambientes disponíveis: dev, staging, prod"
  exit 1
fi

# Definir variáveis
ENV=$1
AUTO_APPROVE=$2
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"

# Verificar se o diretório do ambiente existe
if [ ! -d "$ENV_DIR" ]; then
  echo "Erro: Ambiente '$ENV' não encontrado em $ENV_DIR"
  exit 1
fi

# Navegar para o diretório do ambiente
cd "$ENV_DIR" || exit 1

# Verificar se é ambiente de produção
if [ "$ENV" == "prod" ] && [ "$AUTO_APPROVE" == "auto-approve" ]; then
  echo "ATENÇÃO: Você está prestes a destruir o ambiente de PRODUÇÃO automaticamente!"
  echo "Esta ação é irreversível e pode causar perda de dados e indisponibilidade do serviço."
  echo "Para continuar, digite 'DESTRUIR PRODUÇÃO' (em maiúsculas):"
  read confirmation
  
  if [ "$confirmation" != "DESTRUIR PRODUÇÃO" ]; then
    echo "Operação cancelada."
    exit 1
  fi
fi

echo "Destruindo infraestrutura no ambiente $ENV..."

# Verificar se o Terraform está inicializado
if [ ! -d ".terraform" ]; then
  echo "Terraform não inicializado. Executando terraform init..."
  terraform init
fi

# Destruir a infraestrutura
if [ "$AUTO_APPROVE" == "auto-approve" ]; then
  echo "Destruindo infraestrutura automaticamente..."
  terraform destroy -auto-approve
else
  echo "Destruindo infraestrutura (requer confirmação)..."
  terraform destroy
fi

echo "Terraform destroy concluído no ambiente $ENV!" 