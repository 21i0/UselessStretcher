param(
    [string]$Candidate,
    [string]$Baseline = 'D:\AI\useless_stretcher-1.21.1-1.6.3.jar',
    [string]$Output
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (!$Candidate) { $Candidate = Join-Path $projectRoot 'build\libs\useless_stretcher-1.21.1-1.6.4.jar' }
if (!$Output) { $Output = Join-Path $PSScriptRoot ('candidate-jar-audit-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff') + '.json') }
if (Test-Path -LiteralPath $Output) { throw "Refusing to overwrite existing evidence: $Output" }
$expectedOldHash = '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC'
$expectedVersion = '1.21.1-1.6.4'
$failures = [Collections.Generic.List[string]]::new()

function Get-StreamHash([IO.Stream]$Stream) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($Stream)).Replace('-', '') }
    finally { $algorithm.Dispose() }
}
function Read-JarIndex([string]$Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    $index = @{}
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($index.ContainsKey($entry.FullName)) { throw "Duplicate archive entry: $($entry.FullName)" }
            $stream = $entry.Open()
            try { $hash = Get-StreamHash $stream } finally { $stream.Dispose() }
            $index[$entry.FullName] = [ordered]@{ sha256 = $hash; bytes = $entry.Length }
        }
    } finally { $archive.Dispose() }
    return $index
}
function Read-JarText([string]$Path, [string]$Name) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    try {
        $entry = $archive.GetEntry($Name)
        if (!$entry) { throw "Missing archive entry: $Name" }
        $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}
function Get-SourceClass([string]$Path) {
    if ([IO.Path]::GetFileNameWithoutExtension($Path) -in @('package-info', 'module-info')) { return $null }
    $text = [IO.File]::ReadAllText($Path)
    $package = [regex]::Match($text, '(?m)^\s*package\s+([\w.]+)\s*;')
    if (!$package.Success) { throw "Cannot determine source package: $Path" }
    return ($package.Groups[1].Value.Replace('.', '/') + '/' + [IO.Path]::GetFileNameWithoutExtension($Path))
}

