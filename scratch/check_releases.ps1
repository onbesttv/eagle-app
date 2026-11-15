$releases = Invoke-RestMethod -Uri 'https://api.github.com/repos/onbesttv/eagle-app/releases?per_page=10'
$releases | ForEach-Object {
    [PSCustomObject]@{
        tag = $_.tag_name
        name = $_.name
        assets = ($_.assets.name -join ', ')
    }
} | Format-Table -AutoSize
