[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
Assert-TargetManifests | Out-Null
$api = Join-Path $config.InfraRoot 'api.yaml'
if ((Get-Content -Raw -LiteralPath $api) -match 'HERMES_POC_ENABLED:\s*"?true' -or
    (Get-Content -Raw -LiteralPath $api) -match 'HermesWebhookMessageHandler') {
    Throw-HmlPlatformLocalError 'OUTBOUND' 'API target referencia Hermes/POC.'
}
Write-Output 'HML_PLATFORM_LOCAL_RENDER_OK'
