Add-Type -AssemblyName System.Drawing

$srcPath = (Resolve-Path "scratch/original_dream_foreground.png").Path
$srcBmp = [System.Drawing.Bitmap]::FromFile($srcPath)

function CreateIconWithPadding($targetSize, $scaleFactor) {
    $bmp = [System.Drawing.Bitmap]::new($targetSize, $targetSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    # Background fill matching the dark corners
    $bgColor = [System.Drawing.Color]::FromArgb(255, 7, 10, 16)
    $brush = [System.Drawing.SolidBrush]::new($bgColor)
    $g.FillRectangle($brush, 0, 0, $targetSize, $targetSize)
    $brush.Dispose()

    # Draw scaled original in center
    $scaledW = [int]($targetSize * $scaleFactor)
    $scaledH = [int]($targetSize * $scaleFactor)
    $posX = [int](($targetSize - $scaledW) / 2)
    $posY = [int](($targetSize - $scaledH) / 2)

    $destRect = [System.Drawing.Rectangle]::new($posX, $posY, $scaledW, $scaledH)
    $srcRect = [System.Drawing.Rectangle]::new(0, 0, $srcBmp.Width, $srcBmp.Height)
    $g.DrawImage($srcBmp, $destRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)

    $g.Dispose()
    return $bmp
}

# 1. Generate 512x512 adaptive foreground (scale = 0.65 for perfect safe zone fitting)
$fg512 = CreateIconWithPadding 512 0.65
$fgPath1 = "app/src/dream/res/drawable/eagle_adaptive_foreground.png"
$fgPath2 = "app/src/dream/res/drawable-nodpi/eagle_adaptive_foreground.png"
$fg512.Save($fgPath1, [System.Drawing.Imaging.ImageFormat]::Png)
$fg512.Save($fgPath2, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Generated $fgPath1 & $fgPath2"

# 2. Generate mipmap densities for legacy launchers and APK package viewers
$densities = @(
    @{ Folder = "mipmap-mdpi"; Size = 48 },
    @{ Folder = "mipmap-hdpi"; Size = 72 },
    @{ Folder = "mipmap-xhdpi"; Size = 96 },
    @{ Folder = "mipmap-xxhdpi"; Size = 144 },
    @{ Folder = "mipmap-xxxhdpi"; Size = 192 }
)

foreach ($d in $densities) {
    $iconBmp = CreateIconWithPadding $d.Size 0.65
    $targetDir = "app/src/dream/res/$($d.Folder)"
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    $p1 = "$targetDir/ic_launcher.png"
    $p2 = "$targetDir/ic_launcher_round.png"
    $iconBmp.Save($p1, [System.Drawing.Imaging.ImageFormat]::Png)
    $iconBmp.Save($p2, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host "Generated $($d.Folder): $p1 ($($d.Size)x$($d.Size))"
    $iconBmp.Dispose()
}

$fg512.Dispose()
$srcBmp.Dispose()
Write-Host "All icons generated successfully!"
