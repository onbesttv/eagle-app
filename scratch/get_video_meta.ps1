$videoPath = "C:\Users\guizi\.gemini\antigravity\brain\94586190-f156-4b78-be6b-525550d30f5b\.user_uploaded\media_1795125120904_43e92530.mp4"
$file = Get-Item $videoPath
Write-Host "Tamanho (MB): $([math]::Round($file.Length / 1MB, 2))"

$shell = New-Object -ComObject Shell.Application
$folder = $shell.Namespace($file.DirectoryName)
$fileItem = $folder.ParseName($file.Name)

for ($i = 0; $i -lt 320; $i++) {
    $propName = $folder.GetDetailsOf($null, $i)
    $propVal = $folder.GetDetailsOf($fileItem, $i)
    if ($propVal -and $propName) {
        Write-Host "$propName ($i) : $propVal"
    }
}
