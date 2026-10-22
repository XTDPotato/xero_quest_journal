$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '../src/main/resources/assets/xero_quest_journal/textures/gui/navigation'
foreach ($iconName in @('layers','layers_hide','search_filter','filter_clear','preview')) {
    $bitmap = [Drawing.Bitmap]::new(32,32)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $pen = [Drawing.Pen]::new([Drawing.Color]::White,2)
    try {
        if ($iconName -like 'layers*') {
            $graphics.DrawPolygon($pen,[Drawing.Point[]]@([Drawing.Point]::new(16,4),[Drawing.Point]::new(28,11),
                [Drawing.Point]::new(16,18),[Drawing.Point]::new(4,11)))
            $graphics.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(4,17),[Drawing.Point]::new(16,24),[Drawing.Point]::new(28,17)))
            $graphics.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(4,23),[Drawing.Point]::new(16,30),[Drawing.Point]::new(28,23)))
            if ($iconName -eq 'layers_hide') {
                $graphics.DrawLine([Drawing.Pen]::new([Drawing.Color]::White,3),3,30,29,3)
            }
        } elseif ($iconName -eq 'search_filter') {
            $graphics.DrawEllipse($pen,3,3,16,16);$graphics.DrawLine($pen,16,16,25,25)
            $graphics.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(20,4),[Drawing.Point]::new(30,4),
                [Drawing.Point]::new(27,9),[Drawing.Point]::new(27,15)))
        } elseif ($iconName -eq 'preview') {
            $graphics.DrawBezier($pen,3,16,10,3,22,3,29,16)
            $graphics.DrawBezier($pen,3,16,10,29,22,29,29,16)
            $graphics.DrawEllipse($pen,12,12,8,8)
        } else {
            $graphics.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(3,5),[Drawing.Point]::new(27,5),
                [Drawing.Point]::new(20,15),[Drawing.Point]::new(20,25),[Drawing.Point]::new(14,29),
                [Drawing.Point]::new(14,15),[Drawing.Point]::new(3,5)))
            $graphics.DrawArc($pen,2,15,12,12,20,290)
            $graphics.DrawLine($pen,2,21,2,28)
        }
        $bitmap.Save((Join-Path $root "$iconName.png"),[Drawing.Imaging.ImageFormat]::Png)
    } finally {$pen.Dispose();$graphics.Dispose();$bitmap.Dispose()}
}
