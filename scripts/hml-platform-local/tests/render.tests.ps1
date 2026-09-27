[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '..\common.ps1')
$config = Get-HmlPlatformLocalConfig
$manifestFiles = @(Get-ChildItem -LiteralPath $config.InfraRoot -Filter '*.yaml' -File |
    Where-Object { $_.Name -ne 'cluster-config.yaml' })
$renderText = ($manifestFiles | ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
if ($renderText -match 'local-hml|openrouter|graph.facebook.com|HERMES_POC_ENABLED:\s*"true"|NodePort|LoadBalancer|hostNetwork:\s*true|hostPort:') {
    throw 'STATIC_HML_PLATFORM_LOCAL_RENDER_FAILED: target guard violado'
}
if ($renderText -notmatch 'environment:\s*hml-platform-local' -or $renderText -notmatch 'HERMES_POC_ENABLED:\s*"false"') {
    throw 'STATIC_HML_PLATFORM_LOCAL_RENDER_FAILED: identity/deny-by-default ausente'
}
Write-Output 'STATIC_HML_PLATFORM_LOCAL_RENDER_OK'
