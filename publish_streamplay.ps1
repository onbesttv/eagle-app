$gradleFile = "app/build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw
$streamplayVersion = "1.0"
if ($gradleContent -match 'create\("appstreamplay"\)\s*\{[^}]*versionName\s*=\s*"([^"]+)"') {
    $streamplayVersion = $matches[1]
}

$tag = "v$streamplayVersion"
Write-Host "StreamPlay Versao: $streamplayVersion (Tag: $tag)" -ForegroundColor Cyan

$builtApk = "app/build/outputs/apk/appstreamplay/debug/app-appstreamplay-debug.apk"
$destApk = "revendas/appstreamplay.apk"
if (Test-Path $builtApk) {
    Write-Host "A copiar $builtApk para $destApk..." -ForegroundColor Yellow
    Copy-Item $builtApk -Destination $destApk -Force
}

$apkFile = "revendas/appstreamplay.apk"
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
    "User-Agent"    = "StreamPlay-Publisher"
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
        name        = "StreamPlay v$streamplayVersion"
        body        = "StreamPlay versao $streamplayVersion"
        draft       = $false
        prerelease  = $false
        make_latest = "false"
    } | ConvertTo-Json

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json"
}

Write-Host "Release ID: $($release.id)"

# Verificar se appstreamplay.apk ja existe nos assets
if ($release.assets) {
    $oldAsset = $release.assets | Where-Object { $_.name -eq "appstreamplay.apk" }
    if ($oldAsset) {
        Write-Host "A remover asset antigo appstreamplay.apk (ID $($oldAsset.id))..."
        curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: StreamPlay-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldAsset.id)" | Out-Null
    }
}

# Upload do appstreamplay.apk
Write-Host "A enviar appstreamplay.apk para a release $tag..." -ForegroundColor Yellow
$uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=appstreamplay.apk"
$uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: StreamPlay-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrl | ConvertFrom-Json

if ($uploadResp.id) {
    Write-Host "Upload concluido com sucesso! Asset ID: $($uploadResp.id)" -ForegroundColor Green
    Write-Host "URL: $($uploadResp.browser_download_url)" -ForegroundColor Green
} else {
    Write-Error "Erro no upload: $($uploadResp | ConvertTo-Json)"
}

# Enviar tambem para v7.4 (latest) para garantir disponibilidade universal
try {
    $relLatest = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/v7.4" -Headers $headers -Method Get
    if ($relLatest -and $relLatest.id) {
        if ($relLatest.assets) {
            $oldInLatest = $relLatest.assets | Where-Object { $_.name -eq "appstreamplay.apk" }
            if ($oldInLatest) {
                Write-Host "A remover asset antigo appstreamplay.apk em v7.4..."
                curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: StreamPlay-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldInLatest.id)" | Out-Null
            }
        }
        Write-Host "A enviar appstreamplay.apk para a release v7.4 (latest)..." -ForegroundColor Yellow
        $uploadUrlLatest = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($relLatest.id)/assets?name=appstreamplay.apk"
        $upRespLatest = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: StreamPlay-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrlLatest | ConvertFrom-Json
        if ($upRespLatest.id) {
            Write-Host "appstreamplay.apk adicionado a release v7.4 com sucesso!" -ForegroundColor Green
            Write-Host "URL v7.4: $($upRespLatest.browser_download_url)" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "Nota: Nao foi possivel anexar a v7.4: $_"
}
