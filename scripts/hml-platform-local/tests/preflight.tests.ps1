[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '..\common.ps1')
$config = Get-HmlPlatformLocalConfig
$infra = $config.InfraRoot
$scripts = Split-Path -Parent $PSScriptRoot
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "STATIC_HML_PLATFORM_LOCAL_TEST_FAILED: $Message" }
}
Assert-True (Test-Path -LiteralPath (Join-Path $infra 'kustomization.yaml')) 'kustomization ausente'
Assert-True (Test-Path -LiteralPath (Join-Path $infra 'cluster-config.yaml')) 'cluster-config ausente'
Assert-True (Test-Path -LiteralPath (Join-Path $scripts 'common.ps1')) 'common.ps1 ausente'
$config = Get-HmlPlatformLocalConfig
$repoPrefix = [IO.Path]::GetFullPath($config.RepoRoot) + [IO.Path]::DirectorySeparatorChar
$envPath = [IO.Path]::GetFullPath($config.EnvFile)
Assert-True (-not $envPath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) 'env dedicado nao pode ficar no repositorio'
$startText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'start.ps1')
$kubeconfigPattern = [regex]::Escape("'--kubeconfig'") + '\s+\$config\.KubeconfigPath'
Assert-True ($startText -match $kubeconfigPattern) 'secret nao usa kubeconfig dedicado'
$manifestFiles = @(Get-ChildItem -LiteralPath $infra -Filter '*.yaml' -File |
    Where-Object { $_.Name -ne 'cluster-config.yaml' })
