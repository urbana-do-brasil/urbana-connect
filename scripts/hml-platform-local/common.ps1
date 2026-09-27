[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$localDataRoot = if ($env:LOCALAPPDATA) {
    Join-Path $env:LOCALAPPDATA 'UrbanaConnect\hml-platform-local'
} else {
    Join-Path $env:TEMP 'UrbanaConnect\hml-platform-local'
}
$runtimeRoot = $localDataRoot
$dedicatedEnvFile = if (-not [string]::IsNullOrWhiteSpace($env:HML_PLATFORM_LOCAL_ENV_FILE)) {
    [IO.Path]::GetFullPath($env:HML_PLATFORM_LOCAL_ENV_FILE)
} else {
    Join-Path $localDataRoot 'runtime.env'
}
$repoPrefix = [IO.Path]::GetFullPath($repoRoot) + [IO.Path]::DirectorySeparatorChar
$dedicatedEnvFile = [IO.Path]::GetFullPath($dedicatedEnvFile)
if ($dedicatedEnvFile.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'HML_PLATFORM_LOCAL_TARGET: dedicated env file must be outside the repository.'
}

$script:HmlPlatformLocal = [pscustomobject][ordered]@{
    RepoRoot = $repoRoot
    InfraRoot = Join-Path $repoRoot 'infra\kubernetes\hml-platform-local'
    RuntimeRoot = $runtimeRoot
    DataRoot = $localDataRoot
    EnvFile = $dedicatedEnvFile
    ClusterName = 'urbana-hml-platform-local'
    ContextName = 'k3d-urbana-hml-platform-local'
    Namespace = 'urbana-connect-hml-platform-local'
    KubeconfigPath = Join-Path $localDataRoot 'kubeconfig.yaml'
    ClusterConfigPath = Join-Path $repoRoot 'infra\kubernetes\hml-platform-local\cluster-config.yaml'
    NodeImage = 'docker.io/rancher/k3s:v1.36.4-k3s1@sha256:edad48e12bf81c3a09ac1c05c0c0ffaaa22145980b989d6fae84543a76b83657'
    ApiImage = 'docker.io/library/urbana-connect-hml-platform-local-api:local'
    ApiDockerfile = Join-Path $repoRoot 'apps\urbana-connect-api\Dockerfile'
    ApiBuildContext = Join-Path $repoRoot 'apps\urbana-connect-api'
    ToolVersionsPath = Join-Path $repoRoot 'infra\kubernetes\hml-platform-local\tool-versions.yaml'
    MongoImage = 'docker.io/library/mongo:8.0@sha256:376f5173003b5408d7b8e6989667231c0bf0cefdce379d7c814910429d1a7a85'
    K3dServerContainer = "k3d-urbana-hml-platform-local-server-0"
    ApiPort = 8082
    KubeApiPort = 6551
    Roles = @('mongodb', 'mongodb-rs-init', 'urbana-connect')
    AllowedEnvKeys = @('MONGODB_URI', 'WHATSAPP_APP_SECRET', 'WHATSAPP_VERIFY_TOKEN')
}

if ($script:HmlPlatformLocal.ClusterName.Length -gt 32) {
    throw "HML_PLATFORM_LOCAL_TARGET: cluster name exceeds k3d 32-character limit."
}

function Get-HmlPlatformLocalConfig { return $script:HmlPlatformLocal }

function Throw-HmlPlatformLocalError {
    param([Parameter(Mandatory = $true)][string]$Category,
          [Parameter(Mandatory = $true)][string]$Message)
    throw "HML_PLATFORM_LOCAL_${Category}: $Message"
}

function Resolve-HmlPlatformLocalCommand {
    param([Parameter(Mandatory = $true)][string]$Name)
    $command = Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $command) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' "comando '$Name' nao encontrado."
    }
    $path = [string]$command.Source
    if ([string]::IsNullOrWhiteSpace($path)) { $path = [string]$command.Path }
    if ([string]::IsNullOrWhiteSpace($path)) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' "caminho do comando '$Name' nao foi resolvido."
    }
    return $path
}

