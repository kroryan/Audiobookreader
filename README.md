# BookReader

MVP de lector de PDF/EPUB/texto para Android con síntesis local y reproducción en segundo plano.

## Qué hay ahora

- Importación mediante el selector de documentos de Android.
- Extracción de texto de PDF, EPUB y archivos de texto/HTML.
- Catálogo descargable de modelos de Sherpa-ONNX.
- Los modelos se descargan y se usan dentro de la aplicación; no se registran como motores TTS del sistema.
- Al terminar una descarga, el modelo queda seleccionado automáticamente y se comprueba su contenido real aunque el archivo esté dentro de subcarpetas del paquete.
- Adaptador `OfflineTts` para Piper/VITS, Coqui VITS, Mimic3 VITS, Kokoro y Supertonic.
- Generación de audio PCM a WAV por fragmentos y playlist de Media3.
- `MediaSessionService` para notificación, controles del sistema, pantalla bloqueada y salida de la aplicación.
- Porcentaje de lectura, posición exacta por fragmento, marcadores y persistencia local del progreso.
- Porcentaje y tamaño del audio preparado por libro, limpieza individual o global de caché y límite de 512 MB para evitar llenar el almacenamiento.
- Tema claro/oscuro siguiendo automáticamente el tema del sistema, con contraste específico para lectura.
- Icono original en `assets/bookreader-icon.png`, usado también por el APK.

El proyecto está pensado para Android Studio. La compilación debug verificada queda en `app/build/outputs/apk/debug/app-debug.apk`. El APK contiene el motor nativo, pero no contiene modelos TTS ni archivos WAV: ambos se gestionan durante el uso.

## Firma de release

La firma se lee desde `keystore.properties`, que está excluido de Git. Las huellas SHA-256 de Google no son claves privadas y no sirven para firmar; hay que usar el keystore de subida registrado en Google Play Console. Consulta `keystore.properties.example` y ejecuta `./gradlew assembleRelease`. No se han guardado huellas, claves públicas ni secretos en el repositorio.

## Modelos contemplados

La lista se ha contrastado con `scripts/apk/generate-tts-apk-script.py` del checkout de Sherpa-ONNX:

- Piper/VITS: Miro, Davefx y Sharvard para `es_ES`.
- Coqui VITS: `vits-coqui-es-css10`.
- Mimic3 VITS: `vits-mimic3-es_ES-m-ailabs_low`.
- Kokoro: v0.19 inglés y v1.1 inglés+chino, incluyendo variante INT8.
- Supertonic 3 INT8: español y otros idiomas en un paquete multilingüe.

Kokoro merece una integración separada: el catálogo de voces original de Kokoro-82M sí incluye español (`ef_dora`, `em_alex`, `em_santa`), pero los paquetes oficiales que Sherpa-ONNX documenta actualmente como `kokoro-multi-lang-v1_0/v1_1` se publican y configuran para inglés+chino. Para usar esas voces españolas en el dispositivo habrá que validar una conversión compatible con el frontend/phonemizer de Sherpa; no se debe presentar como soporte terminado hasta generar audio correcto en un teléfono ARM.

## Siguiente trabajo

1. Descargar un modelo español pequeño y probar generación, memoria y factor de tiempo real en varios teléfonos.
2. Generar progresivamente la cola para que un libro largo no tenga que esperar a que termine completo.
3. Añadir resaltado palabra/frase sincronizado, controles de velocidad, temporizador y selección de voz.
4. Incorporar el catálogo completo upstream mediante un manifiesto versionado y mostrar licencia/tamaño de cada modelo.
5. Validar Kokoro español y decidir si se integra como paquete oficial, modelo convertido o backend independiente.

## Fuentes técnicas

- [Sherpa-ONNX Android TTS](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTtsEngine)
- [API Kotlin OfflineTts](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Tts.kt)
- [Catálogo usado por el generador APK](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py)
- [Modelos TTS preentrenados de Sherpa-ONNX](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html)
- [Voces españolas de Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)
