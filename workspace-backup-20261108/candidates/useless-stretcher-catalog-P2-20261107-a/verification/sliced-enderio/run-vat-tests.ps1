$ErrorActionPreference = 'Stop'
$vatRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$vatAttempt = Join-Path $PSScriptRoot ('runs\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $vatAttempt -Force | Out-Null
$vatClasses = Join-Path $vatAttempt 'classes'
New-Item -ItemType Directory -Path $vatClasses -Force | Out-Null
$vatJar = 'D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\enderio-8.2.12-beta.jar'
$vatCore = Join-Path $PSScriptRoot 'com.enderio.endercore-8.2.12-beta.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (-not (Test-Path -LiteralPath $vatCore)) {
    $vatZip = [IO.Compression.ZipFile]::OpenRead($vatJar)
    try { [IO.Compression.ZipFileExtensions]::ExtractToFile($vatZip.GetEntry('META-INF/jarjar/com.enderio.endercore-8.2.12-beta.jar'), $vatCore) }
    finally { $vatZip.Dispose() }
}
$vatArgs = Get-Content -LiteralPath 'D:\AI\sources\UselessStretcher-capsule-variants\build\helper-check\harness-java.args'
$vatOldClasspath = $vatArgs[1].Trim('"').Split(';') | Where-Object {
    $_ -and (Test-Path -LiteralPath $_) -and $_ -notmatch 'useless_mod.*\.jar$'
}
$vatBase = 'D:\AI\sources\DollChronicle\verification\adventure-20261101\mod-freshness\uselessmod-staging\useless_mod-1.21.1-2.4.6.2.jar'
$vatLibraries = Get-ChildItem -LiteralPath (Join-Path $vatRoot 'verification\catalog-P2-20261107\test-libraries') -Filter '*.jar' | ForEach-Object FullName
$vatExtras = Get-ChildItem -LiteralPath (Join-Path $vatRoot 'libs\compile') -Filter '*.jar' | Where-Object Name -NotMatch '^useless_mod' | ForEach-Object FullName
$vatClasspath = (@($vatClasses,$vatBase,$vatJar,$vatCore,(Join-Path $vatRoot 'src\test\resources')) + $vatLibraries + $vatOldClasspath + $vatExtras) -join ';'
$vatSources = @(
    (Join-Path $vatRoot 'src\main\java\com\sorrowmist\useless\stretcher\content\catalog\SlicedRecipeConversions.java'),
    (Join-Path $vatRoot 'src\test\java\com\sorrowmist\useless\stretcher\VanillaTestRegistries.java'),
    (Join-Path $vatRoot 'src\test\java\com\sorrowmist\useless\stretcher\content\catalog\SlicedVatConversionTest.java'),
    (Join-Path $PSScriptRoot 'SlicedTestLauncher.java')
)
function Write-VatArguments([string]$path, [string[]]$arguments) {
    [IO.File]::WriteAllLines($path, @($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
}
Write-VatArguments (Join-Path $vatAttempt 'javac.args') (@('-proc:none','--release','21','-encoding','UTF-8','-d',$vatClasses,'-cp',$vatClasspath) + $vatSources)
& javac "-J-Djava.io.tmpdir=$vatAttempt" "@$(Join-Path $vatAttempt 'javac.args')" *> (Join-Path $vatAttempt 'compile.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $vatAttempt 'compile.log') -Tail 50; throw 'Vat test compilation failed' }
Write-VatArguments (Join-Path $vatAttempt 'java.args') @('-Xmx512m',"-Djava.io.tmpdir=$vatAttempt",'-cp',$vatClasspath,'SlicedTestLauncher')
& java "@$(Join-Path $vatAttempt 'java.args')" *> (Join-Path $vatAttempt 'tests.log')
$vatExit = $LASTEXITCODE
Get-Content -LiteralPath (Join-Path $vatAttempt 'tests.log') -Tail 85
Write-Output "Evidence: $vatAttempt"
exit $vatExit
