$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskBuildReceipt = Join-Path $PSScriptRoot 'selector-build.json'
$taskCompiled = Get-Content -LiteralPath $taskBuildReceipt -Raw | ConvertFrom-Json
$taskBaseline = $taskCompiled.baseline
$taskBaselineHash = '9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7'
if ((Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash -cne $taskBaselineHash) { throw 'Frozen 9556 baseline changed.' }
if ((Get-FileHash -LiteralPath $taskCompiled.source -Algorithm SHA256).Hash -cne $taskCompiled.sourceSha256) { throw 'Source changed since compilation.' }
$taskMergeResults = @('positive-all-1970-a','positive-all-1957-a') | ForEach-Object { Join-Path $PSScriptRoot ('sponge-merge\' + $_ + '\result.json') }
foreach ($taskResultFile in $taskMergeResults) {
    $taskResult = Get-Content -LiteralPath $taskResultFile -Raw | ConvertFrom-Json
    if ($taskResult.status -cne 'PASS' -or $taskResult.checksPassed -ne 7 -or $taskResult.checksFailed -ne 0 -or $taskResult.transformer -cne 'org.spongepowered.asm.mixin.transformer.MixinTransformer' -or $taskResult.gameLaunched -or $taskResult.modLauncherUsed) { throw "Actual Sponge merge did not pass: $taskResultFile" }
}
$taskNegativeFile = Join-Path $PSScriptRoot 'sponge-merge\negative-1970-a\result.json'
$taskNegative = Get-Content -LiteralPath $taskNegativeFile -Raw | ConvertFrom-Json
if ($taskNegative.status -cne 'FAIL' -or $taskNegative.targets[0].trace -notmatch 'stretcher\$timeCategory' -or $taskNegative.targets[0].trace -notmatch 'Scanned 0 target') { throw 'Negative control did not reproduce the reported failure.' }
$taskClassName = 'com/sorrowmist/useless/stretcher/mixin/CatalogJeiBuildTimingMixin.class'
$taskClassFile = Join-Path $taskCompiled.classesDirectory $taskClassName
$taskMetadata = Join-Path $taskRoot 'src\main\resources\META-INF\neoforge.mods.toml'
$taskInputFiles = @($taskBaseline,$taskBuildReceipt,$taskCompiled.source,$taskClassFile,$taskMetadata,$PSCommandPath,$taskNegativeFile) + $taskMergeResults
$taskInputHashes = @($taskInputFiles | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
$taskDelivery = Join-Path $taskRoot 'delivery'
$taskJar = Join-Path $taskDelivery 'useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1.jar'
if (Test-Path -LiteralPath $taskJar) { throw 'Never overwrite an existing candidate.' }
$taskBuild = Join-Path $taskRoot ('build\package-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $taskBuild -Force | Out-Null
$taskStaging = Join-Path $taskBuild 'candidate.jar'
Copy-Item -LiteralPath $taskBaseline -Destination $taskStaging
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZip = [IO.Compression.ZipFile]::Open($taskStaging,[IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($taskEntry in @(@{name=$taskClassName;path=$taskClassFile},@{name='META-INF/neoforge.mods.toml';path=$taskMetadata})) {
        $taskPrevious = $taskZip.GetEntry($taskEntry.name)
        if (-not $taskPrevious) { throw "Expected existing entry missing: $($taskEntry.name)" }
        $taskPrevious.Delete()
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip,$taskEntry.path,$taskEntry.name,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskZip.Dispose() }
function Read-MemberHashes([string]$path) {
    $taskArchive = [IO.Compression.ZipFile]::OpenRead($path)
    $taskHashes = @{}
    try {
        foreach ($taskEntry in $taskArchive.Entries) {
            if ($taskEntry.FullName.EndsWith('/')) { continue }
            if ($taskHashes.ContainsKey($taskEntry.FullName)) { throw "Duplicate ZIP entry: $($taskEntry.FullName)" }
            $taskStream=$taskEntry.Open(); $taskHasher=[Security.Cryptography.SHA256]::Create()
            try { $taskHashes[$taskEntry.FullName]=[Convert]::ToHexString($taskHasher.ComputeHash($taskStream)) } finally { $taskStream.Dispose(); $taskHasher.Dispose() }
        }
    } finally { $taskArchive.Dispose() }
    return $taskHashes
}
$taskOld = Read-MemberHashes $taskBaseline
$taskNew = Read-MemberHashes $taskStaging
if ($taskOld.Count -ne $taskNew.Count) { throw 'Member count changed.' }
$taskChanged = @($taskOld.Keys | Where-Object { -not $taskNew.ContainsKey($_) -or $taskOld[$_] -cne $taskNew[$_] } | Sort-Object)
if ($taskChanged.Count -ne 2 -or $taskChanged -cnotcontains $taskClassName -or $taskChanged -cnotcontains 'META-INF/neoforge.mods.toml') { throw "Unexpected member changes: $($taskChanged -join ', ')" }
foreach ($taskInput in $taskInputHashes) { if ((Get-FileHash -LiteralPath $taskInput.path -Algorithm SHA256).Hash -cne $taskInput.sha256) { throw "Input changed while packaging: $($taskInput.path)" } }
New-Item -ItemType Directory -Path $taskDelivery -Force | Out-Null
Move-Item -LiteralPath $taskStaging -Destination $taskJar
$taskReceipt = [ordered]@{
    packagedUtc=[DateTime]::UtcNow.ToString('o');gameLaunched=$false;modLauncherUsed=$false;full184SuiteRerun=$false
    jar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash;size=(Get-Item -LiteralPath $taskJar).Length
    baseline=$taskBaseline;baselineSha256=$taskBaselineHash;compiledClassesDirectory=$taskCompiled.classesDirectory
    changedMembers=$taskChanged;unchangedMembers=($taskOld.Count-2);addedMembers=0;deletedMembers=0
    inputHashes=$taskInputHashes;negativeControl=$taskNegativeFile;positiveControls=$taskMergeResults
}
$taskReceipt | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-result.json') -Encoding utf8
[pscustomobject]$taskReceipt | Select-Object jar,sha256,size,changedMembers,unchangedMembers | ConvertTo-Json -Depth 4
