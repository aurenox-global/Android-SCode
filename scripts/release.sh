#!/usr/bin/env bash
# release.sh — compila, VERIFICA y publica una release de Android SCode.
#
# Uso:  ./scripts/release.sh 1.0.17
#
# Qué hace (y por qué): sube versionCode/versionName, compila, y ANTES de
# publicar comprueba que el APK generado es de verdad el de esa versión y que
# está firmado. Si algo no cuadra, aborta sin tocar git ni GitHub. Así se
# acabaron las releases con el APK equivocado.
set -euo pipefail

REPO="aurenox-global/Android-SCode"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "uso: $0 <version sin la v, ej: 1.0.17>"
  exit 1
fi
TAG="v$VERSION"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -d "$SDK" ]; then SDK="$HOME/android-sdk"; fi
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || { echo "✗ no encuentro build-tools en $SDK"; exit 1; }

GRADLE_FILE="app/build.gradle"
APK="app/build/outputs/apk/release/app-universal-release.apk"

echo "== 1/5 versión =="
CODE=$(( $(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+' | head -1) + 1 ))
sed -i '' "s/versionCode = [0-9]*/versionCode = $CODE/" "$GRADLE_FILE"
sed -i '' "s/versionName = \"[^\"]*\"/versionName = \"$TAG\"/" "$GRADLE_FILE"
echo "   versionCode $CODE / versionName $TAG"

echo "== 2/5 compilar (tarda varios minutos) =="
./gradlew --no-daemon :app:assembleRelease --console=plain | tail -3
[ -f "$APK" ] || { echo "✗ no se generó el APK"; exit 1; }

echo "== 3/5 verificar el APK ANTES de publicar =="
INFO="$("$BT/aapt" dump badging "$APK")"
if ! echo "$INFO" | grep -q "versionName='$TAG'"; then
  echo "✗ el APK NO es $TAG (build obsoleto o falló la compilación). No se publica nada."
  echo "$INFO" | grep -m1 '^package:'
  exit 1
fi
if ! "$BT/apksigner" verify "$APK" >/dev/null 2>&1; then
  echo "✗ el APK no tiene una firma válida. No se publica nada."
  exit 1
fi
echo "   ✓ $TAG firmado y verificado ($(stat -f%z "$APK") bytes)"

echo "== 4/5 commit + push =="
git add -A
git -c user.name=zota -c user.email=zota@local commit -qm "release: $TAG" || true
git push -q origin main && echo "   ✓ push ok"

echo "== 5/5 release =="
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK" --repo "$REPO" --clobber
else
  gh release create "$TAG" "$APK" --repo "$REPO" \
    --title "Android SCode $TAG — APK universal" \
    --notes "Release $TAG. Verificación automática: versión y firma del APK comprobadas antes de publicar. Misma firma testkey: se instala encima."
fi
echo "✓ publicado: https://github.com/$REPO/releases/tag/$TAG"
