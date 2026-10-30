$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  PUBLICAR ALTERACOES DO PAINEL EM EAGLEPANEL.SITE" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
$token = ($credRaw -split "`n" | Where-Object { $_.Trim().StartsWith("password=") } | Select-Object -First 1).Trim().Substring(9).Trim()

$headers = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github+json"
    "User-Agent"    = "EagleApp-Publisher"
}

Write-Host "[1/2] A enviar painel-admin\index.html para o servidor..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "https://api.github.com/repos/onbesttv/painel-tv" -Headers $headers -Method Get | Out-Null
} catch {
    $createPayload = @{
        name        = "painel-tv"
        description = "Painel de Controlo"
        private     = $false
        auto_init   = $false
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "https://api.github.com/user/repos" -Headers $headers -Method Post -Body $createPayload -ContentType "application/json" | Out-Null
}

$panelDir = Join-Path $env:TEMP "eagle_panel_repo"
if (Test-Path $panelDir) {
    Remove-Item $panelDir -Recurse -Force
}
New-Item -ItemType Directory -Path $panelDir | Out-Null
Copy-Item "painel-admin\index.html" -Destination (Join-Path $panelDir "index.html") -Force
Set-Content -Path (Join-Path $panelDir "CNAME") -Value "eaglepanel.site" -NoNewline -Encoding ASCII

Push-Location $panelDir
git init | Out-Null
git checkout -b main | Out-Null
git add index.html CNAME
git commit -m "Painel Web + Dominio eaglepanel.site" | Out-Null
git remote add origin "https://onbesttv:$token@github.com/onbesttv/painel-tv.git"
git push -f origin main
Pop-Location

# 4. Ativar GitHub Pages em 'painel-tv' com dominio eaglepanel.site
Write-Host "[4/4] A configurar dominio eaglepanel.site no painel..." -ForegroundColor Yellow
$pagesBody = @{
    cname  = "eaglepanel.site"
    source = @{
        branch = "main"
        path   = "/"
    }
} | ConvertTo-Json

try {
    $resp = Invoke-RestMethod -Uri "https://api.github.com/repos/onbesttv/painel-tv/pages" -Headers $headers -Method Put -Body $pagesBody -ContentType "application/json"
} catch {
    try {
        $resp = Invoke-RestMethod -Uri "https://api.github.com/repos/onbesttv/painel-tv/pages" -Headers $headers -Method Get
    } catch {}
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  DOMINIO eaglepanel.site CONFIGURADO NO SERVIDOR!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
