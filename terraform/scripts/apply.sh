#!/bin/bash
# Script para aplicar as mudanças do Terraform em um ambiente específico

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
echo "Aplicando mudanças do Terraform no ambiente $ENV..."

# Verificar se o Terraform está inicializado
if [ ! -d ".terraform" ]; then
  echo "Terraform não inicializado. Executando terraform init..."
  terraform init
fi

# Validar a configuração
echo "Validando a configuração..."
terraform validate
if [ $? -ne 0 ]; then
  echo "Erro na validação da configuração. Abortando."
  exit 1
fi

# Planejar as mudanças
echo "Planejando as mudanças..."
terraform plan -out=tfplan

# Aplicar as mudanças
if [ "$AUTO_APPROVE" == "auto-approve" ]; then
  echo "Aplicando mudanças automaticamente..."
  terraform apply -auto-approve tfplan
else
  echo "Aplicando mudanças (requer confirmação)..."
  terraform apply tfplan
fi

# Limpar o arquivo de plano
rm -f tfplan

echo "Terraform aplicado com sucesso no ambiente $ENV!" 