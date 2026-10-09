Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation'
foreach ($name in @('mail_new_bg', 'mail_new_frame')) {
    $image = [System.Drawing.Bitmap]::new(64, 64)
    $g = [System.Drawing.Graphics]::FromImage($image)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddArc(8, 8, 16, 16, 180, 90)
    $path.AddArc(40, 8, 16, 16, 270, 90)
    $path.AddArc(40, 40, 16, 16, 0, 90)
    $path.AddArc(8, 40, 16, 16, 90, 90)
    $path.CloseFigure()
    $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 3.5)
    try {
        if ($name -eq 'mail_new_bg') { $g.FillPath([System.Drawing.Brushes]::White, $path) }
        else {
            $g.DrawPath($pen, $path)
            foreach ($p in @(@(54,8,10), @(8,54,8))) {
                $x=$p[0]; $y=$p[1]; $r=$p[2]
                $g.FillPolygon([System.Drawing.Brushes]::White, [System.Drawing.PointF[]]@(
                    [System.Drawing.PointF]::new($x,$y-$r), [System.Drawing.PointF]::new($x+2,$y-2),
                    [System.Drawing.PointF]::new($x+$r,$y), [System.Drawing.PointF]::new($x+2,$y+2),
                    [System.Drawing.PointF]::new($x,$y+$r), [System.Drawing.PointF]::new($x-2,$y+2),
                    [System.Drawing.PointF]::new($x-$r,$y), [System.Drawing.PointF]::new($x-2,$y-2)))
            }
        }
        $image.Save((Join-Path $root "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $pen.Dispose(); $path.Dispose(); $g.Dispose(); $image.Dispose() }
}
