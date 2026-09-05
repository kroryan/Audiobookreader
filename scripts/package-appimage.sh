#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/desktop/build/compose/binaries/main/app/BookReader"
VERSION="$(sed -n 's/.*packageVersion = "\([^"]*\)".*/\1/p' "$ROOT_DIR/desktop/build.gradle.kts")"
test -n "$VERSION" || { echo "Missing desktop packageVersion" >&2; exit 1; }
OUTPUT_FILE="${APPIMAGE_OUTPUT_FILE:-$ROOT_DIR/desktop/build/compose/binaries/main/appimage/BookReader-$VERSION-x86_64.AppImage}"
APPIMAGETOOL_BIN="${APPIMAGETOOL:-appimagetool}"
RUNTIME_ARGS=()
if [[ -n "${APPIMAGETOOL_RUNTIME_FILE:-}" ]]; then
  RUNTIME_ARGS=(--runtime-file "$APPIMAGETOOL_RUNTIME_FILE")
fi

test -x "$APP_DIR/bin/BookReader" || {
  echo "Missing desktop AppDir. Run :desktop:createDistributable first." >&2
  exit 1
}
command -v "$APPIMAGETOOL_BIN" >/dev/null 2>&1 || {
  echo "appimagetool is required. Set APPIMAGETOOL to its path." >&2
  exit 1
}

STAGING_DIR="$(mktemp -d /tmp/bookreader-appimage.XXXXXX)"
trap 'rm -rf -- "$STAGING_DIR"' EXIT
mkdir -p "$(dirname "$OUTPUT_FILE")"
cp -a "$APP_DIR" "$STAGING_DIR/BookReader"
cp "$ROOT_DIR/desktop/appimage/AppRun" "$STAGING_DIR/AppRun"
cp "$ROOT_DIR/desktop/appimage/bookreader.desktop" "$STAGING_DIR/bookreader.desktop"
cp "$ROOT_DIR/assets/bookreader-icon.png" "$STAGING_DIR/bookreader.png"
chmod +x "$STAGING_DIR/AppRun"

"$APPIMAGETOOL_BIN" "${RUNTIME_ARGS[@]}" "$STAGING_DIR" "$OUTPUT_FILE"
echo "Created $OUTPUT_FILE"
