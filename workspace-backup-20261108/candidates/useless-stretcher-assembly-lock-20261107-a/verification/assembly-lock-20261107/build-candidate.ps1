$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([IO.Path]::GetPathRoot($taskRoot) -ine 'D:\') { throw 'Outputs must stay on D:.' }
$taskP3 = 'D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a'
$taskBaseline = 'D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4.jar'
$taskExpectedBaseline = '9C57CBB3B1343C8837B773003AC906C630378786A784B7490F1F4E943A5021FC'
if ((Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash -cne $taskExpectedBaseline) { throw 'P4 baseline hash changed.' }
$taskAttempt = Join-Path $PSScriptRoot ('attempts\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $taskAttempt -Force | Out-Null
foreach ($taskName in @('compile-main.log','compile-tests.log','unit-tests.log','build-result.json')) {
    $taskPrevious = Join-Path $PSScriptRoot $taskName
    if (Test-Path -LiteralPath $taskPrevious) { Copy-Item -LiteralPath $taskPrevious -Destination (Join-Path $taskAttempt $taskName) }
}
$taskBuild = Join-Path $taskRoot ('build\assembly-lock-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
$taskClasses = Join-Path $taskBuild 'classes'
$taskTests = Join-Path $taskBuild 'test-classes'
$taskTemp = Join-Path $taskBuild 'tmp'
foreach ($taskDirectory in @($taskClasses,$taskTests,$taskTemp)) { New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null }
$taskHarness = Get-Content -LiteralPath 'D:\AI\sources\UselessStretcher-capsule-variants\build\helper-check\harness-java.args'
$taskDependencies = $taskHarness[1].Trim('"').Split(';') | Where-Object {
    $_ -and (Test-Path -LiteralPath $_) -and $_ -notmatch 'useless_mod.*\.jar$' -and $_ -notmatch 'UselessStretcher/build/(classes|resources|helper-check)'
}
$taskBase = 'D:\AI\sources\DollChronicle\verification\adventure-20261101\mod-freshness\uselessmod-staging\useless_mod-1.21.1-2.4.6.2.jar'
$taskExtras = Get-ChildItem -LiteralPath (Join-Path $taskP3 'libs\compile') -Filter '*.jar' | Where-Object Name -NotMatch '^useless_mod' | ForEach-Object FullName
$taskJei = 'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jei-1.21.1-neoforge-19.57.0.451.jar'
$taskClasspath = (@($taskClasses,$taskBaseline,$taskBase,$taskJei) + $taskDependencies + $taskExtras) -join ';'
[IO.File]::WriteAllText((Join-Path $taskBuild 'classpath.txt'),$taskClasspath,[Text.UTF8Encoding]::new($false))
$taskMainSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$taskTestSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\test\java') -Filter '*.java' -Recurse | ForEach-Object FullName) + @((Join-Path $PSScriptRoot 'OfflineTestLauncher.java'))
$taskMetadata = Join-Path $taskRoot 'src\main\resources\META-INF\neoforge.mods.toml'
$taskInputs = @($taskMainSources) + @($taskTestSources) + @($taskMetadata) + @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\test\resources') -File -Recurse | ForEach-Object FullName)
$taskInputHashes = @($taskInputs | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
$taskInputHashes | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskBuild 'inputs-before-build.json') -Encoding utf8
function Write-JavaArguments([string]$path,[string[]]$arguments) {
    [IO.File]::WriteAllLines($path,@($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }),[Text.UTF8Encoding]::new($false))
}
& javac -version *> (Join-Path $taskBuild 'javac-version.log')
if ($LASTEXITCODE -ne 0) { throw 'No javac.' }
& java -version *> (Join-Path $taskBuild 'java-version.log')
if ($LASTEXITCODE -ne 0) { throw 'No java.' }
$taskCompileArgs = Join-Path $taskBuild 'javac-main.args'
Write-JavaArguments $taskCompileArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskClasses,'-cp',$taskClasspath) + $taskMainSources)
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskCompileArgs" *> (Join-Path $PSScriptRoot 'compile-main.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compile-main.log') -Tail 60; throw 'Main compile failed.' }
$taskTestLibs = (Get-ChildItem -LiteralPath (Join-Path $taskP3 'verification\catalog-P3-20261107\test-libraries') -Filter '*.jar' | ForEach-Object FullName) -join ';'
$taskTestExtras = @('D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jdte-0.6.5.4.jar','D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\enderio-8.2.12-beta.jar',(Join-Path $taskP3 'verification\sliced-enderio\com.enderio.endercore-8.2.12-beta.jar'))
$taskTestClasspath = (@($taskClasses,$taskTests,$taskTestLibs,(Join-Path $taskRoot 'src\test\resources'),$taskClasspath) + $taskTestExtras) -join ';'
$taskTestArgs = Join-Path $taskBuild 'javac-tests.args'
Write-JavaArguments $taskTestArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskTests,'-cp',$taskTestClasspath) + $taskTestSources)
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskTestArgs" *> (Join-Path $PSScriptRoot 'compile-tests.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compile-tests.log') -Tail 65; throw 'Test compile failed.' }
$taskRunArgs = Join-Path $taskBuild 'java-tests.args'
Write-JavaArguments $taskRunArgs @('-Xmx512m',"-Djava.io.tmpdir=$taskTemp",'-cp',$taskTestClasspath,'OfflineTestLauncher',$taskTests)
& java "@$taskRunArgs" *> (Join-Path $PSScriptRoot 'unit-tests.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Tail 85; throw 'Tests failed.' }
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Tail 20
foreach ($taskInput in $taskInputHashes) {
    if ((Get-FileHash -LiteralPath $taskInput.path -Algorithm SHA256).Hash -cne $taskInput.sha256) { throw "Source changed during tests: $($taskInput.path)" }
}
$taskDelivery = Join-Path $taskRoot 'delivery'
New-Item -ItemType Directory -Path $taskDelivery -Force | Out-Null
$taskJar = Join-Path $taskDelivery 'useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock.jar'
if (Test-Path -LiteralPath $taskJar) { throw 'Never overwrite a built candidate.' }
Copy-Item -LiteralPath $taskBaseline -Destination $taskJar
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskPackage = [IO.Compression.ZipFile]::Open($taskJar,[IO.Compression.ZipArchiveMode]::Update)
try {
    $taskEntries = @{'META-INF/neoforge.mods.toml'=$taskMetadata}
    Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object { $taskEntries[$_.FullName.Substring($taskClasses.Length + 1).Replace('\','/')] = $_.FullName }
    foreach ($taskEntry in $taskEntries.GetEnumerator()) {
        $taskOld = $taskPackage.GetEntry($taskEntry.Key)
        if ($taskOld) { $taskOld.Delete() }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskPackage,$taskEntry.Value,$taskEntry.Key,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskPackage.Dispose() }
$taskResult = [ordered]@{
    timestampUtc=[DateTime]::UtcNow.ToString('o');gameLaunched=$false;jar=$taskJar
    sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash;size=(Get-Item -LiteralPath $taskJar).Length
    baselineSha256=$taskExpectedBaseline;classesDirectory=$taskClasses;testClassesDirectory=$taskTests
    testLogSha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'unit-tests.log') -Algorithm SHA256).Hash
    compilerExitCode=0;testCompilerExitCode=0;testExitCode=0
    javaArguments=$taskRunArgs;mainCompilerArguments=$taskCompileArgs;testCompilerArguments=$taskTestArgs;inputs=$taskInputHashes
    compiledClasses=@(Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{name=$_.FullName.Substring($taskClasses.Length + 1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    testCompiledClasses=@(Get-ChildItem -LiteralPath $taskTests -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{name=$_.FullName.Substring($taskTests.Length + 1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    mainSources=@($taskMainSources | Sort-Object | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
    testSources=@($taskTestSources | Sort-Object | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
}
$taskResult | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-result.json') -Encoding utf8
$taskResult | Select-Object jar,sha256,size
