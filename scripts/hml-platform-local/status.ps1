[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'common.ps1')
$config = Get-HmlPlatformLocalConfig
Require-Command 'kubectl'
Assert-LocalKubeContext

function Get-HmlPlatformLocalJsonResource {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $result = Invoke-Kubectl -Arguments $Arguments -AllowFailure
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Output)) {
        return $null
    }
    try {
        return ($result.Output | ConvertFrom-Json)
    } catch {
        Throw-HmlPlatformLocalError 'STATUS' 'readback JSON invalido.'
    }
}

function Get-HmlPlatformLocalReadyCondition {
    param($Resource)
    if ($null -eq $Resource) { return $false }
    return (@($Resource.status.conditions |
        Where-Object { $_.type -eq 'Ready' -and $_.status -eq 'True' }).Count -gt 0)
}

function Get-HmlPlatformLocalInt {
    param($Value)
    if ($null -eq $Value) { return 0 }
    return [int]$Value
}

function Get-HmlPlatformLocalReplicaReadiness {
    param($Resource)
    if ($null -eq $Resource) { return 'absent' }
    $desired = Get-HmlPlatformLocalInt $Resource.spec.replicas
    $ready = Get-HmlPlatformLocalInt $Resource.status.readyReplicas
    if ($desired -gt 0 -and $desired -eq $ready) { return 'ready' }
    return 'not-ready'
}

function Get-HmlPlatformLocalJobReadiness {
    param($Resource)
    if ($null -eq $Resource) { return 'absent' }
    if (@($Resource.status.conditions |
        Where-Object { $_.type -eq 'Complete' -and $_.status -eq 'True' }).Count -gt 0) {
        return 'complete'
    }
    if (@($Resource.status.conditions |
        Where-Object { $_.type -eq 'Failed' -and $_.status -eq 'True' }).Count -gt 0) {
        return 'failed'
    }
    return 'pending'
}

$namespace = Get-HmlPlatformLocalJsonResource @('get', 'namespace', $config.Namespace, '-o', 'json')
if ($null -eq $namespace) {
    Throw-HmlPlatformLocalError 'STATUS' 'namespace dedicado ausente.'
}
$nodes = Get-HmlPlatformLocalJsonResource @('get', 'nodes', '-o', 'json')
if ($null -eq $nodes) {
    Throw-HmlPlatformLocalError 'STATUS' 'readback de nodes falhou.'
}
$api = Get-HmlPlatformLocalJsonResource @('-n', $config.Namespace, 'get', 'deployment/urbana-connect', '-o', 'json')
$mongodb = Get-HmlPlatformLocalJsonResource @('-n', $config.Namespace, 'get', 'statefulset/mongodb', '-o', 'json')
$initJob = Get-HmlPlatformLocalJsonResource @('-n', $config.Namespace, 'get', 'job/mongodb-rs-init', '-o', 'json')
$pods = Get-HmlPlatformLocalJsonResource @('-n', $config.Namespace, 'get', 'pods', '-o', 'json')
$services = Get-HmlPlatformLocalJsonResource @('-n', $config.Namespace, 'get', 'services', '-o', 'json')
$pvcs = Get-HmlPlatformLocalJsonResource @('-n', $config.Namespace, 'get', 'pvc', '-o', 'json')
$envValues = Read-LocalEnv

$apiReadiness = Get-HmlPlatformLocalReplicaReadiness $api
$mongodbReadiness = Get-HmlPlatformLocalReplicaReadiness $mongodb
$jobReadiness = Get-HmlPlatformLocalJobReadiness $initJob
$state = if ($null -eq $api -and $null -eq $mongodb) {
    'stopped'
} elseif ($apiReadiness -eq 'ready' -and $mongodbReadiness -eq 'ready') {
    'running'
} else {
    'degraded'
}

$nodeItems = @($nodes.items)
$nodeSummary = @($nodeItems | ForEach-Object {
    @{
        name = [string]$_.metadata.name
        ready = Get-HmlPlatformLocalReadyCondition $_
    }
})
$podItems = if ($null -eq $pods) { @() } else { @($pods.items) }
$podSummary = @($podItems | ForEach-Object {
    $restartCount = 0
    foreach ($container in @($_.status.containerStatuses)) {
        $restartCount += Get-HmlPlatformLocalInt $container.restartCount
    }
    @{
        name = [string]$_.metadata.name
        phase = [string]$_.status.phase
        ready = Get-HmlPlatformLocalReadyCondition $_
        restartCount = $restartCount
    }
})
$serviceItems = if ($null -eq $services) { @() } else { @($services.items) }
$serviceSummary = @($serviceItems | ForEach-Object {
    @{
        name = [string]$_.metadata.name
        type = [string]$_.spec.type
        clusterIP = [string]$_.spec.clusterIP
    }
})
$pvcItems = if ($null -eq $pvcs) { @() } else { @($pvcs.items) }
$pvcSummary = @($pvcItems | ForEach-Object {
    @{
        name = [string]$_.metadata.name
        phase = [string]$_.status.phase
        capacity = [string]$_.status.capacity.storage
    }
})

$imageEvidence = @{}
foreach ($image in @($config.ApiImage, $config.MongoImage)) {
    $evidence = Get-HmlPlatformLocalImageEvidence -Reference $image
    $imageEvidence[$image] = @{
        id = $evidence.Id
        digests = @($evidence.Digests)
        revision = $evidence.Revision
        worktreeDirty = $evidence.WorktreeDirty
    }
}
$nodeImages = Assert-HmlPlatformLocalImagesImported

$inventory = Invoke-Kubectl -Arguments @('-n', $config.Namespace, 'get', 'pods,svc,pvc', '-o', 'wide')
Write-Output $inventory.Output
Write-HmlPlatformLocalEvidence -Operation 'status' -Data @{
    target = 'hml-platform-local'
    context = $config.ContextName
    namespace = @{
        name = [string]$namespace.metadata.name
        phase = [string]$namespace.status.phase
    }
    state = $state
    readiness = @{
        api = $apiReadiness
        mongodb = $mongodbReadiness
        initJob = $jobReadiness
    }
    nodes = @{
        count = $nodeItems.Count
        ready = @($nodeSummary | Where-Object { $_.ready }).Count
        items = $nodeSummary
    }
    workloads = @{
        api = @{
            present = ($null -ne $api)
            desiredReplicas = Get-HmlPlatformLocalInt $api.spec.replicas
            readyReplicas = Get-HmlPlatformLocalInt $api.status.readyReplicas
        }
        mongodb = @{
            present = ($null -ne $mongodb)
            desiredReplicas = Get-HmlPlatformLocalInt $mongodb.spec.replicas
            readyReplicas = Get-HmlPlatformLocalInt $mongodb.status.readyReplicas
        }
        initJob = @{
            present = ($null -ne $initJob)
            readiness = $jobReadiness
        }
    }
    pods = $podSummary
    services = $serviceSummary
    pvcs = $pvcSummary
    images = $imageEvidence
    importedImageCount = @($nodeImages).Count
    endpoints = 'loopback-only'
    outbound = 'disabled'
    environmentKeys = @($envValues.Keys)
    secretEvidence = 'keys-only; values-not-recorded'
}
