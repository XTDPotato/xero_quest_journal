Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation'
[System.IO.Directory]::CreateDirectory($root) | Out-Null
foreach ($name in @('all', 'recommended', 'guide', 'item', 'other', 'overview', 'track', 'close', 'edit', 'settings', 'back')) {
    $image = [System.Drawing.Bitmap]::new(32, 32)
    $g = [System.Drawing.Graphics]::FromImage($image)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 2.5)
    $brush = [System.Drawing.Brushes]::White
    try {
        switch ($name) {
            'all' {
                foreach ($point in @(@(9,9), @(23,9), @(9,23), @(23,23))) {
                    $x = $point[0]; $y = $point[1]
                    $g.FillPolygon($brush, [System.Drawing.PointF[]]@(
                        [System.Drawing.PointF]::new($x,$y-6), [System.Drawing.PointF]::new($x+6,$y),
                        [System.Drawing.PointF]::new($x,$y+6), [System.Drawing.PointF]::new($x-6,$y)))
                }
            }
            'recommended' {
                $g.DrawPolygon($pen, [System.Drawing.PointF[]]@(
                    [System.Drawing.PointF]::new(16,3), [System.Drawing.PointF]::new(20,12),
                    [System.Drawing.PointF]::new(29,16), [System.Drawing.PointF]::new(20,20),
                    [System.Drawing.PointF]::new(16,29), [System.Drawing.PointF]::new(12,20),
                    [System.Drawing.PointF]::new(3,16), [System.Drawing.PointF]::new(12,12)))
                $g.FillEllipse($brush, 13,13,6,6)
            }
            'guide' {
                $g.DrawEllipse($pen, 3,3,26,26)
                $g.DrawLines($pen, [System.Drawing.PointF[]]@(
                    [System.Drawing.PointF]::new(8,16), [System.Drawing.PointF]::new(14,22),
                    [System.Drawing.PointF]::new(25,10)))
            }
            'item' {
                $g.DrawRectangle($pen, 4,10,24,17)
                $g.DrawLine($pen, 4,10,10,4); $g.DrawLine($pen,10,4,22,4); $g.DrawLine($pen,22,4,28,10)
                $g.DrawLine($pen,16,4,16,27)
            }
            'other' {
                $g.FillRectangle($brush, 13,3,6,18)
                $g.FillEllipse($brush, 13,25,6,6)
            }
            'overview' {
                $g.DrawRectangle($pen, 5,3,22,26)
                $g.DrawLine($pen,10,10,22,10); $g.DrawLine($pen,10,16,22,16); $g.DrawLine($pen,10,22,18,22)
            }
            'track' {
                $g.DrawPolygon($pen, [System.Drawing.PointF[]]@(
                    [System.Drawing.PointF]::new(16,2), [System.Drawing.PointF]::new(30,16),
                    [System.Drawing.PointF]::new(16,30), [System.Drawing.PointF]::new(2,16)))
                $g.FillEllipse($brush, 12,12,8,8)
            }
            'close' { $g.DrawLine($pen,8,8,24,24); $g.DrawLine($pen,24,8,8,24) }
            'edit' {
                $g.DrawPolygon($pen, [System.Drawing.PointF[]]@(
                    [System.Drawing.PointF]::new(5,27), [System.Drawing.PointF]::new(7,20),
                    [System.Drawing.PointF]::new(23,4), [System.Drawing.PointF]::new(28,9),
                    [System.Drawing.PointF]::new(12,25)))
            }
            'settings' {
                $g.DrawEllipse($pen,8,8,16,16); $g.DrawEllipse($pen,13,13,6,6)
                foreach ($angle in @(0,45,90,135,180,225,270,315)) {
                    $r = $angle * [Math]::PI / 180
                    $g.DrawLine($pen, [float](16+10*[Math]::Cos($r)), [float](16+10*[Math]::Sin($r)),
                        [float](16+14*[Math]::Cos($r)), [float](16+14*[Math]::Sin($r)))
                }
            }
            'back' {
                $g.DrawLine($pen,5,16,27,16)
                $g.DrawLines($pen, [System.Drawing.PointF[]]@(
                    [System.Drawing.PointF]::new(14,6), [System.Drawing.PointF]::new(4,16),
                    [System.Drawing.PointF]::new(14,26)))
            }
        }
        $image.Save((Join-Path $root "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $pen.Dispose(); $g.Dispose(); $image.Dispose() }
}
