$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$taskRepository = 'D:\AI\sources\UselessStretcher-capsule-variants'
$taskBaseFixture = 'D:\AI\sources\UselessStretcher\build\mod-fixture-occultism-full-1.224.4-20261019'
$taskPackMods = 'D:\AI\packs\MyGenesis-0.3.0\overrides\mods'
$taskEvidence = Join-Path $taskRepository 'verification\watchdog-20261105\third-party'
$taskDestination = Join-Path $taskRepository 'build\fixture-watchdog-20261105'
if (Test-Path -LiteralPath $taskDestination) { throw "Refusing to overwrite existing fixture: $taskDestination" }

$taskSources = @(
    @{path=(Join-Path $taskPackMods 'appliedenergistics2-19.2.18.jar'); reportVersion='19.2.18'},
    @{path=(Join-Path $taskBaseFixture 'guideme-21.1.17.jar'); reportVersion='21.1.20'; difference='Local 21.1.17 instead of reported 21.1.20; satisfies AE2 and addon requirements.'},
    @{path=(Join-Path $taskBaseFixture 'useless_mod-1.21.1-2.4.6.jar'); reportVersion='1.21.1-2.4.6.3'; difference='Local 2.4.6 instead of reported 2.4.6.3; minimum supported addon dependency.'},
    @{path=(Join-Path $taskBaseFixture 'productivebees-377897-8022994.jar'); reportVersion='1.21.1-13.13.5'},
    @{path=(Join-Path $taskPackMods 'Mekanism-1.21.1-10.7.19.85.jar'); reportVersion='10.7.19'; reportFile='Mekanism-1.21.1-10.7.19.85.jar'},
    @{path=(Join-Path $taskEvidence 'mekanism_extras-1.21.1-1.4.1.jar'); reportVersion='1.4.1'; url='https://github.com/lostmyself8/Mekanism-Extras/releases/download/v1.4.1-1.21.1/mekanism_extras-1.21.1-1.4.1.jar'; expected='7138AA6530CC184EF6838B02EC7138491907C045E870507BF11AE58CC4C70541'},
    @{path=(Join-Path $taskEvidence 'productivebeesgenesis-1.0.9.jar'); reportVersion='1.0.9'; url='https://github.com/Ayoshiko/productive-bees-genesis/releases/download/v1.0.9/productivebeesgenesis-1.0.9.jar'; expected='CBD6280828A5AF08A92A9F857FB886B4CBC3E1C05773765AE80A12675E1FA2E5'},
    @{path=(Join-Path $taskBaseFixture 'just-dire-things-1002348-7463040.jar'); reportVersion='1.5.7'},
    @{path=(Join-Path $taskEvidence 'jdte-0.6.4.3.jar'); reportVersion='0.6.4.3'; url='https://github.com/rulanup/JDTE/releases/download/v0.6.4.3/jdte-0.6.4.3.jar'; expected='E1D4481DDC089F77331EA57BC2E2EDE037BDE8E416DF8B21F4B76CACB32BD605'},
    @{path=(Join-Path $taskBaseFixture 'curios-neoforge-9.5.1+1.21.1.jar'); reportVersion='9.5.1+1.21.1'},
    @{path=(Join-Path $taskBaseFixture 'geckolib-neoforge-1.21.1-4.9.3.jar'); reportVersion='4.9.2'; difference='Local 4.9.3 instead of reported 4.9.2; optional Productive Bees renderer dependency.'}
)

function Read-ArchiveText($archive, $name) {
    $entry = $archive.GetEntry($name)
    if (!$entry) { return $null }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-TomlScalar($section, $key) {
    $match = [regex]::Match($section, '(?m)^\s*' + [regex]::Escape($key) + '\s*=\s*"([^"]*)"')
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

# Validate source existence and official checksums before creating or copying anything.
foreach ($source in $taskSources) {
    $item = Get-Item -LiteralPath $source.path
    $hash = (Get-FileHash -LiteralPath $source.path -Algorithm SHA256).Hash
    if ($source.expected -and $hash -ne $source.expected) { throw "Digest mismatch: $($source.path)" }
    $source.hash = $hash
    $source.bytes = $item.Length
}

New-Item -ItemType Directory -Path $taskDestination | Out-Null
$taskMetadataDirectory = Join-Path $taskDestination 'metadata'
New-Item -ItemType Directory -Path $taskMetadataDirectory | Out-Null
$taskRecords = foreach ($source in $taskSources) {
    $name = Split-Path -Path $source.path -Leaf
    $destination = Join-Path $taskDestination $name
    Copy-Item -LiteralPath $source.path -Destination $destination
    $archive = [IO.Compression.ZipFile]::OpenRead($destination)
    try {
        $toml = Read-ArchiveText $archive 'META-INF/neoforge.mods.toml'
        if (!$toml) { $toml = Read-ArchiveText $archive 'META-INF/mods.toml' }
        $toml | Set-Content -LiteralPath (Join-Path $taskMetadataDirectory ($name + '.toml')) -Encoding utf8
        $mods = @([regex]::Matches($toml, '(?ms)^\[\[mods\]\][^\r\n]*\r?\n(.*?)(?=^\[|\z)') | ForEach-Object {
            @{id=(Get-TomlScalar $_.Groups[1].Value 'modId'); version=(Get-TomlScalar $_.Groups[1].Value 'version')}
        })
        $dependencies = @([regex]::Matches($toml, '(?ms)^\[\[dependencies[^\]]*\]\][^\r\n]*\r?\n(.*?)(?=^\[|\z)') | ForEach-Object {
            $section = $_.Groups[1].Value
            @{id=(Get-TomlScalar $section 'modId'); type=(Get-TomlScalar $section 'type'); range=(Get-TomlScalar $section 'versionRange'); side=(Get-TomlScalar $section 'side')}
        })
        $nested = @($archive.Entries | Where-Object { $_.FullName -like 'META-INF/jarjar/*.jar' } | Select-Object -ExpandProperty FullName)
        [ordered]@{file=$name; source=$source.path; officialUrl=$source.url; bytes=$source.bytes; sha256=$source.hash; officialDigestChecked=[bool]$source.expected; mods=$mods; dependencies=$dependencies; nestedJars=$nested; reportedVersion=$source.reportVersion; reportedFile=$source.reportFile; difference=$source.difference}
    } finally { $archive.Dispose() }
}

$taskManifest = [ordered]@{
    createdUtc=[DateTime]::UtcNow.ToString('o')
    purpose='Isolated real-server regression dependency fixture for Wondrous Staff acceleration / PBG 1.0.9 Extra centrifuge compatibility. No game launched by fixture preparation.'
    minecraft='1.21.1'
    neoforge='21.1.249 (development runtime; production report 21.1.264)'
    addon='Supplied by ModDev source set, deliberately excluded from the mods fixture.'
    originalFilesPreserved=$true
    jdteAe='0.6.4.3 artifact is preserved in verification/third-party but is not installed: the production crash report lists JDTE core only.'
    metadataCaveat='The unchanged Productive Bees 13.13.5 jar declares Minecraft [1.21,1.21.1), although it identifies itself as 1.21.1-13.13.5. Actual NeoForge startup validation remains required; no dependency metadata was patched.'
    files=$taskRecords
}
$taskManifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $taskDestination 'fixture-manifest.json') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $taskDestination 'fixture-manifest.json') -Destination (Join-Path $taskRepository 'verification\watchdog-20261105\fixture-manifest.json')
$taskRecords | ForEach-Object { [pscustomobject]@{file=$_.file; bytes=$_.bytes; sha256=$_.sha256; difference=$_.difference} } | Format-Table -AutoSize
