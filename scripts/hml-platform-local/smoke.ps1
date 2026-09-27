[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
Require-Command 'kubectl'
Assert-LocalKubeContext
$forward = Start-Process -FilePath 'kubectl' -ArgumentList @(
    '--kubeconfig', $config.KubeconfigPath, '--context', $config.ContextName,
    '-n', $config.Namespace, 'port-forward', 'service/urbana-connect', "$($config.ApiPort):8081"
) -PassThru -WindowStyle Hidden
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 500
        try {
            $health = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($config.ApiPort)/api/v1/health" -TimeoutSec 3
            $readiness = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($config.ApiPort)/api/v1/readiness" -TimeoutSec 3
            if ($health.Content.Trim() -eq 'OK' -and $readiness.Content.Trim() -eq 'READY') {
                $ready = $true
                break
            }
        } catch { }
    }
    if (-not $ready) { Throw-HmlPlatformLocalError 'SMOKE' 'API health/readiness nao passou.' }
    $inboundBody = @'
{"object":"whatsapp_business_account","entry":[{"changes":[{"value":{"messages":[{"id":"smoke-inbound-only","from":"5511999999999","type":"text","text":{"body":"smoke"}}]}}]}]}
'@
    $inbound = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://127.0.0.1:$($config.ApiPort)/api/webhook" -ContentType 'application/json' -Body $inboundBody -TimeoutSec 10
    if ($inbound.StatusCode -ne 200) { Throw-HmlPlatformLocalError 'SMOKE' 'POST inbound-only nao foi aceito.' }
    $logs = Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'logs', 'deployment/urbana-connect', '--since=2m', '--tail=50')
    if ($logs.Output -notmatch 'Webhook inbound-only aceito') {
        Throw-HmlPlatformLocalError 'SMOKE' 'evidencia do handler inbound-only ausente nos logs.'
    }
    Write-HmlPlatformLocalEvidence -Operation 'smoke' -Data @{
        target = 'hml-platform-local'
        inbound = 'accepted'
        model = 'not-called'
        outbound = 'disabled'
        endpoints = 'loopback-only'
    }
    Write-Output 'HML_PLATFORM_LOCAL_SMOKE_OK'
} finally {
    if ($forward -and -not $forward.HasExited) { Stop-Process -Id $forward.Id -Force -ErrorAction SilentlyContinue }
}
