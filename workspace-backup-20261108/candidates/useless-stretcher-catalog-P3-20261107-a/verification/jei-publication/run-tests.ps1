param(
    [ValidateSet('P2Baseline', 'P3Source')][string]$Controller = 'P3Source'
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$taskRun = Join-Path $PSScriptRoot ('runs\' + $Controller + '-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
$taskClasses = Join-Path $taskRun 'classes'
$taskTemp = Join-Path $taskRun 'tmp'
foreach ($taskDir in @($taskClasses, $taskTemp)) { New-Item -ItemType Directory -Path $taskDir -Force | Out-Null }
$taskJavaRoot = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin'
$taskOriginalArgs = Get-Content -LiteralPath 'D:\AI\sources\UselessStretcher-capsule-variants\build\helper-check\harness-java.args'
$taskDependencies = $taskOriginalArgs[1].Trim('"').Split(';') | Where-Object {
    $_ -and (Test-Path -LiteralPath $_) -and $_ -notmatch 'useless_mod.*\.jar$' -and $_ -notmatch 'UselessStretcher/build/(classes|resources|helper-check)'
}
$taskBaseline = Join-Path $taskRoot 'verification\catalog-P3-20261107\baseline-catalog-P2.jar'
$taskBaselineHash = (Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash
if ($taskBaselineHash -ne '161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4') { throw 'Frozen P2 baseline hash mismatch' }
$taskBase = 'D:\AI\sources\DollChronicle\verification\adventure-20261101\mod-freshness\uselessmod-staging\useless_mod-1.21.1-2.4.6.2.jar'
$taskJei = 'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jei-1.21.1-neoforge-19.57.0.451.jar'
$taskTestLibraries = Get-ChildItem -LiteralPath (Join-Path $taskRoot 'verification\catalog-P3-20261107\test-libraries') -Filter '*.jar' | ForEach-Object FullName
$taskCompileLibraries = Get-ChildItem -LiteralPath (Join-Path $taskRoot 'libs\compile') -Filter '*.jar' | Where-Object Name -NotMatch '^useless_mod' | ForEach-Object FullName
$taskClasspath = (@($taskClasses, $taskBaseline, $taskBase, $taskJei, (Join-Path $taskRoot 'src\test\resources')) + $taskDependencies + $taskTestLibraries + $taskCompileLibraries) -join ';'
[IO.File]::WriteAllText((Join-Path $taskRun 'classpath.txt'), $taskClasspath, [Text.UTF8Encoding]::new($false))
function Write-JavaArguments([string]$path, [string[]]$arguments) {
    [IO.File]::WriteAllLines($path, @($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
}
$taskSources = @(
    'src\test\java\com\sorrowmist\useless\stretcher\VanillaTestRegistries.java',
    'src\test\java\com\sorrowmist\useless\stretcher\client\CatalogJeiPublicationTest.java',
    'verification\jei-publication\JeiPublicationTestLauncher.java'
) | ForEach-Object { Join-Path $taskRoot $_ }
if ($Controller -eq 'P3Source') {
    $taskSources = @(
        (Join-Path $taskRoot 'src\main\java\com\sorrowmist\useless\stretcher\content\catalog\OwnerRecipePublication.java'),
        (Join-Path $taskRoot 'src\main\java\com\sorrowmist\useless\stretcher\client\CatalogJeiPublication.java')
    ) + $taskSources
}
$taskSourceHashes = @($taskSources | ForEach-Object {
    [PSCustomObject]@{Path=$_; SHA256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}
})
[PSCustomObject]@{Controller=$Controller; P2Baseline=$taskBaseline; P2BaselineSHA256=$taskBaselineHash; Sources=$taskSourceHashes} |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $taskRun 'inputs.json') -Encoding utf8
$taskCompileArgs = Join-Path $taskRun 'compile.args'
Write-JavaArguments $taskCompileArgs (@('-proc:none', '--release', '21', '-encoding', 'UTF-8', '-d', $taskClasses, '-cp', $taskClasspath) + $taskSources)
& (Join-Path $taskJavaRoot 'javac.exe') "-J-Djava.io.tmpdir=$taskTemp" "@$taskCompileArgs" *> (Join-Path $taskRun 'compile.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $taskRun 'compile.log') -Tail 60; throw 'Offline lifecycle test compilation failed' }
$taskRunArgs = Join-Path $taskRun 'run.args'
Write-JavaArguments $taskRunArgs @('-Xmx512m', "-Djava.io.tmpdir=$taskTemp", '-cp', $taskClasspath, 'JeiPublicationTestLauncher')
& (Join-Path $taskJavaRoot 'java.exe') "@$taskRunArgs" *> (Join-Path $taskRun 'tests.log')
$taskExit = $LASTEXITCODE
[PSCustomObject]@{Controller=$Controller; ExitCode=$taskExit; Evidence=$taskRun} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRun 'result.json') -Encoding utf8
Select-String -LiteralPath (Join-Path $taskRun 'tests.log') -Pattern 'CONTROLLER_|OWNER_|REENTRY_THROW|RETURN_FAILED_RUNTIME|NESTED_FRAME|REGISTRATION_RESET|OBSERVED_RUNTIME_ABA|tests found|tests successful|tests failed|Failures \(' | ForEach-Object Line
Write-Output ('Evidence: ' + $taskRun)
if ($taskExit -ne 0) { throw 'Offline lifecycle tests failed; complete evidence retained' }
