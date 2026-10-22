$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui'
New-Item -ItemType Directory -Force -Path (Join-Path $assetRoot 'anecdote') | Out-Null
function Bitmap($name, $w, $h, $draw) {
    $bmp = [Drawing.Bitmap]::new($w, $h)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    try { & $draw $g; $bmp.Save((Join-Path $assetRoot $name), [Drawing.Imaging.ImageFormat]::Png) }
    finally { $g.Dispose(); $bmp.Dispose() }
}
function Brush($hex) { [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($hex)) }
function Heart($g, $x, $y, $s, $brush) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    try {
        $path.AddBezier($x + $s/2, $y + $s*.22, $x + $s*.05, $y - $s*.2, $x - $s*.15, $y + $s*.38, $x + $s*.15, $y + $s*.55)
        $path.AddBezier($x + $s*.15, $y + $s*.55, $x + $s*.28, $y + $s*.7, $x + $s*.4, $y + $s*.85, $x + $s*.5, $y + $s)
        $path.AddBezier($x + $s*.5, $y + $s, $x + $s*.65, $y + $s*.77, $x + $s*.82, $y + $s*.66, $x + $s*.92, $y + $s*.47)
        $path.AddBezier($x + $s*.92, $y + $s*.47, $x + $s*1.12, $y + $s*.04, $x + $s*.72, $y - $s*.14, $x + $s*.5, $y + $s*.22)
        $path.CloseFigure(); $g.FillPath($brush, $path)
    } finally { $path.Dispose() }
}
Bitmap 'anecdote/sticker_empty.png' 192 192 {
    param($g)
    $gold = Brush '#E7D5A9'; $paper = Brush '#FFFBEF'
    $g.FillEllipse($gold, 5, 5, 182, 182); $g.FillEllipse($paper, 9, 9, 174, 174)
    $pen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#EBDDBA'), 2)
    $clip=[Drawing.Drawing2D.GraphicsPath]::new()
    $clip.AddEllipse(15,15,162,162)
    $g.SetClip($clip)
    for ($x=-180; $x -lt 380; $x+=12) { $g.DrawLine($pen,$x,180,$x+170,10) }
    $g.ResetClip(); $clip.Dispose()
    for ($a=0; $a -lt 360; $a+=8) {
        $r=$a*[Math]::PI/180; $g.FillEllipse($gold,[single](94+81*[Math]::Cos($r)),[single](94+81*[Math]::Sin($r)),4,4)
    }
    $heart=Brush '#F0DEB8'; Heart $g 55 69 65 $heart; Heart $g 112 111 25 $heart
    $gold.Dispose(); $paper.Dispose(); $pen.Dispose(); $heart.Dispose()
}
Bitmap 'anecdote/seal.png' 512 256 {
    param($g)
    $gold=Brush '#E7D5A9'; $light=Brush '#FFF9E8'; $pen=[Drawing.Pen]::new($gold.Color,4)
    $g.FillEllipse($light,157,8,198,198); $g.DrawEllipse($pen,157,8,198,198)
    for ($a=0; $a -lt 360; $a+=9) {
        $r=$a*[Math]::PI/180; $g.FillEllipse($gold,[single](254+89*[Math]::Cos($r)),[single](105+89*[Math]::Sin($r)),4,4)
    }
    foreach ($cx in @(154,358)) {
        $points=[Drawing.PointF[]]@([Drawing.PointF]::new($cx,97),[Drawing.PointF]::new($cx+12,107),[Drawing.PointF]::new($cx,117),[Drawing.PointF]::new($cx-12,107))
        $g.FillPolygon($gold,$points)
    }
    foreach ($cy in @(7,206)) {
        $points=[Drawing.PointF[]]@([Drawing.PointF]::new(256,$cy-12),[Drawing.PointF]::new(264,$cy),[Drawing.PointF]::new(256,$cy+12),[Drawing.PointF]::new(248,$cy))
        $g.FillPolygon($gold,$points)
    }
    $ribbon=[Drawing.Drawing2D.GraphicsPath]::new()
    $ribbon.AddBezier(57,177,110,228,162,166,202,172)
    $ribbon.AddBezier(202,172,239,144,290,144,327,172)
    $ribbon.AddBezier(327,172,374,195,404,217,455,176)
    $ribbon.AddLine(455,176,447,214); $ribbon.AddLine(447,214,470,233)
    $ribbon.AddBezier(470,233,403,263,363,229,324,213)
    $ribbon.AddBezier(324,213,280,191,240,191,202,214)
    $ribbon.AddBezier(202,214,144,256,97,250,42,224)
    $ribbon.AddLine(42,224,65,208); $ribbon.CloseFigure(); $g.FillPath($gold,$ribbon)
    $curl=[Drawing.Pen]::new($gold.Color,16)
    $g.DrawArc($curl,94,158,66,65,45,260); $g.DrawArc($curl,350,158,66,65,235,260)
    $fold=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#F7ECD1'),2)
    $g.DrawBezier($fold,173,202,218,163,287,163,337,202)
    $g.DrawBezier($fold,78,207,106,227,130,225,148,215)
    $g.DrawBezier($fold,365,215,386,224,410,220,439,203)
    $curl.Dispose(); $fold.Dispose(); $ribbon.Dispose(); $gold.Dispose(); $light.Dispose(); $pen.Dispose()
}
if (!(Test-Path -LiteralPath (Join-Path $assetRoot 'anecdote/notebook_imprint.png'))) {
Bitmap 'anecdote/notebook_imprint.png' 384 300 {
    param($g)
    $ink=Brush '#DDC69D'; $pen=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#DDC69D'),5)
    $g.TranslateTransform(200,175); $g.RotateTransform(-24)
    $g.FillRectangle($ink,-90,-55,170,112)
    $paper=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#E9D6B3'),3)
    for($i=0;$i -lt 3;$i++){$g.DrawLine($paper,-80,60+$i*4,83,60+$i*4)}
    $g.DrawRectangle($paper,-82,-47,152,94)
    $g.DrawEllipse($paper,-40,-19,39,39); $g.DrawLine($paper,-20,-19,11,-43)
    $g.DrawLine($pen,-90,78,74,-71); $g.DrawLine($paper,-80,78,81,-63)
    $g.ResetTransform()
    for($i=0;$i -lt 4;$i++){$g.DrawBezier($pen,18,35+$i*19,55,17+$i*19,86,61+$i*19,122,34+$i*19)}
    $g.DrawBezier($pen,68,119,72,145,125,137,116,111)
    foreach($s in @(@(322,77),@(301,236),@(102,214))){
        $px=$s[0];$py=$s[1]
        $g.DrawLine($pen,$px-10,$py,$px+10,$py);$g.DrawLine($pen,$px,$py-13,$px,$py+13)
    }
    $ink.Dispose();$pen.Dispose();$paper.Dispose()
}
}
Bitmap 'anecdote/reward_ticket.png' 768 160 {
    param($g)
    $paper=Brush '#FFFBEF';$side=Brush '#FCF0D1'
    $pen=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#E0CA90'),3)
    $inner=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#F1E5C3'),3)
    $path=[Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddArc(4,4,24,24,180,90);$path.AddArc(740,4,24,24,270,90)
    $path.AddArc(740,132,24,24,0,90);$path.AddArc(4,132,24,24,90,90);$path.CloseFigure()
    $g.FillPath($paper,$path); $g.SetClip($path);$g.FillRectangle($side,580,5,184,151);$g.ResetClip()
    $g.DrawPath($pen,$path);$g.DrawRectangle($inner,15,15,738,130)
    for($y=20;$y -lt 143;$y+=18){$g.DrawLine($pen,580,$y,580,$y+8)}
    $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.FillEllipse([Drawing.Brushes]::Transparent,568,-12,24,24);$g.FillEllipse([Drawing.Brushes]::Transparent,568,148,24,24)
    $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceOver
    $g.DrawArc($pen,568,-12,24,24,0,180);$g.DrawArc($pen,568,148,24,24,180,180)
    $paper.Dispose();$side.Dispose();$pen.Dispose();$inner.Dispose();$path.Dispose()
}
Bitmap 'anecdote/close_tab.png' 96 96 {
    param($g)
    $bg=Brush '#454333'; $edge=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#A99B72'),3)
    $g.FillPolygon($bg,[Drawing.Point[]]@([Drawing.Point]::new(3,8),[Drawing.Point]::new(94,5),
        [Drawing.Point]::new(84,46),[Drawing.Point]::new(94,88),[Drawing.Point]::new(3,85)))
    $p=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#F4E6BC'),8)
    $g.DrawLine($p,27,29,67,69); $g.DrawLine($p,67,29,27,69)
    $tip=Brush '#F4E6BC'
    foreach($corner in @(@(23,25,1,1),@(71,25,-1,1),@(23,73,1,-1),@(71,73,-1,-1))){
        $cx=$corner[0];$cy=$corner[1];$sx=$corner[2];$sy=$corner[3]
        $g.FillPolygon($tip,[Drawing.Point[]]@([Drawing.Point]::new($cx,$cy),
            [Drawing.Point]::new($cx+16*$sx,$cy+2*$sy),[Drawing.Point]::new($cx+2*$sx,$cy+16*$sy)))
    }
    $bg.Dispose(); $edge.Dispose(); $p.Dispose();$tip.Dispose()
}
Bitmap 'anecdote/title_note.png' 1024 240 {
    param($g)
    $shadow=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(8,85,65,29))
    $green=Brush '#B7BD7A'; $ink=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#969C60'),3)
    $g.FillPolygon($green,[Drawing.Point[]]@([Drawing.Point]::new(740,3),[Drawing.Point]::new(946,44),[Drawing.Point]::new(927,107),[Drawing.Point]::new(724,65)))
    for($i=0;$i -lt 5;$i++) { $g.DrawBezier($ink,753,14+$i*9,790,2+$i*10,837,43+$i*10,914,47+$i*9) }
    for($i=10;$i -ge 1;$i--) { $g.FillRectangle($shadow,69+$i,51+$i,890,154) }
    $paper=[Drawing.Drawing2D.LinearGradientBrush]::new([Drawing.Point]::new(0,50),[Drawing.Point]::new(0,205),[Drawing.ColorTranslator]::FromHtml('#FFF9E2'),[Drawing.ColorTranslator]::FromHtml('#F9E9B8'))
    $edge=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#E9D6A4'),2)
    $points=[Drawing.Point[]]@([Drawing.Point]::new(64,45),[Drawing.Point]::new(964,45),[Drawing.Point]::new(961,196),[Drawing.Point]::new(91,204),[Drawing.Point]::new(65,182))
    $g.FillPolygon($paper,$points); $g.DrawPolygon($edge,$points)
    $strip=Brush '#EFDCA8'; $g.FillRectangle($strip,65,46,70,135)
    $fold=Brush '#FFF5D3'; $g.FillPolygon($fold,[Drawing.Point[]]@([Drawing.Point]::new(65,181),[Drawing.Point]::new(106,181),[Drawing.Point]::new(91,204)))
    $line=[Drawing.Pen]::new([Drawing.Color]::FromArgb(80,255,255,255),2)
    for($i=0;$i -lt 3;$i++) {$g.DrawLine($line,610,60+$i*10,942,60+$i*10)}
    $orange=Brush '#E3A06A'; $g.TranslateTransform(33,8); $g.RotateTransform(-12); Heart $g 0 0 116 $orange; $g.ResetTransform()
    $tape=Brush '#EBCD7B'; $tapeLight=Brush '#F6DA8C'
    $g.FillPolygon($tape,[Drawing.Point[]]@([Drawing.Point]::new(916,195),[Drawing.Point]::new(996,146),[Drawing.Point]::new(1018,182),[Drawing.Point]::new(939,232)))
    $g.FillPolygon($tapeLight,[Drawing.Point[]]@([Drawing.Point]::new(916,195),[Drawing.Point]::new(996,146),[Drawing.Point]::new(987,188),[Drawing.Point]::new(939,232)))
    foreach($resource in @($shadow,$green,$ink,$paper,$edge,$orange,$tape,$tapeLight,$strip,$fold,$line)) {$resource.Dispose()}
}
Bitmap 'anecdote/book_spread.png' 1400 800 {
    param($g)
    $cover=Brush '#435466'; $rim=Brush '#8F8B7E'; $leftPaper=Brush '#EBD9B5'; $rightPaper=Brush '#F5F0E7'
    $gold=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#D2B98C'),3)
    $g.FillRectangle($cover,4,10,1392,780); $g.FillRectangle($rim,12,14,1376,768)
    $g.FillRectangle($leftPaper,26,30,673,732); $g.FillRectangle($rightPaper,701,30,673,732)
    $g.DrawRectangle($gold,26,30,1348,732); $g.DrawRectangle($gold,31,35,1338,722)
    $g.DrawLine($gold,698,31,698,761); $g.DrawLine($gold,703,31,703,761)
    $fine=[Drawing.Pen]::new([Drawing.Color]::FromArgb(38,161,129,78),2)
    for ($y=120; $y -lt 700; $y+=185) { for($x=52; $x -lt 662; $x+=17) { $g.DrawLine($fine,$x,$y,$x+10,$y) } }
    for ($x=210; $x -lt 680; $x+=159) { for($y=108; $y -lt 720; $y+=16) { $g.DrawLine($fine,$x,$y,$x,$y+8) } }
    $cover.Dispose(); $rim.Dispose(); $leftPaper.Dispose(); $rightPaper.Dispose(); $gold.Dispose(); $fine.Dispose()
}
function DetailedBook($g, $split) {
    $cover=Brush '#344450'; $coverLight=Brush '#7F8078'; $rim=Brush '#ABA394'
    $edge=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#5E625E'),4)
    $g.FillRectangle($cover,5,8,1780,975); $g.FillRectangle($coverLight,16,17,1755,950)
    $g.FillRectangle($rim,25,29,1740,935); $g.DrawRectangle($edge,25,29,1740,935)
    $g.FillRectangle([Drawing.Brushes]::BurlyWood,48,54,1710,890)
    $leftPaper=[Drawing.Drawing2D.LinearGradientBrush]::new([Drawing.Point]::new(45,0),[Drawing.Point]::new($split,0),
        [Drawing.ColorTranslator]::FromHtml('#EDDCB9'),[Drawing.ColorTranslator]::FromHtml('#E7D3AE'))
    $rightPaper=Brush '#F3EFE7'; $paperEdge=Brush '#E2CCAA'
    $g.FillRectangle($paperEdge,44,47,$split-44,909); $g.FillRectangle($leftPaper,44,71,$split-44,867)
    $g.FillRectangle($paperEdge,$split,47,1710-$split,909); $g.FillRectangle($rightPaper,$split,71,1710-$split,867)
    $line=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#F9F0DD'),3)
    $g.DrawLine($line,47,78,1747,78); $g.DrawLine($line,47,930,1747,930)
    $shadow=[Drawing.Pen]::new([Drawing.Color]::FromArgb(24,92,72,42),2)
    for($i=0;$i -lt 5;$i++){$g.DrawLine($shadow,$split-5+$i,67,$split-5+$i,943)}
    $fine=[Drawing.Pen]::new([Drawing.Color]::FromArgb(35,172,143,87),2)
    $colWidth=($split-95)/4
    for($row=1;$row -le 2;$row++){
        $rowY=124+$row*253
        for($x=80;$x -lt $split-33;$x+=20) {$g.DrawLine($fine,$x,$rowY,$x+11,$rowY)}
    }
    for($col=1;$col -le 3;$col++){
        $colX=72+$col*$colWidth
        for($y=146;$y -lt 920;$y+=18){$g.DrawLine($fine,[single]$colX,$y,[single]$colX,$y+9)}
    }
    $margin=Brush '#817D6C'; $g.FillPolygon($margin,[Drawing.Point[]]@([Drawing.Point]::new(1760,296),[Drawing.Point]::new(1799,272),[Drawing.Point]::new(1799,885),[Drawing.Point]::new(1760,912)))
    $flower=Brush '#E7E6C8'; $yellow=Brush '#CBB268'; $stem=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#9C9E73'),4)
    for($i=0;$i -lt 5;$i++){
        $fx=1775+($i%2)*12; $fy=330+$i*112
        $g.DrawBezier($stem,$fx,950,$fx+14,$fy+100,$fx-10,$fy+30,$fx,$fy)
        for($petal=0;$petal -lt 8;$petal++){
            $a=$petal*[Math]::PI/4; $g.FillEllipse($flower,[single]($fx+16*[Math]::Cos($a)-9),[single]($fy+16*[Math]::Sin($a)-17),18,34)
        }
        $g.FillEllipse($yellow,$fx-10,$fy-10,20,20)
    }
    foreach($resource in @($cover,$coverLight,$rim,$edge,$leftPaper,$rightPaper,$paperEdge,$line,$shadow,$fine,$margin,$flower,$yellow,$stem)){$resource.Dispose()}
}
Bitmap 'anecdote/book_spread.png' 1800 1000 {param($g) DetailedBook $g 900}
Bitmap 'anecdote/reward_book_spread.png' 1800 1000 {param($g) DetailedBook $g 1030}
foreach ($name in @('category_journey','category_legendary','category_hot_events','category_anecdotes','refresh','fullscreen','sort_up','sort_down')) {
    $drawingName = $name
    Bitmap ('navigation/'+$name+'.png') 64 64 {
        param($g)
        $white=[Drawing.Brushes]::White; $pen=[Drawing.Pen]::new([Drawing.Color]::White,4)
        switch ($drawingName) {
            'category_journey' {
                $g.FillPolygon($white,[Drawing.Point[]]@([Drawing.Point]::new(12,12),[Drawing.Point]::new(43,9),[Drawing.Point]::new(41,49),[Drawing.Point]::new(17,55)))
                $g.DrawArc($pen,8,9,13,12,40,275); $g.DrawArc($pen,12,46,15,10,0,270)
                $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
                $g.FillRectangle([Drawing.Brushes]::Transparent,23,20,11,11)
                $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceOver
                $g.DrawLine($pen,34,48,55,12); $g.DrawLine($pen,42,32,57,20); $g.DrawLine($pen,46,23,58,10)
            }
            'category_legendary' {
                $g.DrawPolygon($pen,[Drawing.Point[]]@([Drawing.Point]::new(32,5),[Drawing.Point]::new(58,31),[Drawing.Point]::new(32,57),[Drawing.Point]::new(6,31)))
                $g.FillEllipse($white,27,11,10,13); $g.FillPolygon($white,[Drawing.Point[]]@([Drawing.Point]::new(27,18),[Drawing.Point]::new(37,18),[Drawing.Point]::new(33,39),[Drawing.Point]::new(31,39)))
                $g.FillEllipse($white,27,44,10,10)
            }
            'category_hot_events' {
                $g.DrawRectangle($pen,14,8,35,48); $g.DrawLine($pen,20,8,20,56); $g.FillRectangle($white,45,18,10,11)
                $g.FillPolygon($white,[Drawing.Point[]]@([Drawing.Point]::new(34,20),[Drawing.Point]::new(38,29),[Drawing.Point]::new(46,33),[Drawing.Point]::new(38,37),[Drawing.Point]::new(34,46),[Drawing.Point]::new(30,37),[Drawing.Point]::new(23,33),[Drawing.Point]::new(30,29)))
            }
            'category_anecdotes' {
                $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(8,22),[Drawing.Point]::new(23,16),[Drawing.Point]::new(31,47),[Drawing.Point]::new(46,52),[Drawing.Point]::new(56,46)))
                $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(8,22),[Drawing.Point]::new(18,49),[Drawing.Point]::new(31,47)))
                $g.DrawLine($pen,18,49,10,54)
                $g.DrawEllipse($pen,31,6,23,22)
                $g.DrawLine($pen,32,21,43,41); $g.DrawLine($pen,53,21,43,41); $g.FillEllipse($white,39,13,7,7)
            }
            'refresh' { $g.DrawArc($pen,12,12,40,40,35,290); $g.FillPolygon($white,[Drawing.Point[]]@([Drawing.Point]::new(53,7),[Drawing.Point]::new(55,26),[Drawing.Point]::new(37,21))) }
            'fullscreen' {
                foreach ($v in @(0,1,2,3)) {
                    $g.ResetTransform(); if ($v%2) { $g.TranslateTransform(64,0); $g.ScaleTransform(-1,1) }; if ($v -ge 2) { $g.TranslateTransform(0,64); $g.ScaleTransform(1,-1) }
                    $g.DrawLine($pen,11,25,11,11); $g.DrawLine($pen,11,11,25,11)
                }
            }
            { $_ -in 'sort_up','sort_down' } {
                if ($drawingName -eq 'sort_down') { $g.TranslateTransform(0,64); $g.ScaleTransform(1,-1) }
                $g.DrawLine($pen,18,50,18,12); $g.DrawLine($pen,18,12,9,22); $g.DrawLine($pen,18,12,27,22)
                $g.DrawLine($pen,36,15,44,15); $g.DrawLine($pen,36,30,50,30); $g.DrawLine($pen,36,45,57,45)
            }
        }
        $pen.Dispose()
    }
}
Write-Output 'PASS: PNG anecdote stationery, stickers, seal, close tab and category/picker icons generated'