$baselinePath = (Resolve-Path -LiteralPath $Baseline).Path
$candidatePath = (Resolve-Path -LiteralPath $Candidate).Path
if ($candidatePath -eq $baselinePath) { throw 'Candidate must be a separate file from the released baseline' }
$baselineHashBefore = (Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash
if ($baselineHashBefore -ne $expectedOldHash) { $failures.Add('Released baseline SHA256 differs from the pinned 1.6.3 artifact') }
$old = Read-JarIndex $baselinePath
$new = Read-JarIndex $candidatePath
$added = @($new.Keys | Where-Object { !$old.ContainsKey($_) } | Sort-Object)
$removed = @($old.Keys | Where-Object { !$new.ContainsKey($_) } | Sort-Object)
$changed = @($old.Keys | Where-Object { $new.ContainsKey($_) -and $old[$_].sha256 -ne $new[$_].sha256 } | Sort-Object)
$unchanged = @($old.Keys | Where-Object { $new.ContainsKey($_) -and $old[$_].sha256 -eq $new[$_].sha256 })
if ($removed.Count) { $failures.Add('Candidate removed baseline production entries; inspect removed_entries') }

$metadata = Read-JarText $candidatePath 'META-INF/neoforge.mods.toml'
$versionMatch = [regex]::Match($metadata, '(?m)^version\s*=\s*"([^"]+)"')
$version = $versionMatch.Groups[1].Value
if ($version -ne $expectedVersion) { $failures.Add("Wrong packaged mod version: $version") }
if ($metadata -notmatch '(?m)^modId\s*=\s*"useless_stretcher"') { $failures.Add('Wrong packaged mod ID') }
if ($metadata -notmatch '(?m)^displayName\s*=\s*"Useless Stretcher"') { $failures.Add('Formal display name changed unexpectedly') }
if ([IO.Path]::GetFileName($candidatePath) -ne "useless_stretcher-$expectedVersion.jar") { $failures.Add('Candidate filename does not match the established release naming') }
$properties = [IO.File]::ReadAllText((Join-Path $projectRoot 'gradle.properties'))
if ($properties -notmatch '(?m)^mod_version=1\.21\.1-1\.6\.4\s*$') { $failures.Add('Gradle project version is not 1.21.1-1.6.4') }

$mixins = (Read-JarText $candidatePath 'useless_stretcher.mixins.json') | ConvertFrom-Json
$requiredMixins = @('InstalledStretcherAssemblyMixin', 'InstalledStretcherSlotMixin')
foreach ($name in $requiredMixins) {
    if ($name -notin $mixins.mixins) { $failures.Add("Missing required mixin registration: $name") }
    if (!$new.ContainsKey('com/sorrowmist/useless/stretcher/mixin/' + $name + '.class')) { $failures.Add("Missing required mixin class: $name") }
}

$sourceClasses = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -File -Filter '*.java' | ForEach-Object { Get-SourceClass $_.FullName } | Where-Object { $_ })
$missingSourceClasses = @($sourceClasses | Where-Object { !$new.ContainsKey($_ + '.class') })
if ($missingSourceClasses.Count) { $failures.Add('Some production top-level source classes are absent from the JAR') }
$classMismatches = [Collections.Generic.List[string]]::new()
foreach ($name in @($new.Keys | Where-Object { $_.EndsWith('.class') })) {
    $compiled = Join-Path $projectRoot ('build\classes\java\main\' + $name.Replace('/', '\'))
    if (!(Test-Path -LiteralPath $compiled) -or (Get-FileHash -LiteralPath $compiled -Algorithm SHA256).Hash -ne $new[$name].sha256) { $classMismatches.Add($name) }
}
if ($classMismatches.Count) { $failures.Add('Packaged classes do not match the current main compilation outputs') }

$testSourceClasses = @()
foreach ($sourceSet in @('test', 'regression', 'guideTest', 'storyTest')) {
    $directory = Join-Path $projectRoot ('src\' + $sourceSet + '\java')
    if (Test-Path -LiteralPath $directory) {
        $testSourceClasses += @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter '*.java' | ForEach-Object { Get-SourceClass $_.FullName } | Where-Object { $_ })
    }
}
$forbidden = @($new.Keys | Where-Object {
    $name = $_
    $bad = $name -match '(^|/)(regression|storytest|guidetest|test|tests|thirdparty|third-party)(/|\.)|(^|/)(org/junit|org/mockito|org/opentest4j|org/apiguardian|net/bytebuddy|org/objenesis)/|useless_stretcher_test|Checks[^/]*\.class$|\.jar$'
    if ($name.EndsWith('.class') -and !$name.StartsWith('com/sorrowmist/useless/stretcher/')) { $bad = $true }
    foreach ($stem in $testSourceClasses) {
        if ($name -eq ($stem + '.class') -or $name.StartsWith($stem + '$')) { $bad = $true; break }
    }
    $bad
} | Sort-Object)
if ($forbidden.Count) { $failures.Add('Test, third-party, or embedded runtime artifacts entered the production JAR') }
$languageEntries = @($new.Keys | Where-Object { $_ -match '/lang/[^/]+\.json$' } | Sort-Object)
$unexpectedLanguage = @($languageEntries | Where-Object { $_ -notin @('assets/useless_stretcher/lang/en_us.json', 'assets/useless_stretcher/lang/zh_cn.json', 'assets/useless_stretcher/lang/zh_tw.json') })
$testLanguageKeys = [Collections.Generic.List[string]]::new()
foreach ($entry in $languageEntries) {
    $language = (Read-JarText $candidatePath $entry) | ConvertFrom-Json
    foreach ($property in $language.PSObject.Properties) {
        if ($property.Name -match '(^|[._-])(regression|storytest|guidetest|junit|mockito|zz_test)([._-]|$)') { $testLanguageKeys.Add($entry + ':' + $property.Name) }
    }
}
if ($unexpectedLanguage.Count -or $testLanguageKeys.Count) { $failures.Add('Unexpected/test language entries found') }

$resourceMismatches = [Collections.Generic.List[string]]::new()
$sourceResourceCount = 0
foreach ($relativeDirectory in @('src\main\resources', 'src\generated\resources')) {
    $directory = Join-Path $projectRoot $relativeDirectory
    if (!(Test-Path -LiteralPath $directory)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $directory -Recurse -File) {
        $name = $file.FullName.Substring($directory.Length + 1).Replace('\', '/')
        $sourceResourceCount++
        if (!$new.ContainsKey($name) -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne $new[$name].sha256) { $resourceMismatches.Add($name) }
    }
}
if ($resourceMismatches.Count) { $failures.Add('Packaged resources differ from the current production resources') }

$javap = & javap -classpath $candidatePath -c -p 'com.sorrowmist.useless.stretcher.mixin.InstalledStretcherAssemblyMixin' 2>&1
$javapExit = $LASTEXITCODE
$javapText = $javap -join "`n"
$inventoryReference = $javapExit -eq 0 -and $javapText -match 'new\s+#[0-9]+\s+// class com/sorrowmist/useless/stretcher/content/ae/AssemblyStretcherInventory'
if (!$inventoryReference) { $failures.Add('Candidate assembly mixin bytecode does not construct AssemblyStretcherInventory') }
$baselineHashAfter = (Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash
if ($baselineHashAfter -ne $expectedOldHash -or $baselineHashBefore -ne $baselineHashAfter) { $failures.Add('Released baseline bytes were not preserved during this read-only audit') }

$diffDetails = @(foreach ($name in @($added + $removed + $changed | Sort-Object)) {
    $status = if (!$old.ContainsKey($name)) { 'added' } elseif (!$new.ContainsKey($name)) { 'removed' } else { 'changed' }
    [ordered]@{ entry = $name; status = $status; kind = $(if ($name.EndsWith('.class')) { 'class' } else { 'resource' }); baseline = $old[$name]; candidate = $new[$name] }
})
$report = [ordered]@{
    result = $(if ($failures.Count -eq 0) { 'PRODUCTION JAR AUDIT PASSED' } else { 'PRODUCTION JAR AUDIT FAILED' })
    utc = [DateTime]::UtcNow.ToString('o')
    scope = 'Static ZIP/source/build-output/hash audit only; no Minecraft or Mixin runtime was started'
    failures = @($failures)
    baseline = [ordered]@{ path = $baselinePath; sha256_before = $baselineHashBefore; sha256_after = $baselineHashAfter; pinned_sha256 = $expectedOldHash; bytes = (Get-Item -LiteralPath $baselinePath).Length }
    candidate = [ordered]@{ path = $candidatePath; sha256 = (Get-FileHash -LiteralPath $candidatePath -Algorithm SHA256).Hash; bytes = (Get-Item -LiteralPath $candidatePath).Length; mod_version = $version; display_name = 'Useless Stretcher' }
    entries = [ordered]@{ baseline = $old.Count; candidate = $new.Count; unchanged = $unchanged.Count; added = $added.Count; removed = $removed.Count; changed = $changed.Count }
    production_sources = [ordered]@{ top_level_java = $sourceClasses.Count; absent_classes = @($missingSourceClasses); compiled_class_mismatches = @($classMismatches); resources_checked = $sourceResourceCount; resource_mismatches = @($resourceMismatches) }
    exclusions = [ordered]@{ test_sources_checked = $testSourceClasses.Count; forbidden_entries = @($forbidden); language_entries = $languageEntries; unexpected_language_entries = $unexpectedLanguage; test_language_keys = @($testLanguageKeys) }
    mixins = [ordered]@{ required_registrations = $requiredMixins; packaged_mixin_count = @($mixins.mixins).Count; candidate_constructs_new_inventory = $inventoryReference; javap_exit = $javapExit; javap_bytecode = $javapText }
    added_entries = $added
    removed_entries = $removed
    changed_entries = $changed
    removed_resources = @($removed | Where-Object { !$_.EndsWith('.class') })
    changed_resources = @($changed | Where-Object { !$_.EndsWith('.class') })
    entry_diff = $diffDetails
}
[IO.File]::WriteAllText($Output, ($report | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
Write-Output ($report.result + "`n" + $Output)
Write-Output ('entries: unchanged=' + $unchanged.Count + ', changed=' + $changed.Count + ', added=' + $added.Count + ', removed=' + $removed.Count)
if ($failures.Count) { throw ($failures -join '; ') }
