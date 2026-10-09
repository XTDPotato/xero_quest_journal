Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation'
$image = [System.Drawing.Bitmap]::new(32, 32)
$g = [System.Drawing.Graphics]::FromImage($image)
$pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 3)
try {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.DrawArc($pen, 9, 3, 14, 18, 180, 180)
    $g.DrawLine($pen, 9, 12, 9, 17)
    $g.DrawLine($pen, 23, 12, 23, 17)
    $g.FillRectangle([System.Drawing.Brushes]::White, 5, 14, 22, 15)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.FillEllipse([System.Drawing.Brushes]::Transparent, 14, 18, 4, 4)
    $g.FillRectangle([System.Drawing.Brushes]::Transparent, 15, 21, 2, 4)
    $image.Save((Join-Path $root 'lock.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $pen.Dispose()
    $g.Dispose()
    $image.Dispose()
}
