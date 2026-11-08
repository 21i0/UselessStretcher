[CmdletBinding()]
param(
    # A passing report JSON or its audit-run directory; omission uses latest-pass.json.
    [string]$AuditReport,
    [string]$BuildResult = (Join-Path $PSScriptRoot 'build-result.json'),
    [string]$TestLog = (Join-Path $PSScriptRoot 'unit-tests.log'),
    [ValidateRange(0,2147483647)][int]$ExpectedTests = 0
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$expectedVersion = '1.21.1-1.6.4-catalog-P3'
$expectedBaseline = '161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4'
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path.TrimEnd('\')
if ([IO.Path]::GetPathRoot($workspace) -ine 'D:\') { throw 'Freeze outputs must stay on D:.' }

function Assert-InWorkspace([string]$Path, [string]$Purpose) {
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($script:workspace + '\',[StringComparison]::OrdinalIgnoreCase)) {
        throw "$Purpose must belong to this P3 candidate: $full"
    }
    return $full
}

function Get-FinalJUnitSummary([string]$Text) {
    $runs = [regex]::Matches($Text,'(?s)Test run finished after [^\r\n]+(?<summary>.*?)(?=Test run finished after|\z)')
    if ($runs.Count -eq 0) { throw 'No complete JUnit summary found.' }
    $last = $runs[$runs.Count-1].Groups['summary'].Value
    $values = [ordered]@{}
    $metrics = [ordered]@{
        found='tests found'; skipped='tests skipped'; started='tests started'
        aborted='tests aborted'; successful='tests successful'; failed='tests failed'
        containersAborted='containers aborted'; containersFailed='containers failed'
    }
    foreach ($key in $metrics.Keys) {
        $pattern = '(?m)^\[\s*(\d+)\s+' + [regex]::Escape($metrics[$key]) + '\s*\]\s*$'
        $matches = [regex]::Matches($last,$pattern)
        if ($matches.Count -ne 1) { throw "Missing or ambiguous final JUnit metric: $($metrics[$key])" }
        $values[$key] = [long]::Parse($matches[0].Groups[1].Value,[Globalization.CultureInfo]::InvariantCulture)
    }
    if ($values.found -le 0 -or $values.started -ne $values.found -or $values.successful -ne $values.started `
            -or $values.failed -ne 0 -or $values.skipped -ne 0 -or $values.aborted -ne 0 `
            -or $values.containersAborted -ne 0 -or $values.containersFailed -ne 0) {
        throw 'Final JUnit run did not execute and pass every discovered test.'
    }
    return [pscustomobject]$values
}

function Get-EntryHash($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if (-not $entry) { throw "Missing ZIP member: $Name" }
    $stream = $entry.Open(); $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($hasher.ComputeHash($stream)) }
    finally { $stream.Dispose(); $hasher.Dispose() }
}

$BuildResult = Assert-InWorkspace $BuildResult 'Build result'
$TestLog = Assert-InWorkspace $TestLog 'Test log'
$build = Get-Content -LiteralPath $BuildResult -Raw | ConvertFrom-Json
$jar = Assert-InWorkspace $build.jar 'Candidate JAR'
$classes = Assert-InWorkspace $build.classesDirectory 'Compiled classes'
$pointer = $null
if ([string]::IsNullOrWhiteSpace($AuditReport)) {
    $pointerPath = Join-Path $PSScriptRoot 'latest-pass.json'
    if (-not (Test-Path -LiteralPath $pointerPath -PathType Leaf)) { throw 'No latest passing audit. Run audit-patch.ps1 or supply -AuditReport.' }
    $pointer = Get-Content -LiteralPath $pointerPath -Raw | ConvertFrom-Json
    $AuditReport = $pointer.auditReport
}
if (Test-Path -LiteralPath $AuditReport -PathType Container) { $AuditReport = Join-Path $AuditReport 'audit-patch-report.json' }
$AuditReport = Assert-InWorkspace $AuditReport 'Audit report'
$auditReportHash = (Get-FileHash -LiteralPath $AuditReport -Algorithm SHA256).Hash
if ($pointer -and $auditReportHash -cne $pointer.auditReportSha256) { throw 'Latest-pass report changed after the audit.' }
$audit = Get-Content -LiteralPath $AuditReport -Raw | ConvertFrom-Json
if ($audit.status -cne 'PASS' -or $audit.version -cne $expectedVersion) { throw 'Expected a passing P3 audit.' }
if ($audit.hashesBefore.baselineP2 -cne $expectedBaseline -or $audit.hashesAfter.baselineP2 -cne $expectedBaseline `
        -or $build.baselineSha256 -cne $expectedBaseline) { throw 'Build/audit did not use the exact P2 baseline.' }
if ([IO.Path]::GetFullPath($audit.paths.candidate) -ine $jar) { throw 'Audit and build refer to different candidate files.' }
$jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
if ($jarHash -cne $build.sha256 -or $jarHash -cne $audit.hashesAfter.candidate -or $jarHash -cne $audit.hashesBefore.candidate) {
    throw 'Candidate changed after compilation or audit.'
}
if ((Get-Item -LiteralPath $jar).Length -ne $build.size) { throw 'Candidate size differs from the build record.' }
if ($pointer -and ($pointer.candidateSha256 -cne $jarHash -or $pointer.version -cne $expectedVersion)) { throw 'Latest-pass pointer belongs to another candidate.' }
foreach ($tool in $audit.auditTools) {
    $toolPath = Assert-InWorkspace $tool.path 'Audit tool'
    if ((Get-FileHash -LiteralPath $toolPath -Algorithm SHA256).Hash -cne $tool.sha256) { throw "Audit tool changed after PASS: $toolPath" }
}
if (@($audit.auditTools).Count -lt 2) { throw 'Passing audit omitted its tool hashes.' }
foreach ($source in @($build.mainSources) + @($build.testSources)) {
    $sourcePath = Assert-InWorkspace $source.path 'Compiled source'
    if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -cne $source.sha256) { throw "Source changed after tests: $sourcePath" }
}
$testLogHash = (Get-FileHash -LiteralPath $TestLog -Algorithm SHA256).Hash
if ($testLogHash -cne $build.testLogSha256) { throw 'Test log differs from the build record.' }
$tests = Get-FinalJUnitSummary (Get-Content -LiteralPath $TestLog -Raw)
if ($ExpectedTests -gt 0 -and $tests.found -ne $ExpectedTests) { throw "Expected $ExpectedTests tests, final summary found $($tests.found)." }
if (@($audit.mixinChecks).Count -ne 61 -or @($audit.memberAbiChecks).Count -ne 29 -or @($audit.versionGateChecks).Count -ne 32) {
    throw 'Expected the preserved 29 member ABI + 32 version-gate checks (61 total static rows).'
}
$baseline = Assert-InWorkspace $audit.paths.baselineP2 'Frozen P2 baseline'
if ((Get-FileHash -LiteralPath $baseline -Algorithm SHA256).Hash -cne $expectedBaseline) { throw 'Frozen P2 baseline changed.' }
$candidateZip = [IO.Compression.ZipFile]::OpenRead($jar)
$baselineZip = [IO.Compression.ZipFile]::OpenRead($baseline)
try {
    $compiled = @(Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse | ForEach-Object {
        $name = $_.FullName.Substring($classes.Length + 1).Replace('\','/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        if ((Get-EntryHash $candidateZip $name) -cne $hash) { throw "Packaged class differs from tested compilation: $name" }
        [ordered]@{name=$name; sha256=$hash}
    })
    if ($compiled.Count -eq 0) { throw 'No tested compiled production classes.' }
    $protected = @($baselineZip.Entries | Where-Object FullName -Match '^com/sorrowmist/useless/stretcher/(content/mold/MyriadPatternStore|content/ae/AssemblyStretcherInventory|content/ae/InstalledStretcher|content/ae/InstalledPatternHost|mixin/InstalledStretcherAssemblyMixin).*\.class$' | ForEach-Object {
        $hash = Get-EntryHash $baselineZip $_.FullName
        if ((Get-EntryHash $candidateZip $_.FullName) -cne $hash) { throw "UUID/snapshot byte changed: $($_.FullName)" }
        [ordered]@{name=$_.FullName; sha256=$hash; unchanged=$true}
    })
    if ($protected.Count -lt 5) { throw 'Expected UUID and snapshot class family missing.' }
} finally { $candidateZip.Dispose(); $baselineZip.Dispose() }
$frozen = [ordered]@{
    status='FROZEN_OFFLINE_CHECKS_PASSED'; timestampUtc=[DateTime]::UtcNow.ToString('o'); gameLaunched=$false
    jar=$jar; size=$build.size; sha256=$jarHash; version=$expectedVersion
    baselineSha256=$expectedBaseline; entryCounts=$audit.entryCounts
    tests=$tests; testLogSha256=$testLogHash; expectedTests=$ExpectedTests
    totalStaticChecks=@($audit.mixinChecks).Count; memberAbiChecks=@($audit.memberAbiChecks).Count; versionGateChecks=@($audit.versionGateChecks).Count
    compiledClasses=$compiled; protectedUuidSnapshotClasses=$protected
    performancePassed=$false; rFullTupleOracleAvailable=$false
    pending='Real integrated runtime-boundary validation and same-input full ordered tuple comparison'
    buildResult=$BuildResult; auditReport=$AuditReport; auditReportSha256=$auditReportHash
    freezeToolSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash
}
$delivery = Split-Path -Parent $jar
$freezeHistory = Join-Path $PSScriptRoot ('freeze-runs\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $freezeHistory -Force | Out-Null
$frozen | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $freezeHistory 'P3-freeze-manifest.json') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $freezeHistory 'P3-freeze-manifest.json') -Destination (Join-Path $delivery 'P3-freeze-manifest.json')
[ordered]@{baselineSha256=$expectedBaseline; candidateSha256=$jarHash; entryCounts=$audit.entryCounts; added=$audit.added; changed=$audit.changed; removed=$audit.removed} |
    ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $delivery 'P3-members-vs-P2.json') -Encoding utf8
[pscustomobject]$frozen | Select-Object status,sha256,size,entryCounts,tests,totalStaticChecks,memberAbiChecks,versionGateChecks,@{Name='protectedClassCount';Expression={$_.protectedUuidSnapshotClasses.Count}} | ConvertTo-Json -Depth 5