$manifestText = ($manifestFiles | ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
$apiText = Get-Content -Raw -LiteralPath (Join-Path $infra 'api.yaml')
$configMapText = Get-Content -Raw -LiteralPath (Join-Path $infra 'configmap.yaml')
$commonText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'common.ps1')
$startText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'start.ps1')
$clusterScript = Get-Content -Raw -LiteralPath (Join-Path $scripts 'cluster.ps1')
$preflightText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'preflight.ps1')
$stopText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'stop.ps1')
$statusText = Get-Content -Raw -LiteralPath (Join-Path $scripts 'status.ps1')
$clusterConfigText = Get-Content -Raw -LiteralPath (Join-Path $infra 'cluster-config.yaml')
$applicationText = Get-Content -Raw -LiteralPath (Join-Path $config.RepoRoot 'apps/urbana-connect-api/src/main/resources/application.yml')
Assert-True ($manifestText -match 'environment:\s*hml-platform-local') 'identidade de environment ausente'
Assert-True ($manifestText -notmatch 'environment:\s*local\s*$') 'label environment generica encontrada'
Assert-True ($manifestText -match 'HERMES_POC_ENABLED:\s*"false"') 'Hermes nao esta deny-by-default'
Assert-True ($configMapText -match 'WEBHOOK_INBOX_WORKER_ENABLED:\s*"true"') 'inbox inbound-only nao esta habilitado'
Assert-True ($applicationText -match 'webhook:\s+inbox:\s+worker:' -and $applicationText -match 'WEBHOOK_INBOX_WORKER_ENABLED:false') 'flag WEBHOOK_INBOX_WORKER_ENABLED nao e consumida pela aplicacao'
Assert-True ($manifestText -notmatch 'local-hml|openrouter|graph.facebook.com|NodePort|LoadBalancer|hostNetwork:\s*true|hostPort:') 'referencia fora do target encontrada'
Assert-True ($manifestText -notmatch '(?m)^kind:\s*Secret\s*$') 'Secret literal versionada'
Assert-True ($configMapText -notmatch '(?m)^\s*MONGODB_URI\s*:') 'MONGODB_URI nao pode ficar no ConfigMap'
foreach ($key in @('MONGODB_URI', 'WHATSAPP_APP_SECRET', 'WHATSAPP_VERIFY_TOKEN')) {
    $secretRef = "name:\s*$key\s+valueFrom:\s+secretKeyRef:\s+name:\s*hml-platform-local-secrets\s+key:\s*$key"
    Assert-True ($apiText -match $secretRef) "$key nao esta ligado ao Secret dedicado"
}
Assert-True ($commonText -match 'WHATSAPP_VERIFY_TOKEN') 'verify token ausente do wiring do env dedicado'
Assert-True ($apiText -match 'image:\s*docker.io/library/urbana-connect-hml-platform-local-api:local') 'imagem API local ausente'
Assert-True ($apiText -match 'imagePullPolicy:\s*Never') 'imagem API nao esta explicitamente importada/local'
Assert-True ($apiText -match '(?s)startupProbe:.*path:\s*/api/v1/health') 'startup probe ausente/incorreto'
Assert-True ($apiText -match '(?s)readinessProbe:.*path:\s*/api/v1/readiness') 'readiness probe ausente/incorreto'
Assert-True ($apiText -match '(?s)livenessProbe:.*path:\s*/api/v1/health') 'liveness probe ausente/incorreto'
Assert-True ($manifestText -match '(?m)^kind:\s*NetworkPolicy\s*$') 'NetworkPolicy ausente'
Assert-True ($manifestText -match 'type: ClusterIP') 'Service publico ausente'
Assert-True ($commonText -match 'Build-HmlPlatformLocalApiImage') 'build deterministico da API ausente'
Assert-True ($commonText -match "'build', '--pull=false'") 'build da API deve declarar comportamento de pull'
Assert-True ($commonText -match '''pull'',\s+\$Reference') 'pull deve ser explicito e receber referencia fixada'
Assert-True ($commonText -match "'image',\s*'import'" -and $commonText -match '''exec'',\s+\$config\.K3dServerContainer') 'import/readback da imagem no node ausente'
Assert-True ($commonText -notmatch '--from-literal') 'segredo nao pode ser passado como argumento de processo'
Assert-True ($commonText -match 'RuntimeRoot\s*=\s*\$localDataRoot') 'evidencia runtime deve ficar fora do repositorio'
Assert-True ($startText -match 'Ensure-HmlPlatformLocalApiImage' -and $startText -match 'Import-HmlPlatformLocalImages' -and $startText -match 'rollout.*restart') 'start nao reprovisiona imagens ou reinicia a API'
$guardCall = $commonText.IndexOf('Assert-HmlPlatformLocalCliVersion -Name $Name -CommandPath $path')
Assert-True ($guardCall -ge 0) 'Require-Command nao valida as versoes pinadas'
$startPrerequisites = $startText.IndexOf("Require-Command 'kubectl'")
$startBuild = $startText.IndexOf('Ensure-HmlPlatformLocalApiImage')
Assert-True ($startPrerequisites -ge 0 -and $startBuild -gt $startPrerequisites) 'start deve validar as ferramentas antes de build/import/apply'
$clusterPrerequisites = $clusterScript.IndexOf("Require-Command 'kubectl'")
$clusterCreate = $clusterScript.IndexOf("'cluster', 'create'")
Assert-True ($clusterPrerequisites -ge 0 -and $clusterCreate -gt $clusterPrerequisites) 'cluster create deve validar as ferramentas antes de criar o cluster'
$preflightPrerequisites = $preflightText.IndexOf("Require-Command 'kubectl'")
$preflightRender = $preflightText.IndexOf("Invoke-ExternalText -Command 'kubectl' -Arguments @('kustomize'")
Assert-True ($preflightPrerequisites -ge 0 -and $preflightRender -gt $preflightPrerequisites) 'preflight runtime deve validar as ferramentas antes de usar kubectl'
Assert-True ($stopText -notmatch '\$config\.InfraRoot') 'stop nao pode apagar o kustomization inteiro'
Assert-True ($stopText -match 'deployment/urbana-connect' -and $stopText -match 'statefulset/mongodb' -and $stopText -match 'job/mongodb-rs-init') 'stop nao remove explicitamente apenas os workloads esperados'
Assert-True ($stopText -match "get',\s*'namespace" -and $stopText -match "get',\s*'pvc") 'stop nao confirma namespace e PVC preservados'
Assert-True ($statusText -match "Write-HmlPlatformLocalEvidence\s+-Operation\s+'status'" -and $statusText -match '\$imageEvidence' -and $statusText -match '\$state' -and $statusText -match '\$apiReadiness') 'status nao persiste readback de estado, readiness e imagens'
Assert-True ($statusText -match 'Get-HmlPlatformLocalImportedImageEvidence' -and $statusText -match 'importedImageDigests' -and $statusText -match 'environmentKeys') 'status nao persiste digest importado e chaves sanitizadas'
Assert-True ($commonText -match 'Assert-HmlPlatformLocalPromotionSourceClean' -and $commonText -match 'Assert-HmlPlatformLocalPromotionEvidence') 'promocao deve exigir origem limpa e proveniencia coerente'
Assert-True ($commonText -match 'New-HmlPlatformLocalPromotionManifest' -and $commonText -match 'Get-HmlPlatformLocalApiPromotionReference' -and $commonText -match 'Assert-HmlPlatformLocalAppliedPromotion') 'promocao deve renderizar e confirmar imagem imutavel vinculada a revisao'
Assert-True ($startText.Contains("Invoke-Kubectl -Arguments @('apply', '-f', `$promotion.Path)") -and $startText -match 'Assert-HmlPlatformLocalAppliedPromotion' -and -not $startText.Contains("'-k', `$config.InfraRoot")) 'start deve aplicar e confirmar manifesto de promocao renderizado por digest'
$startPromotionGuard = $startText.IndexOf('Assert-HmlPlatformLocalPromotionSourceClean')
$startBuild = $startText.IndexOf('Ensure-HmlPlatformLocalApiImage')
Assert-True ($startPromotionGuard -ge 0 -and $startPromotionGuard -lt $startBuild) 'start deve bloquear origem dirty antes do build/import'
$clusterPromotionGuard = $clusterScript.IndexOf('Assert-HmlPlatformLocalPromotionSourceClean')
$clusterCreate = $clusterScript.IndexOf("'cluster', 'create'")
Assert-True ($clusterPromotionGuard -ge 0 -and $clusterPromotionGuard -lt $clusterCreate) 'cluster create deve bloquear origem dirty antes do provisionamento'
$clusterImagePull = $clusterScript.IndexOf('Ensure-HmlPlatformLocalPinnedImage')
Assert-True ($clusterImagePull -ge 0 -and $clusterImagePull -lt $clusterCreate) 'cluster create deve preparar as imagens pinadas por pull explicito antes do provisionamento'
$importFunction = $commonText.IndexOf('function Import-HmlPlatformLocalImages')
$imageImport = $commonText.IndexOf("'image', 'import'", $importFunction)
$importPromotionGuard = $commonText.IndexOf('Assert-HmlPlatformLocalPromotionEvidence', $importFunction)
Assert-True ($importPromotionGuard -ge 0 -and $importPromotionGuard -lt $imageImport) 'importacao deve validar identidade antes do k3d image import'
$cluster = $clusterConfigText
Assert-True ($cluster -match 'name: urbana-hml-platform-local') 'cluster dedicado ausente'
Assert-True ('urbana-hml-platform-local'.Length -le 32) 'nome do cluster excede limite k3d'
Assert-True ($cluster -match 'hostIP: 127\.0\.0\.1') 'API k3d fora de loopback'
Assert-True ($cluster -match 'updateDefaultKubeconfig: false') 'kubeconfig default pode ser alterado'
Assert-True ($cluster -match 'switchCurrentContext: false') 'contexto default pode ser alterado'
$toolVersions = Get-HmlPlatformLocalExpectedToolVersions
Assert-True ($toolVersions.k3s.version -ceq 'v1.36.4+k3s1') 'versao K3s deve estar fixada no registro de ferramentas'
Assert-True ($toolVersions.k3s.image -match '^docker\.io/rancher/k3s:v1\.36\.4-k3s1@sha256:[0-9a-f]{64}$') 'imagem K3s deve estar fixada por digest imutavel'
$clusterImage = [regex]::Match($cluster, '(?m)^image:\s*(?<image>\S+)\s*$')
Assert-True ($clusterImage.Success -and $clusterImage.Groups['image'].Value -ceq $toolVersions.k3s.image) 'cluster deve usar exatamente a imagem K3s registrada'
Assert-HmlPlatformLocalK3sVersionOutput -Output 'v1.36.4+k3s1' -Expected $toolVersions
Assert-HmlPlatformLocalCliVersionOutput -Name 'k3d' `
    -Output "k3d version $($toolVersions.k3d)`nk3s version v1.35.5-k3s1 (default)" -Expected $toolVersions
Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' `
    -Output "{`"clientVersion`":{`"gitVersion`":`"$($toolVersions.kubectl.gitVersion)`"},`"kustomizeVersion`":`"$($toolVersions.kubectl.kustomizeVersion)`"}" `
    -Expected $toolVersions