function Get-HmlPlatformLocalExpectedToolVersions {
    $config = Get-HmlPlatformLocalConfig
    try {
        $versions = Get-Content -Raw -LiteralPath $config.ToolVersionsPath | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Throw-HmlPlatformLocalError 'PREREQUISITE' 'registro de versoes das ferramentas ausente ou invalido.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$versions.k3d) -or
        [string]::IsNullOrWhiteSpace([string]$versions.kubectl.gitVersion) -or
        [string]::IsNullOrWhiteSpace([string]$versions.kubectl.kustomizeVersion)) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' 'registro de versoes deve fixar k3d, kubectl e Kustomize.'
    }
    return $versions
}

function Assert-HmlPlatformLocalCliVersionOutput {
    param([Parameter(Mandatory = $true)][ValidateSet('k3d', 'kubectl')][string]$Name,
          [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Output,
          [Parameter(Mandatory = $true)]$Expected)
    if ($Name -eq 'k3d') {
        $match = [regex]::Match($Output, '(?m)^k3d version (?<version>v[0-9]+\.[0-9]+\.[0-9]+)\s*$')
        if (-not $match.Success -or $match.Groups['version'].Value -cne [string]$Expected.k3d) {
            Throw-HmlPlatformLocalError 'PREREQUISITE' "k3d ausente ou fora da versao fixada $($Expected.k3d)."
        }
        return
    }

    try {
        $client = $Output | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Throw-HmlPlatformLocalError 'PREREQUISITE' 'kubectl nao retornou JSON de versao valido.'
    }
    if ([string]$client.clientVersion.gitVersion -cne [string]$Expected.kubectl.gitVersion -or
        [string]$client.kustomizeVersion -cne [string]$Expected.kubectl.kustomizeVersion) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' "kubectl/Kustomize ausente ou fora das versoes fixadas ($($Expected.kubectl.gitVersion), $($Expected.kubectl.kustomizeVersion))."
    }
}

function Assert-HmlPlatformLocalCliVersion {
    param([Parameter(Mandatory = $true)][ValidateSet('k3d', 'kubectl')][string]$Name,
          [Parameter(Mandatory = $true)][string]$CommandPath)
    $expected = Get-HmlPlatformLocalExpectedToolVersions
    if ($Name -eq 'k3d') {
        $result = Invoke-ExternalText -Command $CommandPath -Arguments @('version')
    } else {
        $result = Invoke-ExternalText -Command $CommandPath -Arguments @('version', '--client', '-o', 'json')
    }
    Assert-HmlPlatformLocalCliVersionOutput -Name $Name -Output $result.Output -Expected $expected
}

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    $path = Resolve-HmlPlatformLocalCommand -Name $Name
    if ($Name -in @('k3d', 'kubectl')) {
        Assert-HmlPlatformLocalCliVersion -Name $Name -CommandPath $path
    }
}

function Invoke-ExternalText {
    param([Parameter(Mandatory = $true)][string]$Command,
          [Parameter(Mandatory = $true)][string[]]$Arguments,
          [switch]$AllowFailure)
    if ($Command -in @('k3d', 'kubectl')) {
        $Command = Resolve-HmlPlatformLocalCommand -Name $Command
    }
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Command @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $old }
    $text = (($output | ForEach-Object { [string]$_ }) -join [Environment]::NewLine)
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        Throw-HmlPlatformLocalError 'COMMAND' "'$Command' falhou (exit $exitCode)."
    }
    [pscustomobject]@{ ExitCode = $exitCode; Output = $text }
}

function Invoke-Kubectl {
    param([Parameter(Mandatory = $true)][string[]]$Arguments,
          [switch]$AllowFailure)
    $config = Get-HmlPlatformLocalConfig
    $args = @('--kubeconfig', $config.KubeconfigPath, '--context', $config.ContextName) + $Arguments
    Invoke-ExternalText -Command 'kubectl' -Arguments $args -AllowFailure:$AllowFailure
}

