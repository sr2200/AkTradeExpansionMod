# AK Trade Expansion: thumbnail for the mod's download page (1280x720 PNG).
# The mod icon (pixel art) scaled up without smoothing, the title, a price chart and the main features.
# Uses the Japanese font BIZ UDGothic that comes with Windows.
# Usage (in Source/TradeExpansion): pwsh tools/thumbnail/genthumb.ps1 [output.png] [version]
param(
    [string]$Out = "../../docs/release/thumbnail.png",
    [string]$Version = ""
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # Source/TradeExpansion
if (-not $Version) {
    $m = [regex]::Match((Get-Content (Join-Path $root 'gradle.properties') -Raw), '(?m)^version=(\S+)')
    $Version = $m.Groups[1].Value
}
$W = 1280; $H = 720
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAliasGridFit'
function Color([string]$hex, [int]$a = 255) {
    $c = [System.Drawing.ColorTranslator]::FromHtml($hex)
    return [System.Drawing.Color]::FromArgb($a, $c.R, $c.G, $c.B)
}
function Brush([string]$hex, [int]$a = 255) { New-Object System.Drawing.SolidBrush (Color $hex $a) }
$family = 'BIZ UDGothic'

# --- sky and ground ---
$sky = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.Rectangle 0, 0, $W, 560), (Color '#5FB4EA'), (Color '#CFEFFF'), 90
$g.FillRectangle($sky, 0, 0, $W, 560)
$g.SmoothingMode = 'None'   # sharp block edges (no seams)
# blocky ground: grass on top of dirt, 40 px "blocks"
$B = 40
for ($x = 0; $x -lt $W; $x += $B) {
    $g.FillRectangle((Brush '#5DBB4C'), $x, 560, $B, 16)
    $g.FillRectangle((Brush '#4A9E3C'), $x, 576, $B, 6)
    for ($y = 582; $y -lt $H; $y += $B) {
        $shade = if ((($x / $B) + (($y - 582) / $B)) % 2 -eq 0) { '#8B5A2B' } else { '#7C4F25' }
        $g.FillRectangle((Brush $shade), $x, $y, $B, $B)
    }
}
# soft clouds (rounded rectangles of blocks)
foreach ($c in @(@(120, 70, 5), @(520, 40, 4), @(980, 90, 6))) {
    for ($i = 0; $i -lt $c[2]; $i++) { $g.FillRectangle((Brush '#F4FBFF'), $c[0] + $i * 30, $c[1], 30, 24) }
    for ($i = 1; $i -lt $c[2] - 1; $i++) { $g.FillRectangle((Brush '#F4FBFF'), $c[0] + $i * 30, $c[1] - 18, 30, 18) }
}

$g.SmoothingMode = 'AntiAlias'
# --- the mod icon, big and sharp (pixel art) ---
$icon = [System.Drawing.Image]::FromFile((Join-Path $root 'src/main/resources/assets/ak_trade_expansion/icon.png'))
$g.InterpolationMode = 'NearestNeighbor'
$g.PixelOffsetMode = 'Half'
# shadow, then the icon (128 px icon x 3.5 = 448 px)
$g.FillEllipse((Brush '#000000' 60), 70, 548, 450, 40)
$g.DrawImage($icon, 70, 120, 448, 448)
$g.PixelOffsetMode = 'Default'
$g.InterpolationMode = 'HighQualityBicubic'

# --- title with an outline ---
function OutlinedText([string]$text, [float]$size, [string]$style, [float]$x, [float]$y, [string]$fill, [string]$outline, [float]$width) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $font = New-Object System.Drawing.FontFamily $family
    $path.AddString($text, $font, [int][System.Drawing.FontStyle]::$style, $size, (New-Object System.Drawing.PointF $x, $y), [System.Drawing.StringFormat]::GenericTypographic)
    $pen = New-Object System.Drawing.Pen (Color $outline), $width
    $pen.LineJoin = 'Round'
    $g.DrawPath($pen, $path)
    $g.FillPath((Brush $fill), $path)
}
OutlinedText 'AK Trade Expansion' 70 'Bold' 562 124 '#FFFFFF' '#2B1D12' 12
OutlinedText '相場が動く市場と、コインで回る経済' 40 'Bold' 566 222 '#FFE48A' '#2B1D12' 9

