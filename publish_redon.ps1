$gradleFile = "app/build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw
$redonVersion = "3.13"
if ($gradleContent -match 'create\("dream"\)\s*\{[^}]*versionName\s*=\s*"([^"]+)"') {
    $redonVersion = $matches[1]
}

$tag = "v$redonVersion"
Write-Host "RedOn (Dream / MyTvOnline) Versao: $redonVersion (Tag: $tag)"

$builtApk = "app/build/outputs/apk/dream/debug/app-dream-debug.apk"
$destApk = "revendas/RedOn.apk"
if (Test-Path $builtApk) {
    Write-Host "A copiar $builtApk para $destApk..."
    Copy-Item $builtApk -Destination $destApk -Force
}

$apkFile = "revendas/RedOn.apk"
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
    "User-Agent"    = "RedOn-Publisher"
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
        name       = "RedOn v$redonVersion"
        body       = "RedOn (Dream / MyTvOnline) versao $redonVersion"
        draft      = $false
        prerelease = $false
    } | ConvertTo-Json

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json"
}

Write-Host "Release ID: $($release.id)"

# Verificar se RedOn.apk ja existe nos assets
if ($release.assets) {
    $oldAsset = $release.assets | Where-Object { $_.name -eq "RedOn.apk" }
    if ($oldAsset) {
        Write-Host "A remover asset antigo RedOn.apk (ID $($oldAsset.id))..."
        curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: RedOn-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldAsset.id)" | Out-Null
    }
}

# Upload do RedOn.apk
Write-Host "A enviar RedOn.apk para a release $tag..."
$uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=RedOn.apk"
$uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: RedOn-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrl | ConvertFrom-Json

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
            $oldInLatest = $relLatest.assets | Where-Object { $_.name -eq "RedOn.apk" }
            if ($oldInLatest) {
                curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: RedOn-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldInLatest.id)" | Out-Null
            }
        }
        Write-Host "A enviar RedOn.apk para a release v7.4 (latest)..."
        $uploadUrlLatest = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($relLatest.id)/assets?name=RedOn.apk"
        curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: RedOn-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkFile" $uploadUrlLatest | Out-Null
        Write-Host "RedOn.apk adicionado a release v7.4 com sucesso!"
    }
} catch {
    Write-Host "Nota: Nao foi possivel anexar a v7.4: $_"
}
