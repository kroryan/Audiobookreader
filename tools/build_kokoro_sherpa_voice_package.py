#!/usr/bin/env python3
"""Build a Sherpa-ONNX Kokoro v1.0 package with em_santa.

The official Sherpa v1.0 package contains 53 style embeddings.  Kokoro's
em_santa embedding is published separately by the ONNX community repository.
This tool appends that embedding as speaker id 53 and updates the Sherpa
model metadata so the native runtime accepts the new voices.bin.

It intentionally does not modify or download anything into the Android app.
The generated package must be hosted at a URL configured in ModelCatalog
before it can be offered as an app download.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
import sys
import tempfile
import urllib.request
from pathlib import Path


VOICE_SHAPE = (510, 1, 256)
FLOAT32_BYTES = 4
VOICE_BYTES = VOICE_SHAPE[0] * VOICE_SHAPE[1] * VOICE_SHAPE[2] * FLOAT32_BYTES
EXPECTED_OFFICIAL_VOICES = 53
SANTA_SPEAKER_ID = 53

OFFICIAL_VOICES_URL = (
    "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/resolve/main/voices.bin"
)
OFFICIAL_MODEL_URL = (
    "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/resolve/main/model.onnx"
)
SANTA_URL = (
    "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/"
    "voices/em_santa.bin"
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url: str, destination: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "audiobookreader Kokoro builder"})
    with urllib.request.urlopen(request) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output, length=1024 * 1024)


def require_file(path: Path, label: str) -> Path:
    if not path.is_file():
        raise SystemExit(f"{label} not found: {path}")
    return path


def patch_model_metadata(source: Path, destination: Path) -> None:
    try:
        import onnx
    except ImportError as error:
        raise SystemExit(
            "The Python package 'onnx' is required to patch model.onnx. "
            "Install it with: python3 -m pip install onnx"
        ) from error

    model = onnx.load_model(str(source), load_external_data=False)
    metadata = {entry.key: entry.value for entry in model.metadata_props}
    old_value = metadata.get("n_speakers")
    if old_value is None:
        raise SystemExit("model.onnx has no n_speakers metadata")
    try:
        old_count = int(old_value)
    except ValueError as error:
        raise SystemExit(f"Invalid n_speakers metadata: {old_value!r}") from error
    if old_count != EXPECTED_OFFICIAL_VOICES:
        raise SystemExit(
            f"Expected the official 53-speaker model, but found n_speakers={old_count}"
        )

    for entry in model.metadata_props:
        if entry.key == "n_speakers":
            entry.value = str(EXPECTED_OFFICIAL_VOICES + 1)
        elif entry.key == "id2speaker":
            entry.value = f"{entry.value},53->em_santa"
        elif entry.key == "speaker2id":
            entry.value = f"{entry.value},em_santa->53"
        elif entry.key == "speaker_names":
            entry.value = f"{entry.value},em_santa"
    onnx.save_model(model, str(destination), save_as_external_data=False)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="Output package directory")
    parser.add_argument("--voices", type=Path, help="Existing official 53-voice voices.bin")
    parser.add_argument("--model", type=Path, help="Existing official 53-speaker model.onnx")
    parser.add_argument("--santa", type=Path, help="Existing em_santa.bin embedding")
    parser.add_argument(
        "--download",
        action="store_true",
        help="Download missing inputs from the official public repositories",
    )
    args = parser.parse_args()

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="kokoro-54-") as temp_name:
        temp = Path(temp_name)

        def input_file(argument: Path | None, name: str, url: str) -> Path:
            if argument is not None:
                return require_file(argument.resolve(), name)
            if not args.download:
                raise SystemExit(f"Pass --{name} or use --download")
            path = temp / name
            print(f"Downloading {name}...", flush=True)
            download(url, path)
            return path

        voices = input_file(args.voices, "voices", OFFICIAL_VOICES_URL)
        model = input_file(args.model, "model", OFFICIAL_MODEL_URL)
        santa = input_file(args.santa, "santa", SANTA_URL)

        voices_size = voices.stat().st_size
        santa_size = santa.stat().st_size
        expected_voices_size = EXPECTED_OFFICIAL_VOICES * VOICE_BYTES
        if voices_size != expected_voices_size:
            raise SystemExit(
                f"Unexpected voices.bin size: {voices_size}; "
                f"expected {expected_voices_size}"
            )
        if santa_size != VOICE_BYTES:
            raise SystemExit(
                f"Unexpected em_santa.bin size: {santa_size}; expected {VOICE_BYTES}"
            )

        output_voices = output / "voices.bin"
        with output_voices.open("wb") as destination:
            with voices.open("rb") as source:
                shutil.copyfileobj(source, destination, length=1024 * 1024)
            with santa.open("rb") as source:
                shutil.copyfileobj(source, destination, length=1024 * 1024)

        output_model = output / "model.onnx"
        patch_model_metadata(model, output_model)

        manifest = {
            "format": "sherpa-onnx-kokoro-v1.0",
            "speaker_count": EXPECTED_OFFICIAL_VOICES + 1,
            "speaker_ids": {"em_santa": SANTA_SPEAKER_ID},
            "voice_shape": list(VOICE_SHAPE),
            "voice_embedding_dtype": "float32",
            "voices_sha256": sha256(output_voices),
            "model_sha256": sha256(output_model),
            "sources": {
                "official_sherpa_package": "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0",
                "em_santa_embedding": "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX",
            },
            "notes": [
                "Existing Sherpa v1.0 speaker IDs 0..52 are unchanged.",
                "em_santa is appended as speaker ID 53.",
                "Keep the remaining files from the official Sherpa package unchanged.",
            ],
        }
        (output / "manifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )

    print(f"Created {output}")
    print(f"voices.bin: {output_voices.stat().st_size} bytes")
    print(f"model.onnx: {output_model.stat().st_size} bytes")
    print(f"em_santa speaker id: {SANTA_SPEAKER_ID}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