function Assert-VersionRejected([scriptblock]$Action, [string]$Message) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    Assert-True $rejected $Message
}
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'k3d' -Output '' -Expected $toolVersions } 'k3d sem versao deve falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'k3d' -Output 'k3d version v0.0.0' -Expected $toolVersions } 'k3d fora da versao deve falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' -Output '{}' -Expected $toolVersions } 'kubectl sem versao/Kustomize deve falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalCliVersionOutput -Name 'kubectl' -Output '{"clientVersion":{"gitVersion":"v0.0.0"},"kustomizeVersion":"v0.0.0"}' -Expected $toolVersions } 'kubectl/Kustomize fora das versoes devem falhar fechado'
Assert-VersionRejected { Assert-HmlPlatformLocalK3sVersionOutput -Output 'v1.35.5+k3s1' -Expected $toolVersions } 'K3s fora da versao fixada deve falhar fechado'
Assert-HmlPlatformLocalImageReferences -Text $manifestText -AllowLocalApiTemplate
Assert-VersionRejected { Assert-HmlPlatformLocalImageReferences -Text $manifestText } 'manifesto efetivamente promovido nao pode aceitar a imagem API :local'
Assert-VersionRejected { Assert-HmlPlatformLocalImageReferences -Text 'image: docker.io/example/api:latest' } 'tag latest deve ser rejeitada'
Assert-VersionRejected { Assert-HmlPlatformLocalImageReferences -Text 'image: docker.io/example/api:v1.2.3' } 'tag mutavel sem digest deve ser rejeitada'
Assert-VersionRejected { Ensure-HmlPlatformLocalPinnedImage -Reference 'docker.io/example/api:v1.2.3' } 'pull de imagem sem digest deve ser rejeitado'
$testRevision = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
$testImageEvidence = [pscustomobject]@{
    Revision = $testRevision
    WorktreeDirty = 'false'
    Id = 'sha256:' + ('c' * 64)
    Digests = @(([regex]::Replace($config.ApiImage, ':[^/:@]+$', '') + '@sha256:' + ('b' * 64)))
}
Assert-HmlPlatformLocalPromotionEvidence -SourceDirty 'false' -ExpectedRevision $testRevision -ImageEvidence $testImageEvidence
Assert-True ((Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $testImageEvidence -ImportedDigest ('sha256:' + ('b' * 64))) -ceq ([regex]::Replace($config.ApiImage, ':[^/:@]+$', '') + '@sha256:' + ('b' * 64))) 'referencia de promocao deve usar digest do containerd correspondente ao RepoDigest'
$observedApiDigest = 'sha256:3a6491a5aabef0bc6164a95f0a1e90427dd27be37e33678ae4bc34fc4eea9fad'
$familiarApiEvidence = [pscustomobject]@{ Digests = @('urbana-connect-hml-platform-local-api@' + $observedApiDigest) }
$canonicalApiEvidence = [pscustomobject]@{ Digests = @(([regex]::Replace($config.ApiImage, ':[^/:@]+$', '') + '@' + $observedApiDigest)) }
$expectedCanonicalApiReference = [regex]::Replace($config.ApiImage, ':[^/:@]+$', '') + '@' + $observedApiDigest
Assert-True ((Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $familiarApiEvidence -ImportedDigest $observedApiDigest) -ceq $expectedCanonicalApiReference) 'RepoDigest Docker familiar deve normalizar para o repositorio canonico esperado'
Assert-True ((Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $canonicalApiEvidence -ImportedDigest $observedApiDigest) -ceq $expectedCanonicalApiReference) 'RepoDigest canonico deve permanecer canonico'
Assert-VersionRejected { Get-HmlPlatformLocalApiPromotionReference -ImageEvidence ([pscustomobject]@{ Digests = @('docker.io/library/other-api@' + $observedApiDigest) }) -ImportedDigest $observedApiDigest } 'RepoDigest de outro repositorio deve ser rejeitado'
Assert-VersionRejected { Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $familiarApiEvidence -ImportedDigest ('sha256:' + ('d' * 64)) } 'RepoDigest familiar com digest divergente deve ser rejeitado'
Assert-VersionRejected { Get-HmlPlatformLocalApiPromotionReference -ImageEvidence ([pscustomobject]@{ Id = $testImageEvidence.Id; Digests = @() }) -ImportedDigest ('sha256:' + ('b' * 64)) } 'imagem sem RepoDigests deve falhar mesmo quando houver Docker config ID'
Assert-VersionRejected { Get-HmlPlatformLocalApiPromotionReference -ImageEvidence $testImageEvidence -ImportedDigest ('sha256:' + ('d' * 64)) } 'digest do containerd divergente do RepoDigest deve ser rejeitado'
$apiRepository = [regex]::Replace($config.ApiImage, ':[^/:@]+$', '')
$expectedApiReference = $apiRepository + '@sha256:' + ('b' * 64)
$promotionManifest = @"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: urbana-connect
spec:
  template:
    metadata:
      annotations:
        br.com.urbana.connect/source-revision: $testRevision
    spec:
      containers:
        - name: urbana-connect
          image: $expectedApiReference
          imagePullPolicy: Never
          env:
            - name: AUX_IMAGE
              value: mongo
      - image: docker.io/library/mongo:8.0@sha256:$('e' * 64)
