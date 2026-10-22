param([Parameter(Mandatory=$true)][string]$ReferencePath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = [Drawing.Bitmap]::new((Resolve-Path -LiteralPath $ReferencePath).Path)
$result = [Drawing.Bitmap]::new(790, 505)
try {
    # Remove the paper tint row by row while retaining the supplied map, pin and handwriting contours.
    for ($y=0; $y -lt 505; $y++) {
        $sourceY = $y + 35
        $reference = $source.GetPixel(945, $sourceY)
        for ($x=0; $x -lt 790; $x++) {
            $pixel = $source.GetPixel($x + 100, $sourceY)
            $delta = (($reference.R - $pixel.R) + ($reference.G - $pixel.G) + ($reference.B - $pixel.B)) / 3
            $alpha = [Math]::Clamp([int](($delta - 1.5) * 9), 0, 130)
            $result.SetPixel($x,$y,[Drawing.Color]::FromArgb($alpha,128,128,128))
        }
    }
    $target = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/anecdote/notebook_imprint.png'
    $result.Save($target,[Drawing.Imaging.ImageFormat]::Png)
    Write-Output 'PASS: transparent PNG map watermark extracted from the user-provided reference'
} finally { $source.Dispose(); $result.Dispose() }
