#!/usr/bin/env bash
# Execute DANS un appareil virtuel : tests de navigation de chaque ecran, puis test aleatoire (monkey).
# Usage : tester-emulateur.sh <taille_police> <mode_sombre yes|no>
set -u
POLICE="${1:-1.0}"
SOMBRE="${2:-no}"
mkdir -p sortie-monkey
ECHEC=0

adb shell settings put system font_scale "$POLICE"
adb shell "cmd uimode night $SOMBRE" || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# Les fenetres "ne repond plus" du lanceur Android (emulateur lent) volent le focus aux tests : on les masque et on laisse l'appareil se calmer.
adb shell settings put global hide_error_dialogs 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
sleep 20
adb shell input keyevent KEYCODE_HOME || true

# 1. tests de navigation (chaque ecran : retour, aide, fleche Android, rien hors de l'ecran)
for ROLE in Enfant Parent; do
  echo "=== tests instrumentes : $ROLE (police $POLICE, sombre $SOMBRE) ==="
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_HOME || true
  if ! gradle "connected${ROLE}DebugAndroidTest" --no-daemon --console=plain; then
    echo "ECHEC des tests instrumentes : $ROLE"
    ECHEC=1
  fi
done

# 2. test aleatoire : 3000 gestes au hasard, la moindre erreur fatale ou ANR fait echouer
for APP in enfant parent; do
  PAQUET="fr.famille.$APP"
  APK=$(ls app/build/outputs/apk/$APP/debug/*.apk | head -n 1)
  adb install -r -g "$APK" >/dev/null
  adb logcat -c
  echo "=== monkey : $PAQUET ==="
  adb shell monkey -p "$PAQUET" --throttle 60 --ignore-security-exceptions --pct-syskeys 8 -v 3000 > "sortie-monkey/monkey-$APP.txt" 2>&1
  adb logcat -d > "sortie-monkey/logcat-$APP.txt"
  if grep -E "FATAL EXCEPTION|ANR in" "sortie-monkey/logcat-$APP.txt" | grep -q "$PAQUET\|FATAL EXCEPTION"; then
    if grep -B2 -A12 "FATAL EXCEPTION" "sortie-monkey/logcat-$APP.txt" | grep -q "$PAQUET"; then
      echo "ECHEC monkey : erreur fatale dans $PAQUET"
      grep -A14 "FATAL EXCEPTION" "sortie-monkey/logcat-$APP.txt" | head -n 40
      ECHEC=1
    fi
  fi
  if grep -q "ANR in $PAQUET" "sortie-monkey/logcat-$APP.txt"; then
    echo "ECHEC monkey : ANR dans $PAQUET"
    ECHEC=1
  fi
done

exit $ECHEC
