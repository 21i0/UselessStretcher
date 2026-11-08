$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$taskAttempt = Join-Path $PSScriptRoot ('attempts\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $taskAttempt -Force | Out-Null
foreach ($taskLog in @('compile-main.log','compile-tests.log','unit-tests.log')) {
    $taskPrevious = Join-Path $PSScriptRoot $taskLog
    if (Test-Path -LiteralPath $taskPrevious) { Copy-Item -LiteralPath $taskPrevious -Destination (Join-Path $taskAttempt $taskLog) }
}
$taskBuild = Join-Path $taskRoot ('build\catalog-patch-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $taskBuild -Force | Out-Null
$taskClasses = Join-Path $taskBuild 'classes'
$taskTests = Join-Path $taskBuild 'test-classes'
$taskTemp = Join-Path $taskBuild 'tmp'
foreach ($taskDir in @($taskClasses,$taskTests,$taskTemp)) { New-Item -ItemType Directory -Path $taskDir -Force | Out-Null }
$taskArgs = Get-Content -LiteralPath 'D:\AI\sources\UselessStretcher-capsule-variants\build\helper-check\harness-java.args'
$taskClasspathLine = $taskArgs[1].Trim('"')
$taskClasspathEntries = $taskClasspathLine.Split(';') | Where-Object {
    $_ -and (Test-Path -LiteralPath $_) -and $_ -notmatch 'useless_mod.*\.jar$' -and $_ -notmatch 'UselessStretcher/build/(classes|resources|helper-check)'
}
$taskOriginalBaseline = 'D:\AI\candidates\useless-stretcher-catalog-P-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P1.jar'
$taskBaseline = Join-Path $PSScriptRoot 'baseline-catalog-P1.jar'
if (-not (Test-Path -LiteralPath $taskBaseline)) { Copy-Item -LiteralPath $taskOriginalBaseline -Destination $taskBaseline }
if ((Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash -ne '004AE477920BAA6ADF890417FF11431771A52D2F2733E818B49C446484549BD3') { throw 'Baseline hash mismatch' }
$taskBase = 'D:\AI\sources\DollChronicle\verification\adventure-20261101\mod-freshness\uselessmod-staging\useless_mod-1.21.1-2.4.6.2.jar'
$taskExtras = Get-ChildItem -LiteralPath (Join-Path $taskRoot 'libs\compile') -Filter '*.jar' | Where-Object Name -NotMatch '^useless_mod' | ForEach-Object FullName
$taskJei = 'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jei-1.21.1-neoforge-19.57.0.451.jar'
$taskClasspath = (@($taskClasses,$taskBaseline,$taskBase,$taskJei) + $taskClasspathEntries + $taskExtras) -join ';'
[IO.File]::WriteAllText((Join-Path $taskBuild 'classpath.txt'), $taskClasspath, [Text.UTF8Encoding]::new($false))
$taskMainSources = @(
    Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\main\java\com\sorrowmist\useless\stretcher\content\catalog') -Filter '*.java' | ForEach-Object FullName
    foreach ($taskRelative in @(
        'client\ClientCatalogLifecycle.java','client\CatalogJeiPublication.java','client\CatalogJeiTiming.java','event\BudgetedCatalogEvents.java',
        'mixin\BudgetedRecipeCatalogMixin.java','mixin\BudgetedClientCatalogMixin.java','mixin\CatalogJeiTimingMixin.java',
        'mixin\CatalogJeiLookupMixin.java','mixin\CatalogJeiGuiMixin.java','mixin\UselessStretcherMixinPlugin.java')) {
        Join-Path $taskRoot ('src\main\java\com\sorrowmist\useless\stretcher\' + $taskRelative)
    }
)
function Write-JavaArguments([string]$path, [string[]]$arguments) {
    [IO.File]::WriteAllLines($path, @($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
}
$taskCompileArgs = Join-Path $taskBuild 'javac-main.args'
Write-JavaArguments $taskCompileArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskClasses,'-cp',$taskClasspath) + $taskMainSources)
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskCompileArgs" *> (Join-Path $PSScriptRoot 'compile-main.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compile-main.log') -Tail 60; throw 'Main compilation failed' }
$taskTestLibs = (Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test-libraries') -Filter '*.jar' | ForEach-Object FullName) -join ';'
$taskTestExtras = @(
    'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jdte-0.6.5.4.jar',
    'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\enderio-8.2.12-beta.jar',
    (Join-Path $taskRoot 'verification\sliced-enderio\com.enderio.endercore-8.2.12-beta.jar')
)
$taskTestClasspath = @($taskClasses,$taskTests,$taskTestLibs,(Join-Path $taskRoot 'src\test\resources'),$taskClasspath) + $taskTestExtras -join ';'
$taskTestSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\test\java') -Filter '*.java' -Recurse | ForEach-Object FullName) + @((Join-Path $PSScriptRoot 'OfflineTestLauncher.java'))
$taskTestArgs = Join-Path $taskBuild 'javac-tests.args'
Write-JavaArguments $taskTestArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskTests,'-cp',$taskTestClasspath) + $taskTestSources)
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskTestArgs" *> (Join-Path $PSScriptRoot 'compile-tests.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compile-tests.log') -Tail 70; throw 'Test compilation failed' }
$taskRunArgs = Join-Path $taskBuild 'java-tests.args'
Write-JavaArguments $taskRunArgs @('-Xmx512m',"-Djava.io.tmpdir=$taskTemp",'-cp',$taskTestClasspath,'OfflineTestLauncher',$taskTests)
& java "@$taskRunArgs" *> (Join-Path $PSScriptRoot 'unit-tests.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Tail 85; throw 'Unit tests failed' }
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Tail 20

Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskDelivery = Join-Path $taskRoot 'delivery'
New-Item -ItemType Directory -Path $taskDelivery -Force | Out-Null
$taskJar = Join-Path $taskDelivery 'useless_stretcher-1.21.1-1.6.4-catalog-P2.jar'
if (Test-Path -LiteralPath $taskJar) { throw 'Candidate already exists; do not overwrite verified candidates' }
Copy-Item -LiteralPath $taskBaseline -Destination $taskJar
$taskZip = [IO.Compression.ZipFile]::Open($taskJar, [IO.Compression.ZipArchiveMode]::Update)
try {
    $taskEntries = @{}
    Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object {
        $taskEntries[$_.FullName.Substring($taskClasses.Length + 1).Replace('\','/')] = $_.FullName
    }
    foreach ($taskResource in @('META-INF/neoforge.mods.toml','useless_stretcher.mixins.json')) {
        $taskEntries[$taskResource] = Join-Path $taskRoot ('src/main/resources/' + $taskResource)
    }
    foreach ($taskEntry in $taskEntries.GetEnumerator()) {
        $taskOld = $taskZip.GetEntry($taskEntry.Key)
        if ($taskOld) { $taskOld.Delete() }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip, $taskEntry.Value, $taskEntry.Key, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskZip.Dispose() }
Get-FileHash -LiteralPath $taskJar -Algorithm SHA256 | ConvertTo-Json
$taskResult = [ordered]@{
    timestampUtc = [DateTime]::UtcNow.ToString('o')
    gameLaunched = $false
    jar = $taskJar
    sha256 = (Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash
    size = (Get-Item -LiteralPath $taskJar).Length
    baselineSha256 = (Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash
    classesDirectory = $taskClasses
    testClassesDirectory = $taskTests
    testLogSha256 = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Algorithm SHA256).Hash
    mainSources = @($taskMainSources | Sort-Object | ForEach-Object {
        [ordered]@{ path=$_; sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }
    })
    testSources = @($taskTestSources | Sort-Object | ForEach-Object {
        [ordered]@{ path=$_; sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }
    })
}
$taskResult | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-result.json') -Encoding utf8
