Add-Type -AssemblyName System.Drawing

$file = (Resolve-Path "app/src/tv_joker/res/drawable-nodpi/bg_joker_dashboard.png").Path
$bmp = New-Object System.Drawing.Bitmap($file)
$w = $bmp.Width
$h = $bmp.Height

# Let's inspect vertical slice through the middle of each card to find card top and bottom edges
# Card 1 is Live TV around X = 500
# Card 2 is Guia TV around X = 820
# Card 3 is Filmes around X = 1130
# Card 4 is Series around X = 1440

function Get-EdgePoints([int]$x, [int]$yStart, [int]$yEnd) {
    $prev = $bmp.GetPixel($x, $yStart).GetBrightness()
    $maxDiff = 0
    $bestY = $yStart
    for ($y = $yStart + 1; $y -le $yEnd; $y++) {
        $curr = $bmp.GetPixel($x, $y).GetBrightness()
        $diff = [Math]::Abs($curr - $prev)
        Write-Host "X=$x, Y=$y : b=$curr, diff=$diff"
        $prev = $curr
    }
}

# Also let's scan horizontal slices:
# Let's scan along Y = 500 (inside the 4 cards) from X = 250 to 1700
# And find the left and right border of each card!
Write-Host "=== SCANNING CARDS HORIZONTALLY AT Y=520 ==="
$prevB = $bmp.GetPixel(300, 520).GetBrightness()
$transitions = @()
for ($x = 300; $x -le 1650; $x += 2) {
    # Check pixels around (x, 520)
    $c = $bmp.GetPixel($x, 520)
    # The background between cards is very dark or purple
    # Let's see R, G, B
}

# Let's sample colors across X at Y=520
$samples = @()
for ($x = 300; $x -le 1650; $x += 10) {
    $c = $bmp.GetPixel($x, 520)
    $samples += [PSCustomObject]@{ X = $x; R = $c.R; G = $c.G; B = $c.B; Brightness = [Math]::Round($c.GetBrightness(), 3) }
}

$samples | Where-Object { $_.Brightness -gt 0.05 } | Select-Object -First 30 | Format-Table

$bmp.Dispose()
