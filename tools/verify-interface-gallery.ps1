param([string]$Document = "docs/interface-gallery.md")
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$documentPath = Join-Path $repoRoot $Document
$content = Get-Content -LiteralPath $documentPath -Raw -Encoding utf8
$photos = [regex]::Matches($content, '!\[([^\]]*)\]\(((?:\.\./)?images/[^)]+)\)') |
    ForEach-Object { [pscustomobject]@{ Caption=$_.Groups[1].Value; Path=$_.Groups[2].Value } }
if ($photos.Count -lt 40) { throw "The common-interface gallery is incomplete" }
Add-Type -AssemblyName System.Drawing
$reportRoot = Join-Path $repoRoot ("build/verification/interface-gallery/" + $(if ($Document -match '[/\\]en[/\\]') { "en" } else { "zh" }))
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$results = [Collections.Generic.List[object]]::new()
foreach ($photo in $photos) {
    $path = Join-Path (Split-Path -Parent $documentPath) $photo.Path
    $image = [Drawing.Image]::FromFile($path)
    try {
        if ($image.Width -ne 1920 -or $image.Height -ne 1080) {
            throw "Not native 1920x1080: $($photo.Path)"
        }
        $results.Add([pscustomobject]@{
            File=$photo.Path; Caption=$photo.Caption; Width=$image.Width; Height=$image.Height
            SHA256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    } finally { $image.Dispose() }
}
$results | ConvertTo-Json -Depth 3 |
    Set-Content -LiteralPath (Join-Path $reportRoot "screenshots.json") -Encoding utf8
for ($page = 0; $page * 16 -lt $photos.Count; $page++) {
    $sheet = [Drawing.Bitmap]::new(1920, 1176)
    $canvas = [Drawing.Graphics]::FromImage($sheet)
    $font = [Drawing.Font]::new("Arial", 11)
    try {
        $canvas.Clear([Drawing.Color]::FromArgb(24, 28, 33))
        for ($cell = 0; $cell -lt 16 -and $page * 16 + $cell -lt $photos.Count; $cell++) {
            $photo = $photos[$page * 16 + $cell]
            $image = [Drawing.Image]::FromFile((Join-Path (Split-Path -Parent $documentPath) $photo.Path))
            try {
                $x = ($cell % 4) * 480
                $y = [Math]::Floor($cell / 4) * 294
                $canvas.DrawImage($image, [int]$x, [int]$y, 480, 270)
                $canvas.DrawString((Split-Path $photo.Path -Leaf), $font, [Drawing.Brushes]::White,
                    [single]($x + 4), [single]($y + 272))
            } finally { $image.Dispose() }
        }
        $sheet.Save((Join-Path $reportRoot "contact-$page.png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $canvas.Dispose(); $sheet.Dispose() }
}
"PASS: $($photos.Count) gallery images at native 1920x1080; hashes and contact sheets: $reportRoot"
