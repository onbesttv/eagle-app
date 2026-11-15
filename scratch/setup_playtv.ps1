Add-Type -AssemblyName System.Drawing

$srcLogo = "C:\Users\guizi\.gemini\antigravity\brain\53231ca4-cc8b-481c-a509-f061556bdc05\.user_uploaded\media_1794667200751_6cafdddf.png"
$srcBgRed = "C:\Users\guizi\.gemini\antigravity\brain\53231ca4-cc8b-481c-a509-f061556bdc05\.user_uploaded\media_1794667206715_0917f460.jpg"

$targetDir = "app\src\play_tv"
$resDir = Join-Path $targetDir "res"
$drawableDir = Join-Path $resDir "drawable"
$drawableNoDpiDir = Join-Path $resDir "drawable-nodpi"
$layoutDir = Join-Path $resDir "layout"
$valuesDir = Join-Path $resDir "values"

# Ensure directories
foreach ($d in @($drawableDir, $drawableNoDpiDir, $layoutDir, $valuesDir)) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
    }
}

Write-Host "[1/6] Copiar e configurar logotipo PlayTV..." -ForegroundColor Cyan
$imgBytes = [System.IO.File]::ReadAllBytes($srcLogo)
$ms = New-Object System.IO.MemoryStream(,$imgBytes)
$logoBmp = [System.Drawing.Image]::FromStream($ms)

