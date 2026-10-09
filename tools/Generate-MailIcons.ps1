Add-Type -AssemblyName System.Drawing
$destination = Join-Path $PSScriptRoot '..\src\main\resources\assets\xero_quest_journal\textures\gui\navigation\mail_pending.png'
$bitmap = [System.Drawing.Bitmap]::new(128, 128)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$shadow = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#932B43'))
$red = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#EE4765'))
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
try {
    $graphics.FillEllipse($shadow, 12, 18, 104, 104)
    $graphics.FillEllipse($red, 12, 8, 104, 104)
    $graphics.TranslateTransform(64, 60)
    $graphics.RotateTransform(5)
    $graphics.FillRectangle($white, -6, -32, 12, 42)
    $graphics.FillEllipse($white, -6, 19, 12, 12)
    $graphics.Dispose()
    $icon = [System.Drawing.Bitmap]::new(32, 32)
    $output = [System.Drawing.Graphics]::FromImage($icon)
    $output.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $output.DrawImage($bitmap, 0, 0, 32, 32)
    $output.Dispose()
    $icon.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
    $shadow.Dispose()
    $red.Dispose()
    $white.Dispose()
}
