Add-Type -AssemblyName System.Drawing
$destination = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation/mail.png'
$image = [System.Drawing.Bitmap]::new(32, 32)
$graphics = [System.Drawing.Graphics]::FromImage($image)
$pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 2)
try {
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $graphics.DrawRectangle($pen, 4, 8, 24, 17)
    $graphics.DrawLine($pen, 4, 8, 16, 18)
    $graphics.DrawLine($pen, 16, 18, 28, 8)
    $graphics.DrawLine($pen, 4, 25, 12, 17)
    $graphics.DrawLine($pen, 28, 25, 20, 17)
    $image.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $pen.Dispose()
    $graphics.Dispose()
    $image.Dispose()
}
