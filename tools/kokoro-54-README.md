# Kokoro Sherpa package with `em_santa`

This directory contains a Sherpa-ONNX Kokoro v1.0 package extended with the
Spanish voice `em_santa`.

## Files

- `model.onnx` — the official Kokoro v1.0 model with `n_speakers` changed from
  53 to 54 and metadata for speaker ID 53.
- `voices.bin` — the official 53 voice embeddings plus `em_santa` appended as
  speaker ID `53`.
- `kokoro-multi-lang-v1_0-em-santa.tar.bz2` — complete ready-to-extract package.
- `manifest.json` — source information and SHA-256 checksums.

The other files required by Kokoro (`tokens.txt`, lexicons, rule FSTs and
`espeak-ng-data`) must remain alongside these files from the original Sherpa
package. Do not use only `voices.bin`: Sherpa validates the voice count in
`model.onnx`, so both patched files are required.

## Sherpa-ONNX configuration

Configure the Kokoro model with the patched files and select speaker ID 53:

```text
model   = /path/to/model.onnx
voices  = /path/to/voices.bin
tokens  = /path/to/tokens.txt
lexicon = /path/to/lexicon-us-en.txt,/path/to/lexicon-zh.txt
data_dir = /path/to/espeak-ng-data
lang    = es
sid     = 53
```

In the Sherpa-ONNX native API this corresponds to an
`OfflineTtsKokoroModelConfig` whose `model`, `voices`, `tokens`, `lexicon` and
`dataDir` point to the files above. Set `OfflineTtsConfig.max_num_sentences`
to the value appropriate for the host application and pass `sid=53` in the
generation configuration. The voice is selected per generation; it is not
installed as a system TTS voice.

## Important

Keep the original Sherpa-ONNX and Kokoro license/attribution files when
redistributing this package. This generated package does not add or replace
any upstream license. Sources are listed in `manifest.json`.

Generated with `tools/build_kokoro_sherpa_voice_package.py` in the BookReader
repository.

## Detailed integration instructions

The complete model directory must contain the official Kokoro support files
as well as the two replacement files in this folder. Replace the official
model.onnx and voices.bin; keep tokens.txt, all lexicons, all rule FST files,
and the complete espeak-ng-data directory.

For Spanish Santa, configure the Kokoro model with lang = "es" and generate
with sid = 53. Do not use the voice name as sid and do not use only voices.bin:
Sherpa validates the embedding count against the n_speakers metadata in
model.onnx.

Native configuration:

    config.model.kokoro.model = "/path/to/model.onnx";
    config.model.kokoro.voices = "/path/to/voices.bin";
    config.model.kokoro.tokens = "/path/to/tokens.txt";
    config.model.kokoro.lexicon =
        "/path/to/lexicon-us-en.txt,/path/to/lexicon-zh.txt";
    config.model.kokoro.data_dir = "/path/to/espeak-ng-data";
    config.model.kokoro.lang = "es";
    generation.sid = 53;
    generation.speed = 1.0f;

Android Kotlin uses the same paths and IDs:

    val kokoro = OfflineTtsKokoroModelConfig(
        model = File(modelDir, "model.onnx").absolutePath,
        voices = File(modelDir, "voices.bin").absolutePath,
        tokens = File(modelDir, "tokens.txt").absolutePath,
        lexicon = listOf(
            File(modelDir, "lexicon-us-en.txt").absolutePath,
            File(modelDir, "lexicon-zh.txt").absolutePath,
        ).joinToString(","),
        dataDir = File(modelDir, "espeak-ng-data").absolutePath,
        lang = "es",
    )
    val audio = tts.generateWithConfig(
        "Texto en español.",
        GenerationConfig(sid = 53, speed = 1.0f),
    )

To verify an installation, check that model.onnx reports n_speakers=54,
speaker ID 53 is em_santa, and voices.bin is 28,200,960 bytes. The final
voice block is 522,240 bytes and uses float32 values with shape 510 x 1 x 256.
Keep the original Sherpa-ONNX and Kokoro license and attribution files when
redistributing the package.

## Model card and licensing

This repository is a derived distribution of the Sherpa-ONNX Kokoro v1.0
package. The original package is distributed under the Apache License 2.0;
the complete license text is included as LICENSE in the original package.
The em_santa embedding is sourced from the public ONNX Community Kokoro
v1.0 repository and must retain the applicable upstream terms.

Upstream references:

- Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx
- Official Sherpa Kokoro package:
  https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0
- Kokoro voice catalogue and license:
  https://huggingface.co/hexgrad/Kokoro-82M
- em_santa source embedding:
  https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX

This repository does not claim ownership of the Kokoro model or its voice
embeddings. Redistributors must preserve this notice, the included LICENSE
file, and all applicable notices from Sherpa-ONNX, Kokoro, espeak-ng and
other bundled dependencies.
