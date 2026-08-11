# Secrets do GitHub Actions para Homolog

Este documento define os secrets esperados pelos workflows do repositório para homolog.

## Escopo

Obrigatórios para deploy em homolog:

- `KUBE_CONFIG_HML`

Opcionais neste estágio:

- `SONAR_TOKEN`

## Onde cadastrar

Preferência:

1. GitHub repository `urbana-connect`
2. `Settings`
3. `Secrets and variables`
4. `Actions`
5. Environment `homolog`

Se o environment `homolog` ainda não estiver configurado, o secret pode ser criado em nível de repositório como fallback.

## Contrato dos secrets

### `KUBE_CONFIG_HML`

Uso:

- workflow `.github/workflows/deploy-hml.yml`
- autenticação do `kubectl` contra o cluster de homolog

Formato esperado:

- conteúdo bruto do arquivo kubeconfig
- multiline permitido
- não usar base64 no valor salvo

Origem:

- kubeconfig da VPS/cluster de homolog com acesso ao namespace `urbana-connect-hml`

### `SONAR_TOKEN`

Uso:

- workflow `.github/workflows/build-test.yml`
- análise SonarCloud quando disponível

Comportamento atual:

- se ausente, a pipeline de CI continua verde e apenas ignora a análise SonarCloud

## O que não precisa virar secret no GitHub Actions

- credenciais do GHCR para pull no cluster
  - isso já é tratado pelo secret Kubernetes `container-registry-credentials`
- secrets de runtime da aplicação
  - `urbana-connect-mongodb-uri`
  - `urbana-connect-whatsapp`
  - `urbana-connect-openai` quando aplicável

Esses valores pertencem ao cluster/VPS, não ao GitHub Actions.

## Passo a passo de configuração

### 1. Cadastrar `KUBE_CONFIG_HML`

No GitHub:

1. Abra o repositório `urbana-connect`
2. Vá em `Settings > Secrets and variables > Actions`
3. Abra o environment `homolog` ou use `New repository secret`
4. Crie o secret `KUBE_CONFIG_HML`
5. Cole o conteúdo completo do kubeconfig

### 2. Cadastrar `SONAR_TOKEN` se desejado

1. Crie o secret `SONAR_TOKEN`
2. Cole o token do SonarCloud com acesso ao projeto

## Validação

### Deploy para homolog

1. Abra `Actions > Deploy para homolog`
2. Execute via `Run workflow`
3. Verifique:
   - autenticação do kubeconfig sem erro
   - build/push da imagem no GHCR
   - `kubectl apply -k infra/kubernetes/apps/urbana-connect-api/overlays/hml`
   - rollout do deployment `urbana-connect`

### CI com SonarCloud

1. Abra `Actions > Build, Test e Análise de Qualidade`
2. Rode o workflow ou abra um PR
3. Verifique se a etapa `Análise SonarCloud` executa em vez de ser ignorada

## Rotação e segurança

- rotacionar `KUBE_CONFIG_HML` quando houver troca de credencial ou regeneração do kubeconfig
- preferir environment secret em `homolog` para reduzir exposição
- nunca versionar esses valores no repositório
