#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=1.27.1
SHERPA_VERSION=1.13.7
SHERPA_AAR_SHA256=c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8
SOURCE_SHA256=e53b06ccd454f56088fde374d1af6660ef111ca7ce7a98d62b274ff9094d3005
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$ROOT/app/src/main/jniLibs" "$ROOT/app/src/main/third_party"
curl -fL "https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-${SHERPA_VERSION}.aar" -o "$TMP/sherpa.aar"
echo "$SHERPA_AAR_SHA256  $TMP/sherpa.aar" | sha256sum --check --status
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
    unzip -p "$TMP/sherpa.aar" "jni/$abi/libonnxruntime.so" > "$ROOT/app/src/main/jniLibs/$abi/libonnxruntime.so"
done
curl -fL "https://github.com/microsoft/onnxruntime/archive/refs/tags/v${VERSION}.tar.gz" -o "$TMP/onnxruntime.tar.gz"
echo "$SOURCE_SHA256  $TMP/onnxruntime.tar.gz" | sha256sum --check --status
tar -xzf "$TMP/onnxruntime.tar.gz" -C "$TMP"
rm -rf "$ROOT/app/src/main/third_party/onnxruntime"
mkdir -p "$ROOT/app/src/main/third_party/onnxruntime"
cp -R "$TMP/onnxruntime-${VERSION}/include" "$ROOT/app/src/main/third_party/onnxruntime/include"
echo "ONNX Runtime ${VERSION} matching Sherpa ${SHERPA_VERSION} is ready."
