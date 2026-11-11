$gradleFile = "app/build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw
$eagleTvVersion = "3.0"
if ($gradleContent -match 'create\("eagle_tv"\)\s*\{[^}]*versionName\s*=\s*"([^"]+)"') {
    $eagleTvVersion = $matches[1]
}

$tag = "v$eagleTvVersion"
Write-Host "Eagle TV (MyTvOnline) Versao: $eagleTvVersion (Tag: $tag)"

$apkFile = "revendas/eagle_tv.apk"
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
    "User-Agent"    = "EagleApp-Publisher"
}

$repoOwner = "onbesttv"
$repoName = "eagle-app"

# Verificar se a release ja existe
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$tag" -Headers $headers -Method Get
} catch {}

if (-not $release) {
    Write-Host "A criar nova release $tag..."
    $payload = @{
        tag_name   = $tag
        name       = "Eagle TV v$eagleTvVersion"
        body       = "Eagle TV (MyTvOnline) versao $eagleTvVersion"
        draft      = $false
        prerelease = $false
    } | ConvertTo-Json

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json"
}

Write-Host "Release ID: $($release.id)"

# Verificar se eagle_tv.apk ja existe nos assets
if ($release.assets) {
    $oldAsset = $release.assets | Where-Object { $_.name -eq "eagle_tv.apk" }
    if ($oldAsset) {
        Write-Host "A remover asset antigo eagle_tv.apk (ID $($oldAsset.id))..."
        curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldAsset.id)" | Out-Null
    }
}

# Upload do eagle_tv.apk
Write-Host "A enviar eagle_tv.apk para a release $tag..."
$uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=eagle_tv.apk"
$uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrl | ConvertFrom-Json

if ($uploadResp.id) {
    Write-Host "Upload concluido com sucesso! Asset ID: $($uploadResp.id)"
    Write-Host "URL: $($uploadResp.browser_download_url)"
} else {
    Write-Error "Erro no upload: $($uploadResp | ConvertTo-Json)"
}

# Enviar tambem para v7.4 (latest) para garantir disponibilidade universal
try {
    $relLatest = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/v7.4" -Headers $headers -Method Get
    if ($relLatest -and $relLatest.id) {
        if ($relLatest.assets) {
            $oldInLatest = $relLatest.assets | Where-Object { $_.name -eq "eagle_tv.apk" }
            if ($oldInLatest) {
                curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldInLatest.id)" | Out-Null
            }
        }
        Write-Host "A enviar eagle_tv.apk para a release v7.4 (latest)..."
        $uploadUrlLatest = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($relLatest.id)/assets?name=eagle_tv.apk"
        curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrlLatest | Out-Null
        Write-Host "eagle_tv.apk adicionado a release v7.4 com sucesso!"
    }
} catch {
    Write-Host "Nota: Nao foi possivel anexar a v7.4: $_"
}
