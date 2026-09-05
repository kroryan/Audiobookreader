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

Model links and license links:

- Piper: https://huggingface.co/rhasspy/piper-voices
- Coqui metadata: https://github.com/coqui-ai/TTS/blob/dev/TTS/.models.json
- Mimic3 Voices: https://github.com/MycroftAI/mimic3-voices/blob/master/LICENSE
- Kokoro: https://huggingface.co/hexgrad/Kokoro-82M/blob/main/LICENSE
- Supertonic: https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE
- CC BY-NC-SA 4.0: https://creativecommons.org/licenses/by-nc-sa/4.0/

## Edge TTS

Edge voices are an online provider. BookReader does not download or embed
Microsoft voice model files. Availability and use remain subject to the
provider's service terms, endpoint availability, network access, and rate
limits.

## Other runtime dependencies

The Android build also uses AndroidX, Media3, OkHttp, jsoup, PDFBox-Android,
Commons Compress, and Sherpa-ONNX. Their license and notice files are supplied
by their respective distributions and are not relicensed by BookReader.
