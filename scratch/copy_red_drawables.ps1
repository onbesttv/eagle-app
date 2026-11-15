$srcDraw = "app\src\godinho_tv\res\drawable"
$dstDraw = "app\src\play_tv\res\drawable"
Get-ChildItem $srcDraw -Filter "*.xml" | ForEach-Object {
    if ($_.Name -ne "ic_launcher_background.xml") {
        Copy-Item $_.FullName -Destination (Join-Path $dstDraw $_.Name) -Force
        Write-Host "Copied $($_.Name)"
    }
}

$srcNodpi = "app\src\godinho_tv\res\drawable-nodpi"
$dstNodpi = "app\src\play_tv\res\drawable-nodpi"
Get-ChildItem $srcNodpi -Filter "ic_home_*.png" | ForEach-Object {
    Copy-Item $_.FullName -Destination (Join-Path $dstNodpi $_.Name) -Force
    Write-Host "Copied $($_.Name)"
}
