param([string]$Jar)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path $PSScriptRoot -Parent
$propertiesText = [IO.File]::ReadAllText((Join-Path $repo 'gradle.properties'))
$versionMatch = [regex]::Match($propertiesText, '(?m)^mod_version=([^\r\n]+)')
if (!$versionMatch.Success) { throw 'Missing mod_version in gradle.properties' }
$expectedVersion = $versionMatch.Groups[1].Value.Trim()
if (!$Jar) { $Jar = "build/libs/useless_stretcher-$expectedVersion.jar" }
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
    $versionPattern = '(?m)^version="' + [regex]::Escape($expectedVersion) + '"\r?$'
    if ($metadata -notmatch $versionPattern) { throw "Wrong package version; expected $expectedVersion" }
    if ($metadata -notmatch '(?s)modId="useless_mod"\s+type="required"\s+versionRange="\[1\.21\.1-2\.4\.6,\)"') {
        throw 'Wrong minimum Useless Mod dependency'
    }
    $networkSource = [IO.File]::ReadAllText((Join-Path $repo 'src/main/java/com/sorrowmist/useless/stretcher/network/Network.java'))
    if ($networkSource -notmatch 'event\.registrar\("22"\)') { throw 'Wrong network protocol source' }
    $mixins = (Read-Entry $zip.GetEntry('useless_stretcher.mixins.json')) | ConvertFrom-Json -AsHashtable
    foreach ($mixin in @('StaffPhaseMovementMixin', 'StaffPhasePacketMovementMixin', 'CapsuleGenerationHeightMixin',
            'CapsuleScatteredHeightMixin', 'DesertPyramidCheckpointMixin', 'TwoIDollRemovalGuardMixin',
            'TwoIDollLookupGuardMixin', 'TwoIDollTickListGuardMixin', 'TwoIDollSectionGuardMixin',
            'TwoIDollManagerLifecycleMixin', 'TwoIDollSectionLifecycleMixin', 'TwoIDollLevelCallbackGuardMixin')) {
        if ($mixins.mixins -notcontains $mixin) { throw "Missing common mixin: $mixin" }
    }
    foreach ($mixin in @('StaffPhaseLocalMovementMixin', 'StaffPhaseVisibilityMixin', 'TwoIDollClientRemovalMixin')) {
        if ($mixins.client -notcontains $mixin) { throw "Missing client mixin: $mixin" }
    }
    $guidePages = @($zip.Entries | Where-Object { $_.FullName -match '^assets/ae2/ae2guide/useless_stretcher/.*\.md$' })
    if ($guidePages.Count -ne 9) { throw 'Incomplete GuideME pages' }
    foreach ($page in $guidePages) {
        if ((Read-Entry $page) -match 'useful_armor|有用锭装备') { throw "Hidden armor remains in GuideME: $($page.FullName)" }
    }
    $armorPieces = @('helmet', 'chestplate', 'leggings', 'boots')
    $hiddenEntry = $zip.GetEntry('data/c/tags/item/hidden_from_recipe_viewers.json')
    if (!$hiddenEntry) { throw 'Missing Useful Ingot armor recipe-viewer hide tag' }
    $hiddenTag = (Read-Entry $hiddenEntry) | ConvertFrom-Json -AsHashtable
    $expectedHidden = @($armorPieces | ForEach-Object { "useless_stretcher:useful_armor_$_" })
    if ($hiddenTag.replace -ne $false -or $hiddenTag.values.Count -ne 4 -or
            @(Compare-Object $expectedHidden $hiddenTag.values).Count -ne 0) {
        throw 'Armor hide tag must add exactly the four armor items without replacing other mods'
    }
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
    $skinHash = '597DBE84E55288118666187E127167F30686E426D3C0693B398AA63EA6B4E44D'
    if ((Entry-Hash $zip.GetEntry('assets/useless_stretcher/textures/entity/two_i.png')) -ne $skinHash) {
        throw '2i skin differs from the supplied artist skin'
    }
    $oldSkinHash = 'FE85AC40D1952D4A4A8FAA123FA0DD496DC080C7BF777F1EACD962CF8CD4D322'
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.EndsWith('.png') -and (Entry-Hash $entry) -eq $oldSkinHash) {
            throw "Old 2i skin remains: $($entry.FullName)"
        }
    }
    foreach ($modelPath in @('assets/useless_stretcher/models/item/two_i_doll.json', 'assets/useless_stretcher/models/block/two_i_doll_display.json')) {
        $particleModel = (Read-Entry $zip.GetEntry($modelPath)) | ConvertFrom-Json -AsHashtable
        if ($particleModel.textures.particle -ne 'useless_stretcher:entity/two_i') { throw "Old 2i particle reference: $modelPath" }
    }
    foreach ($tailResource in @('assets/useless_stretcher/models/entity/two_i_tail.json', 'assets/useless_stretcher/textures/entity/two_i_tail.png', 'TWO_I_TAIL_CREDITS.txt', 'TWO_I_TAIL_LICENSE.txt')) {
        if (!$zip.GetEntry($tailResource)) { throw "Missing tail resource: $tailResource" }
    }
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
        version = $expectedVersion
        network_protocol = 22
        guide_pages_without_hidden_armor = $guidePages.Count
        minimum_useless_mod = '1.21.1-2.4.6'
        animated_armor_items = $armorPieces.Count
        armor_hidden_from_recipe_viewers = $expectedHidden.Count
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
        artist_skin_sha256 = $skinHash
        old_skin_png_matches = 0
        tail_geometry_texture_and_attribution = $true
    } | ConvertTo-Json
} finally { $zip.Dispose() }
