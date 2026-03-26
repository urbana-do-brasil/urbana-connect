#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIR="${1:-$SCRIPT_DIR/prod}"
APP_NAMESPACE="urbana-connect-hml"
MONITORING_NAMESPACE="monitoring"

required_files=(
  "registry-secret.yaml"
  "mongodb-secret.yaml"
  "mongodb-uri-secret.yaml"
  "whatsapp-secret.yaml"
)

optional_files=(
  "openai-secret.yaml"
  "grafana-admin-secret.yaml"
)

required_secrets=(
  "container-registry-credentials"
  "urbana-connect-mongodb"
  "urbana-connect-mongodb-uri"
  "urbana-connect-whatsapp"
)

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl não encontrado no PATH."
  exit 1
fi

if [ ! -d "$PROD_DIR" ]; then
  echo "Diretório de secrets não encontrado: $PROD_DIR"
  exit 1
fi

for file in "${required_files[@]}"; do
  if [ ! -f "$PROD_DIR/$file" ]; then
    echo "Arquivo obrigatório ausente: $PROD_DIR/$file"
    exit 1
  fi
done

echo "Aplicando runtime secrets obrigatórios em homolog..."
for file in "${required_files[@]}"; do
  kubectl apply -f "$PROD_DIR/$file"
done

echo "Aplicando runtime secrets opcionais disponíveis..."
for file in "${optional_files[@]}"; do
  if [ -f "$PROD_DIR/$file" ]; then
    kubectl apply -f "$PROD_DIR/$file"
  fi
done

echo "Validando secrets obrigatórios no namespace $APP_NAMESPACE..."
for secret in "${required_secrets[@]}"; do
  echo "Verificando secret: $secret..."
  kubectl get secret "$secret" -n "$APP_NAMESPACE" >/dev/null
done

if [ -f "$PROD_DIR/grafana-admin-secret.yaml" ]; then
  echo "Validando secret opcional do Grafana no namespace $MONITORING_NAMESPACE..."
  kubectl get secret grafana-admin-credentials -n "$MONITORING_NAMESPACE" >/dev/null
fi

echo "Runtime secrets de homolog aplicados com sucesso."
