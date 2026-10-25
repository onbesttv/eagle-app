# Script de publicacao automatica para o GitHub
# Uso no terminal do Android Studio ou PowerShell:
#   .\publish.ps1

$ErrorActionPreference = "Stop"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  EAGLE APP - PUBLICACAO AUTOMATICA NO GITHUB" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Ler versao do build.gradle.kts
$gradleFile = "app/build.gradle.kts"
if (-not (Test-Path $gradleFile)) {
    Write-Error "Ficheiro $gradleFile nao encontrado!"
}

$gradleContent = Get-Content $gradleFile -Raw
if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
    $versionName = $matches[1]
} else {
    Write-Error "Nao foi possivel detetar versionName em $gradleFile"
}

$tag = "v$versionName"
Write-Host "`n[1/6] Versao detetada: $versionName (Tag: $tag)" -ForegroundColor Yellow

# 2. Compilar APK (Variante Master = App Eagle Oficial)
Write-Host "[2/6] A compilar APK Master com Gradle..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$buildResult = .\gradlew.bat assembleMasterDebug
if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha na compilacao com Gradle."
}

# 3. Copiar para eagle.apk
$srcApk = "app\build\outputs\apk\master\debug\app-master-debug.apk"
if (-not (Test-Path $srcApk)) {
    Write-Error "APK gerado nao encontrado em $srcApk"
}
Copy-Item $srcApk -Destination "eagle.apk" -Force
$apkSizeMB = [math]::Round((Get-Item "eagle.apk").Length / 1MB, 2)
Write-Host "[3/6] eagle.apk atualizado com sucesso ($apkSizeMB MB)" -ForegroundColor Green

# 4. Git commit e push
Write-Host "[4/6] A atualizar Git e enviar para origin/main e $tag..." -ForegroundColor Yellow
git add -A
$status = git status --porcelain
if ($status) {
    git commit -m "Versao ${tag}"
}
git tag -f $tag
git push origin main
git push origin $tag --force

# 5. Obter credencial do GitHub do Windows Credential Manager
Write-Host "[5/6] A autenticar na API do GitHub..." -ForegroundColor Yellow
$credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
$token = ""
foreach ($line in ($credRaw -split "`n")) {
    if ($line.Trim().StartsWith("password=")) {
        $token = $line.Trim().Substring(9).Trim()
        break
    }
}

if (-not $token) {
    Write-Error "Nao foi possivel obter o token de acesso do Git Credential Manager."
}

# 6. Criar ou atualizar Release no GitHub e enviar eagle.apk
Write-Host "[6/6] A publicar no GitHub Releases..." -ForegroundColor Yellow

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
    if ($release -and $release.id) {
        # Garante que a release nao tem qualquer descricao
        $clearBody = @{ body = "" } | ConvertTo-Json
        Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/$($release.id)" -Headers $headers -Method Patch -Body $clearBody -ContentType "application/json" | Out-Null
    }
} catch {}

if (-not $release) {
    Write-Host "  -> A criar nova Release $tag..." -ForegroundColor Gray
    $payload = @{
        tag_name   = $tag
        name       = "Versao $versionName"
        body       = ""
        draft      = $false
        prerelease = $false
    } | ConvertTo-Json

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json"
}

if (-not $release.id) {
    Write-Error "Falha ao obter/criar Release no GitHub: $($release | ConvertTo-Json)"
}

# Se ja existir um asset eagle.apk nesta release, apagar
if ($release.assets) {
    $oldAsset = $release.assets | Where-Object { $_.name -eq "eagle.apk" }
    if ($oldAsset) {
        Write-Host "  -> A substituir eagle.apk anterior (ID $($oldAsset.id))..." -ForegroundColor Gray
        curl.exe -s -X DELETE -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($oldAsset.id)" | Out-Null
    }
}

# Upload do eagle.apk
Write-Host "  -> A enviar ficheiro eagle.apk para a Release..." -ForegroundColor Gray
$uploadUrl = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=eagle.apk"
$uploadResp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@eagle.apk" $uploadUrl | ConvertFrom-Json

if ($uploadResp.id) {
    Write-Host "`n============================================================" -ForegroundColor Green
    Write-Host "  PUBLICACAO CONCLUIDA COM SUCESSO!" -ForegroundColor Green
    Write-Host "  Release: $($release.html_url)" -ForegroundColor Green
    Write-Host "  Download APK: https://github.com/$repoOwner/$repoName/releases/download/$tag/eagle.apk" -ForegroundColor Green
    Write-Host "============================================================`n" -ForegroundColor Green
} else {
    Write-Error "Erro no upload do APK: $($uploadResp | ConvertTo-Json)"
}
