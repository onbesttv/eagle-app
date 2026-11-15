Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile((Resolve-Path "scratch/original_dream_foreground.png").Path)

# Sample the background color from the 4 corners
$c00 = $bmp.GetPixel(2, 2)
$c01 = $bmp.GetPixel($bmp.Width - 3, 2)
$c10 = $bmp.GetPixel(2, $bmp.Height - 3)
$c11 = $bmp.GetPixel($bmp.Width - 3, $bmp.Height - 3)

Write-Host "Corner 00: $($c00.R), $($c00.G), $($c00.B), A=$($c00.A)"
Write-Host "Corner 01: $($c01.R), $($c01.G), $($c01.B), A=$($c01.A)"
Write-Host "Corner 10: $($c10.R), $($c10.G), $($c10.B), A=$($c10.A)"
Write-Host "Corner 11: $($c11.R), $($c11.G), $($c11.B), A=$($c11.A)"

# Find actual content bounding box
$minX = $bmp.Width; $maxX = 0
$minY = $bmp.Height; $maxY = 0

for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $p = $bmp.GetPixel($x, $y)
        $diff = [Math]::Abs($p.R - $c00.R) + [Math]::Abs($p.G - $c00.G) + [Math]::Abs($p.B - $c00.B)
        if ($diff -gt 45) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
Write-Host "Real Logo BBox: X=[$minX, $maxX], Y=[$minY, $maxY], W=$($maxX - $minX + 1), H=$($maxY - $minY + 1)"
$bmp.Dispose()
