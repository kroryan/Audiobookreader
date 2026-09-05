#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/desktop/build/compose/binaries/main/app/BookReader"
STAGING_DIR="${TMPDIR:-/tmp}/bookreader-appimage"
OUTPUT_FILE="$ROOT_DIR/desktop/build/compose/binaries/main/appimage/BookReader-0.1.6-x86_64.AppImage"
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

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR" "$(dirname "$OUTPUT_FILE")"
cp -a "$APP_DIR" "$STAGING_DIR/BookReader"
cp "$ROOT_DIR/desktop/appimage/AppRun" "$STAGING_DIR/AppRun"
cp "$ROOT_DIR/desktop/appimage/bookreader.desktop" "$STAGING_DIR/bookreader.desktop"
cp "$ROOT_DIR/assets/bookreader-icon.png" "$STAGING_DIR/bookreader.png"
chmod +x "$STAGING_DIR/AppRun"

"$APPIMAGETOOL_BIN" "${RUNTIME_ARGS[@]}" "$STAGING_DIR" "$OUTPUT_FILE"
echo "Created $OUTPUT_FILE"
