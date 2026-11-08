[CmdletBinding()]
param(
    [switch]$VerifySourceOnly,
    [string]$ReadOnlyClasspathFile = 'D:\AI\candidates\useless-stretcher-catalog-P-20261107-a\build\catalog-patch\classpath.txt'
)
# Builds a separate diagnostic from frozen local copies. Does not start a game,
# touch R, modify original P1, or change any mod, world, registry or recipe data.
$ErrorActionPreference = 'Stop'
if ([IO.Path]::GetPathRoot($PSScriptRoot) -ine 'D:\') { throw 'All evidence writes must stay on D:.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$expectedBase = '004AE477920BAA6ADF890417FF11431771A52D2F2733E818B49C446484549BD3'
$diagnosticVersion = '1.21.1-1.6.4-catalog-P1-evidence'
$baseJar = Join-Path $PSScriptRoot 'baseline-catalog-P1.jar'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'preparation-manifest.json') -Raw | ConvertFrom-Json
$attempt = Join-Path $PSScriptRoot ('attempts\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $attempt -Force | Out-Null
$report = [ordered]@{ utc=[DateTime]::UtcNow.ToString('o'); purpose='P1 full ordered tuple evidence only'; status='RUNNING'; gameLaunched=$false; performanceBaseline=$false; rTupleExportAvailable=$false; rTupleDigest='unavailable; not inferred' }
function Zip-Hashes([string]$File) {
    $hashes = @{}
    $archive = [IO.Compression.ZipFile]::OpenRead($File)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($hashes.ContainsKey($entry.FullName)) { throw "Duplicate ZIP entry: $($entry.FullName)" }
            $stream = $entry.Open(); $digest = [Security.Cryptography.SHA256]::Create()
            try { $hashes[$entry.FullName] = [Convert]::ToHexString($digest.ComputeHash($stream)) }
            finally { $stream.Dispose(); $digest.Dispose() }
        }
    } finally { $archive.Dispose() }
    return ,$hashes
}
function Compile-Sources([string]$Destination, [string[]]$Sources, [string]$Log) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    & javac "-J-Djava.io.tmpdir=$attempt" -proc:none --release 21 -encoding UTF-8 -cp $script:classpath -d $Destination @Sources *> $Log
    if ($LASTEXITCODE -ne 0) { throw "javac failed; see $Log" }
}
try {
    if ((Get-FileHash -LiteralPath $baseJar).Hash -cne $expectedBase) { throw 'Frozen P1 baseline hash mismatch.' }
    $baselineSource = Join-Path $PSScriptRoot 'baseline-source\BudgetedRecipeCatalog.java'
    $instrumentedSource = Join-Path $PSScriptRoot 'source\BudgetedRecipeCatalog.java'
    $helperSource = Join-Path $PSScriptRoot 'source\CatalogTupleEvidence.java'
    foreach ($check in @(@($baselineSource,$manifest.p1SourceSha256),@($instrumentedSource,$manifest.instrumentedSourceSha256),@($helperSource,$manifest.tupleHelperSha256))) {
        if ((Get-FileHash -LiteralPath $check[0]).Hash -cne $check[1]) { throw "Frozen source was changed: $($check[0])" }
    }
    $classpathText = Get-Content -LiteralPath $ReadOnlyClasspathFile -Raw
    $dependencies = @($classpathText.Trim().Split(';') | Where-Object {
        if ($_ -match '(?i)mygenesis-native-R-') { throw 'R runtime entries are forbidden in this diagnostic build classpath.' }
        $_ -and $_.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $_ -PathType Leaf)
    } | Select-Object -Unique)
    if ($dependencies.Count -lt 10) { throw 'The previously recorded read-only Java classpath is incomplete.' }
    $script:classpath = (@($baseJar) + $dependencies) -join ';'
    [IO.File]::WriteAllText((Join-Path $attempt 'classpath.txt'),$script:classpath,[Text.UTF8Encoding]::new($false))
    $baseHashes = Zip-Hashes $baseJar
    $baselineClasses = Join-Path $attempt 'baseline-classes'
    Compile-Sources $baselineClasses @($baselineSource) (Join-Path $attempt 'baseline-compile.log')
    $family = '^com/sorrowmist/useless/stretcher/content/catalog/BudgetedRecipeCatalog(?:\$[^/]*)?\.class$'
    $expectedFamily = @($baseHashes.Keys | Where-Object { $_ -cmatch $family } | Sort-Object)
    $actualFamily = @()
    foreach ($file in (Get-ChildItem -LiteralPath $baselineClasses -Recurse -Filter '*.class')) {
        $entry = $file.FullName.Substring($baselineClasses.Length + 1).Replace('\','/')
        if ($entry -cnotmatch $family) { throw "Unexpected baseline compiler output: $entry" }
        if (-not $baseHashes.ContainsKey($entry) -or (Get-FileHash -LiteralPath $file.FullName).Hash -cne $baseHashes[$entry]) {
            throw "P1 source is not byte-identical to the exact P1 compiled artifact: $entry"
        }
        $actualFamily += $entry
    }
    if ((($actualFamily | Sort-Object) -join "`n") -cne ($expectedFamily -join "`n")) { throw 'P1 class-family set differs from the exact artifact.' }
    $report.unmodifiedP1SourceBytecodeEquivalent = $true
    $report.baselineClassFamily = $expectedFamily
    $report.baseSha256 = $expectedBase
    $report.originalBudget = [ordered]@{nanos=2000000; maxSteps=8192; fingerprintsPerTick=128}
    if ($VerifySourceOnly) {
        $report.status = 'SOURCE_VERIFIED_ONLY'
    } else {
        $classes = Join-Path $attempt 'instrumented-classes'
        Compile-Sources $classes @($instrumentedSource,$helperSource) (Join-Path $attempt 'instrumented-compile.log')
        $diagnosticDirectory = Join-Path $PSScriptRoot 'diagnostic'
        New-Item -ItemType Directory -Path $diagnosticDirectory -Force | Out-Null
        $diagnosticJar = Join-Path $diagnosticDirectory 'useless_stretcher-1.21.1-1.6.4-catalog-P1-evidence.jar'
        if (Test-Path -LiteralPath $diagnosticJar) { throw 'Diagnostic already exists; preserve it instead of overwriting.' }
        Copy-Item -LiteralPath $baseJar -Destination $diagnosticJar
        $archive = [IO.Compression.ZipFile]::Open($diagnosticJar,[IO.Compression.ZipArchiveMode]::Update)
        try {
            foreach ($file in (Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class')) {
                $name = $file.FullName.Substring($classes.Length+1).Replace('\','/')
                if ($name -cnotmatch '^com/sorrowmist/useless/stretcher/content/catalog/(?:BudgetedRecipeCatalog|CatalogTupleEvidence)(?:\$[^/]*)?\.class$') {
                    throw "Unexpected instrumented output: $name"
                }
                $old = $archive.GetEntry($name)
                if ($old) { $old.Delete() }
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$name,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
            }
            $metadata = $archive.GetEntry('META-INF/neoforge.mods.toml')
            $reader = [IO.StreamReader]::new($metadata.Open(),[Text.Encoding]::UTF8)
            try { $toml = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $versionPattern = '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4-catalog-P1"'
            if ([regex]::Matches($toml,$versionPattern).Count -ne 1) { throw 'Unexpected P1 mod metadata.' }
            $toml = $toml -replace $versionPattern,('version="' + $diagnosticVersion + '"')
            $metadata.Delete()
            $writer = [IO.StreamWriter]::new($archive.CreateEntry('META-INF/neoforge.mods.toml').Open(),[Text.UTF8Encoding]::new($false))
            try { $writer.Write($toml) } finally { $writer.Dispose() }
        } finally { $archive.Dispose() }
        $diagnosticHashes = Zip-Hashes $diagnosticJar
        $added=@(); $changed=@(); $unchanged=0
        foreach ($name in $baseHashes.Keys) { if (-not $diagnosticHashes.ContainsKey($name)) { throw "Baseline entry removed: $name" } }
        foreach ($name in $diagnosticHashes.Keys) {
            if ($baseHashes.ContainsKey($name) -and $baseHashes[$name] -ceq $diagnosticHashes[$name]) { $unchanged++; continue }
            if ($name -cne 'META-INF/neoforge.mods.toml' -and $name -cnotmatch '^com/sorrowmist/useless/stretcher/content/catalog/(?:BudgetedRecipeCatalog|CatalogTupleEvidence)(?:\$[^/]*)?\.class$') {
                throw "Unexpected diagnostic delta: $name"
            }
            if ($baseHashes.ContainsKey($name)) { $changed += [ordered]@{entry=$name; oldSha256=$baseHashes[$name]; sha256=$diagnosticHashes[$name]} }
            else { $added += [ordered]@{entry=$name; sha256=$diagnosticHashes[$name]} }
        }
        if ((Get-FileHash -LiteralPath $baseJar).Hash -cne $expectedBase) { throw 'Frozen P1 baseline changed during diagnostic compilation.' }
        $report.status = 'BUILT_STATIC_DIAGNOSTIC_ONLY'
        $report.jar = $diagnosticJar
        $report.sha256 = (Get-FileHash -LiteralPath $diagnosticJar).Hash
        $report.unchangedEntries = $unchanged; $report.changed = $changed; $report.added = $added
        $report.version = $diagnosticVersion
    }
} catch { $report.status='FAILED'; $report.error=$_.Exception.Message }
finally {
    $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $attempt 'build-report.json') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $attempt 'build-report.json') -Destination (Join-Path $PSScriptRoot 'latest-build-report.json')
}
Write-Output ("P1 evidence {0}: {1}" -f $report.status,(Join-Path $attempt 'build-report.json'))
if ($report.status -eq 'FAILED') { throw $report.error }
