[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
Require-Command 'kubectl'
Assert-LocalKubeContext
Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'get', 'pods,svc,pvc', '-o', 'wide') | Select-Object -ExpandProperty Output
Write-HmlPlatformLocalEvidence -Operation 'status' -Data @{ target = 'hml-platform-local'; endpoints = 'loopback-only' }
