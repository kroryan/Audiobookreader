# Keep sherpa-onnx JNI entry points and model configuration fields.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# PocketTTS JNI: native symbols are bound by class/method name.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.audiobookreader.tts.NativePocketTts { *; }

# PDFBox-Android loads filters/fonts reflectively and references optional libs.
-keep class com.tom_roush.pdfbox.filter.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.pdfbox.**
-dontwarn javax.**
-dontwarn java.awt.**

# Optional/compile-only dependencies of OkHttp, Commons Compress and jsoup.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.google.re2j.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.objectweb.asm.**
-dontwarn org.slf4j.**
