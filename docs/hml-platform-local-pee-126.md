# HML platform local — rollback, impacto e custo

Status: procedimento versionado para o primeiro provisionamento local do PEE-126.
Escopo: somente o cluster k3d dedicado `urbana-hml-platform-local`; não é HML remoto nem produção.

## Limites operacionais

- Cluster: `urbana-hml-platform-local`.
- Contexto: `k3d-urbana-hml-platform-local` em kubeconfig dedicado fora do checkout.
- Namespace: `urbana-connect-hml-platform-local`.
- Exposição: somente loopback (`127.0.0.1`); Service `ClusterIP`; sem Ingress, `NodePort`, `LoadBalancer`, `hostNetwork` ou `hostPort`.
- Egress: a NetworkPolicy permite apenas DNS e MongoDB; o caminho inbound-only não instancia Hermes, AI, WhatsApp, SMTP ou outro adaptador de saída.
- Segredos: o arquivo externo é lido por allowlist e materializado como Secret efêmero; valores não entram em stdout, evidência ou Git.

## Ferramentas locais fixadas

No preflight runtime, `preflight.ps1` valida as versões de k3d, kubectl e do Kustomize embutido antes de usar esses CLIs. As versões aprovadas ficam em `infra/kubernetes/hml-platform-local/tool-versions.yaml` (conteúdo JSON, que também é YAML válido); ausência, saída inválida ou divergência interrompe a operação. A checagem de versão usa apenas comandos de versão e, por si só, não acessa o cluster. `preflight.ps1 -SkipRuntime` continua sendo apenas uma verificação estática e não consulta ferramentas nem cluster.

No PowerShell, coloque os CLIs já instalados apenas no PATH do processo/terminal atual antes de executar os scripts (isso não altera o PATH do usuário nem do sistema):

```powershell
$env:PATH = "$env:LOCALAPPDATA\hermes\profiles\sre-devops\bin;C:\Program Files\Docker\Docker\resources\bin;$env:PATH"
```

O caminho de k3d acima aponta para a cópia existente do perfil `sre-devops`; kubectl e Kustomize são fornecidos pela instalação local do Docker Desktop. Não é necessário instalar ou atualizar ferramentas.

O handler desta entrega permanece um sink inbound-only: não chama Hermes/LLM nem adaptadores outbound e não persiste uma inbox durável ou oferece idempotência/reprocessamento. Os logs registram apenas tipo e instante; `providerMessageId`, telefone e corpo não são registrados. Inbox durável e idempotência pertencem às subtarefas correspondentes.

## Rollback

1. Para retirar somente os workloads, executar `scripts/hml-platform-local/stop.ps1` e confirmar o status com `scripts/hml-platform-local/status.ps1`. O `stop.ps1` remove apenas `Deployment/urbana-connect`, `StatefulSet/mongodb` e `Job/mongodb-rs-init`; ele não executa `kubectl delete -k`, não remove o Namespace e não remove PVC.
2. Para retornar a uma revisão anterior, selecionar explicitamente a revisão Git desejada, executar `scripts/hml-platform-local/cluster.ps1 -Create` para reconstruir e importar a imagem, e então executar `scripts/hml-platform-local/start.ps1`.
3. Para descartar o cluster e o PVC local, executar `scripts/hml-platform-local/cluster.ps1 -Delete -ConfirmDataLoss`; essa ação exige confirmação explícita e remove dados locais.
4. Após qualquer rollback, repetir `preflight.ps1`, `smoke.ps1` e `status.ps1`. O rollback só é considerado concluído após health, readiness, imagem importada no node e namespace dedicado serem lidos novamente.

## Impacto registrado

- Zero impacto em HML remoto, produção, DNS público ou provedores externos.
- O target aceita o POST de webhook sintético somente para validar a fronteira inbound-only; o handler não chama Hermes/AI/WhatsApp/SMTP e não produz resposta outbound.
- O estado Mongo é local ao PVC de 5 GiB do StatefulSet e pode ser perdido somente pelo rollback destrutivo explicitamente confirmado.
- O worktree e o kubeconfig padrão do usuário não são alterados pelo cluster dedicado.

## Custo registrado

- Custo cloud/provedor: zero; este procedimento não cria recurso remoto nem realiza chamada de modelo/provedor.
- Custo local: consumo temporário de Docker Desktop, k3d, uma instância MongoDB e uma instância API; a imagem da API é reconstruída quando revision ou estado dirty mudam.
- Evidência de custo operacional deve registrar apenas identificadores de imagem, contagem de nodes/pods/PVC e duração dos comandos; não registrar segredos, URIs ou payloads completos.

## Evidência esperada

Os scripts gravam JSON sanitizado em `DataRoot` fora do repositório (por padrão `%LOCALAPPDATA%/UrbanaConnect/hml-platform-local`, ou `%TEMP%/UrbanaConnect/hml-platform-local` quando `LOCALAPPDATA` não existe). Os arquivos esperados são `cluster-create.json`, `preflight.json`, `start.json`, `smoke.json`, `status.json` e, após um stop seguro, `stop.json`. O `status.json` deve conter o readback de estado (`running`, `degraded` ou `stopped`), readiness dos workloads, namespace, nodes, pods, Services, PVCs, IDs/digests e referências importadas das imagens, além das chaves de ambiente sem valores. A evidência nunca deve conter valores de Secret.

A execução local só pode ser marcada como GO quando os comandos versionados passarem nesta ordem: testes estáticos, `cluster.ps1 -Create`, `preflight.ps1`, `start.ps1`, `smoke.ps1`, `status.ps1` e testes da aplicação. Na ausência de k3d, Docker ou contexto dedicado, o resultado permanece NO-GO runtime mesmo que os testes estáticos passem.
