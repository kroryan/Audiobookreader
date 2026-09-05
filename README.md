# BookReader

Android PDF/EPUB/text reader with local text-to-speech and background playback.

## Current features

- Import documents through Android's document picker.
- Extract text from PDF, EPUB, text, and HTML files.
- Downloadable multilingual Sherpa-ONNX model catalog with language filtering.
- Models are downloaded and used inside the app; they are not registered as system TTS engines.
- After a download completes, the model is selected automatically and its actual contents are validated, including packages containing nested directories.
- `OfflineTts` adapter for Piper/VITS, Coqui VITS, Mimic3 VITS, Kokoro, and Supertonic.
- Edge TTS voices loaded from the live public voice catalogue and marked `ONLINE`; no Edge model files are bundled or downloaded.
- License-aware downloads: models with additional conditions show their license, attribution, obligations, and accept/reject action before downloading.
- Chunk-based PCM-to-WAV generation with a Media3 playlist.
- Resumable progressive generation: playback starts after the first chunks are ready, and temporary look-ahead audio is removed when an unfinished book is stopped.
- `MediaSessionService` for notifications, system controls, lock-screen playback, and playback outside the app.
- Persistent media controls in the notification shade and on the lock screen, including pause/resume, 15-second rewind, 30-second forward, and stop.
- Reading percentage, exact chunk position, bookmarks, position reset, and local progress persistence.
- Visual highlighting of the complete chunk currently being played. Manual scrolling is preserved; any chunk can be tapped to select it as the starting point, even when earlier audio has not been prepared.
- Previous/next chunk navigation and a seek bar for prepared audio.
- Per-book settings: downloaded model, reading speed, and `speaker/voice ID`. Changing the speed or voice clears that model's generated audio so it can be regenerated without mixing settings.
- Prepared-audio percentage and size per book, per-book or global cache cleanup, and a 512 MB limit to prevent excessive storage use.
- Light/dark theme automatically follows the system theme, with reading-specific contrast.
- Local ONNX model import from Settings. The user provides an ISO 639-1/639-2/639-3 language code (`es` or `spa`, for example), and `tokens.txt` is required; `.onnx.json`, lexicons, and other auxiliary files can also be selected. Files are stored inside BookReader's private sandbox.
- Original app icon in `assets/bookreader-icon.png`, also used by the APK.
- Third-party credits and license references are available in `THIRD_PARTY_NOTICES.md` and in the installed app assets.

The project is intended for Android Studio. The verified debug build is generated at `app/build/outputs/apk/debug/app-debug.apk`. The APK contains the native engine, but no TTS models or WAV files; both are managed at runtime.

## Desktop targets

The desktop module uses the shared Kotlin core and Compose Desktop. It opens PDF, EPUB, HTML, and text documents, preserves paragraph-aware segmentation, provides a bookshelf library with persistent per-book position/bookmarks/cache controls, generates and plays selected fragments with the desktop sherpa-onnx JVM engine, exposes Models and Settings, downloads local model packages beside the executable, and loads the full Edge voice catalogue online. Linux packaging uses `:desktop:packageDeb` plus `scripts/package-appimage.sh`; Windows packaging uses `:desktop:packageMsi` and `:desktop:packageExe` on a Windows build machine. Native installers must be built on their target operating system.

## Release signing

Release signing is read from `keystore.properties`, which is excluded from Git. SHA-256 fingerprints shown by Google are not private keys and cannot sign builds; use the upload keystore registered in Google Play Console. See `keystore.properties.example` and run `./gradlew assembleRelease`. No fingerprints, public keys, or secrets are stored in the repository.

## Supported model families

The list was cross-checked against `scripts/apk/generate-tts-apk-script.py` from the Sherpa-ONNX checkout:

- Piper/VITS: Sherpa-ONNX's catalog for dozens of languages, including Miro, Davefx, and Sharvard for `es_ES`.
- Coqui VITS and Mimic3 VITS: Sherpa-ONNX packages that the `OfflineTts` engine can run internally.
- Kokoro: v0.19 English and v1.0/v1.1 English+Chinese, including the INT8 variant.
- Supertonic 3 INT8: Spanish and other languages in a multilingual package.

Kokoro Spanish requires separate validation: the original Kokoro-82M voice catalog includes Spanish (`ef_dora`, `em_alex`, `em_santa`), but the official packages currently documented by Sherpa-ONNX as `kokoro-multi-lang-v1_0/v1_1` are published and configured for English and Chinese. Spanish voices on the device still need a compatible conversion and frontend/phonemizer validation; they should not be presented as finished support until correct audio is confirmed on an ARM phone.

## Technical references

- [Sherpa-ONNX Android TTS](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTtsEngine)
- [OfflineTts Kotlin API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Tts.kt)
- [APK generator catalog](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py)
- [Sherpa-ONNX pretrained TTS models](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html)
- [Kokoro-82M Spanish voices](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)
