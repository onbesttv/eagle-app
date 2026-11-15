Add-Type -AssemblyName System.Drawing

$outPath = "app\src\play_tv\res\drawable\eagle_wordmark.png"
$bmp = [System.Drawing.Bitmap]::FromFile($outPath)
$minX = $bmp.Width
$maxX = 0
$minY = $bmp.Height
$maxY = 0

for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.A -gt 30) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}

Write-Host "Logo bounds: X: $minX to $maxX, Y: $minY to $maxY (W: $($maxX - $minX), H: $($maxY - $minY))"

# Let's crop it to the logo bounds with a small margin (e.g. 10px)
$pad = 12
$cropX = [Math]::Max(0, $minX - $pad)
$cropY = [Math]::Max(0, $minY - $pad)
$cropW = [Math]::Min($bmp.Width - $cropX, ($maxX - $minX) + ($pad * 2))
$cropH = [Math]::Min($bmp.Height - $cropY, ($maxY - $minY) + ($pad * 2))

$rect = New-Object System.Drawing.Rectangle($cropX, $cropY, $cropW, $cropH)
$cropped = $bmp.Clone($rect, $bmp.PixelFormat)
$bmp.Dispose()

$cropped.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Cropped to: $cropW x $cropH and saved!"
$cropped.Dispose()
