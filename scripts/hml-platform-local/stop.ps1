[CmdletBinding()]
param(
    [switch]$DeleteCluster
)

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
Require-Command 'kubectl'
if ($DeleteCluster) {
    Require-Command 'k3d'
    $confirmation = Read-Host "Digite DELETE_DATA para remover $($config.ClusterName) e os dados locais"
    if ($confirmation -ne 'DELETE_DATA') { throw 'confirmacao de perda de dados nao recebida' }
    Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'delete', $config.ClusterName) | Out-Null
    Write-Output 'HML_PLATFORM_LOCAL_CLUSTER_DELETED'
    exit 0
}
Assert-LocalKubeContext
Invoke-Kubectl -Arguments @(
    '-n', $config.Namespace, 'delete',
    'deployment/urbana-connect',
    'statefulset/mongodb',
    'job/mongodb-rs-init',
    '--ignore-not-found=true') | Select-Object -ExpandProperty Output
$namespaceReadback = Invoke-Kubectl -Arguments @('get', 'namespace', $config.Namespace, '-o', 'json') -AllowFailure
if ($namespaceReadback.ExitCode -ne 0) {
    Throw-HmlPlatformLocalError 'STOP' 'namespace dedicado nao foi preservado.'
}
$pvcReadback = Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'get', 'pvc', '-o', 'json') -AllowFailure
if ($pvcReadback.ExitCode -ne 0) {
    Throw-HmlPlatformLocalError 'STOP' 'leitura do PVC dedicado falhou; stop nao confirmou preservacao de dados.'
}
$pvcItems = @($pvcReadback.Output | ConvertFrom-Json).items
if (@($pvcItems).Count -eq 0) {
    Throw-HmlPlatformLocalError 'STOP' 'nenhum PVC dedicado foi encontrado apos stop.'
}
Write-HmlPlatformLocalEvidence -Operation 'stop' -Data @{
    target = 'hml-platform-local'
    state = 'stopped'
    workloads = @('deployment/urbana-connect', 'statefulset/mongodb', 'job/mongodb-rs-init')
    namespacePreserved = $true
    pvcNames = @($pvcItems | ForEach-Object { [string]$_.metadata.name })
    dataLossConfirmation = 'not-required-for-stop'
}
Write-Output 'HML_PLATFORM_LOCAL_RESOURCES_STOPPED'
