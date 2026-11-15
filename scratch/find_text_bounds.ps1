Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile((Resolve-Path "scratch/original_dream_foreground.png").Path)

# Let's inspect where red/bright text pixels are
# "RED" is likely red/bright, "ON" is likely white/bright or red
$textMinX = $bmp.Width; $textMaxX = 0
$textMinY = $bmp.Height; $textMaxY = 0

for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $p = $bmp.GetPixel($x, $y)
        # Bright or high saturation pixels (logo and text)
        $isBright = ($p.R -gt 100 -or $p.G -gt 100 -or $p.B -gt 100)
        if ($isBright) {
            if ($x -lt $textMinX) { $textMinX = $x }
            if ($x -gt $textMaxX) { $textMaxX = $x }
            if ($y -lt $textMinY) { $textMinY = $y }
            if ($y -gt $textMaxY) { $textMaxY = $y }
        }
    }
}
Write-Host "Bright Elements (Logo + Text) bounds in 512x512:"
Write-Host "X: $textMinX to $textMaxX (Width: $($textMaxX - $textMinX + 1))"
Write-Host "Y: $textMinY to $textMaxY (Height: $($textMaxY - $textMinY + 1))"
$bmp.Dispose()
