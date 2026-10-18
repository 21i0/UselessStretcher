param([string]$Jar = 'build/libs/useless_stretcher-1.21.1-1.6.0.jar')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path $PSScriptRoot -Parent
$artifact = if ([IO.Path]::IsPathRooted($Jar)) { $Jar } else { Join-Path $repo $Jar }
$zip = [IO.Compression.ZipFile]::OpenRead($artifact)
function Read-Entry($entry) {
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Entry-Hash($entry) {
    $stream = $entry.Open()
    try { return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
    finally { $stream.Dispose() }
}
try {
    $names = @($zip.Entries.FullName)
    $forbidden = @($names | Where-Object {
        $_ -match '/(regression|storytest|guidetest)/|Checks[^/]*\.class$|^net/|^mekanism/|^mcjty/|^com/klikli_dev/|^software/bernie/|^top/theillusivec4/curios/|^data/useless_stretcher_test/|QuadChunk|NineChunk|quad_chunk|nine_chunk'
    })
    if ($forbidden.Count) { throw "Unexpected packaged entries: $($forbidden -join ', ')" }
    $metadata = Read-Entry $zip.GetEntry('META-INF/neoforge.mods.toml')
    if ($metadata -notmatch '(?m)^version="1\.21\.1-1\.6\.0"') { throw 'Wrong package version' }
    if ($metadata -notmatch '(?s)modId="useless_mod"\s+type="required"\s+versionRange="\[1\.21\.1-2\.4\.6,\)"') {
        throw 'Wrong minimum Useless Mod dependency'
    }
    $armorPieces = @('helmet', 'chestplate', 'leggings', 'boots')
    foreach ($piece in $armorPieces) {
        $modelEntry = $zip.GetEntry("assets/useless_stretcher/models/item/useful_armor_$piece.json")
        $textureEntry = $zip.GetEntry("assets/useless_stretcher/textures/item/useful_armor_$piece.png")
        $animationEntry = $zip.GetEntry("assets/useless_stretcher/textures/item/useful_armor_$piece.png.mcmeta")
        if (!$modelEntry -or !$textureEntry -or !$animationEntry) { throw "Missing armor asset: $piece" }
        $model = (Read-Entry $modelEntry) | ConvertFrom-Json -AsHashtable
        if ($model.textures.layer0 -ne "useless_stretcher:item/useful_armor_$piece") {
            throw "Wrong armor model texture: $piece"
        }
        $animation = (Read-Entry $animationEntry) | ConvertFrom-Json -AsHashtable
        if (!$animation.ContainsKey('animation')) { throw "Missing armor animation: $piece" }
        if ($zip.GetEntry("data/useless_stretcher/recipe/useful_armor_$piece.json")) {
            throw "Armor recipe bypasses the default-off server switch: $piece"
        }
    }
    $jsonCount = 0
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match '\.(json|mcmeta)$') {
            $null = (Read-Entry $entry) | ConvertFrom-Json -AsHashtable
            $jsonCount++
        }
    }
    $sourceCount = 0
    foreach ($source in Get-ChildItem -LiteralPath (Join-Path $repo 'src/main/java') -Recurse -File -Filter '*.java') {
        $relative = [IO.Path]::GetRelativePath((Join-Path $repo 'src/main/java'), $source.FullName).Replace('\', '/')
        $class = $relative -replace '\.java$', '.class'
        $entry = $zip.GetEntry($class)
        if (!$entry) { throw "Missing production class: $class" }
        $compiled = Join-Path $repo "build/classes/java/main/$class"
        if ((Entry-Hash $entry) -ne (Get-FileHash -LiteralPath $compiled -Algorithm SHA256).Hash) {
            throw "Compiled class mismatch: $class"
        }
        $sourceCount++
    }
    $classCount = 0
    foreach ($entry in $zip.Entries) {
        if (!$entry.FullName.EndsWith('.class')) { continue }
        $compiled = Join-Path $repo "build/classes/java/main/$($entry.FullName)"
        if (!(Test-Path -LiteralPath $compiled) -or
                (Entry-Hash $entry) -ne (Get-FileHash -LiteralPath $compiled -Algorithm SHA256).Hash) {
            throw "Packaged bytecode mismatch: $($entry.FullName)"
        }
        $classCount++
    }
    $resourceCount = 0
    foreach ($root in @('src/main/resources', 'src/generated/resources')) {
        $base = Join-Path $repo $root
        foreach ($resource in Get-ChildItem -LiteralPath $base -Recurse -File) {
            if ($resource.FullName -match '[\\/]\.cache[\\/]') { continue }
            $relative = [IO.Path]::GetRelativePath($base, $resource.FullName).Replace('\', '/')
            $entry = $zip.GetEntry($relative)
            if (!$entry) { throw "Missing production resource: $relative" }
            if ((Entry-Hash $entry) -ne (Get-FileHash -LiteralPath $resource.FullName -Algorithm SHA256).Hash) {
                throw "Source resource mismatch: $relative"
            }
            $resourceCount++
        }
    }
    $chapters = @($names | Where-Object { $_ -match '/long_stories/chapter_[0-5]\.json$' })
    $pictures = @($names | Where-Object { $_ -match '/textures/dialogue/.*\.png$' })
    $sounds = @($names | Where-Object { $_ -match '/sounds/.*\.ogg$' })
    if ($chapters.Count -ne 6 -or $pictures.Count -ne 17 -or $sounds.Count -ne 7) {
        throw 'Incomplete story/visual/audio resources'
    }
    [ordered]@{
        result = 'JAR CHECKS PASSED'
        jar = $artifact
        version = '1.21.1-1.6.0'
        minimum_useless_mod = '1.21.1-2.4.6'
        animated_armor_items = $armorPieces.Count
        bytes = (Get-Item -LiteralPath $artifact).Length
        sha256 = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
        production_sources_with_matching_class = $sourceCount
        production_classes_with_matching_bytecode = $classCount
        source_resources_with_matching_hash = $resourceCount
        json_mcmeta_parsed = $jsonCount
        story_chapters = $chapters.Count
        story_images = $pictures.Count
        original_ogg_files = $sounds.Count
        forbidden_entries = $forbidden.Count
    } | ConvertTo-Json
} finally { $zip.Dispose() }
