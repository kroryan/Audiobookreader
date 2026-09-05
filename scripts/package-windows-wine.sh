#!/usr/bin/env bash
# Build Windows x64 installers from staged JVM bytecode using a Windows JDK.
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "$0")/.." && pwd)"
: "${BOOKREADER_WINDOWS_JDK:?Set BOOKREADER_WINDOWS_JDK to an extracted Windows JDK 21}"
: "${BOOKREADER_WIX_DIR:?Set BOOKREADER_WIX_DIR to WiX 3 binaries}"
: "${WINEPREFIX:?Set WINEPREFIX to a dedicated Wine prefix with Wine Mono installed}"
wine_bin="${BOOKREADER_WINE_BIN:-/usr/lib/wine/wine64}"
export WINELOADER="$wine_bin"
export WINEDEBUG="${WINEDEBUG:--all}"
win_path() {
    local file_path="$1"
    if [[ "$file_path" == "$WINEPREFIX/drive_c/"* ]]; then
        file_path="${file_path#"$WINEPREFIX/drive_c"}"
        printf 'C:%s' "${file_path//\//\\}"
    else
        printf 'Z:%s' "${file_path//\//\\}"
    fi
}
export WINEPATH="$(win_path "$BOOKREADER_WIX_DIR");$(win_path "$BOOKREADER_WINDOWS_JDK/bin")"
output_dir="$repo_dir/desktop/build/compose/binaries/main"
stage_dir="$repo_dir/desktop/build/windows-input"
work_dir="$(mktemp -d "$WINEPREFIX/drive_c/bookreader-win-package.XXXXXX")"
mkdir -p "$work_dir/wix-launchers"
# This shim is for the verified WiX 3.11.2 archive, whose version Mono omits.
strings -el "$BOOKREADER_WIX_DIR/candle.exe" | rg -Fx '3.11.2.4516' >/dev/null
x86_64-w64-mingw32-gcc -O2 -municode -static \
    '-DWIX_BUILD_VERSION="3.11.2.4516"' "$repo_dir/scripts/wine-wix-launcher.c" \
    -o "$work_dir/wix-launchers/candle.exe"
cp "$work_dir/wix-launchers/candle.exe" "$work_dir/wix-launchers/light.exe"
export BOOKREADER_WIX_REAL_DIR="$(win_path "$BOOKREADER_WIX_DIR")"
export WINEPATH="$(win_path "$work_dir/wix-launchers");$WINEPATH"
version="$(sed -n 's/.*packageVersion = "\([^"]*\)".*/\1/p' "$repo_dir/desktop/build.gradle.kts")"
test -f "$stage_dir/BookReader-launcher.jar"
printf 'Packaging Windows %s in %s\n' "$version" "$work_dir"
mkdir -p "$work_dir/app"
"$wine_bin" "$BOOKREADER_WINDOWS_JDK/bin/jlink.exe" \
    --add-modules java.desktop,java.logging,java.prefs,jdk.crypto.ec,jdk.unsupported \
    --strip-debug --no-header-files --no-man-pages \
    --output "$(win_path "$work_dir/runtime")"
"$wine_bin" "$BOOKREADER_WINDOWS_JDK/bin/jpackage.exe" \
    --type app-image --name BookReader --app-version "$version" --vendor BookReader \
    --input "$(win_path "$stage_dir")" --main-jar BookReader-launcher.jar \
    --main-class com.audiobookreader.desktop.MainKt \
    --icon "$(win_path "$repo_dir/assets/bookreader-icon.ico")" \
    --runtime-image "$(win_path "$work_dir/runtime")" \
    --dest "$(win_path "$work_dir/app")" --verbose
for format in msi exe; do
    mkdir -p "$output_dir/$format" "$work_dir/output/$format"
    "$wine_bin" "$BOOKREADER_WINDOWS_JDK/bin/jpackage.exe" \
        --type "$format" --name BookReader --app-version "$version" --vendor BookReader \
        --description 'Read books aloud with downloadable voices' \
        --icon "$(win_path "$repo_dir/assets/bookreader-icon.ico")" \
        --app-image "$(win_path "$work_dir/app/BookReader")" \
        --dest "$(win_path "$work_dir/output/$format")" \
        --win-per-user-install --win-menu --win-menu-group BookReader --win-shortcut \
        --win-dir-chooser --win-upgrade-uuid 628af63c-4199-4878-acb5-72581a0d727a \
        --temp "$(win_path "$work_dir/$format")" --verbose
    cp "$work_dir/output/$format/BookReader-$version.$format" "$output_dir/$format/"
done
printf 'Windows installers: %s/msi and %s/exe\n' "$output_dir" "$output_dir"
