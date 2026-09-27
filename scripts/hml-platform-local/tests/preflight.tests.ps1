[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '..\common.ps1')
$config = Get-HmlPlatformLocalConfig
$infra = $config.InfraRoot
$scripts = Split-Path -Parent $PSScriptRoot
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "STATIC_HML_PLATFORM_LOCAL_TEST_FAILED: $Message" }
}
Assert-True (Test-Path -LiteralPath (Join-Path $infra 'kustomization.yaml')) 'kustomization ausente'
Assert-True (Test-Path -LiteralPath (Join-Path $infra 'cluster-config.yaml')) 'cluster-config ausente'
Assert-True (Test-Path -LiteralPath (Join-Path $scripts 'common.ps1')) 'common.ps1 ausente'
$config = Get-HmlPlatformLocalConfig
$repoPrefix = [IO.Path]::GetFullPath($config.RepoRoot) + [IO.Path]::DirectorySeparatorChar
$envPath = [IO.Path]::GetFullPath($config.EnvFile)
Assert-True (-not $envPath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) 'env dedicado nao pode ficar no repositorio'
$startText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'start.ps1')
$kubeconfigPattern = [regex]::Escape("'--kubeconfig'") + '\s+\$config\.KubeconfigPath'
Assert-True ($startText -match $kubeconfigPattern) 'secret nao usa kubeconfig dedicado'
$manifestFiles = @(Get-ChildItem -LiteralPath $infra -Filter '*.yaml' -File |
    Where-Object { $_.Name -ne 'cluster-config.yaml' })
