[CmdletBinding()]
param(
    [switch]$SkipRuntime
)

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
$manifestText = Assert-TargetManifests
$clusterText = Get-Content -Raw -LiteralPath $config.ClusterConfigPath
if ($clusterText -match 'local-hml' -or $clusterText -notmatch 'hml-platform-local') {
    Throw-HmlPlatformLocalError 'TARGET' 'cluster-config nao corresponde ao target dedicado.'
}

if ($SkipRuntime) {
    Write-Output 'STATIC_HML_PLATFORM_LOCAL_PREFLIGHT_OK'
    exit 0
}

Require-Command 'docker'
Require-Command 'k3d'
Require-Command 'kubectl'
$cluster = Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'list') -AllowFailure
if ($cluster.ExitCode -ne 0 -or $cluster.Output -notmatch [regex]::Escape($config.ClusterName)) {
    Throw-HmlPlatformLocalError 'CLUSTER' "cluster dedicado '$($config.ClusterName)' ausente."
}
Assert-LocalKubeContext
$render = Invoke-ExternalText -Command 'kubectl' -Arguments @('kustomize', $config.InfraRoot)
Assert-TargetText -Text $render.Output
$nodes = Invoke-Kubectl -Arguments @('get', 'nodes', '-o', 'json') | Select-Object -ExpandProperty Output | ConvertFrom-Json
$ready = @($nodes.items | Where-Object { @($_.status.conditions | Where-Object { $_.type -eq 'Ready' -and $_.status -eq 'True' }).Count -gt 0 })
if ($ready.Count -ne 1) { Throw-HmlPlatformLocalError 'CLUSTER' 'target dedicado precisa ter exatamente um node Ready.' }
$imageEvidence = [ordered]@{}
$envValues = Read-LocalEnv
foreach ($image in @($config.ApiImage, $config.MongoImage)) {
    $evidence = Get-HmlPlatformLocalImageEvidence -Reference $image
    $imageEvidence[$image] = [ordered]@{
        id = $evidence.Id
        digests = @($evidence.Digests)
        revision = $evidence.Revision
        worktreeDirty = $evidence.WorktreeDirty
    }
}
$revision = Get-HmlPlatformLocalGitRevision
$dirty = Get-HmlPlatformLocalWorktreeDirty
if ($imageEvidence[$config.ApiImage].revision -ne $revision -or
    $imageEvidence[$config.ApiImage].worktreeDirty -ne $dirty) {
    Throw-HmlPlatformLocalError 'IMAGE' 'imagem API nao corresponde ao revision/estado atual; reprovisione com cluster.ps1 -Create ou start.ps1.'
}
$nodeImages = Assert-HmlPlatformLocalImagesImported
Write-HmlPlatformLocalEvidence -Operation 'preflight' -Data @{
    target = 'hml-platform-local'
    namespace = $config.Namespace
    outbound = 'disabled'
    endpoints = 'loopback-only'
    images = $imageEvidence
    importedImageCount = @($nodeImages).Count
    environmentKeys = @($envValues.Keys)
}
Write-Output 'HML_PLATFORM_LOCAL_PREFLIGHT_OK'
