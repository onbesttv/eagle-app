# Script para compilar (e opcionalmente publicar no GitHub) o APK de um Revendedor (ex: tv_fabio)
# Exemplo de uso:
#   powershell -ExecutionPolicy Bypass -File .\gerar-revenda.ps1 -Id "tv_fabio" -Publicar

param(
    [Parameter(Mandatory=$false)]
    [string]$Id = "tv_fabio",

    [Parameter(Mandatory=$false)]
    [switch]$Publicar
)

$ErrorActionPreference = "Stop"
$cleanId = $Id.Trim().ToLower()
$taskName = "assemble" + $cleanId.Substring(0,1).ToUpper() + $cleanId.Substring(1) + "Debug"

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  COMPILADOR DE APK DO REVENDEDOR ($cleanId)" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

# Garantir que os recursos originais Eagle estao em app\src\master\res
$mainNoDpi = "app\src\main\res\drawable-nodpi"
$masterNoDpi = "app\src\master\res\drawable-nodpi"
if (-not (Test-Path $masterNoDpi)) {
    New-Item -ItemType Directory -Force -Path $masterNoDpi | Out-Null
}
foreach ($file in @("eagle_wordmark.webp", "eagle_badge.webp", "eagle_adaptive_foreground.webp")) {
    $srcFile = Join-Path $mainNoDpi $file
    if (Test-Path $srcFile) {
        Move-Item $srcFile -Destination (Join-Path $masterNoDpi $file) -Force
    }
}

