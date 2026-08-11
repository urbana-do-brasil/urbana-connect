#!/usr/bin/env bash

set -euo pipefail

HOST="${HOST:-api-hml.urbanadobrasil.com}"
VERIFY_TOKEN="${WHATSAPP_VERIFY_TOKEN:-}"
CHALLENGE_VALUE="${CHALLENGE_VALUE:-codex-public-webhook-check}"
BASE_URL="https://${HOST}"

if [[ -z "${VERIFY_TOKEN}" ]]; then
  echo "WHATSAPP_VERIFY_TOKEN nao configurado."
  echo "Exporte o token real do webhook antes de executar este script."
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl nao encontrado."
  exit 1
fi

if ! command -v getent >/dev/null 2>&1; then
  echo "getent nao encontrado."
  exit 1
fi

SUCCESS_BODY_FILE="$(mktemp)"
INVALID_BODY_FILE="$(mktemp)"
POST_OK_BODY_FILE="$(mktemp)"
POST_BAD_BODY_FILE="$(mktemp)"

cleanup() {
  rm -f "${SUCCESS_BODY_FILE}" "${INVALID_BODY_FILE}" "${POST_OK_BODY_FILE}" "${POST_BAD_BODY_FILE}"
}

trap cleanup EXIT

echo "Validando resolucao DNS de ${HOST}"
if ! getent hosts "${HOST}" >/dev/null 2>&1; then
  echo "Host ${HOST} ainda nao resolve publicamente."
  exit 1
fi

echo "Validando challenge com token correto"
SUCCESS_STATUS="$(
  curl -sS -o "${SUCCESS_BODY_FILE}" -w "%{http_code}" \
    "${BASE_URL}/api/webhook?hub.mode=subscribe&hub.verify_token=${VERIFY_TOKEN}&hub.challenge=${CHALLENGE_VALUE}"
)"

if [[ "${SUCCESS_STATUS}" != "200" ]]; then
  echo "Falha no challenge valido. HTTP ${SUCCESS_STATUS}."
  cat "${SUCCESS_BODY_FILE}"
  exit 1
fi

SUCCESS_BODY="$(cat "${SUCCESS_BODY_FILE}")"
if [[ "${SUCCESS_BODY}" != "${CHALLENGE_VALUE}" ]]; then
  echo "Challenge valido retornou corpo inesperado: ${SUCCESS_BODY}"
  exit 1
fi

echo "Validando challenge com token invalido"
INVALID_STATUS="$(
  curl -sS -o "${INVALID_BODY_FILE}" -w "%{http_code}" \
    "${BASE_URL}/api/webhook?hub.mode=subscribe&hub.verify_token=invalid-token&hub.challenge=${CHALLENGE_VALUE}"
)"

if [[ "${INVALID_STATUS}" != "403" ]]; then
  echo "Challenge invalido deveria retornar 403, mas retornou ${INVALID_STATUS}."
  cat "${INVALID_BODY_FILE}"
  exit 1
fi

echo "Validando POST publico com payload minimo aceito"
POST_OK_STATUS="$(
  curl -sS -o "${POST_OK_BODY_FILE}" -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"object":"whatsapp_business_account","entry":[]}' \
    "${BASE_URL}/api/webhook"
)"

if [[ "${POST_OK_STATUS}" != "200" ]]; then
  echo "POST valido deveria retornar 200, mas retornou ${POST_OK_STATUS}."
  cat "${POST_OK_BODY_FILE}"
  exit 1
fi

echo "Validando rejeicao de provider invalido"
POST_BAD_STATUS="$(
  curl -sS -o "${POST_BAD_BODY_FILE}" -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"object":"unknown_provider","entry":[]}' \
    "${BASE_URL}/api/webhook"
)"

if [[ "${POST_BAD_STATUS}" != "400" ]]; then
  echo "POST invalido deveria retornar 400, mas retornou ${POST_BAD_STATUS}."
  cat "${POST_BAD_BODY_FILE}"
  exit 1
fi

echo "Validacao publica do webhook concluida com sucesso para ${HOST}."
