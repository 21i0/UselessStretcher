$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskBuild = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'build-result.json') -Raw | ConvertFrom-Json
$taskAuditPath = Join-Path $PSScriptRoot 'audit-runs\20261106-221658679\audit-patch-report.json'
$taskAudit = Get-Content -LiteralPath $taskAuditPath -Raw | ConvertFrom-Json
if ($taskAudit.status -ne 'PASS') { throw 'Candidate audit did not pass' }
if ((Get-FileHash -LiteralPath $taskBuild.jar -Algorithm SHA256).Hash -ne $taskBuild.sha256) { throw 'Candidate changed after build' }
foreach ($taskFile in @($taskBuild.mainSources) + @($taskBuild.testSources)) {
    if ((Get-FileHash -LiteralPath $taskFile.path -Algorithm SHA256).Hash -ne $taskFile.sha256) { throw "Source changed after build: $($taskFile.path)" }
}
if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Algorithm SHA256).Hash -ne $taskBuild.testLogSha256) { throw 'Test log changed' }
$taskTestLog = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Raw
if ($taskTestLog -notmatch '\[\s*101 tests successful\s*\]' -or $taskTestLog -notmatch '\[\s*0 tests failed\s*\]') { throw 'Final 101-test passing summary missing' }
$taskCandidateZip = [IO.Compression.ZipFile]::OpenRead($taskBuild.jar)
$taskBaselineZip = [IO.Compression.ZipFile]::OpenRead($taskAudit.paths.baselineP1)
function Get-EntryHash($zip, [string]$name) {
    $taskEntry = $zip.GetEntry($name)
    if (-not $taskEntry) { throw "Missing ZIP member $name" }
    $taskStream = $taskEntry.Open()
    $taskHasher = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($taskHasher.ComputeHash($taskStream)) }
    finally { $taskStream.Dispose(); $taskHasher.Dispose() }
}
try {
    $taskCompiled = @(Get-ChildItem -LiteralPath $taskBuild.classesDirectory -Filter '*.class' -Recurse | ForEach-Object {
        $taskName = $_.FullName.Substring($taskBuild.classesDirectory.Length + 1).Replace('\','/')
        $taskHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        if ((Get-EntryHash $taskCandidateZip $taskName) -ne $taskHash) { throw "Packaged class differs from tested compilation: $taskName" }
        [ordered]@{name=$taskName; sha256=$taskHash}
    })
    $taskProtected = @($taskBaselineZip.Entries | Where-Object FullName -Match '^com/sorrowmist/useless/stretcher/(content/mold/MyriadPatternStore|content/ae/InstalledStretcher|content/ae/InstalledPatternHost|mixin/InstalledStretcherAssemblyMixin).*\.class$' | ForEach-Object {
        $taskHash = Get-EntryHash $taskBaselineZip $_.FullName
        if ((Get-EntryHash $taskCandidateZip $_.FullName) -ne $taskHash) { throw "UUID/snapshot byte changed: $($_.FullName)" }
        [ordered]@{name=$_.FullName; sha256=$taskHash; unchanged=$true}
    })
    if ($taskProtected.Count -lt 5) { throw 'Expected UUID and snapshot class family missing' }
} finally { $taskCandidateZip.Dispose(); $taskBaselineZip.Dispose() }
$taskFrozen = [ordered]@{
    status='FROZEN_OFFLINE_CHECKS_PASSED'; timestampUtc=[DateTime]::UtcNow.ToString('o'); gameLaunched=$false
    jar=$taskBuild.jar; size=$taskBuild.size; sha256=$taskBuild.sha256; version=$taskAudit.version
    baselineSha256=$taskBuild.baselineSha256; entryCounts=$taskAudit.entryCounts
    tests=[ordered]@{found=101; successful=101; failed=0; logSha256=$taskBuild.testLogSha256}
    mixinAbiChecks=@($taskAudit.mixinChecks).Count; versionGateChecks=@($taskAudit.versionGateChecks).Count
    compiledClasses=$taskCompiled; protectedUuidSnapshotClasses=$taskProtected
    performancePassed=$false; rFullTupleOracleAvailable=$false
    pending='S modpack wall-clock / maximum callback / final JEI / same-input full tuple validation'
    auditReport=$taskAuditPath
}
$taskDelivery = Split-Path -Parent $taskBuild.jar
$taskFrozen | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $taskDelivery 'P2-freeze-manifest.json') -Encoding utf8
[ordered]@{ baselineSha256=$taskBuild.baselineSha256; candidateSha256=$taskBuild.sha256; entryCounts=$taskAudit.entryCounts; added=$taskAudit.added; changed=$taskAudit.changed; removed=$taskAudit.removed } |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskDelivery 'P2-members-vs-P1.json') -Encoding utf8
[pscustomobject]$taskFrozen | Select-Object status,sha256,size,entryCounts,mixinAbiChecks,versionGateChecks,@{Name='protectedClassCount';Expression={$_.protectedUuidSnapshotClasses.Count}} | ConvertTo-Json -Depth 4
