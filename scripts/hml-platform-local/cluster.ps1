[CmdletBinding()]
param(
    [switch]$Create,
    [switch]$Start,
    [switch]$Stop,
    [switch]$Delete,
    [switch]$ConfirmDataLoss
)

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
$actions = @($Create, $Start, $Stop, $Delete | Where-Object { $_ }).Count
if ($actions -ne 1) {
    Throw-HmlPlatformLocalError 'USAGE' 'informe exatamente uma acao: -Create, -Start, -Stop ou -Delete.'
}
if ($Delete -and -not $ConfirmDataLoss) {
    Throw-HmlPlatformLocalError 'CONFIRMATION' 'Delete pode remover PVC/volume local; repita com -ConfirmDataLoss.'
}
Require-Command 'docker'
Require-Command 'k3d'
Require-Command 'kubectl'

function Test-ClusterPresent {
    $result = Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'list') -AllowFailure
    return $result.ExitCode -eq 0 -and $result.Output -match [regex]::Escape($config.ClusterName)
}

function Save-DedicatedKubeconfig {
    if (-not (Test-ClusterPresent)) {
        Throw-HmlPlatformLocalError 'CLUSTER' "cluster '$($config.ClusterName)' ausente."
    }
    New-Item -ItemType Directory -Force -Path $config.DataRoot | Out-Null
    $yaml = Invoke-ExternalText -Command 'k3d' -Arguments @('kubeconfig', 'get', $config.ClusterName)
    if ([string]::IsNullOrWhiteSpace($yaml.Output)) {
        Throw-HmlPlatformLocalError 'CLUSTER' 'kubeconfig dedicado vazio.'
    }
    [IO.File]::WriteAllText($config.KubeconfigPath, $yaml.Output, (New-Object Text.UTF8Encoding($false)))
}

if ($Create) {
    if (-not (Test-ClusterPresent)) {
        Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'create', '--config', $config.ClusterConfigPath) | Out-Null
    }
    Save-DedicatedKubeconfig
    Write-HmlPlatformLocalEvidence -Operation 'cluster-create' -Data @{ target = 'hml-platform-local'; cluster = $config.ClusterName; context = $config.ContextName }
    Write-Output "HML_PLATFORM_LOCAL_CLUSTER_READY: $($config.ClusterName)"
    exit 0
}

if (-not (Test-ClusterPresent)) {
    Throw-HmlPlatformLocalError 'CLUSTER' "cluster '$($config.ClusterName)' ausente."
}

if ($Start) {
    Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'start', $config.ClusterName) | Out-Null
    Save-DedicatedKubeconfig
    Write-Output "HML_PLATFORM_LOCAL_CLUSTER_STARTED: $($config.ContextName)"
    exit 0
}

if ($Stop) {
    Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'stop', $config.ClusterName) | Out-Null
    Write-Output 'HML_PLATFORM_LOCAL_CLUSTER_STOPPED'
    exit 0
}

Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'delete', $config.ClusterName) | Out-Null
if (Test-Path -LiteralPath $config.KubeconfigPath) {
    Remove-Item -LiteralPath $config.KubeconfigPath -Force
}
Write-Output 'HML_PLATFORM_LOCAL_CLUSTER_DELETED'
