Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await($asyncOp, $type) {
    $asTask = $asTaskGeneric.MakeGenericMethod($type)
    $netTask = $asTask.Invoke($null, @($asyncOp))
    $netTask.Wait(-1) | Out-Null
    return $netTask.Result
}

$videoPath = "C:\Users\guizi\.gemini\antigravity\brain\94586190-f156-4b78-be6b-525550d30f5b\.user_uploaded\media_1795125120904_43e92530.mp4"
$outDir = "C:\Users\guizi\AndroidStudioProjects\MyApplication\scratch\frames"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

try {
    [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
    [Windows.Media.Editing.MediaClip, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
    [Windows.Media.Editing.MediaComposition, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null

    $fileOp = [Windows.Storage.StorageFile]::GetFileFromPathAsync($videoPath)
    $file = Await $fileOp ([Windows.Storage.StorageFile])

    $clipOp = [Windows.Media.Editing.MediaClip]::CreateFromFileAsync($file)
    $clip = Await $clipOp ([Windows.Media.Editing.MediaClip])

    $comp = New-Object Windows.Media.Editing.MediaComposition
    $comp.Clips.Add($clip)

    $times = @(1, 5, 12, 20, 30, 45, 60, 75)
    foreach ($sec in $times) {
        $ts = [TimeSpan]::FromSeconds($sec)
        $thumbOp = $comp.GetThumbnailAsync($ts, 960, 540, [Windows.Media.Editing.VideoFramePrecision]::NearestFrame)
        $thumb = Await $thumbOp ([Windows.Graphics.Imaging.ImageStream])
        
        $outFile = Join-Path $outDir "frame_${sec}s.jpg"
        
        # Read stream
        $stream = $thumb.AsStreamForRead()
        $fs = [System.IO.File]::Create($outFile)
        $stream.CopyTo($fs)
        $fs.Close()
        $stream.Close()
        
        Write-Host "Frame guardado: $outFile ($([System.IO.FileInfo]::new($outFile).Length) bytes)"
    }
} catch {
    Write-Host "Erro: $_"
    $_.ScriptStackTrace
}
