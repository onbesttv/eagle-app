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

# Verificar se o revendedor tem eagle_wordmark.png na sua pasta drawable
$resDrawableDir = "app\src\$cleanId\res\drawable"
$resLogo = Join-Path $resDrawableDir "eagle_wordmark.png"

if (Test-Path $resLogo) {
    Write-Host "[*] A preparar logotipo personalizado de '$cleanId' (Icone, Splash, Banner e Topo)..." -ForegroundColor Yellow
    Add-Type -AssemblyName System.Drawing
    $imgBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $resLogo).Path)
    $ms = New-Object System.IO.MemoryStream(,$imgBytes)
    $bitmap = [System.Drawing.Image]::FromStream($ms)

    $bitmap.Save((Resolve-Path $resLogo).Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Save((Join-Path (Resolve-Path $resDrawableDir).Path "eagle_badge.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Save((Join-Path (Resolve-Path $resDrawableDir).Path "eagle_adaptive_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)

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
Copy-Item $srcApk -Destination $destApk -Force
$sizeMB = [math]::Round((Get-Item $destApk).Length / 1MB, 2)

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
    Write-Host "`n[*] A publicar '$cleanId.apk' na Release $tag do GitHub..." -ForegroundColor Yellow

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
            prerelease = $false
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
    $uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$destApk" $uploadUrl | ConvertFrom-Json

    if ($uploadResp.id) {
        Write-Host "  -> Publicado com sucesso: $($uploadResp.browser_download_url)" -ForegroundColor Green
    } else {
        throw "Erro ao enviar ${cleanId}.apk para o GitHub."
    }
}
