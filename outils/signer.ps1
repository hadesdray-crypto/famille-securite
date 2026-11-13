# Fabrique les deux APK signees avec la cle permanente (hors depot) et calcule leur empreinte.
$cles = "D:\projo-fabrication\cles-famille\mots-de-passe.txt"
if (-not (Test-Path $cles)) { throw "Cle de signature introuvable : $cles" }
$c = @{}
Get-Content $cles | ForEach-Object { $k, $v = $_ -split '=', 2; $c[$k] = $v }
$env:FAMILLE_KEYSTORE = $c['keystore']
$env:FAMILLE_KEYALIAS = $c['alias']
$env:FAMILLE_STOREPASS = $c['storepass']
$env:FAMILLE_KEYPASS = $c['keypass']
$projet = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\gradle.ps1" assembleEnfantRelease assembleParentRelease
if ($LASTEXITCODE -ne 0) { throw "La fabrication a echoue." }
$sortie = Join-Path $projet "livraison"
New-Item -ItemType Directory -Force -Path $sortie | Out-Null
foreach ($r in @("enfant", "parent")) {
    $src = Join-Path $projet "app\build\outputs\apk\$r\release\app-$r-release.apk"
    $dst = Join-Path $sortie "Famille-$([char]::ToUpper($r[0]))$($r.Substring(1)).apk"
    Copy-Item $src $dst -Force
    $h = (Get-FileHash $dst -Algorithm SHA256).Hash.ToLower()
    "$dst  $([math]::Round((Get-Item $dst).Length/1KB)) Ko  sha256=$h"
}
