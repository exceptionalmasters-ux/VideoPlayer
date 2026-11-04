$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase
# Reuse the exact Android logo's foreground geometry and palette.
$group = New-Object System.Windows.Media.DrawingGroup
$bg = New-Object System.Windows.Media.LinearGradientBrush ([System.Windows.Media.ColorConverter]::ConvertFromString('#1A3344')), ([System.Windows.Media.ColorConverter]::ConvertFromString('#0A1422')), 90
$group.Children.Add((New-Object System.Windows.Media.GeometryDrawing $bg, $null, (New-Object System.Windows.Media.RectangleGeometry (New-Object System.Windows.Rect 18,18,72,72),18,18)))
$mint = New-Object System.Windows.Media.LinearGradientBrush ([System.Windows.Media.ColorConverter]::ConvertFromString('#B2FFE9')), ([System.Windows.Media.ColorConverter]::ConvertFromString('#25ADA7')), 90
$geometry = [System.Windows.Media.Geometry]::Parse('F0 M38,28 C34.4,25.9 30,28.5 30,32.7 L30,75.3 C30,79.5 34.4,82.1 38,80 L79,58.7 C82.6,56.6 82.6,51.4 79,49.3 Z M43,42.7 C42.2,42.2 41.2,42.8 41.2,43.8 L41.2,64.2 C41.2,65.2 42.2,65.8 43,65.3 L60.8,55.1 C61.6,54.6 61.6,53.4 60.8,52.9 Z')
$group.Children.Add((New-Object System.Windows.Media.GeometryDrawing $mint, $null, $geometry))
$visual = New-Object System.Windows.Media.DrawingVisual
$dc = $visual.RenderOpen()
$dc.PushTransform((New-Object System.Windows.Media.ScaleTransform (256/72),(256/72)))
$dc.PushTransform((New-Object System.Windows.Media.TranslateTransform -18,-18))
$dc.DrawDrawing($group)
$dc.Close()
$bitmap = New-Object System.Windows.Media.Imaging.RenderTargetBitmap 256,256,96,96,([System.Windows.Media.PixelFormats]::Pbgra32)
$bitmap.Render($visual)
$encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
$memory = New-Object System.IO.MemoryStream
$encoder.Save($memory)
$png = $memory.ToArray()
$destination = Join-Path $PSScriptRoot 'VideoPlayer.Windows/app.ico'
$stream = [System.IO.File]::Create($destination)
$writer = New-Object System.IO.BinaryWriter $stream
$writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]1)
$writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0)
$writer.Write([UInt16]1); $writer.Write([UInt16]32); $writer.Write([UInt32]$png.Length); $writer.Write([UInt32]22)
$writer.Write($png); $writer.Dispose(); $memory.Dispose()