# Salvar eagle_wordmark.png e eagle_badge.png
$logoBmp.Save((Join-Path $drawableDir "eagle_wordmark.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$logoBmp.Save((Join-Path $drawableDir "eagle_badge.png"), [System.Drawing.Imaging.ImageFormat]::Png)

# Criar eagle_adaptive_foreground.png (432x432 com margem de segurança para ícones adaptativos)
$fgSize = 432
$fgBmp = New-Object System.Drawing.Bitmap($fgSize, $fgSize)
$gFg = [System.Drawing.Graphics]::FromImage($fgBmp)
$gFg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gFg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gFg.Clear([System.Drawing.Color]::FromArgb(255, 30, 27, 30))
$pad = [int]($fgSize * 0.12)
$inner = $fgSize - ($pad * 2)
$gFg.DrawImage($logoBmp, $pad, $pad, $inner, $inner)
$gFg.Dispose()
$fgBmp.Save((Join-Path $drawableDir "eagle_adaptive_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$fgBmp.Dispose()

# Criar Android TV Banner (320x180)
$bannerW = 320
$bannerH = 180
$bannerBmp = New-Object System.Drawing.Bitmap($bannerW, $bannerH)
$gBanner = [System.Drawing.Graphics]::FromImage($bannerBmp)
$gBanner.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gBanner.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gBanner.Clear([System.Drawing.Color]::FromArgb(255, 30, 27, 30))

$targetH = [int]($bannerH * 0.75)
$targetW = [int]($targetH * ($logoBmp.Width / $logoBmp.Height))
if ($targetW -gt [int]($bannerW * 0.85)) {
    $targetW = [int]($bannerW * 0.85)
    $targetH = [int]($targetW * ($logoBmp.Height / $logoBmp.Width))
}
$bX = [int](($bannerW - $targetW) / 2)
$bY = [int](($bannerH - $targetH) / 2)
$gBanner.DrawImage($logoBmp, $bX, $bY, $targetW, $targetH)
$gBanner.Dispose()
$bannerBmp.Save((Join-Path $drawableDir "banner_eagle.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bannerBmp.Dispose()

# Fundo escuro do ícone do launcher
$bgLauncherXml = @"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#1E1B1E" android:pathData="M0,0h108v108h-108z" />
</vector>
"@
Set-Content -Path (Join-Path $drawableDir "ic_launcher_background.xml") -Value $bgLauncherXml -Encoding UTF8

# Gerar Mipmaps
Write-Host "[2/6] Gerar mipmaps do launcher (ic_launcher.png)..." -ForegroundColor Cyan
$sizes = @{
    "mdpi"    = 48
    "hdpi"    = 72
    "xhdpi"   = 96
    "xxhdpi"  = 144
    "xxxhdpi" = 192
}
foreach ($entry in $sizes.GetEnumerator()) {
    $mipDir = Join-Path $resDir "mipmap-$($entry.Key)"
    if (-not (Test-Path $mipDir)) {
        New-Item -ItemType Directory -Path $mipDir -Force | Out-Null
    }
    $sz = $entry.Value
    $iconBmp = New-Object System.Drawing.Bitmap($sz, $sz)
    $gIcon = [System.Drawing.Graphics]::FromImage($iconBmp)
    $gIcon.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gIcon.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gIcon.Clear([System.Drawing.Color]::FromArgb(255, 30, 27, 30))
    $gIcon.DrawImage($logoBmp, 0, 0, $sz, $sz)
    $gIcon.Dispose()
    $iconBmp.Save((Join-Path $mipDir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $iconBmp.Save((Join-Path $mipDir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $iconBmp.Dispose()
}

$logoBmp.Dispose()
$ms.Dispose()

# [3/6] Processar fundo vermelho do menu da app
Write-Host "[3/6] Configurar fundo vermelho do menu e manter login escuro..." -ForegroundColor Cyan
$bgRedBytes = [System.IO.File]::ReadAllBytes($srcBgRed)
$msBg = New-Object System.IO.MemoryStream(,$bgRedBytes)
$bgRedBmp = [System.Drawing.Image]::FromStream($msBg)
$bgRedBmp.Save((Join-Path $drawableNoDpiDir "bg_menu_red.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bgRedBmp.Dispose()
$msBg.Dispose()

# Copiar bg_app_futuristic.png de master para drawable-nodpi para que o login continue com o fundo escuro original
$masterBgFuturistic = "app\src\master\res\drawable-nodpi\bg_app_futuristic.png"
if (Test-Path $masterBgFuturistic) {
    Copy-Item $masterBgFuturistic -Destination (Join-Path $drawableNoDpiDir "bg_app_futuristic.png") -Force
}

# [4/6] Criar layout activity_main.xml personalizado com o fundo vermelho
Write-Host "[4/6] Criar activity_main.xml com fundo vermelho no menu..." -ForegroundColor Cyan
$mainLayoutContent = Get-Content "app\src\main\res\layout\activity_main.xml" -Raw
$mainLayoutContent = $mainLayoutContent.Replace('@drawable/bg_app_futuristic', '@drawable/bg_menu_red')
Set-Content -Path (Join-Path $layoutDir "activity_main.xml") -Value $mainLayoutContent -Encoding UTF8

# [5/6] Configurar themes.xml vermelho
Write-Host "[5/6] Criar themes.xml com acentos vermelhos..." -ForegroundColor Cyan
$themesXml = @"
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Base.Theme.MyApplication" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">#DC2626</item>
        <item name="colorSecondary">#EF4444</item>
        <item name="colorTertiary">#991B1B</item>
        <item name="android:statusBarColor">#060405</item>
        <item name="android:navigationBarColor">#060405</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:colorAccent">#EF4444</item>
        <item name="android:windowBackground">@drawable/bg_launch_splash</item>
        <item name="android:windowSplashScreenBackground">#060405</item>
        <item name="android:windowSplashScreenIconBackgroundColor">#060405</item>
    </style>

    <style name="Theme.MyApplication" parent="Base.Theme.MyApplication" />

    <style name="Theme.StartupSplash" parent="Theme.MyApplication">
        <item name="android:windowBackground">#060405</item>
        <item name="android:windowSplashScreenBackground">#060405</item>
        <item name="android:windowSplashScreenIconBackgroundColor">#060405</item>
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/eagle_badge</item>
    </style>
</resources>
"@
Set-Content -Path (Join-Path $valuesDir "themes.xml") -Value $themesXml -Encoding UTF8

Write-Host "`n[6/6] Assets de PlayTV configurados com sucesso!" -ForegroundColor Green
