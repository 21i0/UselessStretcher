param(
    [Parameter(Mandatory=$true)][string]$DependencyPlan,
    [Parameter(Mandatory=$true)][string]$CheckPlan
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([IO.Path]::GetPathRoot($taskRoot) -ine 'D:\') { throw 'Outputs must remain on D:.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$DependencyPlan = (Resolve-Path -LiteralPath $DependencyPlan).Path
$CheckPlan = (Resolve-Path -LiteralPath $CheckPlan).Path
$taskBaseline = 'D:\AI\candidates\useless-stretcher-jei-hud-layout-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar'
$taskBaselineHash = 'E0E15336F1FFA8D1F9AC1839B7A29509BB2D6ECDC03C502AE92B1B4251048FB6'
if ((Get-FileHash -LiteralPath $taskBaseline -Algorithm SHA256).Hash -cne $taskBaselineHash) { throw 'Frozen HUD baseline changed.' }
$taskDelivery = Join-Path $taskRoot 'delivery'
$taskJar = Join-Path $taskDelivery 'useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970.jar'
if (Test-Path -LiteralPath $taskJar) { throw 'Never overwrite an existing candidate.' }
$taskDependencies = @(Get-Content -LiteralPath $DependencyPlan -Raw | ConvertFrom-Json)
if ($taskDependencies.Count -eq 0) { throw 'Exact dependency inputs are required.' }
$taskDependencyIds = @{}
foreach ($taskDependency in $taskDependencies) {
    if (-not $taskDependency.id -or $taskDependencyIds.ContainsKey($taskDependency.id)) { throw 'Dependency ids must be nonempty and unique.' }
    $taskDependencyIds[$taskDependency.id] = $true
    $taskDependency.path = (Resolve-Path -LiteralPath $taskDependency.path).Path
    if ([IO.Path]::GetExtension($taskDependency.path) -ine '.jar' -or $taskDependency.sha256 -notmatch '^[0-9a-fA-F]{64}$') { throw "Invalid exact JAR dependency: $($taskDependency.id)" }
    if ((Get-FileHash -LiteralPath $taskDependency.path -Algorithm SHA256).Hash -ine $taskDependency.sha256) { throw "Dependency hash mismatch: $($taskDependency.path)" }
}
$taskNewBase = @($taskDependencies | Where-Object id -CEQ 'base')
if ($taskNewBase.Count -ne 1) { throw "Dependency plan must contain exactly one id='base'." }
$taskNewBase = $taskNewBase[0]
$taskChecks = @(Get-Content -LiteralPath $CheckPlan -Raw | ConvertFrom-Json)
if ($taskChecks.Count -eq 0) { throw 'Focused checks are required.' }
$taskCheckIds = @{}
$taskLegacyBases = @()
foreach ($taskCheck in $taskChecks) {
    if ($taskCheck.id -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]*$' -or $taskCheckIds.ContainsKey($taskCheck.id)) { throw 'Check ids must be unique safe file-name components.' }
    if (-not $taskCheck.main -or @($taskCheck.sources).Count -eq 0) { throw "Missing main or sources for check $($taskCheck.id)" }
    $taskCheckIds[$taskCheck.id] = $true
    if ($null -ne $taskCheck.legacyBase) {
        $taskCheck.legacyBase.path = (Resolve-Path -LiteralPath $taskCheck.legacyBase.path).Path
        if ([IO.Path]::GetExtension($taskCheck.legacyBase.path) -ine '.jar' -or $taskCheck.legacyBase.sha256 -notmatch '^[0-9a-fA-F]{64}$') { throw "Invalid legacy base for $($taskCheck.id)" }
        if ($taskCheck.legacyBase.path -ieq $taskNewBase.path) { throw "Legacy base must differ from new base: $($taskCheck.id)" }
        if ((Get-FileHash -LiteralPath $taskCheck.legacyBase.path -Algorithm SHA256).Hash -ine $taskCheck.legacyBase.sha256) { throw "Legacy base hash mismatch: $($taskCheck.id)" }
        $taskLegacyBases += $taskCheck.legacyBase
    }
}
$taskRunId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$taskAttempt = Join-Path $PSScriptRoot ('attempts\' + $taskRunId)
New-Item -ItemType Directory -Path $taskAttempt -Force | Out-Null
foreach ($taskPrevious in @(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object { $_.Name -match '^(compile-|focused-|check-result|build-result)' })) {
    Copy-Item -LiteralPath $taskPrevious.FullName -Destination (Join-Path $taskAttempt $taskPrevious.Name)
}
$taskBuild = Join-Path $taskRoot ('build\upstream-' + $taskRunId)
$taskClasses = Join-Path $taskBuild 'classes'
$taskCheckClasses = Join-Path $taskBuild 'check-classes'
$taskTemp = Join-Path $taskBuild 'tmp'
foreach ($taskDir in @($taskClasses,$taskCheckClasses,$taskTemp)) { New-Item -ItemType Directory -Path $taskDir -Force | Out-Null }
$taskPriorResultPath = 'D:\AI\candidates\useless-stretcher-jei-hud-layout-20261107-a\verification\jei-hud-layout-20261107\build-result.json'
$taskPrior = Get-Content -LiteralPath $taskPriorResultPath -Raw | ConvertFrom-Json
$taskPriorClasspathPath = Join-Path (Split-Path $taskPrior.classesDirectory) 'classpath.txt'
$taskPriorClasspath = [IO.File]::ReadAllText($taskPriorClasspathPath)
# Use one frozen stretcher baseline and the exact new third-party group. No old mod
# JAR or loose helper class directory can hide a missing member in the new group.
$taskRetainedClasspath = @($taskPriorClasspath.Split(';') | Where-Object {
    $_ -and [IO.Path]::GetExtension($_) -ieq '.jar' -and (Test-Path -LiteralPath $_) -and
    [IO.Path]::GetFileName($_) -notmatch '^(useless_stretcher|useless[-_]mod|jei-|extendedae[-_]plus|data[-_]?energistics|appliedenergistics2|AppliedFlux|ldlib2|ae2wtlib)'
})
# Inspect contents as well as names: a renamed old base/JEI must not supply members
# absent from the official new JARs. Loose helper directories were excluded above.
foreach ($taskRetainedJar in $taskRetainedClasspath) {
    $taskProbe = [IO.Compression.ZipFile]::OpenRead($taskRetainedJar)
    try {
        if ($taskProbe.GetEntry('com/sorrowmist/useless/content/recipe/AlloyFurnaceRecipeCatalog.class') -or $taskProbe.GetEntry('mezz/jei/api/IModPlugin.class')) {
            throw "Retained classpath contains a shadow base/JEI: $taskRetainedJar"
        }
    } finally { $taskProbe.Dispose() }
}
$taskClasspathEntries = @(@($taskClasses,$taskBaseline) + @($taskDependencies | ForEach-Object path) + $taskRetainedClasspath | Select-Object -Unique)
$taskClasspath = $taskClasspathEntries -join ';'
[IO.File]::WriteAllText((Join-Path $taskBuild 'classpath.txt'),$taskClasspath,[Text.UTF8Encoding]::new($false))
$taskTestLibraries = @(Get-ChildItem -LiteralPath 'D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a\verification\catalog-P3-20261107\test-libraries' -Filter '*.jar' | ForEach-Object FullName)
$taskCheckClasspathEntries = @($taskCheckClasses) + $taskClasspathEntries + $taskTestLibraries
$taskCheckClasspath = $taskCheckClasspathEntries -join ';'
[IO.File]::WriteAllText((Join-Path $taskBuild 'check-classpath.txt'),$taskCheckClasspath,[Text.UTF8Encoding]::new($false))
$taskMainSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$taskCheckSources = @($taskChecks | ForEach-Object { $_.sources } | ForEach-Object { (Resolve-Path -LiteralPath $_).Path } | Select-Object -Unique)
$taskMetadata = Join-Path $taskRoot 'src\main\resources\META-INF\neoforge.mods.toml'
$taskInputPaths = @(@($taskMainSources) + @($taskCheckSources) + @($taskMetadata,$DependencyPlan,$CheckPlan,$PSCommandPath,$taskBaseline,$taskPriorResultPath,$taskPriorClasspathPath) + @($taskDependencies | ForEach-Object path) + @($taskLegacyBases | ForEach-Object path) + $taskRetainedClasspath + $taskTestLibraries | Select-Object -Unique)
$taskInputHashes = @($taskInputPaths | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
$taskInputHashes | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskBuild 'inputs-before-build.json') -Encoding utf8
function Write-JavaArguments([string]$path,[string[]]$arguments) {
    [IO.File]::WriteAllLines($path,@($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }),[Text.UTF8Encoding]::new($false))
}
function Read-FocusedResult([string]$path) {
    $taskValue = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    $taskFields = @($taskValue.PSObject.Properties.Name)
    if ($taskValue.status -ceq 'PASS' -and $taskFields -contains 'checksFound') {
        foreach ($taskField in @('checksFound','checksPassed','checksFailed')) {
            if ($taskFields -notcontains $taskField -or [string]$taskValue.$taskField -notmatch '^\d+$') { throw "Missing/invalid result count $taskField in $path" }
        }
        $taskFound = [long]$taskValue.checksFound
        $taskPassed = [long]$taskValue.checksPassed
        $taskFailed = [long]$taskValue.checksFailed
        $taskStartedDerived = $taskFields -notcontains 'checksStarted'
        if ($taskStartedDerived) { $taskStarted = $taskPassed + $taskFailed }
        else {
            if ([string]$taskValue.checksStarted -notmatch '^\d+$') { throw "Invalid checksStarted in $path" }
            $taskStarted = [long]$taskValue.checksStarted
        }
        $taskSchema = 'PASS/checksFound/checksPassed/checksFailed'
    } elseif ($taskValue.status -ceq 'PASSED' -and $taskFields -contains 'checks') {
        foreach ($taskField in @('passed','failed')) {
            if ($taskFields -notcontains $taskField -or [string]$taskValue.$taskField -notmatch '^\d+$') { throw "Missing/invalid result count $taskField in $path" }
        }
        $taskRows = @($taskValue.checks)
        $taskFound = [long]$taskRows.Count
        $taskPassed = [long]$taskValue.passed
        $taskFailed = [long]$taskValue.failed
        if (@($taskRows | Where-Object status -CEQ 'PASSED').Count -ne $taskPassed -or @($taskRows | Where-Object status -CEQ 'FAILED').Count -ne $taskFailed) { throw "Result rows disagree with counts: $path" }
        $taskStarted = $taskRows.Count
        $taskStartedDerived = $true
        $taskSchema = 'PASSED/passed/failed/checks-rows'
    } else { throw "Unknown or failing focused result schema: $path" }
    if ($taskFailed -ne 0 -or $taskFound -le 0 -or $taskPassed -ne $taskFound -or $taskStarted -ne $taskFound) { throw "Incomplete/failing focused checks: $path" }
    [pscustomobject]@{schema=$taskSchema;checksFound=$taskFound;checksStarted=$taskStarted;checksPassed=$taskPassed;checksFailed=$taskFailed;startedDerivedFromCompleted=$taskStartedDerived}
}
& javac -version *> (Join-Path $taskBuild 'javac-version.log')
if ($LASTEXITCODE -ne 0) { throw 'No javac.' }
& java -version *> (Join-Path $taskBuild 'java-version.log')
if ($LASTEXITCODE -ne 0) { throw 'No java.' }
$taskMainArgs = Join-Path $taskBuild 'javac-main.args'
Write-JavaArguments $taskMainArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskClasses,'-cp',$taskClasspath) + $taskMainSources)
$taskMainLog = Join-Path $taskBuild 'compile-main.log'
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskMainArgs" *> $taskMainLog
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskMainLog -Tail 65; throw 'Production compilation failed.' }
$taskCheckArgs = Join-Path $taskBuild 'javac-checks.args'
Write-JavaArguments $taskCheckArgs (@('-proc:none','--release','21','-encoding','UTF-8','-d',$taskCheckClasses,'-cp',$taskCheckClasspath) + $taskCheckSources)
$taskCheckCompileLog = Join-Path $taskBuild 'compile-checks.log'
& javac "-J-Djava.io.tmpdir=$taskTemp" "@$taskCheckArgs" *> $taskCheckCompileLog
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskCheckCompileLog -Tail 65; throw 'Focused check compilation failed.' }
$taskCheckClassNames = @(Get-ChildItem -LiteralPath $taskCheckClasses -Filter '*.class' -Recurse | ForEach-Object { $_.FullName.Substring($taskCheckClasses.Length+1).Replace('\','/') })
foreach ($taskCheckClassName in $taskCheckClassNames) {
    if (Test-Path -LiteralPath (Join-Path $taskClasses $taskCheckClassName)) { throw "Focused class shadows compiled production: $taskCheckClassName" }
}
foreach ($taskProductionJar in @($taskClasspathEntries | Where-Object { [IO.Path]::GetExtension($_) -ieq '.jar' })) {
    $taskProbe = [IO.Compression.ZipFile]::OpenRead($taskProductionJar)
    try {
        foreach ($taskCheckClassName in $taskCheckClassNames) {
            if ($taskProbe.GetEntry($taskCheckClassName)) { throw "Focused class shadows production/dependency $taskCheckClassName in $taskProductionJar" }
        }
    } finally { $taskProbe.Dispose() }
}
$taskCompiledHashes = @(Get-ChildItem -LiteralPath $taskClasses,$taskCheckClasses -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
$taskCheckReceipts = @()
foreach ($taskCheck in $taskChecks) {
    $taskLog = Join-Path $taskBuild ('focused-' + $taskCheck.id + '.log')
    $taskResult = Join-Path $taskBuild ('check-result-' + $taskCheck.id + '.json')
    $taskRunArgs = Join-Path $taskBuild ('java-' + $taskCheck.id + '.args')
    $taskRuntimeClasspathEntries = @($taskCheckClasspathEntries)
    if ($null -ne $taskCheck.legacyBase) {
        if (@($taskRuntimeClasspathEntries | Where-Object { [IO.Path]::GetFullPath($_) -ieq $taskNewBase.path }).Count -ne 1) { throw "Expected exactly one new-base runtime entry: $($taskCheck.id)" }
        $taskRuntimeClasspathEntries = @($taskRuntimeClasspathEntries | ForEach-Object {
            if ([IO.Path]::GetFullPath($_) -ieq $taskNewBase.path) { $taskCheck.legacyBase.path } else { $_ }
        })
        if (@($taskRuntimeClasspathEntries | Where-Object { [IO.Path]::GetFullPath($_) -ieq $taskNewBase.path }).Count -ne 0) { throw "New base survived legacy replacement: $($taskCheck.id)" }
        if ((Get-FileHash -LiteralPath $taskCheck.legacyBase.path -Algorithm SHA256).Hash -ine $taskCheck.legacyBase.sha256) { throw "Legacy base changed before check: $($taskCheck.id)" }
    }
    $taskRuntimeClasspath = Join-Path $taskBuild ('runtime-classpath-' + $taskCheck.id + '.txt')
    [IO.File]::WriteAllText($taskRuntimeClasspath,($taskRuntimeClasspathEntries -join ';'),[Text.UTF8Encoding]::new($false))
    $taskArguments = @($taskCheck.arguments | ForEach-Object { $_.Replace('{result}',$taskResult) })
    Write-JavaArguments $taskRunArgs (@('-Xmx256m',"-Djava.io.tmpdir=$taskTemp",'-cp',($taskRuntimeClasspathEntries -join ';'),$taskCheck.main) + $taskArguments)
    & java "@$taskRunArgs" *> $taskLog
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskLog -Tail 75; throw "Focused check failed: $($taskCheck.id)" }
    Get-Content -LiteralPath $taskLog -Tail 5
    $taskResultValue = Read-FocusedResult $taskResult
    if ($null -ne $taskCheck.legacyBase -and (Get-FileHash -LiteralPath $taskCheck.legacyBase.path -Algorithm SHA256).Hash -ine $taskCheck.legacyBase.sha256) { throw "Legacy base changed during check: $($taskCheck.id)" }
    $taskCheckReceipts += [ordered]@{
        id=$taskCheck.id;exitCode=0;arguments=$taskRunArgs;argumentsSha256=(Get-FileHash -LiteralPath $taskRunArgs -Algorithm SHA256).Hash
        runtimeClasspath=$taskRuntimeClasspath;runtimeClasspathSha256=(Get-FileHash -LiteralPath $taskRuntimeClasspath -Algorithm SHA256).Hash;legacyBase=$taskCheck.legacyBase
        log=$taskLog;logSha256=(Get-FileHash -LiteralPath $taskLog -Algorithm SHA256).Hash;result=$taskResult;resultSha256=(Get-FileHash -LiteralPath $taskResult -Algorithm SHA256).Hash
        resultSchema=$taskResultValue.schema;checksFound=$taskResultValue.checksFound;checksStarted=$taskResultValue.checksStarted;checksPassed=$taskResultValue.checksPassed;checksFailed=$taskResultValue.checksFailed;startedDerivedFromCompleted=$taskResultValue.startedDerivedFromCompleted
    }
}
foreach ($taskInput in $taskInputHashes) { if ((Get-FileHash -LiteralPath $taskInput.path -Algorithm SHA256).Hash -cne $taskInput.sha256) { throw "Input changed during build: $($taskInput.path)" } }
foreach ($taskDependency in $taskDependencies) { if ((Get-FileHash -LiteralPath $taskDependency.path -Algorithm SHA256).Hash -ine $taskDependency.sha256) { throw "Dependency changed during build: $($taskDependency.path)" } }
foreach ($taskCompiled in $taskCompiledHashes) { if ((Get-FileHash -LiteralPath $taskCompiled.path -Algorithm SHA256).Hash -cne $taskCompiled.sha256) { throw "Compiled class changed during checks: $($taskCompiled.path)" } }
New-Item -ItemType Directory -Path $taskDelivery -Force | Out-Null
if (Test-Path -LiteralPath $taskJar) { throw 'Never overwrite an existing candidate.' }
# Incomplete packaging remains in this unique build directory, never at the final name.
$taskStagedJar = Join-Path $taskBuild 'candidate-unverified.jar'
Copy-Item -LiteralPath $taskBaseline -Destination $taskStagedJar
$taskArchive = [IO.Compression.ZipFile]::Open($taskStagedJar,[IO.Compression.ZipArchiveMode]::Update)
try {
    $taskEntries = @{'META-INF/neoforge.mods.toml'=$taskMetadata}
    Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object { $taskEntries[$_.FullName.Substring($taskClasses.Length+1).Replace('\','/')]=$_.FullName }
    foreach ($taskEntry in $taskEntries.GetEnumerator()) {
        $taskOld = $taskArchive.GetEntry($taskEntry.Key); if ($taskOld) { $taskOld.Delete() }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,$taskEntry.Value,$taskEntry.Key,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskArchive.Dispose() }
$taskArchive = [IO.Compression.ZipFile]::OpenRead($taskStagedJar)
try {
    $taskMemberNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($taskMember in $taskArchive.Entries) {
        if (-not $taskMemberNames.Add($taskMember.FullName)) { throw "Duplicate packaged member: $($taskMember.FullName)" }
        if ($taskCheckClassNames -ccontains $taskMember.FullName -or $taskMember.FullName -match '^(org/junit/|org/mockito/|net/bytebuddy/|org/objenesis/|com/sorrowmist/useless/stretcher/verification/)' -or $taskMember.FullName -match '(Test|Tests|Checks)(\$[^/]*)?\.class$|\.jar$') {
            throw "Test/dependency material entered candidate: $($taskMember.FullName)"
        }
    }
} finally { $taskArchive.Dispose() }
foreach ($taskInput in $taskInputHashes) { if ((Get-FileHash -LiteralPath $taskInput.path -Algorithm SHA256).Hash -cne $taskInput.sha256) { throw "Input changed during packaging: $($taskInput.path)" } }
if (Test-Path -LiteralPath $taskJar) { throw 'Never overwrite an existing candidate.' }
Move-Item -LiteralPath $taskStagedJar -Destination $taskJar
$taskBuildResult = [ordered]@{
    timestampUtc=[DateTime]::UtcNow.ToString('o');gameLaunched=$false;full184SuiteRerun=$false
    jar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash;size=(Get-Item -LiteralPath $taskJar).Length;baselineSha256=$taskBaselineHash
    classesDirectory=$taskClasses;checkClassesDirectory=$taskCheckClasses;compilerExitCode=0;checkCompilerExitCode=0
    mainCompilerArguments=$taskMainArgs;mainCompilerArgumentsSha256=(Get-FileHash -LiteralPath $taskMainArgs -Algorithm SHA256).Hash
    checkCompilerArguments=$taskCheckArgs;checkCompilerArgumentsSha256=(Get-FileHash -LiteralPath $taskCheckArgs -Algorithm SHA256).Hash
    mainCompilerLog=$taskMainLog;mainCompilerLogSha256=(Get-FileHash -LiteralPath $taskMainLog -Algorithm SHA256).Hash
    checkCompilerLog=$taskCheckCompileLog;checkCompilerLogSha256=(Get-FileHash -LiteralPath $taskCheckCompileLog -Algorithm SHA256).Hash
    classpath=(Join-Path $taskBuild 'classpath.txt');classpathSha256=(Get-FileHash -LiteralPath (Join-Path $taskBuild 'classpath.txt') -Algorithm SHA256).Hash
    checkClasspath=(Join-Path $taskBuild 'check-classpath.txt');checkClasspathSha256=(Get-FileHash -LiteralPath (Join-Path $taskBuild 'check-classpath.txt') -Algorithm SHA256).Hash
    dependencies=$taskDependencies;legacyBases=$taskLegacyBases;checks=$taskCheckReceipts;inputs=$taskInputHashes
    mainSources=@($taskMainSources | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
    checkSources=@($taskCheckSources | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} })
    compiledClasses=@(Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{name=$_.FullName.Substring($taskClasses.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    checkCompiledClasses=@(Get-ChildItem -LiteralPath $taskCheckClasses -Filter '*.class' -Recurse | ForEach-Object { [ordered]@{name=$_.FullName.Substring($taskCheckClasses.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
}
$taskBuildResult | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskBuild 'build-result.json') -Encoding utf8
$taskBuildResult | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-result.json') -Encoding utf8
[pscustomobject]$taskBuildResult | Select-Object jar,sha256,size
