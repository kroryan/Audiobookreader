# BookReader third-party notices

BookReader uses the following third-party projects and model packages. Their
licenses apply independently from BookReader's own license.

## Sherpa-ONNX

The Android inference engine and Kotlin adapter are provided by Sherpa-ONNX.
Copyright and license notices are available in the upstream distribution:

- https://github.com/k2-fsa/sherpa-onnx
- Apache License 2.0: https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE

BookReader does not bundle the downloadable TTS model weights in the APK.
Models are fetched on demand from the upstream Sherpa-ONNX model release and
their package notices are retained when extracted.

## Downloadable model families

- Piper voices: each voice has its own `MODEL_CARD`; the catalog links to the
  upstream voice repository. Miro/Dii OpenVoiceOS voices are CC BY-NC-SA 4.0
  and require explicit acceptance before download.
- Coqui VITS: the CSS10 and Common Voice entries follow the licenses declared
  by Coqui's model metadata, including BSD-3-Clause for the CSS10 VITS entry.
- Mimic3 Voices: CC BY-SA 4.0. Attribution and ShareAlike obligations apply.
- Kokoro-82M: Apache 2.0 weights; see the upstream model card and license.
- Supertonic: model weights are distributed under OpenRAIL-M; the model's
  use-based restrictions must be respected. The app requests acceptance
  before download.
- PocketTTS: BookReader uses the multilingual PocketTTS.cpp ONNX runtime
  together with separately downloaded language packs. The runtime is MIT
  licensed; Kyutai model weights and the community ONNX export have separate
  attribution and use conditions. The app requires acceptance and a
  user-provided reference WAV; cloned voices must be used only with consent.
- ZipVoice: the Sherpa-ONNX distill model is a Chinese/English zero-shot
  voice-cloning model. It requires both a reference WAV and its exact text,
  plus the Vocos vocoder. The app requires acceptance before downloading.

Model links and license links:

- Piper: https://huggingface.co/rhasspy/piper-voices
- Coqui metadata: https://github.com/coqui-ai/TTS/blob/dev/TTS/.models.json
- Mimic3 Voices: https://github.com/MycroftAI/mimic3-voices/blob/master/LICENSE
- Kokoro: https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE
- Supertonic: https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE
- PocketTTS: https://github.com/kyutai-labs/pocket-tts/blob/main/LICENSE
- ZipVoice: https://github.com/k2-fsa/ZipVoice/blob/main/LICENSE
- CC BY-NC-SA 4.0: https://creativecommons.org/licenses/by-nc-sa/4.0/

## Edge TTS

Edge voices are an online provider. BookReader does not download or embed
Microsoft voice model files. Availability and use remain subject to the
provider's service terms, endpoint availability, network access, and rate
limits.

Voice cloning

PocketTTS and ZipVoice are local zero-shot voice-cloning models. BookReader
does not ship a person's voice recording as a default for cloning. Users are
responsible for having permission to use any reference audio and for complying
with applicable disclosure and synthetic-media laws. ZipVoice currently uses
the upstream bilingual Chinese/English checkpoint. PocketTTS language packs
are downloaded on demand from the documented multilingual ONNX export and
are not bundled in the APK.

PocketTTS native runtime and model export:

- https://github.com/VolgaGerm/PocketTTS.cpp (MIT)
- https://huggingface.co/KevinAHM/pocket-tts-onnx (model/export attribution)
- https://github.com/kyutai-labs/pocket-tts (Kyutai Pocket TTS)

## Other runtime dependencies

The Android build also uses AndroidX, Media3, OkHttp, jsoup, PDFBox-Android,
Commons Compress, and Sherpa-ONNX. Their license and notice files are supplied
by their respective distributions and are not relicensed by BookReader.
