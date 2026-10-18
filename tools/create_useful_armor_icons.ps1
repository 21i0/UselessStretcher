param(
    [string]$Upstream = 'libs/compile/useless_mod-1.21.1-2.4.6.jar',
    [string]$Minecraft = 'build/moddev/artifacts/neoforge-21.1.249-client-extra-aka-minecraft-resources.jar'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path $PSScriptRoot -Parent
$upstreamZip = [IO.Compression.ZipFile]::OpenRead((Join-Path $repo $Upstream))
$minecraftZip = [IO.Compression.ZipFile]::OpenRead((Join-Path $repo $Minecraft))
try {
    $ingotStream = $upstreamZip.GetEntry('assets/useless_mod/textures/item/ingot/useful_ingot.png').Open()
    $ingot = [Drawing.Bitmap]::new($ingotStream)
    $metadataReader = [IO.StreamReader]::new($upstreamZip.GetEntry('assets/useless_mod/textures/item/ingot/useful_ingot.png.mcmeta').Open())
    try { $metadata = $metadataReader.ReadToEnd() } finally { $metadataReader.Dispose() }
    $frames = [int]($ingot.Height / $ingot.Width)
    foreach ($piece in @('helmet', 'chestplate', 'leggings', 'boots')) {
        $maskStream = $minecraftZip.GetEntry("assets/minecraft/textures/item/diamond_$piece.png").Open()
        $mask = [Drawing.Bitmap]::new($maskStream)
        $result = [Drawing.Bitmap]::new(16, 16 * $frames)
        try {
            for ($frame = 0; $frame -lt $frames; $frame++) {
                for ($y = 0; $y -lt 16; $y++) {
                    for ($x = 0; $x -lt 16; $x++) {
                        $base = $mask.GetPixel($x, $y)
                        if ($base.A -eq 0) { continue }
                        # Repeat the actual ingot's opaque color field inside the original armor silhouette.
                        $sample = $ingot.GetPixel($x, $frame * 16 + $y)
                        if ($sample.A -eq 0) {
                            $distance = [double]::MaxValue
                            for ($sy = 0; $sy -lt 16; $sy++) {
                                for ($sx = 0; $sx -lt 16; $sx++) {
                                    $candidate = $ingot.GetPixel($sx, $frame * 16 + $sy)
                                    $d = ($x - $sx) * ($x - $sx) + ($y - $sy) * ($y - $sy)
                                    if ($candidate.A -gt 0 -and $d -lt $distance) { $distance = $d; $sample = $candidate }
                                }
                            }
                        }
                        $shade = 0.5 + 0.5 * ([Math]::Max($base.R, [Math]::Max($base.G, $base.B)) / 255.0)
                        $result.SetPixel($x, $frame * 16 + $y, [Drawing.Color]::FromArgb($base.A,
                            [int]($sample.R * $shade), [int]($sample.G * $shade), [int]($sample.B * $shade)))
                    }
                }
            }
            $output = Join-Path $repo "src/main/resources/assets/useless_stretcher/textures/item/useful_armor_$piece.png"
            $result.Save($output, [Drawing.Imaging.ImageFormat]::Png)
            [IO.File]::WriteAllText("$output.mcmeta", $metadata, [Text.UTF8Encoding]::new($false))
            Write-Output "$piece : 16x$($result.Height), $frames original animation frames"
        } finally { $result.Dispose(); $mask.Dispose(); $maskStream.Dispose() }
    }
} finally {
    if ($ingot) { $ingot.Dispose() }
    if ($ingotStream) { $ingotStream.Dispose() }
    $upstreamZip.Dispose()
    $minecraftZip.Dispose()
}
