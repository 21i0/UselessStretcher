$ErrorActionPreference = 'Stop'
$taskEvidence = Join-Path $PSScriptRoot 'negative-control'
if (Test-Path -LiteralPath $taskEvidence) { throw 'Negative-control evidence already exists; refusing to overwrite.' }
New-Item -ItemType Directory -Path $taskEvidence | Out-Null
$taskRecordPath = Join-Path $PSScriptRoot 'build-result.json'
$taskRecord = Get-Content -LiteralPath $taskRecordPath -Raw | ConvertFrom-Json
$taskOriginalArgs = @(Get-Content -LiteralPath $taskRecord.javaArguments | ForEach-Object { $_.Trim('"') })
$taskCpIndex = [Array]::IndexOf($taskOriginalArgs, '-cp') + 1
if ($taskCpIndex -eq 0) { throw 'Original java argument file has no classpath.' }
$taskCp = $taskOriginalArgs[$taskCpIndex]
$taskNegativeClasses = Join-Path $taskEvidence 'red-classes'
$taskNegativeTemp = Join-Path $taskEvidence 'tmp'
New-Item -ItemType Directory -Path $taskNegativeClasses,$taskNegativeTemp | Out-Null
$taskSource = Join-Path $taskEvidence 'InstalledStretcherPlacementGuard.java'
@'
package com.sorrowmist.useless.stretcher.content.ae;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
/** Test-only negative control, equivalent to the previous offered-stretcher-only hook. */
public final class InstalledStretcherPlacementGuard {
    private InstalledStretcherPlacementGuard() {}
    public static Boolean mayPlace(SlotItemHandler slot, ItemStack offered) {
        if (InstalledStretcherPatterns.isStretcher(offered)) {
            return slot.getItemHandler().isItemValid(slot.getSlotIndex(), offered);
        }
        return null;
    }
}
'@ | Set-Content -LiteralPath $taskSource -Encoding utf8
function Write-Args([string]$path, [string[]]$values) {
    [IO.File]::WriteAllLines($path, @($values | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
}
$taskProtected = @($taskRecord.jar,$taskRecordPath,(Join-Path $PSScriptRoot 'unit-tests.log')) + @($taskRecord.mainSources.path)
$taskHashesBefore = @($taskProtected | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
$taskCompileArgs = Join-Path $taskEvidence 'javac-negative-control.args'
Write-Args $taskCompileArgs @('-proc:none','--release','21','-encoding','UTF-8','-d',$taskNegativeClasses,'-cp',$taskCp,$taskSource)
& javac "-J-Djava.io.tmpdir=$taskNegativeTemp" "@$taskCompileArgs" *> (Join-Path $taskEvidence 'compile-negative-control.log')
if ($LASTEXITCODE -ne 0) { throw 'Negative control compilation failed; see independent log.' }
$taskRunArgs = @($taskOriginalArgs)
$taskRunArgs[$taskCpIndex] = $taskNegativeClasses + ';' + $taskCp
for ($taskIndex = 0; $taskIndex -lt $taskRunArgs.Count; $taskIndex++) {
    if ($taskRunArgs[$taskIndex].StartsWith('-Djava.io.tmpdir=')) { $taskRunArgs[$taskIndex] = '-Djava.io.tmpdir=' + $taskNegativeTemp }
}
$taskRunPath = Join-Path $taskEvidence 'java-negative-control.args'
Write-Args $taskRunPath $taskRunArgs
& java "@$taskRunPath" *> (Join-Path $taskEvidence 'negative-control.log')
$taskExit = $LASTEXITCODE
$taskHashesAfter = @($taskProtected | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
for ($taskIndex = 0; $taskIndex -lt $taskHashesBefore.Count; $taskIndex++) {
    if ($taskHashesBefore[$taskIndex].sha256 -cne $taskHashesAfter[$taskIndex].sha256) { throw "Protected input changed: $($taskHashesBefore[$taskIndex].path)" }
}
$taskLog = Get-Content -LiteralPath (Join-Path $taskEvidence 'negative-control.log') -Raw
$taskMetrics = [ordered]@{}
foreach ($taskMetric in @('tests found','tests started','tests successful','tests failed','containers failed')) {
    $taskMatch = [regex]::Match($taskLog, '(?m)^\[\s*(\d+)\s+' + [regex]::Escape($taskMetric) + '\s*\]\s*$')
    if (-not $taskMatch.Success) { throw "Missing summary metric: $taskMetric" }
    $taskMetrics[$taskMetric] = [int]$taskMatch.Groups[1].Value
}
$taskFailureNames = @([regex]::Matches($taskLog, 'JUnit Jupiter:InstalledStretcherPlacementGuardTest:([A-Za-z0-9_]+)\(\)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$taskExpected = @('occupiedAssemblyRejectsEncodedPatternBeforeMenuCanExchangeIt','nativePickupSwapWithGuardConservesBothItemsAndLockedUuid','nativeHotbarSwapWithGuardConservesBothItemsAndLockedUuid') | Sort-Object
$taskPass = $taskExit -ne 0 -and $taskMetrics['tests found'] -eq 184 -and $taskMetrics['tests started'] -eq 184 -and $taskMetrics['tests failed'] -eq 3 -and $taskMetrics['tests successful'] -eq 181 -and $taskMetrics['containers failed'] -eq 0 -and (($taskFailureNames -join '|') -ceq ($taskExpected -join '|'))
$taskReport = [ordered]@{
    status = $(if ($taskPass) {'EXPECTED_FAILURES_CONFIRMED'} else {'UNEXPECTED_RESULT'})
    gameLaunched = $false
    scope = 'Test-only old-behavior helper first on classpath; production source/JAR/frozen records unchanged.'
    processExitCode = $taskExit
    metrics = $taskMetrics
    expectedFailureNames = $taskExpected
    actualFailureNames = $taskFailureNames
    old175TestsPassed = $taskPass
    productionHelperSource = $taskRecord.mainSources[0].path
    negativeControlSource = $taskSource
    negativeControlSourceSha256 = (Get-FileHash -LiteralPath $taskSource -Algorithm SHA256).Hash
    negativeControlClassSha256 = (Get-FileHash -LiteralPath (Join-Path $taskNegativeClasses 'com\sorrowmist\useless\stretcher\content\ae\InstalledStretcherPlacementGuard.class') -Algorithm SHA256).Hash
    logPath = (Join-Path $taskEvidence 'negative-control.log')
    logSha256 = (Get-FileHash -LiteralPath (Join-Path $taskEvidence 'negative-control.log') -Algorithm SHA256).Hash
    protectedBefore = $taskHashesBefore
    protectedAfter = $taskHashesAfter
}
$taskReport | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskEvidence 'negative-control-result.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $taskEvidence 'negative-control.log') -Tail 65
if (-not $taskPass) { throw 'Negative control did not fail exactly the three expected placement/swap tests.' }
