Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile((Resolve-Path "app/src/dream/res/drawable/eagle_adaptive_foreground.png").Path)
$bgR = 12; $bgG = 17; $bgB = 23

$minX = $bmp.Width; $maxX = 0
$minY = $bmp.Height; $maxY = 0

for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $p = $bmp.GetPixel($x, $y)
        # Check if significantly different from background
        $diff = [Math]::Abs($p.R - $bgR) + [Math]::Abs($p.G - $bgG) + [Math]::Abs($p.B - $bgB)
        if ($diff -gt 30) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
Write-Host "Content Bounding Box: X=[$minX, $maxX] (Width: $($maxX - $minX + 1)), Y=[$minY, $maxY] (Height: $($maxY - $minY + 1)) in 512x512"
$bmp.Dispose()
