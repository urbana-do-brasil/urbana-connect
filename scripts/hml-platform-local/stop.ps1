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
Invoke-Kubectl -Arguments @('delete', '-k', $config.InfraRoot, '--ignore-not-found=true') | Select-Object -ExpandProperty Output
Write-Output 'HML_PLATFORM_LOCAL_RESOURCES_STOPPED'
