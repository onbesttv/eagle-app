Add-Type -AssemblyName System.Drawing

$f = "C:\Users\guizi\.gemini\antigravity\brain\53231ca4-cc8b-481c-a509-f061556bdc05\.user_uploaded\media_1794667200751_6cafdddf.png"
$src = [System.Drawing.Bitmap]::FromFile($f)
$w = $src.Width
$h = $src.Height

# 1. Versão Transparente (Original com fundo preto removido)
$transparentBmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

# Background color in corner is R=35, G=31, B=32
$bgR = 35.0
$bgG = 31.0
$bgB = 32.0

for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $c = $src.GetPixel($x, $y)
        # Distance from background color
        $dist = [Math]::Sqrt([Math]::Pow($c.R - $bgR, 2) + [Math]::Pow($c.G - $bgG, 2) + [Math]::Pow($c.B - $bgB, 2))
        if ($dist -lt 18.0) {
            $transparentBmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        } elseif ($dist -lt 40.0) {
            $alpha = [int](($dist - 18.0) / (40.0 - 18.0) * 255.0)
            $transparentBmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, $c.R, $c.G, $c.B))
        } else {
            $transparentBmp.SetPixel($x, $y, $c)
        }
    }
}

$outPath = "app\src\play_tv\res\drawable\eagle_wordmark.png"
$transparentBmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Saved transparent logo to $outPath"

$src.Dispose()
$transparentBmp.Dispose()
