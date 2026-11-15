Add-Type -AssemblyName System.Drawing
$files = @(
    "app/src/dream/res/mipmap-xxhdpi/ic_launcher.png",
    "app/src/dream/res/drawable/eagle_adaptive_foreground.png",
    "app/src/dream/res/drawable/eagle_badge.png"
)
foreach ($f in $files) {
    if (Test-Path $f) {
        $bmp = [System.Drawing.Bitmap]::FromFile((Resolve-Path $f).Path)
        $c0 = $bmp.GetPixel(0,0)
        $cc = $bmp.GetPixel([int]($bmp.Width/2), [int]($bmp.Height/2))
        Write-Host "$f ($($bmp.Width)x$($bmp.Height)): Corner Alpha=$($c0.A), Center Alpha=$($cc.A)"
        $bmp.Dispose()
    }
}
