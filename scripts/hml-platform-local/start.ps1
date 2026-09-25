[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
Require-Command 'kubectl'
Assert-LocalKubeContext
$secret = New-HmlPlatformLocalSecretYaml -Values (Read-LocalEnv)
Invoke-Kubectl -Arguments @('apply', '-f', (Join-Path $config.InfraRoot 'namespace.yaml')) | Out-Null
Invoke-Kubectl -Arguments @('apply', '-k', $config.InfraRoot) | Out-Null
Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'delete', 'secret/hml-platform-local-secrets', '--ignore-not-found=true') | Out-Null
$secretResult = @($secret | & kubectl '--kubeconfig' $config.KubeconfigPath '--context' $config.ContextName '-n' $config.Namespace 'create' '-f' '-' 2>&1)
$secretExitCode = $LASTEXITCODE
if ($secretExitCode -ne 0) {
    Throw-HmlPlatformLocalError 'SECRET' 'criacao do secret dedicado falhou.'
}
Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'rollout', 'status', 'statefulset/mongodb', '--timeout=180s') | Out-Null
Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'wait', '--for=condition=complete', 'job/mongodb-rs-init', '--timeout=300s') | Out-Null
Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'rollout', 'status', 'deployment/urbana-connect', '--timeout=240s') | Out-Null
Write-HmlPlatformLocalEvidence -Operation 'start' -Data @{ target = 'hml-platform-local'; outbound = 'disabled'; model = 'not-called' }
Write-Output 'HML_PLATFORM_LOCAL_STARTED'
