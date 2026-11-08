[CmdletBinding()]
param(
    [string]$JarToAudit = (Join-Path $PSScriptRoot '..\..\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P3.jar'),
    [string]$BaselineJar = (Join-Path $PSScriptRoot 'baseline-catalog-P2.jar'),
    [string]$Published163Jar = 'D:\AI\useless_stretcher-1.21.1-1.6.3.jar',
    [string]$Native246 = (Join-Path $PSScriptRoot '..\..\libs\compile\useless_mod-1.21.1-2.4.6.jar'),
    [string]$Native2462 = 'D:\AI\sources\DollChronicle\build\mygenesis-longhuan-r6-D\mods\useless_mod-1.21.1-2.4.6.2.jar',
    [string]$Jei1957 = 'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jei-1.21.1-neoforge-19.57.0.451.jar'
)
# Read-only with respect to every JAR and game directory. Only audit reports and
# this script's standalone ASM helper are written beside this script. No game,
# server, mod loader, Gradle task, attach command or target class is launched.
$ErrorActionPreference = 'Stop'
if ([IO.Path]::GetPathRoot($PSScriptRoot) -ine 'D:\') { throw 'Audit reports and helper compilation must stay on D:.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$expectedBaseline = '161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4'
$expected163 = '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC'
$expected246 = 'B86A12E8BD6417FD3C89F7C723CA195D68D1F9F9F099480850FFD3C93E5897BC'
$expected2462 = '9F6F54AF76AA255123B354C8725D55C2ACE4525B8683E1136ACAEBCCC8813B17'
$expectedJei = '44FCDA225B26D14502BB1890512F4356DB0A2577A6C63757B92948A6B951180A'
$expectedVersion = '1.21.1-1.6.4-catalog-P3'
$reportDirectory = Join-Path $PSScriptRoot ('audit-runs\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$report = [ordered]@{ timestampUtc = [DateTime]::UtcNow.ToString('o'); status = 'RUNNING'; gameLaunched = $false }
$failures = [System.Collections.Generic.List[string]]::new()
function Assert-Audit([bool]$Condition, [string]$Message) {
    if (-not $Condition) { $failures.Add($Message) }
}
function Read-Zip([string]$Path) {
    $items = @{}
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($items.ContainsKey($entry.FullName)) { throw "Duplicate ZIP entry: $($entry.FullName)" }
            $stream = $entry.Open(); $memory = [System.IO.MemoryStream]::new()
            try { $stream.CopyTo($memory); $bytes = $memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
            $hasher = [System.Security.Cryptography.SHA256]::Create()
            try { $hash = [Convert]::ToHexString($hasher.ComputeHash($bytes)) } finally { $hasher.Dispose() }
            $items[$entry.FullName] = [pscustomobject]@{ Name=$entry.FullName; Size=$bytes.Length; Sha256=$hash; Bytes=$bytes }
        }
    } finally { $zip.Dispose() }
    return ,$items
}
function Text-Entry($Archive, [string]$Name) {
    if (-not $Archive.ContainsKey($Name)) { throw "Missing ZIP entry: $Name" }
    return [System.Text.Encoding]::UTF8.GetString($Archive[$Name].Bytes)
}
try {
    $paths = [ordered]@{ candidate=$JarToAudit; baselineP2=$BaselineJar; published163=$Published163Jar; native246=$Native246; native2462=$Native2462; jei1957=$Jei1957 }
    $before = [ordered]@{}
    foreach ($label in @($paths.Keys)) {
        $paths[$label] = (Resolve-Path -LiteralPath $paths[$label]).Path
        $before[$label] = (Get-FileHash -LiteralPath $paths[$label] -Algorithm SHA256).Hash
    }
    $report.paths = $paths; $report.hashesBefore = $before
    Assert-Audit ($before.baselineP2 -eq $expectedBaseline) 'The precise catalog-P2 baseline differs from its approved exact bytes.'
    Assert-Audit ($before.published163 -eq $expected163) 'Published 1.6.3 differs from its recorded bytes.'
    Assert-Audit ($before.native246 -eq $expected246) 'Native 2.4.6 differs from the audited exact bytes.'
    Assert-Audit ($before.native2462 -eq $expected2462) 'Native 2.4.6.2 differs from the audited exact bytes.'
    Assert-Audit ($before.jei1957 -eq $expectedJei) 'JEI 19.57.0.451 differs from the precise R JAR bytes.'
    if ($failures.Count) { throw 'Input integrity failed.' }
    $base = Read-Zip $paths.baselineP2; $patch = Read-Zip $paths.candidate
    $added = @(); $changed = @(); $removed = @(); $unchanged = 0
    foreach ($name in ($patch.Keys | Sort-Object)) {
        if (-not $base.ContainsKey($name)) { $added += $name }
        elseif ($patch[$name].Sha256 -ne $base[$name].Sha256) { $changed += $name }
        else { $unchanged++ }
    }
    foreach ($name in $base.Keys) { if (-not $patch.ContainsKey($name)) { $removed += $name } }
    $report.entryCounts = [ordered]@{ baseline=$base.Count; candidate=$patch.Count; unchanged=$unchanged; added=$added.Count; changed=$changed.Count; removed=$removed.Count }
    $report.added = @($added | ForEach-Object { [ordered]@{name=$_; size=$patch[$_].Size; sha256=$patch[$_].Sha256} })
    $report.changed = @($changed | ForEach-Object { [ordered]@{name=$_; oldSha256=$base[$_].Sha256; sha256=$patch[$_].Sha256} })
    $report.removed = @($removed)
    Assert-Audit ($removed.Count -eq 0) 'Baseline entries were removed.'
    $prefix = 'com/sorrowmist/useless/stretcher/'
    # This iteration may only change publication state. Add another exact root
    # here only when the root agent explicitly confirms the runtime-failure helper.
    $newRoots = @('client/CatalogJeiPublication')
    $newRootRegex = '^' + [regex]::Escape($prefix) + '(?:' + (($newRoots | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')(?:\$[^/]*)?\.class$'
    foreach ($name in $added) { Assert-Audit ($name -cmatch $newRootRegex) "Unexpected added entry: $name" }
    $allowedChanged = @('META-INF/neoforge.mods.toml')
    $changedRootRegex = $newRootRegex
    foreach ($name in $changed) { Assert-Audit (($name -cin $allowedChanged) -or ($name -cmatch $changedRootRegex)) "Unexpected changed entry: $name" }
    foreach ($root in $newRoots) { Assert-Audit ($patch.ContainsKey($prefix + $root + '.class')) "Missing patch class: $root" }
    $catalogSource = Join-Path $PSScriptRoot '..\..\src\main\java\com\sorrowmist\useless\stretcher\content\catalog'
    foreach ($source in (Get-ChildItem -LiteralPath $catalogSource -Filter '*.java')) {
        Assert-Audit ($patch.ContainsKey($prefix + 'content/catalog/' + $source.BaseName + '.class')) "Missing catalog helper: $($source.Name)"
    }
    foreach ($name in $patch.Keys) {
        Assert-Audit (-not ($name -match '(?i)(^|/)(regression|storytest|guidetest|test|tests)(/|\.)|(?:Test|Tests|Checks)(?:\$[^/]*)?\.class$|useless_stretcher_test|org/(junit|mockito)/|net/bytebuddy/')) "Test entry found: $name"
        Assert-Audit (-not $name.EndsWith('.jar', [StringComparison]::OrdinalIgnoreCase)) "Nested JAR found: $name"
        if ($name.EndsWith('.class')) { Assert-Audit ($name.StartsWith($prefix, [StringComparison]::Ordinal)) "Third-party class found: $name" }
    }
    foreach ($name in ($patch.Keys | Where-Object { $_ -match 'lang/[^/]+\.json$' })) {
        Assert-Audit ($base.ContainsKey($name) -and $patch[$name].Sha256 -eq $base[$name].Sha256) "Language/test-language entry changed: $name"
    }
    $toml = Text-Entry $patch 'META-INF/neoforge.mods.toml'
    Assert-Audit ($toml -match ('(?m)^version\s*=\s*"' + [regex]::Escape($expectedVersion) + '"\s*$')) 'Wrong candidate mod metadata version.'
    $baselineToml = Text-Entry $base 'META-INF/neoforge.mods.toml'
    $expectedToml = $baselineToml -replace '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4-catalog-P2"', ('version="' + $expectedVersion + '"')
    Assert-Audit ($toml.Replace("`r`n","`n") -ceq $expectedToml.Replace("`r`n","`n")) 'Mod metadata changed beyond the expected version.'
    $report.version = $expectedVersion
    $oldMixins = (Text-Entry $base 'useless_stretcher.mixins.json') | ConvertFrom-Json
    $newMixins = (Text-Entry $patch 'useless_stretcher.mixins.json') | ConvertFrom-Json
    foreach ($group in @('mixins','client','server')) {
        $expected = @($oldMixins.$group)
        $actual = @($newMixins.$group)
        Assert-Audit ((@($actual | Sort-Object) -join "`n") -ceq (@($expected | Sort-Object) -join "`n")) "Unexpected mixin registrations: $group"
        Assert-Audit (($actual | Select-Object -Unique).Count -eq $actual.Count) "Duplicate mixin registrations: $group"
    }
    foreach ($property in @('required','minVersion','package','plugin','compatibilityLevel')) {
        Assert-Audit ($newMixins.$property -ceq $oldMixins.$property) "Mixin config property changed: $property"
    }
    Assert-Audit ($patch['useless_stretcher.mixins.json'].Sha256 -ceq $base['useless_stretcher.mixins.json'].Sha256) 'Mixin configuration must remain byte-identical to P2.'
    $protectedPattern = '^com/sorrowmist/useless/stretcher/(?:content/mold/MyriadPatternStore|content/ae/AssemblyStretcherInventory|content/ae/InstalledStretcher|content/ae/InstalledPatternHost|mixin/InstalledStretcherAssemblyMixin).*\.class$'
    $report.protectedUuidSnapshotClasses = @($base.Keys | Where-Object { $_ -cmatch $protectedPattern } | Sort-Object | ForEach-Object {
        $protectedName = $_
        Assert-Audit ($patch.ContainsKey($protectedName) -and $patch[$protectedName].Sha256 -ceq $base[$protectedName].Sha256) "UUID/snapshot class changed: $protectedName"
        [ordered]@{name=$protectedName; sha256=$base[$protectedName].Sha256; unchanged=($patch.ContainsKey($protectedName) -and $patch[$protectedName].Sha256 -ceq $base[$protectedName].Sha256)}
    })
    Assert-Audit ($report.protectedUuidSnapshotClasses.Count -ge 5) 'Expected UUID/snapshot class family missing from exact P2 baseline.'
    $report.auditTools = @($PSCommandPath,(Join-Path $PSScriptRoot 'CatalogPatchBytecodeAudit.java') | ForEach-Object {
        [ordered]@{path=$_; sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}
    })
    $helper = Join-Path $PSScriptRoot 'asm-audit'
    New-Item -ItemType Directory -Path $helper -Force | Out-Null
    $javaSource = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'CatalogPatchBytecodeAudit.java') -Raw
    $javaFile = Join-Path $helper 'CatalogPatchBytecodeAudit.java'
    [IO.File]::WriteAllText($javaFile, $javaSource, [Text.UTF8Encoding]::new($false))
    $asmCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm'
    $asmCore = Get-ChildItem -LiteralPath (Join-Path $asmCache 'asm\9.9.1') -Recurse -Filter 'asm-9.9.1.jar' | Select-Object -First 1
    $asmTree = Get-ChildItem -LiteralPath (Join-Path $asmCache 'asm-tree\9.9.1') -Recurse -Filter 'asm-tree-9.9.1.jar' | Select-Object -First 1
    if (-not $asmCore -or -not $asmTree) { throw 'ASM 9.9.1 core/tree official cached JARs are required.' }
    $asmClasspath = $asmCore.FullName + [IO.Path]::PathSeparator + $asmTree.FullName
    $report.asmDependencies = @($asmCore,$asmTree | ForEach-Object { [ordered]@{ path=$_.FullName; sha256=(Get-FileHash -LiteralPath $_.FullName).Hash } })
    & javac "-J-Djava.io.tmpdir=$helper" -encoding UTF-8 --release 21 -cp $asmClasspath -d $helper $javaFile
    if ($LASTEXITCODE -ne 0) { throw 'Standalone ASM helper compilation failed.' }
    $asmOutput = & java "-Djava.io.tmpdir=$helper" -Xmx128m -cp ($helper + [IO.Path]::PathSeparator + $asmClasspath) CatalogPatchBytecodeAudit $paths.candidate $paths.native246 $paths.native2462 $paths.jei1957 2>&1
    $asmExit = $LASTEXITCODE
    $asmOutput | Set-Content -LiteralPath (Join-Path $reportDirectory 'mixin-target-audit.tsv') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $reportDirectory 'mixin-target-audit.tsv') -Destination (Join-Path $PSScriptRoot 'mixin-target-audit.tsv')
    if ($asmExit -ne 0) { throw "Mixin ABI/target audit failed: $asmOutput" }
    $report.mixinChecks = @($asmOutput | ConvertFrom-Csv -Delimiter "`t")
    $report.versionGateChecks = @($report.mixinChecks | Where-Object kind -EQ 'VersionGate')
    Assert-Audit ($report.versionGateChecks.Count -eq 32) 'Expected 32 symbolic exact-version gate cases.'
    $report.memberAbiChecks = @($report.mixinChecks | Where-Object kind -NE 'VersionGate')
    Assert-Audit ($report.memberAbiChecks.Count -eq 29) 'Expected the same 29 method/field ABI checks as P2.'
    Assert-Audit ($report.mixinChecks.Count -eq 61) 'Expected 61 total static rows: 29 member ABI checks plus 32 version-gate cases.'
    $report.checkCounts = [ordered]@{total=$report.mixinChecks.Count; memberAbi=$report.memberAbiChecks.Count; versionGate=$report.versionGateChecks.Count}
    foreach ($requiredMixin in @('BudgetedRecipeCatalogMixin','BudgetedClientCatalogMixin','CatalogJeiTimingMixin','CatalogJeiLookupMixin','CatalogJeiGuiMixin')) {
        Assert-Audit (@($report.mixinChecks | Where-Object { $_.mixin -eq $requiredMixin -and $_.kind -ne 'VersionGate' }).Count -gt 0) "No static member audit rows: $requiredMixin"
    }
    $after = [ordered]@{}
    foreach ($label in @($paths.Keys)) {
        $after[$label] = (Get-FileHash -LiteralPath $paths[$label] -Algorithm SHA256).Hash
        Assert-Audit ($after[$label] -eq $before[$label]) "Input changed during audit: $label"
    }
    $report.hashesAfter = $after
    $report.status = if ($failures.Count) { 'FAIL' } else { 'PASS' }
} catch {
    $failures.Add($_.Exception.Message)
    $report.status = 'FAIL'
} finally {
    $report.failures = @($failures)
    $report.limits = 'Static ZIP and bytecode audit only. Does not prove runtime Mixin application, JEI behavior, gameplay correctness or performance.'
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $reportDirectory 'audit-patch-report.json') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $reportDirectory 'audit-patch-report.json') -Destination (Join-Path $PSScriptRoot 'audit-patch-report.json')
}
Write-Output ("Audit {0}: {1}" -f $report.status, (Join-Path $reportDirectory 'audit-patch-report.json'))
if ($report.status -eq 'PASS') {
    $passedReport = Join-Path $reportDirectory 'audit-patch-report.json'
    [ordered]@{
        timestampUtc=$report.timestampUtc; auditReport=$passedReport
        auditReportSha256=(Get-FileHash -LiteralPath $passedReport -Algorithm SHA256).Hash
        candidate=$report.paths.candidate; candidateSha256=$report.hashesAfter.candidate
        baselineSha256=$report.hashesAfter.baselineP2; version=$report.version
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'latest-pass.json') -Encoding utf8
}
if ($failures.Count) { $failures | Write-Output; exit 1 }
