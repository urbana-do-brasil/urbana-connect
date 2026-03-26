# Validacao publica do webhook em homolog

Este runbook valida a exposicao publica do webhook da homolog em `api-hml.urbanadobrasil.com`.

## Pre-requisitos

- DNS `api-hml.urbanadobrasil.com` resolvendo para a VPS de homolog
- deploy da branch `hml` aplicado no cluster
- certificado TLS emitido pelo `cert-manager`
- valor real de `WHATSAPP_VERIFY_TOKEN`

## Script de validacao

O repositório possui um script pronto em [infra/k8s/app/overlays/hml/validate-public-webhook.sh](/root/.openclaw/workspace/urbana-connect/infra/k8s/app/overlays/hml/validate-public-webhook.sh).

Uso:

```bash
export WHATSAPP_VERIFY_TOKEN='seu-token-real'
bash infra/k8s/app/overlays/hml/validate-public-webhook.sh
```

Se necessário, o host pode ser sobrescrito:

```bash
HOST=api-hml.urbanadobrasil.com \
WHATSAPP_VERIFY_TOKEN='seu-token-real' \
bash infra/k8s/app/overlays/hml/validate-public-webhook.sh
```

## O que o script valida

- resolucao DNS publica do host
- `GET /api/webhook` com token valido retornando `200` e o `challenge`
- `GET /api/webhook` com token invalido retornando `403`
- `POST /api/webhook` com payload minimo valido retornando `200`
- `POST /api/webhook` com provider invalido retornando `400`

## Falhas esperadas neste momento

Enquanto o DNS ainda nao estiver propagado, o script deve falhar logo na validacao de resolucao do host.

Isso e esperado e indica apenas que a pendencia externa de DNS ainda nao foi concluida.