$manifestText = ($manifestFiles | ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
$apiText = Get-Content -Raw -LiteralPath (Join-Path $infra 'api.yaml')
$configMapText = Get-Content -Raw -LiteralPath (Join-Path $infra 'configmap.yaml')
$commonText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'common.ps1')
$startText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'start.ps1')
$clusterScript = Get-Content -Raw -LiteralPath (Join-Path $scripts 'cluster.ps1')
$preflightText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'preflight.ps1')
$stopText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'stop.ps1')
$statusText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'status.ps1')
$applicationText = Get-Content -Raw -LiteralPath (Join-Path $config.RepoRoot 'apps/urbana-connect-api/src/main/resources/application.yml')
Assert-True ($manifestText -match 'environment:\s*hml-platform-local') 'identidade de environment ausente'
Assert-True ($manifestText -notmatch 'environment:\s*local\s*$') 'label environment generica encontrada'
Assert-True ($manifestText -match 'HERMES_POC_ENABLED:\s*"false"') 'Hermes nao esta deny-by-default'
Assert-True ($configMapText -match 'WEBHOOK_INBOX_WORKER_ENABLED:\s*"true"') 'inbox inbound-only nao esta habilitado'
Assert-True ($applicationText -match 'webhook:\s+inbox:\s+worker:' -and $applicationText -match 'WEBHOOK_INBOX_WORKER_ENABLED:false') 'flag WEBHOOK_INBOX_WORKER_ENABLED nao e consumida pela aplicacao'
Assert-True ($manifestText -notmatch 'local-hml|openrouter|graph.facebook.com|NodePort|LoadBalancer|hostNetwork:\s*true|hostPort:') 'referencia fora do target encontrada'
Assert-True ($manifestText -notmatch '(?m)^kind:\s*Secret\s*$') 'Secret literal versionada'
Assert-True ($configMapText -notmatch '(?m)^\s*MONGODB_URI\s*:') 'MONGODB_URI nao pode ficar no ConfigMap'
foreach ($key in @('MONGODB_URI', 'WHATSAPP_APP_SECRET', 'WHATSAPP_VERIFY_TOKEN')) {
    $secretRef = "name:\s*$key\s+valueFrom:\s+secretKeyRef:\s+name:\s*hml-platform-local-secrets\s+key:\s*$key"
    Assert-True ($apiText -match $secretRef) "$key nao esta ligado ao Secret dedicado"
}
Assert-True ($commonText -match 'WHATSAPP_VERIFY_TOKEN') 'verify token ausente do wiring do env dedicado'
Assert-True ($apiText -match 'image:\s*docker.io/library/urbana-connect-hml-platform-local-api:local') 'imagem API local ausente'
Assert-True ($apiText -match 'imagePullPolicy:\s*Never') 'imagem API nao esta explicitamente importada/local'
Assert-True ($apiText -match '(?s)startupProbe:.*path:\s*/api/v1/health') 'startup probe ausente/incorreto'
Assert-True ($apiText -match '(?s)readinessProbe:.*path:\s*/api/v1/readiness') 'readiness probe ausente/incorreto'
Assert-True ($apiText -match '(?s)livenessProbe:.*path:\s*/api/v1/health') 'liveness probe ausente/incorreto'
Assert-True ($manifestText -match '(?m)^kind:\s*NetworkPolicy\s*$') 'NetworkPolicy ausente'
Assert-True ($manifestText -match 'type: ClusterIP') 'Service publico ausente'
Assert-True ($commonText -match 'Build-HmlPlatformLocalApiImage') 'build deterministico da API ausente'
Assert-True ($commonText -match "'image',\s*'import'" -and $commonText -match '''exec'',\s+\$config\.K3dServerContainer') 'import/readback da imagem no node ausente'
Assert-True ($commonText -notmatch '--from-literal') 'segredo nao pode ser passado como argumento de processo'
Assert-True ($commonText -match 'RuntimeRoot\s*=\s*\$localDataRoot') 'evidencia runtime deve ficar fora do repositorio'
Assert-True ($startText -match 'Ensure-HmlPlatformLocalApiImage' -and $startText -match 'Import-HmlPlatformLocalImages' -and $startText -match 'rollout.*restart') 'start nao reprovisiona imagens ou reinicia a API'
$guardCall = $commonText.IndexOf('Assert-HmlPlatformLocalCliVersion -Name $Name -CommandPath $path')
Assert-True ($guardCall -ge 0) 'Require-Command nao valida as versoes pinadas'
$startPrerequisites = $startText.IndexOf("Require-Command 'kubectl'")
$startBuild = $startText.IndexOf('Ensure-HmlPlatformLocalApiImage')
Assert-True ($startPrerequisites -ge 0 -and $startBuild -gt $startPrerequisites) 'start deve validar as ferramentas antes de build/import/apply'
$clusterPrerequisites = $clusterScript.IndexOf("Require-Command 'kubectl'")
$clusterCreate = $clusterScript.IndexOf("'cluster', 'create'")
Assert-True ($clusterPrerequisites -ge 0 -and $clusterCreate -gt $clusterPrerequisites) 'cluster create deve validar as ferramentas antes de criar o cluster'
$preflightPrerequisites = $preflightText.IndexOf("Require-Command 'kubectl'")
$preflightRender = $preflightText.IndexOf("Invoke-ExternalText -Command 'kubectl' -Arguments @('kustomize'")
Assert-True ($preflightPrerequisites -ge 0 -and $preflightRender -gt $preflightPrerequisites) 'preflight runtime deve validar as ferramentas antes de usar kubectl'
Assert-True ($stopText -notmatch '\$config\.InfraRoot') 'stop nao pode apagar o kustomization inteiro'
Assert-True ($stopText -match 'deployment/urbana-connect' -and $stopText -match 'statefulset/mongodb' -and $stopText -match 'job/mongodb-rs-init') 'stop nao remove explicitamente apenas os workloads esperados'
Assert-True ($stopText -match "get',\s*'namespace" -and $stopText -match "get',\s*'pvc") 'stop nao confirma namespace e PVC preservados'
Assert-True ($statusText -match "Write-HmlPlatformLocalEvidence\s+-Operation\s+'status'" -and $statusText -match '\$imageEvidence' -and $statusText -match '\$state' -and $statusText -match '\$apiReadiness') 'status nao persiste readback de estado, readiness e imagens'
Assert-True ($statusText -match 'Assert-HmlPlatformLocalImagesImported' -and $statusText -match 'environmentKeys') 'status nao persiste proveniencia/import e chaves sanitizadas'
$cluster = Get-Content -Raw -LiteralPath (Join-Path $infra 'cluster-config.yaml')
Assert-True ($cluster -match 'name: urbana-hml-platform-local') 'cluster dedicado ausente'
Assert-True ('urbana-hml-platform-local'.Length -le 32) 'nome do cluster excede limite k3d'
Assert-True ($cluster -match 'hostIP: 127\.0\.0\.1') 'API k3d fora de loopback'
Assert-True ($cluster -match 'updateDefaultKubeconfig: false') 'kubeconfig default pode ser alterado'
Assert-True ($cluster -match 'switchCurrentContext: false') 'contexto default pode ser alterado'
$toolVersions = Get-HmlPlatformLocalExpectedToolVersions
Assert-HmlPlatformLocalCliVersionOutput -Name 'k3d' `
    -Output "k3d version $($toolVersions.k3d)`nk3s version v1.35.5-k3s1 (default)" -Expected $toolVersions
Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' `
    -Output "{`"clientVersion`":{`"gitVersion`":`"$($toolVersions.kubectl.gitVersion)`"},`"kustomizeVersion`":`"$($toolVersions.kubectl.kustomizeVersion)`"}" `
    -Expected $toolVersions
function Assert-VersionRejected([scriptblock]$Action, [string]$Message) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    Assert-True $rejected $Message
}
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'k3d' -Output '' -Expected $toolVersions } 'k3d sem versao deve falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'k3d' -Output 'k3d version v0.0.0' -Expected $toolVersions } 'k3d fora da versao deve falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' -Output '{}' -Expected $toolVersions } 'kubectl sem versao/Kustomize deve falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' -Output '{"clientVersion":{"gitVersion":"v0.0.0"},"kustomizeVersion":"v0.0.0"}' -Expected $toolVersions } 'kubectl/Kustomize fora das versoes devem falhar fechado'
Write-Output 'STATIC_HML_PLATFORM_LOCAL_TESTS_OK'