# Mover mipmaps fixos da Eagle (se ainda estiverem em main) para master
foreach ($density in @("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
    $mainMip = "app\src\main\res\mipmap-$density"
    $masterMip = "app\src\master\res\mipmap-$density"
    if (Test-Path $mainMip) {
        if (-not (Test-Path $masterMip)) {
            New-Item -ItemType Directory -Force -Path $masterMip | Out-Null
        }
        Get-ChildItem $mainMip -Filter "ic_launcher*.webp" | ForEach-Object {
            Move-Item $_.FullName -Destination (Join-Path $masterMip $_.Name) -Force
        }
    }
}

# Verificar se o revendedor tem eagle_wordmark.png na sua pasta drawable
$resDrawableDir = "app\src\$cleanId\res\drawable"
$resLogo = Join-Path $resDrawableDir "eagle_wordmark.png"

if (Test-Path $resLogo) {
    Write-Host "[*] A preparar logotipo personalizado de '$cleanId' (Icone da App, Adaptive Icon, Splash, Banner e Topo)..." -ForegroundColor Yellow
    Add-Type -AssemblyName System.Drawing
    $imgBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $resLogo).Path)
    $ms = New-Object System.IO.MemoryStream(,$imgBytes)
    $bitmap = [System.Drawing.Image]::FromStream($ms)

    $bitmap.Save((Resolve-Path $resLogo).Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Save((Join-Path (Resolve-Path $resDrawableDir).Path "eagle_badge.png"), [System.Drawing.Imaging.ImageFormat]::Png)

    # Criar eagle_adaptive_foreground.png com margem de seguranca Adaptive Icon (108dp -> ~74dp central)
    $fgSize = 432
    $fgBmp = New-Object System.Drawing.Bitmap($fgSize, $fgSize)
    $gFg = [System.Drawing.Graphics]::FromImage($fgBmp)
    $gFg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gFg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gFg.Clear([System.Drawing.Color]::FromArgb(255, 5, 5, 7))
    $pad = [int]($fgSize * 0.14)
    $inner = $fgSize - ($pad * 2)
    $gFg.DrawImage($bitmap, $pad, $pad, $inner, $inner)
    $gFg.Dispose()
    $fgBmp.Save((Join-Path (Resolve-Path $resDrawableDir).Path "eagle_adaptive_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $fgBmp.Dispose()

    # Fundo escuro para o icone adaptativo do revendedor
    $bgLauncherXml = @"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#050507" android:pathData="M0,0h108v108h-108z" />
</vector>
"@
    Set-Content -Path (Join-Path (Resolve-Path $resDrawableDir).Path "ic_launcher_background.xml") -Value $bgLauncherXml -Encoding UTF8

    # Gerar todos os mipmaps ic_launcher.png e ic_launcher_round.png para o revendedor
    $sizes = @{
        "mdpi"    = 48
        "hdpi"    = 72
        "xhdpi"   = 96
        "xxhdpi"  = 144
        "xxxhdpi" = 192
    }
    foreach ($entry in $sizes.GetEnumerator()) {
        $mipDir = "app\src\$cleanId\res\mipmap-$($entry.Key)"
        if (-not (Test-Path $mipDir)) {
            New-Item -ItemType Directory -Force -Path $mipDir | Out-Null
        }
        $sz = $entry.Value
        $iconBmp = New-Object System.Drawing.Bitmap($sz, $sz)
        $gIcon = [System.Drawing.Graphics]::FromImage($iconBmp)
        $gIcon.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $gIcon.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $gIcon.DrawImage($bitmap, 0, 0, $sz, $sz)
        $gIcon.Dispose()
        $iconBmp.Save((Join-Path (Resolve-Path $mipDir).Path "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $iconBmp.Save((Join-Path (Resolve-Path $mipDir).Path "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $iconBmp.Dispose()
    }

    $bitmap.Dispose()
    $ms.Dispose()
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat $taskName
if ($LASTEXITCODE -ne 0) {
    throw "Falha ao compilar a variante $cleanId."
}

$outDir = "revendas"
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}

$srcApk = "app\build\outputs\apk\$cleanId\debug\app-$cleanId-debug.apk"
$destApk = Join-Path $outDir "${cleanId}.apk"
for ($retry = 0; $retry -lt 5; $retry++) {
    try {
        Copy-Item $srcApk -Destination $destApk -Force
        break
    } catch {
        Start-Sleep -Seconds 1
    }
}
$sizeMB = [math]::Round((Get-Item $srcApk).Length / 1MB, 2)

Write-Host "`n===================================================" -ForegroundColor Green
Write-Host "  APK '$cleanId.apk' GERADO COM SUCESSO! ($sizeMB MB)" -ForegroundColor Green
Write-Host "  Ficheiro: $((Resolve-Path $destApk).Path)" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Green

if ($Publicar) {
    $gradleContent = Get-Content "app/build.gradle.kts" -Raw
    if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
        $versionName = $matches[1]
    } else {
        throw "Nao foi possivel detetar versionName em app/build.gradle.kts"
    }
    $tag = "v$versionName"
    Write-Host "`n[*] A publicar '$cleanId.apk' na Release Isolada $tag (prerelease) do GitHub..." -ForegroundColor Yellow

    $credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
    $token = ($credRaw -split "`n" | Where-Object { $_.Trim().StartsWith("password=") } | Select-Object -First 1).Trim().Substring(9).Trim()
    $headers = @{
        "Authorization" = "token $token"
        "Accept"        = "application/vnd.github.v3+json"
        "User-Agent"    = "EagleApp-Publisher"
    }

    $repoOwner = "onbesttv"
    $repoName = "eagle-app"
    $release = $null
    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$tag" -Headers $headers -Method Get
    } catch {}

    if (-not $release) {
        $payload = @{
            tag_name   = $tag
            name       = "Versao $versionName"
            body       = ""
            draft      = $false
            prerelease = $true
        } | ConvertTo-Json
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json"
    }

    if ($release.assets) {
        $oldAsset = $release.assets | Where-Object { $_.name -eq "${cleanId}.apk" }
        if ($oldAsset) {
            curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldAsset.id)" | Out-Null
        }
    }

    $uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=${cleanId}.apk"
    $uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$srcApk" $uploadUrl | ConvertFrom-Json

    if ($uploadResp.id) {
        Write-Host "  -> Publicado com sucesso: $($uploadResp.browser_download_url)" -ForegroundColor Green
    } else {
        throw "Erro ao enviar ${cleanId}.apk para o GitHub."
    }
}