# --- price chart panel (like the trade screen's graph) ---
$px0 = 566; $py0 = 300; $pw = 360; $ph = 170
$g.FillRectangle((Brush '#1E1E1E' 210), $px0, $py0, $pw, $ph)
$g.DrawRectangle((New-Object System.Drawing.Pen (Color '#F4EFE6'), 3), $px0, $py0, $pw, $ph)
$mid = $py0 + $ph / 2
$g.DrawLine((New-Object System.Drawing.Pen (Color '#888888'), 2), $px0 + 10, $mid, $px0 + $pw - 10, $mid)
$values = @(0, 18, 34, 22, 48, 60, 41, 20, -6, -30, -18, 4, 26, 52)
$pts = @()
for ($i = 0; $i -lt $values.Count; $i++) {
    $pts += New-Object System.Drawing.PointF ($px0 + 20 + $i * (($pw - 40) / ($values.Count - 1))), ($mid - $values[$i])
}
for ($i = 1; $i -lt $pts.Count; $i++) {
    $up = $values[$i] -ge $values[$i - 1]
    $pen = New-Object System.Drawing.Pen (Color $(if ($up) { '#FF5555' } else { '#55FF55' })), 5
    $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
    $g.DrawLine($pen, $pts[$i - 1], $pts[$i])
}
$small = New-Object System.Drawing.Font $family, 18, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString('▲ 高い', $small, (Brush '#FF7777'), $px0 + 12, $py0 + 8)
$g.DrawString('▼ 安い', $small, (Brush '#77FF77'), $px0 + 12, $py0 + $ph - 30)

# --- gold coins next to the chart ---
foreach ($c in @(@(960, 330, 70), @(1040, 380, 56), @(985, 420, 62), @(1100, 320, 48))) {
    $x = $c[0]; $y = $c[1]; $d = $c[2]
    $g.FillEllipse((Brush '#9A6510'), $x + 4, $y + 6, $d, $d)
    $g.FillEllipse((Brush '#F6C443'), $x, $y, $d, $d)
    $g.DrawEllipse((New-Object System.Drawing.Pen (Color '#C98D1C'), 4), $x + 8, $y + 8, $d - 16, $d - 16)
    $g.FillEllipse((Brush '#FFE48A'), $x + $d * 0.22, $y + $d * 0.18, $d * 0.22, $d * 0.16)
}

# --- feature chips ---
$chips = @('地域ごとの品揃え', 'コイン・財布・銀行', '評判・依頼・掲示板', '村人の取引もコインに')
$chipFont = New-Object System.Drawing.Font $family, 24, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$cx = 566; $cy = 494
foreach ($t in $chips) {
    $size = $g.MeasureString($t, $chipFont)
    $cw = [int]$size.Width + 24
    if ($cx + $cw -gt $W - 40) { $cx = 566; $cy += 48 }
    $g.FillRectangle((Brush '#2B1D12' 220), $cx, $cy, $cw, 40)
    $g.DrawRectangle((New-Object System.Drawing.Pen (Color '#F6C443'), 2), $cx, $cy, $cw, 40)
    $g.DrawString($t, $chipFont, (Brush '#FFFFFF'), $cx + 12, $cy + 7)
    $cx += $cw + 12
}

# --- footer: version / environment ---
$foot = New-Object System.Drawing.Font $family, 26, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$text = "Minecraft Java Edition 26.3  /  Fabric      v$Version"
$fw = $g.MeasureString($text, $foot).Width
$g.FillRectangle((Brush '#000000' 140), 0, $H - 56, $W, 56)
$g.DrawString($text, $foot, (Brush '#FFFFFF'), $W - $fw - 30, $H - 44)

$outPath = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $root $Out }
$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $icon.Dispose()
Write-Host "thumbnail -> $outPath (v$Version)"
