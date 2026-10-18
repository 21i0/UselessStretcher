$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$output = Join-Path $PSScriptRoot '..\src\main\resources\assets\useless_stretcher\textures\item\dimension_capsule.png'
$bitmap = [System.Drawing.Bitmap]::new(16, 16)
$palette = @{
    '.' = [System.Drawing.Color]::Transparent
    'o' = [System.Drawing.ColorTranslator]::FromHtml('#342516')
    'b' = [System.Drawing.ColorTranslator]::FromHtml('#E6A915')
    'c' = [System.Drawing.ColorTranslator]::FromHtml('#FFD84A')
    'h' = [System.Drawing.ColorTranslator]::FromHtml('#FFF2A6')
    'w' = [System.Drawing.ColorTranslator]::FromHtml('#FFFDF2')
    's' = [System.Drawing.ColorTranslator]::FromHtml('#D8DCE0')
    'g' = [System.Drawing.ColorTranslator]::FromHtml('#89939A')
}

# Project each pixel onto a capsule axis tilted 45 degrees toward the upper-left.
try {
    $bodyPixels = @()
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $along = -($x + $y - 15) / [Math]::Sqrt(2)
            $across = ($x - $y) / [Math]::Sqrt(2)
            $cap = [Math]::Max(0, [Math]::Abs($along) - 3.5)
            $distance = [Math]::Sqrt($cap * $cap + $across * $across)
            if ($distance -le 3.2 -and $distance -gt 2.35) {
                $bitmap.SetPixel($x, $y, $palette['o'])
            } elseif ($distance -le 2.35) {
                $bodyPixels += [pscustomobject]@{ X = $x; Y = $y; Along = $along; Across = $across }
            }
        }
    }

    # The two capsule halves use equal pixel counts, split perpendicular to the capsule axis.
    $orderedPixels = @($bodyPixels | Sort-Object -Property @{ Expression = 'Along'; Descending = $true }, X, Y)
    if (($orderedPixels.Count % 2) -ne 0) { throw "Capsule body cannot split evenly: $($orderedPixels.Count) pixels" }
    $half = [int]($orderedPixels.Count / 2)
    $yellowCount = 0
    $whiteCount = 0
    for ($i = 0; $i -lt $orderedPixels.Count; $i++) {
        $pixel = $orderedPixels[$i]
        if ($i -lt $half) {
            $key = if ($pixel.Across -lt -0.4) { 'h' } elseif ($pixel.Across -gt 1.1) { 'b' } else { 'c' }
            $yellowCount++
        } else {
            $key = if ($pixel.Across -lt -0.4) { 'w' } elseif ($pixel.Across -gt 1.1) { 'g' } else { 's' }
            $whiteCount++
        }
        $bitmap.SetPixel($pixel.X, $pixel.Y, $palette[$key])
    }
    if ($yellowCount -ne $whiteCount) { throw "White/yellow pixel mismatch: $whiteCount/$yellowCount" }
    $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $bitmap.Dispose() }
Write-Output "$output (white: $half px, yellow: $half px)"
