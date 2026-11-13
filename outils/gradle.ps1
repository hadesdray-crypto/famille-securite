param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Taches)
# Lance Gradle avec les outils locaux du PC (meme installation que le generateur d'applis).
$racine = "D:\projo-fabrication"
$projet = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = "$racine\jdk"
$env:ANDROID_HOME = "$racine\android"
$env:ANDROID_SDK_ROOT = "$racine\android"
$env:GRADLE_USER_HOME = "$racine\gradle-maison"
$env:Path = "$racine\jdk\bin;$racine\gradle\bin;$env:Path"
Set-Content -Path "$projet\local.properties" -Value ("sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/'))
Set-Location $projet
& "$racine\gradle\bin\gradle.bat" @Taches --no-daemon --console=plain "-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8" "--max-workers=2" 2>&1
exit $LASTEXITCODE