function Get-HmlPlatformLocalImageEvidence {
    param([Parameter(Mandatory = $true)][string]$Reference)
    $result = Invoke-ExternalText -Command 'docker' -Arguments @('image', 'inspect', $Reference)
    $records = @($result.Output | ConvertFrom-Json)
    if ($records.Count -ne 1) {
        Throw-HmlPlatformLocalError 'IMAGE' "referencia de imagem ambigua: $Reference"
    }
    $record = $records[0]
    $digests = @($record.RepoDigests |
        ForEach-Object { [string]$_ } |
        Where-Object { $_ -match '@sha256:[0-9a-f]{64}$' })
    $imageId = [string]$record.Id
    if ($imageId -notmatch '^sha256:[0-9a-f]{64}$') {
        Throw-HmlPlatformLocalError 'IMAGE' "imagem sem ID OCI materializado: $Reference"
    }
    $requestedDigest = $null
    if ($Reference -match '@(sha256:[0-9a-f]{64})$') {
        $requestedDigest = $Matches[1]
        if ($digests.Count -eq 0) {
            Throw-HmlPlatformLocalError 'IMAGE' "imagem sem RepoDigest para a referencia fixada: $Reference"
        }
        if (-not ($digests | Where-Object { $_.EndsWith('@' + $requestedDigest) })) {
            Throw-HmlPlatformLocalError 'IMAGE' "digest solicitado ausente na imagem: $Reference"
        }
    }
    [pscustomobject]@{
        Reference = $Reference
        Id = $imageId
        Digests = $digests
        Revision = [string]$record.Config.Labels.'org.opencontainers.image.revision'
        WorktreeDirty = [string]$record.Config.Labels.'br.com.urbana.connect.worktree-dirty'
    }
}

function Get-HmlPlatformLocalGitRevision {
    $config = Get-HmlPlatformLocalConfig
    $result = Invoke-ExternalText -Command 'git' -Arguments @('-C', $config.RepoRoot, 'rev-parse', 'HEAD')
    return $result.Output.Trim()
}

function Get-HmlPlatformLocalWorktreeDirty {
    $config = Get-HmlPlatformLocalConfig
    $result = Invoke-ExternalText -Command 'git' -Arguments @(
        '-C', $config.RepoRoot, 'status', '--porcelain', '--untracked-files=all')
    if ([string]::IsNullOrWhiteSpace($result.Output)) { return 'false' }
    return 'true'
}

function Build-HmlPlatformLocalApiImage {
    $config = Get-HmlPlatformLocalConfig
    $revision = Get-HmlPlatformLocalGitRevision
    $dirty = Get-HmlPlatformLocalWorktreeDirty
    Invoke-ExternalText -Command 'docker' -Arguments @(
        'build', '--pull=false',
        '--file', $config.ApiDockerfile,
        '--tag', $config.ApiImage,
        '--label', ('org.opencontainers.image.revision=' + $revision),
        '--label', ('br.com.urbana.connect.worktree-dirty=' + $dirty),
        $config.ApiBuildContext) | Out-Null
    return Get-HmlPlatformLocalImageEvidence -Reference $config.ApiImage
}

function Ensure-HmlPlatformLocalApiImage {
    param([switch]$Rebuild)
    $config = Get-HmlPlatformLocalConfig
    $revision = Get-HmlPlatformLocalGitRevision
    $dirty = Get-HmlPlatformLocalWorktreeDirty
    $inspect = Invoke-ExternalText -Command 'docker' -Arguments @('image', 'inspect', $config.ApiImage) -AllowFailure
    $needsBuild = $Rebuild -or $inspect.ExitCode -ne 0
    if (-not $needsBuild) {
        $records = @($inspect.Output | ConvertFrom-Json)
        if ($records.Count -ne 1) {
            $needsBuild = $true
        } else {
            $labels = $records[0].Config.Labels
            $needsBuild = ([string]$labels.'org.opencontainers.image.revision' -ne $revision) -or
                ([string]$labels.'br.com.urbana.connect.worktree-dirty' -ne $dirty)
        }
    }
    if ($needsBuild) {
        return Build-HmlPlatformLocalApiImage
    }
    return Get-HmlPlatformLocalImageEvidence -Reference $config.ApiImage
}

