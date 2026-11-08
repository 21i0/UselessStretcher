$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([IO.Path]::GetPathRoot($taskRoot) -ine 'D:\') { throw 'Outputs must remain on D:.' }
$taskPriorRoot = 'D:\AI\candidates\useless-stretcher-jei-localization-20261107-a'
$taskBaseline = Join-Path $taskPriorRoot 'delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh.jar'
$taskBaselineHash = '7D54D427EE4E8D19B62B94B9B8DA3630FE20D8207D21A872F2101596EC59127D'
if ((Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash -cne $taskBaselineHash) { throw 'Frozen Chinese P4 baseline changed.' }
$taskPriorBuild = Get-Content -LiteralPath (Join-Path $taskPriorRoot 'verification\jei-localization-20261107\build-result.json') -Raw | ConvertFrom-Json
$taskBuild = Join-Path $taskRoot ('build\hud-layout-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
$taskClasses = Join-Path $taskBuild 'classes'
$taskChecks = Join-Path $taskBuild 'check-classes'
$taskTemp = Join-Path $taskBuild 'tmp'
foreach ($taskDir in @($taskClasses,$taskChecks,$taskTemp)) { New-Item -ItemType Directory -Path $taskDir -Force | Out-Null }
$taskAttempt = Join-Path $PSScriptRoot ('attempts\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $taskAttempt -Force | Out-Null
foreach ($taskName in @('compile-main.log','compile-checks.log','focused-checks.log','check-result.json','build-result.json')) {
    $taskPrevious = Join-Path $PSScriptRoot $taskName
    if (Test-Path -LiteralPath $taskPrevious) { Copy-Item -LiteralPath $taskPrevious -Destination (Join-Path $taskAttempt $taskName) }
}
$taskPriorClasspath = [IO.File]::ReadAllText((Join-Path (Split-Path $taskPriorBuild.classesDirectory) 'classpath.txt'))
$taskClasspath = (@($taskClasses,$taskBaseline) + @($taskPriorClasspath.Split(';') | Where-Object { $_ -and $_ -ine $taskPriorBuild.classesDirectory })) -join ';'
[IO.File]::WriteAllText((Join-Path $taskBuild 'classpath.txt'),$taskClasspath,[Text.UTF8Encoding]::new($false))
$taskMainSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$taskCheckSources = @((Join-Path $PSScriptRoot 'JeiHudLayoutChecks.java'))
$taskMetadata = Join-Path $taskRoot 'src\main\resources\META-INF\neoforge.mods.toml'
$taskInputHashes = @((@($taskMainSources) + @($taskCheckSources) + @($taskMetadata)) | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
$taskInputHashes | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskBuild 'inputs-before-build.json') -Encoding utf8
function Write-JavaArguments([string]$path,[string[]]$arguments) {
    [IO.File]::WriteAllLines($path,@($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }),[Text.UTF8Encoding]::new($false))
}
& javac -version *> (Join-Path $taskBuild 'javac-version.log')
if ($LASTEXITCODE -ne 0) { throw 'No javac.' }
& java -version *> (Join-Path $taskBuild 'java-version.log')
if ($LASTEXITCODE -ne 0) { throw 'No java.' }
$taskMainArgs = Join-Path $taskBuild 'javac-main.args'
Write-JavaArguments $taskMainArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskClasses,'-cp',$taskClasspath) + $taskMainSources)
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskMainArgs" *> (Join-Path $PSScriptRoot 'compile-main.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compile-main.log') -Tail 65; throw 'Production compilation failed.' }
$taskCheckClasspath = (@($taskClasses,$taskChecks,$taskClasspath)) -join ';'
$taskCheckArgs = Join-Path $taskBuild 'javac-checks.args'
Write-JavaArguments $taskCheckArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskChecks,'-cp',$taskCheckClasspath) + $taskCheckSources)
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskCheckArgs" *> (Join-Path $PSScriptRoot 'compile-checks.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compile-checks.log') -Tail 65; throw 'Focused check compilation failed.' }
$taskResultPath = Join-Path $PSScriptRoot 'check-result.json'
$taskCheckLog = Join-Path $PSScriptRoot 'focused-checks.log'
$taskJavaArgs = Join-Path $taskBuild 'java-checks.args'
Write-JavaArguments $taskJavaArgs @('-Xmx256m',"-Djava.io.tmpdir=$taskTemp",'-cp',$taskCheckClasspath,'com.sorrowmist.useless.stretcher.client.JeiHudLayoutChecks',$taskResultPath)
& java "@$taskJavaArgs" *> $taskCheckLog
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskCheckLog -Tail 75; throw 'Focused HUD checks failed.' }
Get-Content -LiteralPath $taskCheckLog -Tail 8
foreach ($taskInput in $taskInputHashes) {
    if ((Get-FileHash -LiteralPath $taskInput.path -Algorithm SHA256).Hash -cne $taskInput.sha256) { throw "Input changed during compilation/checks: $($taskInput.path)" }
}
$taskCheckResult = Get-Content -LiteralPath $taskResultPath -Raw | ConvertFrom-Json
if ($taskCheckResult.status -cne 'PASS' -or $taskCheckResult.checksFailed -ne 0 -or $taskCheckResult.checksPassed -le 0 -or $taskCheckResult.checksFound -ne $taskCheckResult.checksPassed) { throw 'Focused results did not pass.' }
$taskDelivery = Join-Path $taskRoot 'delivery'
New-Item -ItemType Directory -Path $taskDelivery -Force | Out-Null
$taskJar = Join-Path $taskDelivery 'useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar'
if (Test-Path -LiteralPath $taskJar) { throw 'Never overwrite an existing candidate.' }
Copy-Item -LiteralPath $taskBaseline -Destination $taskJar
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [IO.Compression.ZipFile]::Open($taskJar,[IO.Compression.ZipArchiveMode]::Update)
try {
    $taskEntries = @{'META-INF/neoforge.mods.toml'=$taskMetadata}
    Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object { $taskEntries[$_.FullName.Substring($taskClasses.Length+1).Replace('\','/')]=$_.FullName }
    foreach ($taskEntry in $taskEntries.GetEnumerator()) {
        $taskOld = $taskArchive.GetEntry($taskEntry.Key)
        if ($taskOld) { $taskOld.Delete() }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,$taskEntry.Value,$taskEntry.Key,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskArchive.Dispose() }
$taskBuildResult = [ordered]@{
    timestampUtc=[DateTime]::UtcNow.ToString('o');gameLaunched=$false;version='1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud'
    jar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash;size=(Get-Item -LiteralPath $taskJar).Length
    baselineSha256=$taskBaselineHash;classesDirectory=$taskClasses;checkClassesDirectory=$taskChecks
    compilerExitCode=0;checkCompilerExitCode=0;checkExitCode=0;checkArguments=$taskJavaArgs;checkCompilerArguments=$taskCheckArgs;mainCompilerArguments=$taskMainArgs
    checkLog=$taskCheckLog;checkLogSha256=(Get-FileHash -LiteralPath $taskCheckLog -Algorithm SHA256).Hash
    checkResult=$taskResultPath;checkResultSha256=(Get-FileHash -LiteralPath $taskResultPath -Algorithm SHA256).Hash
    inputs=$taskInputHashes
    mainSources=@($taskMainSources | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
    checkSources=@($taskCheckSources | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
    compiledClasses=@(Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{name=$_.FullName.Substring($taskClasses.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    checkCompiledClasses=@(Get-ChildItem -LiteralPath $taskChecks -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{name=$_.FullName.Substring($taskChecks.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    full184SuiteRerun=$false
}
$taskBuildResult | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-result.json') -Encoding utf8
[pscustomobject]$taskBuildResult | Select-Object jar,sha256,size
