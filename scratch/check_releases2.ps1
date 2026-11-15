$credRaw = ("protocol=https`nhost=github.com`nusername=onbesttv`n`n" | git credential fill | Out-String)
$token = ""
foreach ($line in ($credRaw -split "`n")) {
    if ($line.Trim().StartsWith("password=")) {
        $token = $line.Trim().Substring(9).Trim()
        break
    }
}
$headers = @{ "Authorization" = "token $token"; "User-Agent" = "Eagle" }
$rel = Invoke-RestMethod -Uri "https://api.github.com/repos/onbesttv/eagle-app/releases" -Headers $headers
$rel | Where-Object { $_.tag_name -like "*7*" -or $_.name -like "*Eagle*" -or $_.name -like "*Play*" } | ForEach-Object { 
    Write-Host "$($_.tag_name) | $($_.name)"
    $_.assets | ForEach-Object { Write-Host "   Asset: $($_.name)" }
}
