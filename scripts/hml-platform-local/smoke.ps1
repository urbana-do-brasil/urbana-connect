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
    Write-HmlPlatformLocalEvidence -Operation 'smoke' -Data @{ target = 'hml-platform-local'; model = 'not-called'; outbound = 'disabled'; endpoints = 'loopback-only' }
    Write-Output 'HML_PLATFORM_LOCAL_SMOKE_OK'
} finally {
    if ($forward -and -not $forward.HasExited) { Stop-Process -Id $forward.Id -Force -ErrorAction SilentlyContinue }
}
