param([string[]]$Only = @())
Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation'
$names = @('mail_help','mail_treasure','mail_close','mail_star','mail_claim','mail_sender','mail_envelope','mail_corner','mail_check','mail_search')
if ($Only.Count -gt 0) { $names = $names | Where-Object { $_ -in $Only } }
foreach ($name in $names) {
    $image = [System.Drawing.Bitmap]::new(32, 32)
    $g = [System.Drawing.Graphics]::FromImage($image)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $white = [System.Drawing.Brushes]::White
    $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 3)
    try {
        switch ($name) {
            'mail_help' {
                $f = [System.Drawing.Font]::new('Georgia', 30, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
                $g.DrawString('?', $f, $white, 5, -4)
                $f.Dispose()
            }
            'mail_treasure' {
                $g.FillRectangle($white, 4, 8, 24, 21)
                $g.FillRectangle($white, 5, 4, 10, 5)
                $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $clear = [System.Drawing.Brushes]::Transparent
                $g.FillEllipse($clear, 10, 14, 7, 7)
                $g.FillEllipse($clear, 15, 14, 7, 7)
                $g.FillPolygon($clear, [System.Drawing.Point[]]@([System.Drawing.Point]::new(10,18),[System.Drawing.Point]::new(22,18),[System.Drawing.Point]::new(16,25)))
            }
            'mail_close' {
                $pen.Width = 5
                $g.DrawLine($pen, 7, 7, 25, 25)
                $g.DrawLine($pen, 25, 7, 7, 25)
                foreach ($p in @(@(3,3,14,3,3,14),@(29,3,18,3,29,14),@(3,29,14,29,3,18),@(29,29,18,29,29,18))) {
                    $g.FillPolygon($white, [System.Drawing.Point[]]@([System.Drawing.Point]::new($p[0],$p[1]),[System.Drawing.Point]::new($p[2],$p[3]),[System.Drawing.Point]::new($p[4],$p[5])))
                }
            }
            'mail_star' {
                $points = for ($i=0; $i -lt 10; $i++) {
                    $r = if ($i % 2 -eq 0) { 15 } else { 7 }
                    $a = -[Math]::PI/2 + $i*[Math]::PI/5
                    [System.Drawing.PointF]::new(16+$r*[Math]::Cos($a),16+$r*[Math]::Sin($a))
                }
                $g.FillPolygon($white, [System.Drawing.PointF[]]$points)
            }
            'mail_claim' {
                $g.FillRectangle($white, 12, 2, 8, 14)
                $g.FillPolygon($white,[System.Drawing.Point[]]@([System.Drawing.Point]::new(4,13),[System.Drawing.Point]::new(28,13),[System.Drawing.Point]::new(16,26)))
                $g.DrawLines($pen,[System.Drawing.Point[]]@([System.Drawing.Point]::new(6,23),[System.Drawing.Point]::new(16,31),[System.Drawing.Point]::new(26,23)))
            }
            'mail_sender' {
                $g.FillEllipse($white,10,2,12,12)
                $g.FillPolygon($white,[System.Drawing.Point[]]@([System.Drawing.Point]::new(9,17),[System.Drawing.Point]::new(23,17),[System.Drawing.Point]::new(28,24),[System.Drawing.Point]::new(16,30),[System.Drawing.Point]::new(4,24)))
            }
            'mail_envelope' {
                $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
                $g.FillPolygon($white,[System.Drawing.Point[]]@(
                    [System.Drawing.Point]::new(3,9),[System.Drawing.Point]::new(27,5),
                    [System.Drawing.Point]::new(31,23),[System.Drawing.Point]::new(7,27)))
                $seam = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255,110,110,110), 1)
                try {
                    $g.DrawLines($seam,[System.Drawing.Point[]]@(
                        [System.Drawing.Point]::new(4,10),[System.Drawing.Point]::new(17,17),
                        [System.Drawing.Point]::new(28,7)))
                    $g.FillPolygon($white,[System.Drawing.Point[]]@(
                        [System.Drawing.Point]::new(16,18),[System.Drawing.Point]::new(20,17),
                        [System.Drawing.Point]::new(23,29),[System.Drawing.Point]::new(20,27),
                        [System.Drawing.Point]::new(18,30)))
                    $g.DrawLine($seam,16,19,18,28)
                    $g.DrawLine($seam,20,18,22,27)
                    $g.FillEllipse($white,13,13,8,8)
                    $g.DrawEllipse($seam,13,13,8,8)
                } finally { $seam.Dispose() }
            }
            'mail_corner' {
                $g.FillPolygon($white,[System.Drawing.Point[]]@([System.Drawing.Point]::new(2,30),[System.Drawing.Point]::new(2,7),[System.Drawing.Point]::new(7,2),[System.Drawing.Point]::new(30,2),[System.Drawing.Point]::new(16,10),[System.Drawing.Point]::new(12,22),[System.Drawing.Point]::new(6,16)))
            }
            'mail_check' {
                $pen.Width = 5
                $g.DrawLines($pen,[System.Drawing.Point[]]@([System.Drawing.Point]::new(4,16),[System.Drawing.Point]::new(12,25),[System.Drawing.Point]::new(29,5)))
            }
            'mail_search' {
                $g.DrawEllipse($pen,3,3,18,18)
                $g.DrawLine($pen,19,19,29,29)
            }
        }
        $image.Save((Join-Path $root "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $pen.Dispose()
        $g.Dispose()
        $image.Dispose()
    }
}
if ($Only.Count -gt 0 -and 'mail_corner' -notin $Only) { return }
$corner = [System.Drawing.Bitmap]::FromFile((Join-Path $root 'mail_corner.png'))
try {
    foreach ($entry in @(@('mail_corner_tr', [System.Drawing.RotateFlipType]::RotateNoneFlipX),
                         @('mail_corner_bl', [System.Drawing.RotateFlipType]::RotateNoneFlipY),
                         @('mail_corner_br', [System.Drawing.RotateFlipType]::Rotate180FlipNone))) {
        $copy = $corner.Clone()
        $copy.RotateFlip($entry[1])
        $copy.Save((Join-Path $root ($entry[0] + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
        $copy.Dispose()
    }
} finally { $corner.Dispose() }
