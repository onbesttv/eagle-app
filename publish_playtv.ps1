$gradleFile = "app/build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw
$playtvVersion = "1.0"
if ($gradleContent -match 'create\("play_tv"\)\s*\{[^}]*versionName\s*=\s*"([^"]+)"') {
    $playtvVersion = $matches[1]
}

$tag = "v$playtvVersion"
Write-Host "PlayTV Versao: $playtvVersion (Tag: $tag)" -ForegroundColor Cyan

$builtApk = "app/build/outputs/apk/play_tv/debug/app-play_tv-debug.apk"
$destApk = "revendas/PlayTV.apk"
if (Test-Path $builtApk) {
    Write-Host "A copiar $builtApk para $destApk..." -ForegroundColor Yellow
    Copy-Item $builtApk -Destination $destApk -Force
}

$apkFile = "revendas/PlayTV.apk"
if (-not (Test-Path $apkFile)) {
    Write-Error "Ficheiro $apkFile nao encontrado!"
    exit 1
}

$credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
$token = ""
foreach ($line in ($credRaw -split "`n")) {
    if ($line.Trim().StartsWith("password=")) {
        $token = $line.Trim().Substring(9).Trim()
        break
    }
}

if (-not $token) {
    Write-Error "Nao foi possivel obter o token do GitHub!"
    exit 1
}

$headers = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "PlayTV-Publisher"
}

$repoOwner = "onbesttv"
$repoName = "eagle-app"

# Verificar se a release ja existe
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$tag" -Headers $headers -Method Get
} catch {}

if (-not $release) {
    Write-Host "A criar nova release $tag..." -ForegroundColor Yellow
    $payload = @{
        tag_name    = $tag
        name        = "PlayTV v$playtvVersion"
        body        = "PlayTV versao $playtvVersion"
        draft       = $false
        prerelease  = $false
        make_latest = "false"
    } | ConvertTo-Json

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json"
}

Write-Host "Release ID: $($release.id)"

# Verificar se PlayTV.apk ja existe nos assets
if ($release.assets) {
    $oldAsset = $release.assets | Where-Object { $_.name -eq "PlayTV.apk" }
    if ($oldAsset) {
        Write-Host "A remover asset antigo PlayTV.apk (ID $($oldAsset.id))..."
        curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: PlayTV-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldAsset.id)" | Out-Null
    }
}

# Upload do PlayTV.apk
Write-Host "A enviar PlayTV.apk para a release $tag..." -ForegroundColor Yellow
$uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=PlayTV.apk"
$uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: PlayTV-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrl | ConvertFrom-Json

if ($uploadResp.id) {
    Write-Host "Upload concluido com sucesso! Asset ID: $($uploadResp.id)" -ForegroundColor Green
    Write-Host "URL: $($uploadResp.browser_download_url)" -ForegroundColor Green
} else {
    Write-Error "Erro no upload: $($uploadResp | ConvertTo-Json)"
}

# Garantir que v7.4 se mantem como latest release oficial para os jornais PDF
try {
    $patchLatest = @{ make_latest = "true" } | ConvertTo-Json
    Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/442181560" -Headers $headers -Method Patch -Body $patchLatest -ContentType "application/json" | Out-Null
    Write-Host "Release v7.4 confirmada como repo latest para jornais PDF." -ForegroundColor Green
} catch {}
