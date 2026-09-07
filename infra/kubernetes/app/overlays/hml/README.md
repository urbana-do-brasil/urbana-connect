# Homolog Container Runtime

Esta overlay define a estrutura mínima de execução em container da aplicação para homolog.

Inclui:
- `Deployment` da aplicação
- `Service` interno
- `Ingress` público em `api-hml.urbanadobrasil.com`
- exposição pública apenas dos paths necessários da integração
- `readinessProbe` e `livenessProbe` para homolog
- `ConfigMap` com perfil `hml`
- referência aos secrets e ao GHCR privado
- tag da imagem pinada no `kustomization.yaml`, atualizada pela pipeline de deploy

Não inclui:
- integração com OpenAI
- configuração de secrets reais

Esses pontos ficam para subtarefas seguintes da `PEE-30`.

## Aplicação

```bash
kubectl apply -k infra/kubernetes/app/overlays/hml
```

## Pré-requisitos

- secret `container-registry-credentials`
- secret `urbana-connect-mongodb-uri`
- secret `urbana-connect-whatsapp`
- secret `urbana-connect-terms` quando `TERMS_CONSENT_ENABLED=true`
- `ClusterIssuer` `letsencrypt-prod` aplicado
- DNS `api-hml.urbanadobrasil.com` apontando para o IP público da VPS de homolog

## Exposição pública

O `Ingress` desta overlay assume o stack atual de homolog:
- `k3s` com `Traefik` como ingress controller
- `cert-manager` emitindo certificado TLS via Let's Encrypt
- `/api/webhook`, `/termos`, `/api/terms`, `/api/v1/health` e `/api/v1/readiness` ficam expostos publicamente

O bloco de consentimento permanece desligado no ConfigMap versionado até que
os quatro textos jurídicos, versões, hash, segredo do token e recursos sandbox
sejam fornecidos. Ao habilitá-lo, o endpoint usa a mesma origem HTTPS e não há
fallback para aceite textual ou para a state machine legada.

O ConfigMap também mantém `CATALOG_FIXTURE_FALLBACK=false`: enquanto os quatro
recursos operacionais não forem fornecidos, nenhum link de fixture pode ser
ofertado ao cliente.

Depois de aplicar a overlay:

```bash
kubectl get ingress urbana-connect -n urbana-connect-hml
kubectl describe certificate urbana-connect-hml-tls -n urbana-connect-hml
```

Para validar o host publicamente depois do deploy e da propagacao do DNS:

```bash
bash infra/kubernetes/app/overlays/hml/validate-public-webhook.sh
```
