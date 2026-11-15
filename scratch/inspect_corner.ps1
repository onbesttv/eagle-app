Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile((Resolve-Path "app/src/dream/res/drawable/eagle_adaptive_foreground.png").Path)
$c0 = $bmp.GetPixel(0,0)
Write-Host "Corner RGBA: R=$($c0.R) G=$($c0.G) B=$($c0.B) A=$($c0.A)"
$bmp.Dispose()
