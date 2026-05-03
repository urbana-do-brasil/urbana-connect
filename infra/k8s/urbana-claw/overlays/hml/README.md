# Urbana Claw HML

Este overlay sobe o OpenClaw Gateway como `urbana-claw` no namespace
`urbana-connect-hml`.

## Recursos

- `Deployment/urbana-claw`
- `Service/urbana-claw`
- `PersistentVolumeClaim/urbana-claw-home-pvc`
- `ConfigMap/urbana-claw-config`
- `NetworkPolicy/urbana-claw-allow-urbana-connect`

## Secret

O secret real nao deve ser commitado. Use `secret-template.yaml` apenas como
referencia dos campos esperados.

Para homologacao, o secret dedicado `urbana-claw-secrets` deve conter:

- `OPENCLAW_GATEWAY_TOKEN`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`

## Validacao

Renderizar:

```bash
kubectl kustomize infra/k8s/urbana-claw/overlays/hml
```

Dry-run server-side:

```bash
kubectl apply --dry-run=server -k infra/k8s/urbana-claw/overlays/hml
```

Aplicar:

```bash
kubectl apply -k infra/k8s/urbana-claw/overlays/hml
```

Validar rollout:

```bash
kubectl rollout status deployment/urbana-claw -n urbana-connect-hml --timeout=300s
```

Validar acesso permitido:

```bash
kubectl run urbana-claw-allowed-smoke \
  -n urbana-connect-hml \
  --rm -i --restart=Never \
  --labels=app=urbana-connect \
  --image=curlimages/curl:8.15.0 \
  -- curl -fsS --max-time 5 http://urbana-claw:18789/healthz
```

Validar bloqueio pela NetworkPolicy:

```bash
kubectl run urbana-claw-blocked-smoke \
  -n urbana-connect-hml \
  --rm -i --restart=Never \
  --labels=app=blocked-smoke \
  --image=curlimages/curl:8.15.0 \
  -- curl -fsS --max-time 5 http://urbana-claw:18789/healthz
```

O segundo comando deve falhar por timeout/bloqueio de rede.
