# BookReader

MVP de lector de PDF/EPUB/texto para Android con síntesis local y reproducción en segundo plano.

## Qué hay ahora

- Importación mediante el selector de documentos de Android.
- Extracción de texto de PDF, EPUB y archivos de texto/HTML.
- Catálogo descargable de modelos de Sherpa-ONNX.
- Adaptador `OfflineTts` para Piper/VITS, Coqui VITS, Mimic3 VITS, Kokoro y Supertonic.
- Generación de audio PCM a WAV por fragmentos y playlist de Media3.
- `MediaSessionService` para notificación, controles del sistema, pantalla bloqueada y salida de la aplicación.

El proyecto está pensado para Android Studio. En este entorno no hay SDK/Gradle instalados, por lo que queda pendiente ejecutar el primer `assembleDebug` en una máquina Android preparada y probar el rendimiento con modelos reales.

## Modelos contemplados

La lista se ha contrastado con `scripts/apk/generate-tts-apk-script.py` del checkout de Sherpa-ONNX:

- Piper/VITS: Miro, Davefx y Sharvard para `es_ES`.
- Coqui VITS: `vits-coqui-es-css10`.
- Mimic3 VITS: `vits-mimic3-es_ES-m-ailabs_low`.
- Kokoro: v0.19 inglés y v1.1 inglés+chino, incluyendo variante INT8.
- Supertonic 3 INT8: español y otros idiomas en un paquete multilingüe.

Kokoro merece una integración separada: el catálogo de voces original de Kokoro-82M sí incluye español (`ef_dora`, `em_alex`, `em_santa`), pero los paquetes oficiales que Sherpa-ONNX documenta actualmente como `kokoro-multi-lang-v1_0/v1_1` se publican y configuran para inglés+chino. Para usar esas voces españolas en el dispositivo habrá que validar una conversión compatible con el frontend/phonemizer de Sherpa; no se debe presentar como soporte terminado hasta generar audio correcto en un teléfono ARM.

## Siguiente trabajo

1. Ejecutar y corregir la compilación con el AAR `sherpa-onnx:1.13.7`.
2. Descargar un modelo español pequeño y probar generación, memoria y factor de tiempo real en varios teléfonos.
3. Añadir persistencia de libros, posición y modelo seleccionado.
4. Generar progresivamente la cola para que un libro largo no tenga que esperar a que termine completo.
5. Añadir resaltado palabra/frase sincronizado, controles de velocidad, temporizador y selección de voz.
6. Incorporar el catálogo completo upstream mediante un manifiesto versionado y mostrar licencia/tamaño de cada modelo.
7. Validar Kokoro español y decidir si se integra como paquete oficial, modelo convertido o backend independiente.

## Fuentes técnicas

- [Sherpa-ONNX Android TTS](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTtsEngine)
- [API Kotlin OfflineTts](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Tts.kt)
- [Catálogo usado por el generador APK](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py)
- [Modelos TTS preentrenados de Sherpa-ONNX](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html)
- [Voces españolas de Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)
