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
$expectedVersions = Get-HmlPlatformLocalExpectedToolVersions
$dockerVersion = (Invoke-ExternalText -Command 'docker' -Arguments @('version', '--format', '{{.Server.Version}}')).Output.Trim()
$k3dVersionOutput = (Invoke-ExternalText -Command 'k3d' -Arguments @('version')).Output
$k3dVersionMatch = [regex]::Match($k3dVersionOutput, '(?m)^k3d version (?<version>v[0-9]+\.[0-9]+\.[0-9]+)\s*$')
if (-not $k3dVersionMatch.Success -or $k3dVersionMatch.Groups['version'].Value -cne [string]$expectedVersions.k3d) {
    Throw-HmlPlatformLocalError 'PREREQUISITE' 'k3d nao corresponde a versao fixada para esta evidencia.'
}
$kubectlVersionOutput = (Invoke-ExternalText -Command 'kubectl' -Arguments @('version', '--client', '-o', 'json')).Output
$kubectlVersion = $kubectlVersionOutput | ConvertFrom-Json
Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' -Output $kubectlVersionOutput -Expected $expectedVersions
$cluster = Invoke-ExternalText -Command 'k3d' -Arguments @('cluster', 'list') -AllowFailure
if ($cluster.ExitCode -ne 0 -or $cluster.Output -notmatch [regex]::Escape($config.ClusterName)) {
    Throw-HmlPlatformLocalError 'CLUSTER' "cluster dedicado '$($config.ClusterName)' ausente."
}
Assert-LocalKubeContext
$render = Invoke-ExternalText -Command 'kubectl' -Arguments @('kustomize', $config.InfraRoot)
Assert-TargetText -Text $render.Output
Assert-HmlPlatformLocalImageReferences -Text $render.Output -AllowLocalApiTemplate
$nodes = Invoke-Kubectl -Arguments @('get', 'nodes', '-o', 'json') | Select-Object -ExpandProperty Output | ConvertFrom-Json
$ready = @($nodes.items | Where-Object { @($_.status.conditions | Where-Object { $_.type -eq 'Ready' -and $_.status -eq 'True' }).Count -gt 0 })
if ($ready.Count -ne 1) { Throw-HmlPlatformLocalError 'CLUSTER' 'target dedicado precisa ter exatamente um node Ready.' }
$k3sVersion = [string]$ready[0].status.nodeInfo.kubeletVersion
Assert-HmlPlatformLocalK3sVersionOutput -Output $k3sVersion -Expected $expectedVersions
$toolVersionEvidence = [ordered]@{
    docker = $dockerVersion
    k3d = $k3dVersionMatch.Groups['version'].Value
    k3s = $k3sVersion
    kubectl = [string]$kubectlVersion.clientVersion.gitVersion
    kustomize = [string]$kubectlVersion.kustomizeVersion
    k3sImage = [string]$expectedVersions.k3s.image
}
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
Assert-HmlPlatformLocalPromotionSourceClean -Dirty $dirty
$importedImages = Get-HmlPlatformLocalImportedImageEvidence
$promotion = New-HmlPlatformLocalPromotionManifest `
    -ImageEvidence (Get-HmlPlatformLocalImageEvidence -Reference $config.ApiImage) `
    -ImportedDigest ([string]$importedImages.Digests[$config.ApiImage].digest) `
    -ExpectedRevision $revision -SourceDirty $dirty
Write-HmlPlatformLocalEvidence -Operation 'preflight' -Data @{
    target = 'hml-platform-local'
    namespace = $config.Namespace
    sourceRevision = $revision
    sourceWorktreeDirty = $dirty
    versions = $toolVersionEvidence
    outbound = 'disabled'
    endpoints = 'loopback-only'
    images = $imageEvidence
    importedImageCount = @($importedImages.References).Count
    importedImageDigests = $importedImages.Digests
    promotion = @{
        apiImageReference = $promotion.ApiReference
        apiImageManifestDigest = $promotion.ApiDigest
        sourceRevision = $promotion.SourceRevision
        manifestSha256 = $promotion.ManifestSha256
        manifestPath = $promotion.Path
    }
    environmentKeys = @($envValues.Keys)
}
Write-Output 'HML_PLATFORM_LOCAL_PREFLIGHT_OK'
