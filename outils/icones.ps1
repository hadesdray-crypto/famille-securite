param([string]$Racine = (Split-Path $PSScriptRoot -Parent))
# Genere les icones de lancement (PNG pour Android 7-8, vecteurs adaptatifs pour Android 8+).
Add-Type -AssemblyName System.Drawing
$tailles = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
$roles = @{
    "enfant" = @{ couleur = [System.Drawing.Color]::FromArgb(34, 211, 238); hex = "#22D3EE"; deux = $false }
    "parent" = @{ couleur = [System.Drawing.Color]::FromArgb(46, 158, 91);  hex = "#2E9E5B"; deux = $true }
}
$fond = [System.Drawing.Color]::FromArgb(14, 26, 47)

function Dessiner($taille, $couleur, $deux) {
    $bmp = New-Object System.Drawing.Bitmap($taille, $taille)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.Clear([System.Drawing.Color]::Transparent)
    $r = $taille * 0.22
    $chemin = New-Object System.Drawing.Drawing2D.GraphicsPath
    $chemin.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90)
    $chemin.AddArc($taille - 2 * $r, 0, 2 * $r, 2 * $r, 270, 90)
    $chemin.AddArc($taille - 2 * $r, $taille - 2 * $r, 2 * $r, 2 * $r, 0, 90)
    $chemin.AddArc(0, $taille - 2 * $r, 2 * $r, 2 * $r, 90, 90)
    $chemin.CloseFigure()
    $g.FillPath((New-Object System.Drawing.SolidBrush($fond)), $chemin)
    $s = $taille / 108.0
    $pts = @(
        (New-Object System.Drawing.PointF((54 * $s), (22 * $s))),
        (New-Object System.Drawing.PointF((80 * $s), (31 * $s))),
        (New-Object System.Drawing.PointF((80 * $s), (54 * $s))),
        (New-Object System.Drawing.PointF((68 * $s), (74 * $s))),
        (New-Object System.Drawing.PointF((54 * $s), (88 * $s))),
        (New-Object System.Drawing.PointF((40 * $s), (74 * $s))),
        (New-Object System.Drawing.PointF((28 * $s), (54 * $s))),
        (New-Object System.Drawing.PointF((28 * $s), (31 * $s)))
    )
    $g.FillPolygon((New-Object System.Drawing.SolidBrush($couleur)), $pts)
    $b = New-Object System.Drawing.SolidBrush($fond)
    if ($deux) {
        $g.FillEllipse($b, (40 * $s), (36 * $s), (14 * $s), (14 * $s)); $g.FillEllipse($b, (58 * $s), (46 * $s), (10 * $s), (10 * $s))
        $g.FillPie($b, (36 * $s), (54 * $s), (22 * $s), (26 * $s), 180, 180); $g.FillPie($b, (55 * $s), (58 * $s), (16 * $s), (20 * $s), 180, 180)
    } else {
        $g.FillEllipse($b, (46 * $s), (36 * $s), (16 * $s), (16 * $s))
        $g.FillPie($b, (39 * $s), (54 * $s), (30 * $s), (28 * $s), 180, 180)
    }
    $g.Dispose()
    return $bmp
}

foreach ($role in $roles.Keys) {
    $cfg = $roles[$role]
    foreach ($d in $tailles.Keys) {
        $dossier = Join-Path $Racine "app\src\$role\res\mipmap-$d"
        New-Item -ItemType Directory -Force -Path $dossier | Out-Null
        $bmp = Dessiner $tailles[$d] $cfg.couleur $cfg.deux
        $bmp.Save((Join-Path $dossier "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    }
    $dv = Join-Path $Racine "app\src\$role\res\drawable"
    $da = Join-Path $Racine "app\src\$role\res\mipmap-anydpi-v26"
    New-Item -ItemType Directory -Force -Path $dv, $da | Out-Null
    if ($cfg.deux) {
        $symbole = 'M46,43 m-7,0 a7,7 0 1,0 14,0 a7,7 0 1,0 -14,0 M36,68 C36,56 56,56 56,68 Z M63,51 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0 M56,70 C56,62 70,62 70,70 Z'
    } else {
        $symbole = 'M54,44 m-8,0 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 M39,70 C39,57 69,57 69,70 Z'
    }
    $vec = @"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="$($cfg.hex)"
        android:pathData="M54,24 L78,32 L78,54 C78,69 68,79 54,86 C40,79 30,69 30,54 L30,32 Z" />
    <path
        android:fillColor="#0E1A2F"
        android:pathData="$symbole" />
</vector>
"@
    Set-Content -Path (Join-Path $dv "ic_launcher_fg.xml") -Value $vec -Encoding UTF8
    $ad = @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/fond" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
"@
    Set-Content -Path (Join-Path $da "ic_launcher.xml") -Value $ad -Encoding UTF8
}
"Icones generees."
