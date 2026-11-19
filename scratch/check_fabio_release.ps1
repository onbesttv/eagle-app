$releases = Invoke-RestMethod -Uri 'https://api.github.com/repos/onbesttv/eagle-app/releases?per_page=100' -Headers @{ "User-Agent" = "PowerShell" }
foreach ($r in $releases) {
    foreach ($a in $r.assets) {
        if ($a.name -like "*fabio*" -or $a.name -like "*fpinto*") {
            Write-Host "FOUND: Tag: $($r.tag_name) | Name: $($a.name) | URL: $($a.browser_download_url) | Size: $($a.size) bytes | Created: $($a.created_at)"
        }
    }
}
Write-Host "Done scanning releases for fabio."
