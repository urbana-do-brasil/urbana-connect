# Spec SDD — PEE-100: Infraestrutura Kubernetes do Urbana Claw

## Metadados

- `Título da feature`: Infraestrutura Kubernetes do Urbana Claw
- `Ticket Jira`: PEE-100
- `Status`: Draft
- `Responsável pela spec`: Visão Codex
- `Branch`: `feature/PEE-100-openclaw-poc-spec`
- `Contexto de branch`: `feature/* -> hml -> main`
- `Data`: 2026-05-03
- `Fonte principal`: [Refinamento - POC arquitetura conversacional inspirada no OpenClaw](https://urbanadobrasil.atlassian.net/wiki/spaces/pro/pages/376111105)

---

## 1. Contexto

A primeira implementação da POC OpenClaw na PEE-100 criou o caminho da Urbana
Connect para chamar um endpoint HTTP, mas não entregou a peça operacional mais
básica: um OpenClaw rodando no cluster e acessível pela aplicação.

Antes de evoluir o `OpenClawClient` direto na Urbana Connect ou decidir por um
bridge HTTP, precisamos subir o OpenClaw puro como um serviço interno do
ecossistema da Urba.

O serviço será chamado de `urbana-claw`.

Objetivo desta spec:

- definir como versionar a infraestrutura do `urbana-claw` no repositório;
- deixar os manifests revisáveis antes de qualquer aplicação em homologação;
- definir como validar que o pod subiu, ficou saudável e está acessível pela
  rede interna do namespace da Urbana Connect;
- preparar a base para o spike seguinte: Urbana Connect chamando o OpenClaw
  Gateway diretamente.

---

## 2. Decisão arquitetural

O `urbana-claw` deve ser uma aplicação separada da `urbana-connect`, com pod,
deployment, service, PVC, config e secrets próprios.

Ele deve rodar no mesmo namespace de homologação da Urbana Connect:

```text
urbana-connect-hml
  - deployment/urbana-connect
  - service/urbana-connect
  - deployment/urbana-claw
  - service/urbana-claw
```

Nesta etapa, o `urbana-claw` não deve expor Ingress público. A comunicação deve
ser apenas interna ao cluster.

Endpoint interno esperado:

```text
urbana-claw:18789
```

O protocolo consumido pela Urbana Connect será definido no spike seguinte. Esta
spec entrega somente a fundação operacional para que esse spike seja viável.

---

## 3. Escopo

### Dentro do escopo

- Criar manifests Kubernetes versionados para `urbana-claw`.
- Criar overlay de homologação no mesmo namespace `urbana-connect-hml`.
- Subir OpenClaw Gateway puro, sem WhatsApp, sem canais externos e sem bridge.
- Criar `Service` interno `ClusterIP`.
- Criar `PVC` para estado/configuração do OpenClaw.
- Criar `ConfigMap` com `openclaw.json` mínimo e workspace inicial.
- Criar template/documentação de `Secret` para token do gateway e chave de
  provedor LLM.
- Definir probes de liveness/readiness.
- Definir recursos mínimos de CPU/memória.
- Definir roteiro de validação pós-deploy.

### Fora do escopo

- Implementar `OpenClawClient` na Urbana Connect.
- Implementar bridge HTTP.
- Habilitar `OPENCLAW_POC_ENABLED=true`.
- Alterar o webhook real.
- Expor o OpenClaw por Ingress.
- Configurar WhatsApp dentro do OpenClaw.
- Permitir que o OpenClaw envie mensagens por canais próprios.
- Configurar sandbox Docker-in-Docker.
- Criar automações, cron jobs ou tools perigosas.
- Aplicar os manifests em homologação sem aprovação explícita.

---

## 4. Estrutura de arquivos proposta

Os manifests devem ficar no repositório `urbana-connect`, junto da infra já
versionada:

```text
infra/k8s/urbana-claw/
  base/
    configmap.yaml
    deployment.yaml
    kustomization.yaml
    pvc.yaml
    service.yaml
  overlays/
    hml/
      kustomization.yaml
      configmap-patch.yaml
      deployment-patch.yaml
      secret-template.yaml
      README.md
```

Motivo:

- mantém a POC revisável no mesmo fluxo de PR;
- evita instalação manual sem rastreabilidade;
- permite validar o manifest com `kubectl kustomize`;
- reaproveita o padrão já usado em `infra/k8s/app` e `infra/k8s/mongodb`.

---

## 5. Configuração mínima do OpenClaw Gateway

O `openclaw.json` mínimo deve:

- habilitar o gateway na porta `18789`;
- usar autenticação por token;
- usar bind acessível via Kubernetes Service;
- declarar um agente dedicado `urba`;
- desabilitar cron;
- não configurar canais de WhatsApp/Telegram/etc.

Exemplo conceitual:

```json
{
  "gateway": {
    "mode": "local",
    "bind": "lan",
    "port": 18789,
    "auth": {
      "mode": "token"
    },
    "controlUi": {
      "enabled": false
    }
  },
  "agents": {
    "defaults": {
      "workspace": "~/.openclaw/workspace"
    },
    "list": [
      {
        "id": "urba",
        "name": "Urba",
        "workspace": "~/.openclaw/workspace"
      }
    ]
  },
  "cron": {
    "enabled": false
  }
}
```

Observação importante:

- Os manifests oficiais do OpenClaw para Kubernetes usam `gateway.bind =
  loopback` no exemplo mínimo, porque o caminho inicial é `kubectl
  port-forward`.
- Para a Urbana Connect acessar o Gateway via `Service`, o bind precisa ser
  não-loopback, como `lan`.

---

## 6. Recursos Kubernetes esperados

### Deployment

Nome:

```text
deployment/urbana-claw
```

Container principal:

```text
gateway
```

Imagem:

```text
ghcr.io/openclaw/openclaw:<versao-fixada>
```

Regras:

- não usar `latest` na versão final dos manifests;
- escolher tag estável/pinada durante a implementação;
- rodar como usuário não-root;
- usar `readOnlyRootFilesystem` quando compatível;
- montar estado em PVC;
- montar `/tmp` como `emptyDir`;
- definir requests/limits.

Comando esperado:

```text
node /app/dist/index.js gateway run
```

### Service

Nome:

```text
service/urbana-claw
```

Tipo:

```text
ClusterIP
```

Porta:

```text
18789
```

### PVC

Nome:

```text
persistentvolumeclaim/urbana-claw-home-pvc
```

Uso:

```text
/home/node/.openclaw
```

### ConfigMap

Nome:

```text
configmap/urbana-claw-config
```

Deve conter:

- `openclaw.json`;
- `AGENTS.md`;
- opcionalmente `SOUL.md` ou instruções mínimas do agente.

### Secret

Nome:

```text
secret/urbana-claw-secrets
```

Deve conter:

- `OPENCLAW_GATEWAY_TOKEN`;
- pelo menos uma chave de provedor LLM, por exemplo `OPENAI_API_KEY`,
  `GEMINI_API_KEY`, `ANTHROPIC_API_KEY` ou outro provedor aprovado.

Nenhum secret real deve ser commitado no repositório.

---

## 7. Segurança e isolamento

Regras mínimas:

1. Não criar Ingress público para `urbana-claw`.
2. Usar autenticação por token no Gateway.
3. Manter token em `Secret`, nunca em `ConfigMap`.
4. Não configurar canal WhatsApp no OpenClaw nesta fase.
5. Não permitir que OpenClaw envie mensagens diretamente.
6. Não habilitar cron.
7. Não habilitar sandbox Docker-in-Docker nesta POC inicial.
8. Não reutilizar secrets da Urbana Connect sem decisão explícita.
9. Se o cluster suportar NetworkPolicy, limitar acesso ao `urbana-claw` a pods
   necessários no namespace.

---

## 8. Plano de implementação

### Fase 1 — PR de infra sem aplicar

1. Criar manifests em `infra/k8s/urbana-claw`.
2. Criar README operacional do overlay `hml`.
3. Criar template de secret sem valores reais.
4. Renderizar manifests com:

   ```bash
   kubectl kustomize infra/k8s/urbana-claw/overlays/hml
   ```

5. Rodar validação server-side dry-run quando houver acesso ao cluster:

   ```bash
   kubectl apply --dry-run=server -k infra/k8s/urbana-claw/overlays/hml
   ```

6. Abrir PR para revisão.

### Fase 2 — Aplicação em homologação

Somente após aprovação explícita:

1. Criar/aplicar secret real `urbana-claw-secrets`.
2. Aplicar manifests:

   ```bash
   kubectl apply -k infra/k8s/urbana-claw/overlays/hml
   ```

3. Aguardar rollout:

   ```bash
   kubectl rollout status deployment/urbana-claw -n urbana-connect-hml --timeout=300s
   ```

### Fase 3 — Validação operacional

Executar o roteiro da seção 9.

### Fase 4 — Próximo spike

Com `urbana-claw` saudável, iniciar o spike do client direto da Urbana Connect
para o OpenClaw Gateway.

---

## 9. Roteiro de validação pós-deploy

### 9.1 Validar recursos Kubernetes

```bash
kubectl get deployment urbana-claw -n urbana-connect-hml
kubectl get pods -n urbana-connect-hml -l app=urbana-claw
kubectl get svc urbana-claw -n urbana-connect-hml
kubectl get pvc urbana-claw-home-pvc -n urbana-connect-hml
```

Critério:

- deployment existe;
- pod está `Running`;
- readiness está `1/1`;
- service existe com porta `18789`;
- PVC está `Bound`.

### 9.2 Validar rollout

```bash
kubectl rollout status deployment/urbana-claw -n urbana-connect-hml --timeout=300s
```

Critério:

- rollout finaliza com sucesso.

### 9.3 Validar logs de inicialização

```bash
kubectl logs deployment/urbana-claw -n urbana-connect-hml --tail=200
```

Critério:

- não há crash loop;
- gateway sobe na porta `18789`;
- não há erro de autenticação/configuração;
- não há tentativa de iniciar canais externos.

### 9.4 Validar health interno via Service

Usar pod efêmero no mesmo namespace:

```bash
kubectl run urbana-claw-smoke \
  -n urbana-connect-hml \
  --rm -i --restart=Never \
  --image=curlimages/curl:8.7.1 \
  -- curl -fsS http://urbana-claw:18789/healthz
```

Critério:

- comando retorna sucesso HTTP.

### 9.5 Validar readiness interno via Service

```bash
kubectl run urbana-claw-ready-smoke \
  -n urbana-connect-hml \
  --rm -i --restart=Never \
  --image=curlimages/curl:8.7.1 \
  -- curl -fsS http://urbana-claw:18789/readyz
```

Critério:

- comando retorna sucesso HTTP.

### 9.6 Validar DNS interno

```bash
kubectl run urbana-claw-dns-smoke \
  -n urbana-connect-hml \
  --rm -i --restart=Never \
  --image=busybox:1.37 \
  -- nslookup urbana-claw
```

Critério:

- DNS resolve `urbana-claw` para um IP de Service.

### 9.7 Validar comunicação a partir do mesmo namespace da Urbana Connect

Usar pod efêmero no namespace `urbana-connect-hml` é suficiente para validar a
comunicação básica se ainda não houver NetworkPolicy restritiva.

Se uma NetworkPolicy for adicionada permitindo apenas pods com label
`app=urbana-connect`, repetir o teste com um pod efêmero que possua label
compatível ou validar a partir do pod da própria Urbana Connect, caso a imagem
tenha ferramenta HTTP disponível.

Critério:

- um workload do namespace consegue acessar `http://urbana-claw:18789/healthz`
  via DNS interno.

### 9.8 Validar health autenticado do Gateway

Depois que o token real estiver no secret, validar também um comando autenticado
do OpenClaw Gateway usando o token do `Secret`.

Critério:

- token é resolvido;
- Gateway responde a uma chamada autenticada;
- erro de autenticação não aparece nos logs.

O comando exato deve ser confirmado durante a implementação, usando a CLI/SDK
da versão pinada do OpenClaw.

---

## 10. Critérios de aceite

1. Manifests do `urbana-claw` estão versionados e revisáveis.
2. Nenhum secret real foi commitado.
3. `kubectl kustomize` renderiza o overlay `hml` sem erro.
4. `kubectl apply --dry-run=server` passa antes de aplicar de verdade.
5. Após aplicação autorizada, deployment `urbana-claw` sobe com pod `Ready`.
6. Service `urbana-claw` responde internamente na porta `18789`.
7. Health e readiness respondem via DNS interno do cluster.
8. Logs não mostram tentativa de iniciar canais externos.
9. Urbana Connect ainda não é alterada nem habilitada para usar OpenClaw nesta
   entrega.
10. O resultado deixa o cluster pronto para o próximo spike: client direto da
    Urbana Connect para OpenClaw Gateway.

---

## 11. Dúvidas em aberto

1. Qual tag do `ghcr.io/openclaw/openclaw` será pinada?
2. Qual provedor LLM será usado no `urbana-claw` de homologação?
3. Criaremos um secret dedicado `urbana-claw-secrets` ou reutilizaremos algum
   secret existente com decisão explícita?
4. O `controlUi` deve ficar desabilitado ou habilitado apenas sem Ingress?
5. Será necessário NetworkPolicy nesta primeira fase?
6. O PVC de 10Gi dos manifests oficiais é adequado para a POC ou podemos usar
   menor?
7. O OpenClaw precisa de algum arquivo adicional de workspace além de
   `AGENTS.md` nesta fase?

---

## 12. Regra operacional

Esta spec não autoriza aplicação em homologação.

Fluxo obrigatório:

1. versionar a spec;
2. revisar a spec;
3. implementar manifests em PR;
4. revisar manifests;
5. pedir aprovação explícita para aplicar em homologação;
6. aplicar e executar o roteiro de validação;
7. registrar evidências antes de iniciar o spike do client direto.
