$ErrorActionPreference = "Stop"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  UPLOAD DE JORNAIS PARA O GITHUB RELEASES" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Obter token do Git Credential Manager
$credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
$token = ""
foreach ($line in ($credRaw -split "`n")) {
    if ($line.Trim().StartsWith("password=")) {
        $token = $line.Trim().Substring(9).Trim()
        break
    }
}

if (-not $token) {
    throw "Nao foi possivel obter o token do GitHub."
}

$headers = @{
    "Authorization" = "token $token"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "EagleApp-Publisher"
}

# Obter a release mais recente
$latest = Invoke-RestMethod -Uri "https://api.github.com/repos/onbesttv/eagle-app/releases/latest" -Headers $headers -Method Get
Write-Host "Release Alvo: $($latest.tag_name) (ID: $($latest.id))" -ForegroundColor Yellow

$existingAssets = $latest.assets
$existingNames = @{}
if ($existingAssets) {
    foreach ($a in $existingAssets) {
        $existingNames[$a.name] = $a.id
    }
}
Write-Host "Assets ja existentes na release: $($existingNames.Count)" -ForegroundColor Gray

$tgDir = "C:\Users\guizi\Downloads\Telegram Desktop"
$files = Get-ChildItem $tgDir -Filter "*.pdf" | Where-Object { $_.Name -ne "Comprovativo.pdf" }

Write-Host "Total de PDFs para verificar: $($files.Count)" -ForegroundColor Cyan

$uploadedCount = 0
$skippedCount = 0
$total = $files.Count
$idx = 0

foreach ($f in $files) {
    $idx++
    $cleanName = $f.Name -replace '[^a-zA-Z0-9_\.]', '_'

    if ($existingNames.ContainsKey($cleanName)) {
        $skippedCount++
        continue
    }

    $sizeMB = [math]::Round($f.Length / 1MB, 2)
    Write-Host "[$idx/$total] A enviar $cleanName ($sizeMB MB)..." -ForegroundColor Yellow

    $uploadUrl = "https://uploads.github.com/repos/onbesttv/eagle-app/releases/$($latest.id)/assets?name=$cleanName"
    
    $success = $false
    for ($retry = 1; $retry -le 3; $retry++) {
        try {
            $resp = curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/pdf" --data-binary "@$($f.FullName)" $uploadUrl
            $respObj = $resp | ConvertFrom-Json
            if ($respObj.id) {
                $success = $true
                $uploadedCount++
                Write-Host "  -> Enviado com sucesso (Asset ID: $($respObj.id))" -ForegroundColor Green
                break
            } else {
                Write-Host "  -> Tentativa $retry falhou: $resp" -ForegroundColor Red
                Start-Sleep -Seconds 2
            }
        } catch {
            Write-Host "  -> Falha no upload: $($_.Exception.Message)" -ForegroundColor Red
            Start-Sleep -Seconds 2
        }
    }

    if (-not $success) {
        Write-Warning "Falha ao enviar $cleanName apos 3 tentativas."
    }
}

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host "  CONCLUIDO!" -ForegroundColor Green
Write-Host "  Novos ficheiros enviados: $uploadedCount" -ForegroundColor Green
Write-Host "  Ja existentes ignorados: $skippedCount" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
