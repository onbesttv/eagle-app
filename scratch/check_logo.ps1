Add-Type -AssemblyName System.Drawing
$f = "C:\Users\guizi\.gemini\antigravity\brain\53231ca4-cc8b-481c-a509-f061556bdc05\.user_uploaded\media_1794667200751_6cafdddf.png"
$bmp = [System.Drawing.Bitmap]::FromFile($f)
Write-Host "Corner (0,0): $($bmp.GetPixel(0,0))"
Write-Host "Pixel (10,10): $($bmp.GetPixel(10,10))"
Write-Host "Pixel (50,50): $($bmp.GetPixel(50,50))"
Write-Host "Pixel (256,256): $($bmp.GetPixel(256,256))"

# Check histogram of dark pixels vs bright pixels
$darkCount = 0
$brightCount = 0
for ($y = 0; $y -lt $bmp.Height; $y += 4) {
    for ($x = 0; $x -lt $bmp.Width; $x += 4) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.R -lt 50 -and $c.G -lt 50 -and $c.B -lt 50) {
            $darkCount++
        } else {
            $brightCount++
        }
    }
}
Write-Host "Dark pixels: $darkCount, Bright pixels: $brightCount"
$bmp.Dispose()
