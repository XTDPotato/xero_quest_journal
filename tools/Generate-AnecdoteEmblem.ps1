$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$output = Join-Path $PSScriptRoot "../src/main/resources/assets/xero_quest_journal/textures/gui/anecdote_emblem.png"
$image = [Drawing.Bitmap]::new(512, 512, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($image)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.ScaleTransform(2, 2)
$resources = [Collections.Generic.List[IDisposable]]::new()
function Brush([string]$hex) {
    $value = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($hex))
    $resources.Add($value)
    return $value
}
function Pen([string]$hex, [single]$width) {
    $value = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml($hex), $width)
    $value.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $value.StartCap = $value.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $resources.Add($value)
    return $value
}
function Points([single[]]$values) {
    $result = for ($index = 0; $index -lt $values.Length; $index += 2) {
        [Drawing.PointF]::new($values[$index], $values[$index + 1])
    }
    return ,([Drawing.PointF[]]$result)
}
try {
    $graphics.FillEllipse((Brush "#AC7246"), 22, 15, 212, 224)
    $graphics.DrawEllipse((Pen "#DCA362" 3), 28, 21, 200, 212)
    $dashes = Pen "#F1C78C" 2
    $dashes.DashPattern = [single[]]@(1, 3)
    $graphics.DrawEllipse($dashes, 37, 30, 182, 194)
    $book = [Drawing.Drawing2D.GraphicsPath]::new()
    $resources.Add($book)
    $book.AddLines((Points @(34, 52, 107, 52, 128, 61, 149, 52, 222, 52, 222, 207, 149, 207, 128, 214, 107, 207, 34, 207)))
    $book.CloseFigure()
    $graphics.FillPath((Brush "#FFF5D8"), $book)
    $graphics.DrawPath((Pen "#8F5A37" 9), $book)
    $graphics.DrawPath((Pen "#FFE2AC" 4), $book)
    $graphics.DrawLines((Pen "#D8A25E" 2.5), (Points @(44, 63, 106, 63, 122, 69, 122, 200, 106, 196, 44, 196, 44, 63)))
    $graphics.DrawLines((Pen "#D8A25E" 2.5), (Points @(134, 69, 150, 63, 211, 63, 211, 196, 150, 196, 134, 200, 134, 69)))
    $graphics.DrawLine((Pen "#B87A45" 2), 128, 73, 128, 198)
    foreach ($lineY in @(81, 94, 107)) {
        $graphics.DrawLine((Pen "#E9D2A4" 5), 152, $lineY, 195, $lineY)
    }
    $graphics.DrawLines((Pen "#E9D2A4" 4), (Points @(54, 75, 98, 75, 108, 81)))
    # A warm wax seal and a green keepsake card give the book a distinct silhouette.
    $graphics.FillEllipse((Brush "#FFAB57"), 35, 109, 94, 91)
    $graphics.DrawEllipse((Pen "#BB703B" 6), 35, 109, 94, 91)
    $graphics.DrawEllipse((Pen "#FFF0CA" 3), 42, 116, 80, 77)
    $graphics.FillEllipse((Brush "#FFF2D8"), 66, 132, 12, 20)
    $graphics.FillEllipse((Brush "#FFF2D8"), 91, 139, 10, 17)
    $graphics.DrawArc((Pen "#FFF2D8" 5), 69, 145, 34, 26, 12, 145)
    $graphics.FillEllipse((Brush "#FFF0CA"), 43, 92, 24, 23)
    $graphics.FillEllipse((Brush "#D58D4A"), 48, 97, 14, 13)
    $card = Points @(151, 111, 233, 137, 218, 192, 139, 168)
    $graphics.FillPolygon((Brush "#A8B173"), $card)
    $graphics.DrawPolygon((Pen "#7B884F" 5), $card)
    $graphics.DrawLine((Pen "#D3D7A0" 3), 155, 122, 219, 142)
    $graphics.FillPolygon((Brush "#F6C778"), (Points @(144, 105, 162, 111, 149, 133, 132, 126)))
    $heart = [Drawing.Drawing2D.GraphicsPath]::new()
    $resources.Add($heart)
    $heart.AddBezier(183, 190, 150, 170, 160, 143, 181, 155)
    $heart.AddBezier(181, 155, 198, 139, 221, 165, 183, 190)
    $graphics.FillPath((Brush "#FF9751"), $heart)
    $graphics.DrawPath((Pen "#FFF1D1" 4), $heart)
    $graphics.DrawLine((Pen "#FFD1A0" 2), 180, 161, 181, 180)
    $image.Save($output, [Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($resource in $resources) { $resource.Dispose() }
    $graphics.Dispose()
    $image.Dispose()
}
Write-Output "Created transparent anecdote book emblem: $output"
