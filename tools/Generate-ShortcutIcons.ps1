$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root "src/main/resources/assets/xero_quest_journal/textures/gui/navigation"

# Draw the quest pennant from the reference shape on a supersampled transparent canvas.
$scale = 4
$image = [Drawing.Bitmap]::new(16 * $scale, 16 * $scale, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($image)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graphics.ScaleTransform($scale, $scale)
$outline = [Drawing.Pen]::new([Drawing.Color]::FromArgb(255, 153, 130, 108), 0.55)
$paper = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 244, 235, 215))
$clear = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(0, 0, 0, 0))
$shape = [Drawing.Drawing2D.GraphicsPath]::new()
$shape.AddPolygon([Drawing.Point[]]@(
    [Drawing.Point]::new(4, 3), [Drawing.Point]::new(12, 3),
    [Drawing.Point]::new(12, 13), [Drawing.Point]::new(8, 11),
    [Drawing.Point]::new(4, 13)
))
$graphics.FillPolygon($paper, [Drawing.Point[]]@(
    [Drawing.Point]::new(4, 3), [Drawing.Point]::new(12, 3),
    [Drawing.Point]::new(12, 13), [Drawing.Point]::new(8, 11),
    [Drawing.Point]::new(4, 13)
))
$graphics.DrawPath($outline, $shape)
$graphics.FillRectangle($paper, 3, 2.5, 1.5, 1.2)
$graphics.FillRectangle($paper, 11.5, 2.5, 1.5, 1.2)
$graphics.DrawLine($outline, 3, 2.5, 4.5, 2.5)
$graphics.DrawLine($outline, 11.5, 2.5, 13, 2.5)
$graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
$graphics.FillRectangle($clear, 7.55, 4.7, 0.9, 4.2)
$graphics.FillEllipse($clear, 7.2, 10, 1.6, 1.6)
$graphics.Dispose()
$shape.Dispose()
$outline.Dispose()
$paper.Dispose()
$clear.Dispose()
$task = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$resample = [Drawing.Graphics]::FromImage($task)
$resample.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$resample.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
$resample.DrawImage($image, 0, 0, 16, 16)
$resample.Dispose()
for ($y = 5; $y -le 9; $y++) {
    $task.SetPixel(8, $y, [Drawing.Color]::Transparent)
}
$task.SetPixel(8, 11, [Drawing.Color]::Transparent)
$task.SetPixel(8, 12, [Drawing.Color]::Transparent)
$task.Save((Join-Path $output "task_shortcut.png"), [Drawing.Imaging.ImageFormat]::Png)
$image.Dispose()
$task.Dispose()

# Keep the existing dedicated envelope art, but ship a native 16x16 shortcut variant.
$mail = [Drawing.Image]::FromFile((Join-Path $output "mail.png"))
$mailSmall = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$mailGraphics = [Drawing.Graphics]::FromImage($mailSmall)
$mailGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$mailGraphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
$mailGraphics.DrawImage($mail, 0, 0, 16, 16)
$mailGraphics.Dispose()
$mail.Dispose()
$mailSmall.Save((Join-Path $output "mail_shortcut.png"), [Drawing.Imaging.ImageFormat]::Png)
$mailSmall.Dispose()
Write-Output "Created transparent 16x16 task and mail shortcut textures."
