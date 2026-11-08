$credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
$token = ""
foreach ($line in ($credRaw -split "`n")) {
    if ($line.Trim().StartsWith("password=")) {
        $token = $line.Trim().Substring(9).Trim()
        break
    }
}

$releaseId = "437879190"

# Find pdfs in Telegram Desktop
$tgFiles = Get-ChildItem "C:\Users\guizi\Downloads\Telegram Desktop" -Filter "*.pdf"
foreach ($f in $tgFiles) {
    $cleanName = $f.Name -replace '[^a-zA-Z0-9_\.]', '_'
    Write-Host "Uploading $($cleanName)..."
    $url = "https://uploads.github.com/repos/onbesttv/eagle-app/releases/$releaseId/assets?name=$cleanName"
    curl.exe -s -X POST -H "Authorization: token $token" -H "User-Agent: EagleApp-Publisher" -H "Content-Type: application/pdf" --data-binary "@$($f.FullName)" $url | Out-Null
    Write-Host "Uploaded $cleanName"
}
