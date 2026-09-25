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
Assert-True ($manifestText -match 'environment:\s*hml-platform-local') 'identidade de environment ausente'
Assert-True ($manifestText -notmatch 'environment:\s*local\s*$') 'label environment generica encontrada'
Assert-True ($manifestText -match 'HERMES_POC_ENABLED:\s*"false"') 'Hermes nao esta deny-by-default'
Assert-True ($manifestText -notmatch 'local-hml|openrouter|graph.facebook.com|NodePort|LoadBalancer|hostNetwork:\s*true|hostPort:') 'referencia fora do target encontrada'
Assert-True ($manifestText -notmatch '(?m)^kind:\s*Secret\s*$') 'Secret literal versionada'
Assert-True ($configMapText -notmatch '(?m)^\s*MONGODB_URI\s*:') 'MONGODB_URI nao pode ficar no ConfigMap'
foreach ($key in @('MONGODB_URI', 'WHATSAPP_APP_SECRET', 'WHATSAPP_VERIFY_TOKEN')) {
    $secretRef = "name:\s*$key\s+valueFrom:\s+secretKeyRef:\s+name:\s*hml-platform-local-secrets\s+key:\s*$key"
    Assert-True ($apiText -match $secretRef) "$key nao esta ligado ao Secret dedicado"
}
Assert-True ($commonText -match 'WHATSAPP_VERIFY_TOKEN') 'verify token ausente do wiring do env dedicado'
Assert-True ($apiText -match 'image:\s*urbana-connect-hml-platform-local-api:local') 'imagem API local ausente'
Assert-True ($apiText -match 'imagePullPolicy:\s*Never') 'imagem API nao esta explicitamente importada/local'
Assert-True ($apiText -match '(?s)startupProbe:.*path:\s*/api/v1/health') 'startup probe ausente/incorreto'
Assert-True ($apiText -match '(?s)readinessProbe:.*path:\s*/api/v1/readiness') 'readiness probe ausente/incorreto'
Assert-True ($apiText -match '(?s)livenessProbe:.*path:\s*/api/v1/health') 'liveness probe ausente/incorreto'
Assert-True ($manifestText -match '(?m)^kind:\s*NetworkPolicy\s*$') 'NetworkPolicy ausente'
Assert-True ($manifestText -match 'type: ClusterIP') 'Service publico ausente'
$cluster = Get-Content -Raw -LiteralPath (Join-Path $infra 'cluster-config.yaml')
Assert-True ($cluster -match 'name: urbana-hml-platform-local') 'cluster dedicado ausente'
Assert-True ('urbana-hml-platform-local'.Length -le 32) 'nome do cluster excede limite k3d'
Assert-True ($cluster -match 'hostIP: 127\.0\.0\.1') 'API k3d fora de loopback'
Assert-True ($cluster -match 'updateDefaultKubeconfig: false') 'kubeconfig default pode ser alterado'
Assert-True ($cluster -match 'switchCurrentContext: false') 'contexto default pode ser alterado'
Write-Output 'STATIC_HML_PLATFORM_LOCAL_TESTS_OK'