function Get-HmlPlatformLocalNodeImages {
    $config = Get-HmlPlatformLocalConfig
    $result = Invoke-ExternalText -Command 'docker' -Arguments @(
        'exec', $config.K3dServerContainer, '/bin/ctr', '-n', 'k8s.io', 'images', 'ls', '-q')
    return @($result.Output -split "`r?`n" | ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Import-HmlPlatformLocalImages {
    $config = Get-HmlPlatformLocalConfig
    $apiEvidence = Get-HmlPlatformLocalImageEvidence -Reference $config.ApiImage
    $mongoEvidence = Get-HmlPlatformLocalImageEvidence -Reference $config.MongoImage
    Invoke-ExternalText -Command 'k3d' -Arguments @(
        'image', 'import', '--cluster', $config.ClusterName, $config.ApiImage, $config.MongoImage) | Out-Null
    $nodeImages = Get-HmlPlatformLocalNodeImages
    if ($config.ApiImage -notin $nodeImages) {
        Throw-HmlPlatformLocalError 'IMAGE' "imagem API nao foi importada no node k3d: $($config.ApiImage)"
    }
    $mongoDigest = ($mongoEvidence.Digests | Where-Object { $_.EndsWith('@' + ($config.MongoImage -replace '^.*@', '')) } | Select-Object -First 1)
    if ($mongoDigest -and -not ($nodeImages -contains $config.MongoImage)) {
        Throw-HmlPlatformLocalError 'IMAGE' "imagem Mongo fixada nao foi importada no node k3d: $($config.MongoImage)"
    }
    return [pscustomobject]@{ Api = $apiEvidence; Mongo = $mongoEvidence; NodeImages = $nodeImages }
}

function Assert-HmlPlatformLocalImagesImported {
    $config = Get-HmlPlatformLocalConfig
    $nodeImages = Get-HmlPlatformLocalNodeImages
    if ($config.ApiImage -notin $nodeImages) {
        Throw-HmlPlatformLocalError 'IMAGE' "imagem API ausente no node k3d: $($config.ApiImage)"
    }
    if ($config.MongoImage -notin $nodeImages) {
        Throw-HmlPlatformLocalError 'IMAGE' "imagem Mongo fixada ausente no node k3d: $($config.MongoImage)"
    }
    return $nodeImages
}

function Assert-TargetText {
    param([Parameter(Mandatory = $true)][string]$Text)
    $forbidden = @(
        'local-hml', 'HERMES_POC_ENABLED:\s*"?true', 'HermesWebhookMessageHandler',
        'WebhookInboxDispatchWorker', 'openrouter', 'graph.facebook.com',
        'whatsapp outbound', 'NodePort', 'LoadBalancer', 'hostNetwork:\s*true', 'hostPort:'
    )
    foreach ($pattern in $forbidden) {
        if ($Text -match $pattern) {
            Throw-HmlPlatformLocalError 'TARGET' "marcador proibido no target dedicado: $pattern"
        }
    }
    if ($Text -notmatch 'hml-platform-local' -or $Text -notmatch 'HERMES_POC_ENABLED:\s*"false"') {
        Throw-HmlPlatformLocalError 'TARGET' 'identidade hml-platform-local ou Hermes deny-by-default ausente.'
    }
    if ($Text -match '(?m)^kind:\s*Secret\s*$') {
        Throw-HmlPlatformLocalError 'SECRET' 'Secret literal nao pode ser versionada.'
    }
}

function Assert-TargetManifests {
    $config = Get-HmlPlatformLocalConfig
    if (-not (Test-Path -LiteralPath $config.InfraRoot -PathType Container)) {
        Throw-HmlPlatformLocalError 'TARGET' 'diretorio infra/kubernetes/hml-platform-local ausente.'
    }
    $files = @(Get-ChildItem -LiteralPath $config.InfraRoot -Filter '*.yaml' -File |
        Where-Object { $_.Name -ne 'cluster-config.yaml' })
    if ($files.Count -lt 7) { Throw-HmlPlatformLocalError 'TARGET' 'manifests dedicados incompletos.' }
    $text = ($files | ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
    Assert-TargetText -Text $text
    return $text
}

function Assert-LocalKubeContext {
    $config = Get-HmlPlatformLocalConfig
    $result = Invoke-Kubectl -Arguments @('config', 'current-context')
    if ($result.Output.Trim() -ne $config.ContextName) {
        Throw-HmlPlatformLocalError 'CONTEXT' 'contexto atual nao corresponde ao target dedicado.'
    }
}

function Read-LocalEnv {
    $config = Get-HmlPlatformLocalConfig
    if (-not (Test-Path -LiteralPath $config.EnvFile -PathType Leaf)) {
        Throw-HmlPlatformLocalError 'CONFIG' 'arquivo .env local ausente; nenhum valor foi exposto.'
    }
    $values = [ordered]@{}
    foreach ($raw in (Get-Content -LiteralPath $config.EnvFile)) {
        $line = ([string]$raw).Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) { Throw-HmlPlatformLocalError 'CONFIG' 'linha .env invalida.' }
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if ($key -notin $config.AllowedEnvKeys) { Throw-HmlPlatformLocalError 'CONFIG' "chave .env nao permitida: $key" }
        if ($value -match '[\r\n]') {
            Throw-HmlPlatformLocalError 'CONFIG' "valor multiline detectado na chave $key."
        }
        if ($key -eq 'MONGODB_URI' -and $value -match '(?i)prod|local-hml|cloud|graph') {
            Throw-HmlPlatformLocalError 'CONFIG' 'MONGODB_URI aponta para um destino fora do target local.'
        }
        $values[$key] = $value.Trim('"', "'")
    }
    foreach ($required in @('MONGODB_URI', 'WHATSAPP_APP_SECRET', 'WHATSAPP_VERIFY_TOKEN')) {
        if (-not $values.Contains($required) -or [string]::IsNullOrWhiteSpace([string]$values[$required])) {
            Throw-HmlPlatformLocalError 'CONFIG' "$required ausente/vazio."
        }
    }
    return $values
}

function New-HmlPlatformLocalSecretYaml {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary]$Values)
    $encode = {
        param([string]$Value)
        [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
    }
    return @(
        'apiVersion: v1'
        'kind: Secret'
        'metadata:'
        '  name: hml-platform-local-secrets'
        'type: Opaque'
        'data:'
        ('  MONGODB_URI: ' + (& $encode ([string]$Values.MONGODB_URI)))
        ('  WHATSAPP_APP_SECRET: ' + (& $encode ([string]$Values.WHATSAPP_APP_SECRET)))
        ('  WHATSAPP_VERIFY_TOKEN: ' + (& $encode ([string]$Values.WHATSAPP_VERIFY_TOKEN)))
    ) -join [Environment]::NewLine
}

function Write-HmlPlatformLocalEvidence {
    param([Parameter(Mandatory = $true)][string]$Operation,
          [Parameter(Mandatory = $true)][hashtable]$Data)
    New-Item -ItemType Directory -Force -Path $script:HmlPlatformLocal.RuntimeRoot | Out-Null
    $safe = [ordered]@{ operation = $Operation; timestamp = (Get-Date).ToUniversalTime().ToString('o'); data = $Data }
    $safe | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $script:HmlPlatformLocal.RuntimeRoot "$Operation.json") -Encoding UTF8
}