"@
Assert-HmlPlatformLocalImageReferences -Text $promotionManifest -ExpectedApiReference $expectedApiReference -SourceRevision $testRevision
Assert-VersionRejected { Assert-HmlPlatformLocalImageReferences -Text $promotionManifest -ExpectedApiReference ($apiRepository + '@sha256:' + ('f' * 64)) -SourceRevision $testRevision } 'digest renderizado diferente do OCI importado deve ser rejeitado'
Assert-VersionRejected { Assert-HmlPlatformLocalImageReferences -Text ($promotionManifest -replace [regex]::Escape($expectedApiReference), $config.ApiImage) -SourceRevision $testRevision } 'referencia de template :local nao pode ser aplicada como promocao'
Assert-VersionRejected { Assert-HmlPlatformLocalImageReferences -Text ($promotionManifest -replace [regex]::Escape($testRevision), ('f' * 40)) -ExpectedApiReference $expectedApiReference -SourceRevision $testRevision } 'manifesto sem vinculo para a revisao limpa deve ser rejeitado'
Assert-VersionRejected { Assert-HmlPlatformLocalPromotionEvidence -SourceDirty 'true' -ExpectedRevision $testRevision -ImageEvidence $testImageEvidence } 'origem dirty deve ser rejeitada antes de promover imagem'
Assert-VersionRejected { Assert-HmlPlatformLocalPromotionEvidence -SourceDirty 'false' -ExpectedRevision $testRevision -ImageEvidence ([pscustomobject]@{ Revision = $testRevision; WorktreeDirty = 'true'; Digests = $testImageEvidence.Digests }) } 'imagem buildada de origem dirty deve ser rejeitada'
Assert-VersionRejected { Assert-HmlPlatformLocalPromotionEvidence -SourceDirty 'false' -ExpectedRevision $testRevision -ImageEvidence ([pscustomobject]@{ Revision = ('c' * 40); WorktreeDirty = 'false'; Digests = $testImageEvidence.Digests }) } 'imagem de outra revisao deve ser rejeitada'
Assert-VersionRejected { Assert-HmlPlatformLocalPromotionEvidence -SourceDirty 'false' -ExpectedRevision $testRevision -ImageEvidence ([pscustomobject]@{ Revision = $testRevision; WorktreeDirty = 'false'; Digests = @() }) } 'imagem local sem identidade por digest deve ser rejeitada'
Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest ('sha256:' + ('b' * 64)) -ActualDigest ('sha256:' + ('b' * 64))
Assert-VersionRejected { Assert-HmlPlatformLocalImportedImageDigest -ExpectedDigest ('sha256:' + ('b' * 64)) -ActualDigest ('sha256:' + ('c' * 64)) } 'digest importado divergente deve ser rejeitado'
Write-Output 'STATIC_HML_PLATFORM_LOCAL_TESTS_OK'
