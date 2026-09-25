[CmdletBinding()]
param(
    [switch]$Start,
    [switch]$Stop
)

if (@($Start, $Stop | Where-Object { $_ }).Count -ne 1) {
    throw 'Use exatamente uma acao: -Start ou -Stop.'
}
$root = $PSScriptRoot
if ($Start) { & (Join-Path $root 'start.ps1'); exit $LASTEXITCODE }
& (Join-Path $root 'stop.ps1'); exit $LASTEXITCODE
