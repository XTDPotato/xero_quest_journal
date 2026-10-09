Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation'
$names = @('undo','redo','fit','variables','grid','source','visual','save','delete','add','choose','expand','refresh','unlink','clause','bold','italic','underline','strike','linebreak','color')
foreach ($name in $names) {
    $image = [System.Drawing.Bitmap]::new(32, 32)
    $g = [System.Drawing.Graphics]::FromImage($image)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 2)
    try {
        switch ($name) {
            { $_ -in 'undo','redo','refresh' } {
                if ($name -eq 'redo') { $g.TranslateTransform(32,0); $g.ScaleTransform(-1,1) }
                $g.DrawArc($pen,7,9,19,16,205,250)
                $g.DrawLine($pen,6,7,6,16)
                $g.DrawLine($pen,6,16,15,16)
            }
            { $_ -in 'fit','expand' } {
                foreach ($p in @(@(6,6,12,6,6,12),@(26,6,20,6,26,12),@(6,26,12,26,6,20),@(26,26,20,26,26,20))) {
                    $g.DrawLine($pen,$p[0],$p[1],$p[2],$p[3])
                    $g.DrawLine($pen,$p[0],$p[1],$p[4],$p[5])
                }
                if ($name -eq 'fit') { $g.DrawRectangle($pen,12,12,8,8) }
            }
            'variables' {
                $g.DrawEllipse($pen,5,5,22,7)
                $g.DrawArc($pen,5,13,22,7,0,180)
                $g.DrawArc($pen,5,21,22,7,0,180)
                $g.DrawLine($pen,5,9,5,25); $g.DrawLine($pen,27,9,27,25)
            }
            'grid' {
                $g.DrawRectangle($pen,6,6,20,20)
                foreach($v in @(13,20)) { $g.DrawLine($pen,$v,6,$v,26); $g.DrawLine($pen,6,$v,26,$v) }
            }
            'source' {
                $g.DrawLine($pen,11,9,5,16); $g.DrawLine($pen,5,16,11,23)
                $g.DrawLine($pen,21,9,27,16); $g.DrawLine($pen,27,16,21,23)
                $g.DrawLine($pen,18,6,14,26)
            }
            'visual' {
                $g.DrawRectangle($pen,4,12,8,8); $g.DrawRectangle($pen,22,4,6,6); $g.DrawRectangle($pen,22,22,6,6)
                $g.DrawLine($pen,12,16,22,7); $g.DrawLine($pen,12,16,22,25)
            }
            'save' {
                $g.DrawRectangle($pen,6,5,20,22); $g.DrawRectangle($pen,11,5,10,8); $g.DrawRectangle($pen,11,19,10,8)
            }
            'delete' {
                $g.DrawLine($pen,6,9,26,9); $g.DrawRectangle($pen,12,5,8,4)
                $g.DrawLine($pen,9,10,10,27); $g.DrawLine($pen,23,10,22,27); $g.DrawLine($pen,10,27,22,27)
                $g.DrawLine($pen,14,13,14,23); $g.DrawLine($pen,18,13,18,23)
            }
            'add' { $g.DrawLine($pen,6,16,26,16); $g.DrawLine($pen,16,6,16,26) }
            'choose' {
                $g.DrawRectangle($pen,5,5,22,22)
                $g.DrawLine($pen,10,16,14,21); $g.DrawLine($pen,14,21,23,11)
            }
            'unlink' {
                $g.DrawArc($pen,4,8,14,14,70,250); $g.DrawArc($pen,14,10,14,14,250,250)
                $g.DrawLine($pen,8,28,24,4)
            }
            'clause' {
                $g.DrawRectangle($pen,5,6,22,8); $g.DrawRectangle($pen,5,19,22,8)
                $g.DrawLine($pen,16,14,16,19)
            }
            'bold' {
                $g.DrawLine($pen,10,6,10,26); $g.DrawLine($pen,10,6,17,6)
                $g.DrawArc($pen,12,6,12,10,270,180); $g.DrawLine($pen,10,16,18,16)
                $g.DrawArc($pen,12,16,12,10,270,180); $g.DrawLine($pen,10,26,18,26)
            }
            'italic' {
                $g.DrawLine($pen,12,6,24,6); $g.DrawLine($pen,8,26,20,26); $g.DrawLine($pen,20,6,12,26)
            }
            'underline' {
                $g.DrawLine($pen,9,5,9,16); $g.DrawLine($pen,23,5,23,16); $g.DrawArc($pen,9,9,14,14,0,180)
                $g.DrawLine($pen,7,27,25,27)
            }
            'strike' {
                $g.DrawArc($pen,9,5,14,12,80,250); $g.DrawArc($pen,9,15,14,12,260,250)
                $g.DrawLine($pen,5,16,27,16)
            }
            'linebreak' {
                $g.DrawLine($pen,26,6,26,20); $g.DrawLine($pen,26,20,7,20)
                $g.DrawLine($pen,7,20,13,14); $g.DrawLine($pen,7,20,13,26)
            }
            'color' {
                $g.DrawEllipse($pen,6,6,20,20); $g.FillEllipse([System.Drawing.Brushes]::White,10,10,12,12)
            }
        }
        $image.Save((Join-Path $root "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $pen.Dispose(); $g.Dispose(); $image.Dispose() }
}
