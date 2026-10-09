Add-Type -AssemblyName System.Drawing
$destination = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation/send.png'
$image = [System.Drawing.Bitmap]::new(32, 32)
$graphics = [System.Drawing.Graphics]::FromImage($image)
$pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 2)
try {
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $points = [System.Drawing.PointF[]]@(
        [System.Drawing.PointF]::new(5, 14),
        [System.Drawing.PointF]::new(26, 6),
        [System.Drawing.PointF]::new(20, 27),
        [System.Drawing.PointF]::new(14, 20)
    )
    $graphics.DrawPolygon($pen, $points)
    $graphics.DrawLine($pen, 14, 20, 26, 6)
    $image.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $pen.Dispose()
    $graphics.Dispose()
    $image.Dispose()
}
