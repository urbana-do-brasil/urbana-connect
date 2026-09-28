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
    if ([string]::IsNullOrWhiteSpace([string]$versions.k3s.version) -or
        [string]::IsNullOrWhiteSpace([string]$versions.k3s.image)) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' 'registro de versoes deve fixar K3s e sua imagem por digest.'
    }
    $k3sTag = ([string]$versions.k3s.version).Replace('+', '-')
    $expectedK3sImagePattern = '^docker\.io/rancher/k3s:' + [regex]::Escape($k3sTag) + '@sha256:[0-9a-f]{64}$'
    if ([string]$versions.k3s.image -notmatch $expectedK3sImagePattern -or
        [string]$versions.k3s.image -cne [string]$config.NodeImage) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' 'imagem K3s deve corresponder a versao registrada e ao digest configurado.'
    }
    $clusterConfig = Get-Content -Raw -LiteralPath $config.ClusterConfigPath
    $clusterImage = [regex]::Match($clusterConfig, '(?m)^image:\s*(?<image>\S+)\s*$')
    if (-not $clusterImage.Success -or $clusterImage.Groups['image'].Value -cne [string]$versions.k3s.image) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' 'cluster-config deve usar exatamente a imagem K3s fixada.'
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

function Assert-HmlPlatformLocalK3sVersionOutput {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Output,
          [Parameter(Mandatory = $true)]$Expected)
    if ($Output.Trim() -cne [string]$Expected.k3s.version) {
        Throw-HmlPlatformLocalError 'PREREQUISITE' "K3s ausente ou fora da versao fixada $($Expected.k3s.version)."
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

function Get-HmlPlatformLocalSanitizedCommandFailure {
    param([Parameter(Mandatory = $true)][string]$CommandName,
          [Parameter(Mandatory = $true)][ValidatePattern('^[a-z0-9]+(?:-[a-z0-9]+)*$')][string]$Stage,
          [Parameter(Mandatory = $true)][int]$ExitCode,
          [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Diagnostic)
    $safeCommand = [IO.Path]::GetFileNameWithoutExtension($CommandName).ToLowerInvariant()
    if ($safeCommand -notin @('docker', 'k3d', 'kubectl', 'git')) { $safeCommand = 'external' }
    $category = 'EXTERNAL_COMMAND_FAILURE'
    $resource = 'none'
    $field = 'none'
    if ($safeCommand -eq 'kubectl' -and $Diagnostic -match '(?i)(field is immutable|immutable|cannot be changed)') {
        $category = 'IMMUTABLE_FIELD'
        if ($Diagnostic -match '(?i)mongodb-rs-init') { $resource = 'job/mongodb-rs-init' }
        if ($Diagnostic -match '(?i)spec\.template') { $field = 'spec.template' }
        elseif ($Diagnostic -match '(?i)spec\.selector') { $field = 'spec.selector' }
    }
    return "stage=$Stage command=$safeCommand category=$category exit=$ExitCode resource=$resource field=$field"
}

function Invoke-ExternalText {
    param([Parameter(Mandatory = $true)][string]$Command,
          [Parameter(Mandatory = $true)][string[]]$Arguments,
          [ValidatePattern('^[a-z0-9]+(?:-[a-z0-9]+)*$')][string]$Stage = 'external-command',
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
        $safeFailure = Get-HmlPlatformLocalSanitizedCommandFailure -CommandName $Command `
            -Stage $Stage -ExitCode $exitCode -Diagnostic $text
        Throw-HmlPlatformLocalError 'COMMAND' $safeFailure
    }
    [pscustomobject]@{ ExitCode = $exitCode; Output = $text }
}

function Invoke-Kubectl {
    param([Parameter(Mandatory = $true)][string[]]$Arguments,
          [ValidatePattern('^[a-z0-9]+(?:-[a-z0-9]+)*$')][string]$Stage = 'kubectl-operation',
          [switch]$AllowFailure)
    $config = Get-HmlPlatformLocalConfig
    $args = @('--kubeconfig', $config.KubeconfigPath, '--context', $config.ContextName) + $Arguments
    Invoke-ExternalText -Command 'kubectl' -Arguments $args -Stage $Stage -AllowFailure:$AllowFailure
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
    if ($Reference -ceq $config.ApiImage -and $digests.Count -eq 0) {
        Throw-HmlPlatformLocalError 'IMAGE' 'imagem API local sem digest OCI nao pode ser promovida.'
    }
    [pscustomobject]@{
        Reference = $Reference
        Id = $imageId
        Digests = $digests
        Revision = [string]$record.Config.Labels.'org.opencontainers.image.revision'
        WorktreeDirty = [string]$record.Config.Labels.'br.com.urbana.connect.worktree-dirty'
    }
}

function Ensure-HmlPlatformLocalPinnedImage {
    param([Parameter(Mandatory = $true)][string]$Reference)
    if ($Reference -notmatch '@sha256:[0-9a-f]{64}$') {
        Throw-HmlPlatformLocalError 'IMAGE' 'pull explicito exige referencia fixada por digest.'
    }
    $inspect = Invoke-ExternalText -Command 'docker' -Arguments @('image', 'inspect', $Reference) -AllowFailure
    $pulled = $false
    if ($inspect.ExitCode -ne 0) {
        Invoke-ExternalText -Command 'docker' -Arguments @('pull', $Reference) | Out-Null
        $pulled = $true
    }
    $evidence = Get-HmlPlatformLocalImageEvidence -Reference $Reference
    [pscustomobject]@{ Evidence = $evidence; Pulled = $pulled }
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

function Assert-HmlPlatformLocalPromotionSourceClean {
    param([string]$Dirty)
    if (-not $PSBoundParameters.ContainsKey('Dirty')) { $Dirty = Get-HmlPlatformLocalWorktreeDirty }
    if ($Dirty -cne 'false' -and $Dirty -cne 'true') {
        Throw-HmlPlatformLocalError 'SOURCE' 'estado dirty da origem nao foi determinado.'
    }
    if ($Dirty -cne 'false') {
        Throw-HmlPlatformLocalError 'SOURCE' 'origem dirty; build/import no target local foram bloqueados.'
    }
}

function Assert-HmlPlatformLocalPromotionEvidence {
    param([Parameter(Mandatory = $true)][ValidateSet('false', 'true')][string]$SourceDirty,
          [Parameter(Mandatory = $true)][string]$ExpectedRevision,
          [Parameter(Mandatory = $true)]$ImageEvidence)
    Assert-HmlPlatformLocalPromotionSourceClean -Dirty $SourceDirty
    if ($ExpectedRevision -notmatch '^[0-9a-f]{40}$' -or
        [string]$ImageEvidence.Revision -cne $ExpectedRevision -or
        [string]$ImageEvidence.WorktreeDirty -cne 'false') {
        Throw-HmlPlatformLocalError 'IMAGE' 'imagem API nao corresponde a uma revisao limpa e atual.'
    }
    $immutableDigests = @($ImageEvidence.Digests | Where-Object { [string]$_ -match '@sha256:[0-9a-f]{64}$' })
    if ($immutableDigests.Count -eq 0) {
        Throw-HmlPlatformLocalError 'IMAGE' 'imagem API sem digest imutavel nao pode ser promovida.'
    }
}

function Assert-HmlPlatformLocalImportedImageDigest {
    param([Parameter(Mandatory = $true)][string]$ExpectedDigest,
          [Parameter(Mandatory = $true)][string]$ActualDigest)
    if ($ExpectedDigest -notmatch '^sha256:[0-9a-f]{64}$' -or $ActualDigest -cne $ExpectedDigest) {
        Throw-HmlPlatformLocalError 'IMAGE' 'digest importado no node nao corresponde a imagem local verificada.'
    }
}

function Get-HmlPlatformLocalApiPromotionReference {
    param([Parameter(Mandatory = $true)]$ImageEvidence,
          [Parameter(Mandatory = $true)][string]$ImportedDigest)
    $config = Get-HmlPlatformLocalConfig
    if ($ImportedDigest -notmatch '^sha256:[0-9a-f]{64}$') {
        Throw-HmlPlatformLocalError 'IMAGE' 'digest OCI da imagem API no containerd ausente ou invalido.'
    }
    $repository = [regex]::Replace([string]$config.ApiImage, ':[^/:@]+$', '')
    $acceptedRepositories = @($repository)
    if ($repository.StartsWith('docker.io/library/', [StringComparison]::OrdinalIgnoreCase)) {
        $acceptedRepositories += $repository.Substring('docker.io/library/'.Length)
    }
    $repoDigestPatterns = @($acceptedRepositories | ForEach-Object {
        '^' + [regex]::Escape([string]$_) + '@sha256:[0-9a-f]{64}$'
    })
    $repoDigests = @($ImageEvidence.Digests | ForEach-Object { [string]$_ } |
        Where-Object {
            $candidate = $_
            @($repoDigestPatterns | Where-Object { $candidate -match $_ }).Count -gt 0
        })
    if ($repoDigests.Count -eq 0) {
        Throw-HmlPlatformLocalError 'IMAGE' 'imagem API sem RepoDigest do repositorio esperado; ID de configuracao nao substitui digest OCI.'
    }
    $matchingRepoDigest = @($repoDigests | Where-Object { $_.EndsWith('@' + $ImportedDigest) })
    if ($matchingRepoDigest.Count -eq 0) {
        Throw-HmlPlatformLocalError 'IMAGE' 'digest OCI do containerd nao corresponde ao RepoDigest da imagem API local.'
    }
    return "$repository@$ImportedDigest"
}

function Ensure-HmlPlatformLocalNodeImageDigestAlias {
    param([Parameter(Mandatory = $true)][string]$SourceReference,
          [Parameter(Mandatory = $true)][string]$TargetReference,
          [Parameter(Mandatory = $true)][string]$ExpectedDigest)
    Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest $ExpectedDigest -ActualDigest $ExpectedDigest
    if ($TargetReference -notmatch '@(sha256:[0-9a-f]{64})$' -or $Matches[1] -cne $ExpectedDigest) {
        Throw-HmlPlatformLocalError 'IMAGE' 'alias imutavel nao corresponde ao digest verificado no containerd.'
    }
    $nodeDigests = Get-HmlPlatformLocalNodeImageManifestDigests
    if (-not $nodeDigests.ContainsKey($SourceReference)) {
        Throw-HmlPlatformLocalError 'IMAGE' "referencia importada ausente no node k3d: $SourceReference"
    }
    Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest $ExpectedDigest -ActualDigest ([string]$nodeDigests[$SourceReference])
    if ($nodeDigests.ContainsKey($TargetReference)) {
        Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest $ExpectedDigest -ActualDigest ([string]$nodeDigests[$TargetReference])
        return
    }
    $config = Get-HmlPlatformLocalConfig
    Invoke-ExternalText -Command 'docker' -Arguments @(
        'exec', $config.K3dServerContainer, '/bin/ctr', '-n', 'k8s.io', 'images', 'tag',
        $SourceReference, $TargetReference) | Out-Null
    $nodeDigests = Get-HmlPlatformLocalNodeImageManifestDigests
    if (-not $nodeDigests.ContainsKey($TargetReference)) {
        Throw-HmlPlatformLocalError 'IMAGE' 'alias repo@digest nao apareceu no containerd apos a importacao.'
    }
    Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest $ExpectedDigest -ActualDigest ([string]$nodeDigests[$TargetReference])
}

function New-HmlPlatformLocalPromotionManifest {
    param([Parameter(Mandatory = $true)]$ImageEvidence,
          [Parameter(Mandatory = $true)][string]$ImportedDigest,
          [Parameter(Mandatory = $true)][string]$ExpectedRevision,
          [Parameter(Mandatory = $true)][ValidateSet('false', 'true')][string]$SourceDirty)
    $config = Get-HmlPlatformLocalConfig
    Assert-HmlPlatformLocalPromotionEvidence -SourceDirty $SourceDirty `
        -ExpectedRevision $ExpectedRevision -ImageEvidence $ImageEvidence
    $apiReference = Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $ImageEvidence -ImportedDigest $ImportedDigest
    $source = Invoke-ExternalText -Command 'kubectl' -Arguments @('kustomize', $config.InfraRoot) `
        -Stage 'promotion-base-render'
    Assert-TargetText -Text $source.Output
    Assert-HmlPlatformLocalImageReferences -Text $source.Output -AllowLocalApiTemplate

    $revisionPrefix = [IO.Path]::GetFullPath($config.RepoRoot) + [IO.Path]::DirectorySeparatorChar
    $promotionRoot = [IO.Path]::GetFullPath((Join-Path $config.DataRoot 'promotion'))
    if ($promotionRoot.StartsWith($revisionPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        Throw-HmlPlatformLocalError 'SOURCE' 'manifesto de promocao deve ser gerado fora do checkout.'
    }
    $promotionDirectory = Join-Path $promotionRoot ($ExpectedRevision + '-' + $ImportedDigest.Substring(7))
    New-Item -ItemType Directory -Force -Path $promotionDirectory | Out-Null
    $basePath = Join-Path $promotionDirectory 'base.yaml'
    $kustomizationPath = Join-Path $promotionDirectory 'kustomization.yaml'
    $deploymentPatchPath = Join-Path $promotionDirectory 'deployment-annotations.yaml'
    $manifestPath = Join-Path $promotionDirectory 'rendered.yaml'
    $utf8 = New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($basePath, $source.Output, $utf8)
    $deploymentPatch = @(
        'apiVersion: apps/v1'
        'kind: Deployment'
        'metadata:'
        '  name: urbana-connect'
        '  annotations:'
        '    br.com.urbana.connect/source-revision: ' + $ExpectedRevision
        'spec:'
        '  template:'
        '    metadata:'
        '      annotations:'
        '        br.com.urbana.connect/source-revision: ' + $ExpectedRevision
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($deploymentPatchPath, $deploymentPatch, $utf8)
    $kustomization = @(
        'apiVersion: kustomize.config.k8s.io/v1beta1'
        'kind: Kustomization'
        'resources:'
        '  - base.yaml'
        'images:'
        '  - name: ' + ([regex]::Replace([string]$config.ApiImage, ':[^/:@]+$', ''))
        '    newName: ' + ([regex]::Replace([string]$config.ApiImage, ':[^/:@]+$', ''))
        '    digest: ' + $ImportedDigest
        'patches:'
        '  - path: deployment-annotations.yaml'
        '    target:'
        '      group: apps'
        '      version: v1'
        '      kind: Deployment'
        '      name: urbana-connect'
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($kustomizationPath, $kustomization, $utf8)
    $rendered = Invoke-ExternalText -Command 'kubectl' -Arguments @('kustomize', $promotionDirectory) `
        -Stage 'promotion-final-render'
    Assert-TargetText -Text $rendered.Output
    Assert-HmlPlatformLocalImageReferences -Text $rendered.Output `
        -ExpectedApiReference $apiReference -SourceRevision $ExpectedRevision
    [IO.File]::WriteAllText($manifestPath, $rendered.Output, $utf8)
    $manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    [pscustomobject]@{
        Path = $manifestPath
        ApiReference = $apiReference
        ApiDigest = $ImportedDigest
        SourceRevision = $ExpectedRevision
        ManifestSha256 = $manifestHash
    }
}

function Assert-HmlPlatformLocalAppliedPromotion {
    param([Parameter(Mandatory = $true)][string]$ExpectedApiReference,
          [Parameter(Mandatory = $true)][string]$ExpectedRevision)
    $config = Get-HmlPlatformLocalConfig
    $result = Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'get', 'deployment/urbana-connect', '-o', 'json')
    try { $deployment = $result.Output | ConvertFrom-Json -ErrorAction Stop } catch {
        Throw-HmlPlatformLocalError 'APPLY' 'deployment aplicado nao retornou JSON valido.'
    }
    $containers = @($deployment.spec.template.spec.containers | Where-Object { $_.name -ceq 'urbana-connect' })
    if ($containers.Count -ne 1 -or [string]$containers[0].image -cne $ExpectedApiReference) {
        Throw-HmlPlatformLocalError 'APPLY' 'deployment ativo nao usa a referencia API repo@digest promovida.'
    }
    if ([long]$deployment.status.observedGeneration -lt [long]$deployment.metadata.generation) {
        Throw-HmlPlatformLocalError 'APPLY' 'deployment ativo ainda nao observou a geracao aplicada.'
    }
    $annotations = $deployment.spec.template.metadata.annotations
    if ([string]$annotations.'br.com.urbana.connect/source-revision' -cne $ExpectedRevision) {
        Throw-HmlPlatformLocalError 'APPLY' 'deployment ativo nao esta anotado com a revisao limpa da origem.'
    }
    [pscustomobject]@{
        Generation = [long]$deployment.metadata.generation
        ObservedGeneration = [long]$deployment.status.observedGeneration
        ApiReference = [string]$containers[0].image
        SourceRevision = [string]$annotations.'br.com.urbana.connect/source-revision'
    }
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

function Get-HmlPlatformLocalNodeImageManifestDigests {
    $config = Get-HmlPlatformLocalConfig
    $result = Invoke-ExternalText -Command 'docker' -Arguments @(
        'exec', $config.K3dServerContainer, '/bin/ctr', '-n', 'k8s.io', 'images', 'ls')
    $digests = @{}
    foreach ($line in ($result.Output -split "`r?`n")) {
        $columns = [regex]::Split($line.Trim(), '\s+')
        if ($columns.Count -ge 3 -and $columns[0] -ne 'REF' -and
            $columns[2] -match '^sha256:[0-9a-f]{64}$') {
            $digests[$columns[0]] = $columns[2]
        }
    }
    return $digests
}

function Get-HmlPlatformLocalImportedImageEvidence {
    $config = Get-HmlPlatformLocalConfig
    $references = Get-HmlPlatformLocalNodeImages
    $nodeDigests = Get-HmlPlatformLocalNodeImageManifestDigests
    $imageEvidence = [ordered]@{}
    foreach ($reference in @($config.ApiImage, $config.MongoImage)) {
        if ($reference -notin $references) {
            Throw-HmlPlatformLocalError 'IMAGE' "imagem esperada ausente no node k3d: $reference"
        }
        $localEvidence = Get-HmlPlatformLocalImageEvidence -Reference $reference
        if (-not $nodeDigests.ContainsKey($reference)) {
            Throw-HmlPlatformLocalError 'IMAGE' "digest OCI da referencia importada nao foi lido no node: $reference"
        }
        $nodeDigest = [string]$nodeDigests[$reference]
        $allowedDigests = @($localEvidence.Digests | ForEach-Object { ([string]$_ -replace '^.*@', '') })
        if ($reference -match '@(sha256:[0-9a-f]{64})$') {
            $allowedDigests = @($Matches[1])
        }
        if ($nodeDigest -notin $allowedDigests) {
            $expected = if ($allowedDigests.Count -gt 0) { [string]$allowedDigests[0] } else { '' }
            Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest $expected -ActualDigest $nodeDigest
        }
        $imageEvidence[$reference] = @{ digest = $nodeDigest }
    }
    $apiDigest = [string]$imageEvidence[$config.ApiImage].digest
    $apiLocalEvidence = Get-HmlPlatformLocalImageEvidence -Reference $config.ApiImage
    $apiReference = Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $apiLocalEvidence -ImportedDigest $apiDigest
    if (-not $nodeDigests.ContainsKey($apiReference)) {
        Throw-HmlPlatformLocalError 'IMAGE' "alias imutavel ausente no node k3d: $apiReference"
    }
    Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest $apiDigest -ActualDigest ([string]$nodeDigests[$apiReference])
    $imageEvidence[$apiReference] = @{ digest = [string]$nodeDigests[$apiReference]; sourceReference = $config.ApiImage }
    [pscustomobject]@{ References = $references; Digests = $imageEvidence; ApiReference = $apiReference }
}

function Import-HmlPlatformLocalImages {
    $config = Get-HmlPlatformLocalConfig
    $apiEvidence = Get-HmlPlatformLocalImageEvidence -Reference $config.ApiImage
    $revision = Get-HmlPlatformLocalGitRevision
    $dirty = Get-HmlPlatformLocalWorktreeDirty
    Assert-HmlPlatformLocalPromotionEvidence -SourceDirty $dirty -ExpectedRevision $revision -ImageEvidence $apiEvidence
    $mongoImage = Ensure-HmlPlatformLocalPinnedImage -Reference $config.MongoImage
    $mongoEvidence = $mongoImage.Evidence
    Invoke-ExternalText -Command 'k3d' -Arguments @(
        'image', 'import', '--cluster', $config.ClusterName, $config.ApiImage, $config.MongoImage) | Out-Null
    $nodeDigests = Get-HmlPlatformLocalNodeImageManifestDigests
    if (-not $nodeDigests.ContainsKey($config.ApiImage)) {
        Throw-HmlPlatformLocalError 'IMAGE' "imagem API ausente no containerd apos import: $($config.ApiImage)"
    }
    $apiReference = Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $apiEvidence `
        -ImportedDigest ([string]$nodeDigests[$config.ApiImage])
    Ensure-HmlPlatformLocalNodeImageDigestAlias -SourceReference $config.ApiImage `
        -TargetReference $apiReference -ExpectedDigest ([string]$nodeDigests[$config.ApiImage])
    $imported = Get-HmlPlatformLocalImportedImageEvidence
    return [pscustomobject]@{ Api = $apiEvidence; Mongo = $mongoEvidence; MongoPulled = $mongoImage.Pulled; NodeImages = $imported.References; ApiReference = $imported.ApiReference; ImportedDigests = $imported.Digests }
}

function Assert-HmlPlatformLocalImagesImported {
    return (Get-HmlPlatformLocalImportedImageEvidence).References
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

function Assert-HmlPlatformLocalImageReferences {
    param([Parameter(Mandatory = $true)][string]$Text,
          [string]$ExpectedApiReference,
          [string]$SourceRevision,
          [switch]$AllowLocalApiTemplate)
    $config = Get-HmlPlatformLocalConfig
    $apiRepository = [regex]::Replace([string]$config.ApiImage, ':[^/:@]+$', '')
    $references = @([regex]::Matches($Text, '(?m)^[ \t]*image:[ \t]*(?<reference>\S+)[ \t]*[\x0d]?$') |
        ForEach-Object { [string]$_.Groups['reference'].Value })
    if ($references.Count -eq 0) {
        Throw-HmlPlatformLocalError 'IMAGE' 'nenhuma imagem foi declarada nos manifests do target.'
    }
    if ($ExpectedApiReference -and $ExpectedApiReference -notmatch ('^' + [regex]::Escape($apiRepository) + '@sha256:[0-9a-f]{64}$')) {
        Throw-HmlPlatformLocalError 'IMAGE' 'referencia API esperada deve ser repo@digest OCI, sem tag.'
    }
    if ($SourceRevision -and $SourceRevision -notmatch '^[0-9a-f]{40}$') {
        Throw-HmlPlatformLocalError 'SOURCE' 'revisao da origem deve ser um commit Git completo.'
    }
    $apiReferenceCount = 0
    $apiTemplateCount = 0
    foreach ($reference in $references) {
        if ($reference -match '(^|/)latest(@|$)|:latest(@|$)') {
            Throw-HmlPlatformLocalError 'IMAGE' "referencia latest proibida: $reference"
        }
        $referenceParts = $reference -split '@', 2
        $repository = [regex]::Replace([string]$referenceParts[0], ':[^/:]+$', '')
        $hasDigest = $referenceParts.Count -eq 2 -and $referenceParts[1] -match '^sha256:[0-9a-f]{64}$'
        if ($repository -ceq $apiRepository) {
            if ($AllowLocalApiTemplate -and $reference -ceq $config.ApiImage) {
                $apiTemplateCount++
                continue
            }
            if (-not $hasDigest -or $reference -cnotmatch ('^' + [regex]::Escape($apiRepository) + '@sha256:[0-9a-f]{64}$')) {
                Throw-HmlPlatformLocalError 'IMAGE' "imagem API deve usar referencia imutavel repo@sha256, nao tag local: $reference"
            }
            if ($ExpectedApiReference -and $reference -cne $ExpectedApiReference) {
                Throw-HmlPlatformLocalError 'IMAGE' 'manifesto API nao corresponde ao digest OCI aprovado para esta promocao.'
            }
            $apiReferenceCount++
        } elseif (-not $hasDigest) {
            Throw-HmlPlatformLocalError 'IMAGE' "imagem sem identidade imutavel por digest: $reference"
        }
    }
    if (($apiReferenceCount + $apiTemplateCount) -ne 1) {
        Throw-HmlPlatformLocalError 'IMAGE' 'manifesto deve conter exatamente uma referencia da API (template ou repo@digest).'
    }
    if ($SourceRevision) {
        $annotation = '(?m)^\s*br\.com\.urbana\.connect/source-revision:\s*' + [regex]::Escape($SourceRevision) + '\s*$'
        if ($Text -notmatch $annotation) {
            Throw-HmlPlatformLocalError 'SOURCE' 'manifesto de promocao nao esta ligado a revisao limpa esperada.'
        }
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
    Assert-HmlPlatformLocalImageReferences -Text $text -AllowLocalApiTemplate
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
