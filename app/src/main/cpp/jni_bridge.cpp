#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>

extern "C" {
void* ptt_create(const char*, const char*, const char*, const char*, float, int, int, int, int);
void ptt_destroy(void*);
void* ptt_stream_start(void*, const char*, const char*);
int ptt_stream_read(void*, float**, int*);
void ptt_stream_cancel(void*);
void ptt_stream_end(void*);
void ptt_free_audio(float*);
}

struct PocketEngine {
    void* tts = nullptr;
    std::atomic<void*> stream{nullptr};
    std::mutex synthesisMutex;
};

static PocketEngine* engineFrom(jlong value) {
    return reinterpret_cast<PocketEngine*>(value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_audiobookreader_tts_NativePocketTts_nativeCreate(
        JNIEnv* env, jobject, jstring models, jstring voices, jstring precision,
        jfloat temperature, jint lsdSteps, jint threads, jint sentencePauseMs,
        jint maxTextTokens) {
    const char* modelPath = env->GetStringUTFChars(models, nullptr);
    const char* voicePath = env->GetStringUTFChars(voices, nullptr);
    const char* precisionValue = env->GetStringUTFChars(precision, nullptr);
    auto* engine = new PocketEngine();
    const std::string tokenizerPath = std::string(modelPath) + "/tokenizer.model";
    engine->tts = ptt_create(modelPath, voicePath, tokenizerPath.c_str(), precisionValue,
                             temperature, lsdSteps, threads, sentencePauseMs, maxTextTokens);
    env->ReleaseStringUTFChars(models, modelPath);
    env->ReleaseStringUTFChars(voices, voicePath);
    env->ReleaseStringUTFChars(precision, precisionValue);
    if (!engine->tts) {
        delete engine;
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_audiobookreader_tts_NativePocketTts_nativeSynthesize(
        JNIEnv* env, jobject, jlong value, jstring text, jstring voice, jobject sink) {
    auto* engine = engineFrom(value);
    if (!engine || !engine->tts) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(engine->synthesisMutex);
    const char* textValue = env->GetStringUTFChars(text, nullptr);
    const char* voiceValue = env->GetStringUTFChars(voice, nullptr);
    void* stream = ptt_stream_start(engine->tts, textValue, voiceValue);
    env->ReleaseStringUTFChars(text, textValue);
    env->ReleaseStringUTFChars(voice, voiceValue);
    if (!stream) return JNI_FALSE;
    engine->stream.store(stream);
    jclass sinkClass = env->GetObjectClass(sink);
    jmethodID onAudio = env->GetMethodID(sinkClass, "onAudio", "([F)Z");
    bool success = onAudio != nullptr;
    while (success) {
        float* samples = nullptr;
        int count = 0;
        const int result = ptt_stream_read(stream, &samples, &count);
        if (result == 0) break;
        if (result < 0) {
            success = false;
            break;
        }
        jfloatArray chunk = env->NewFloatArray(count);
        if (!chunk) {
            ptt_free_audio(samples);
            success = false;
            break;
        }
        env->SetFloatArrayRegion(chunk, 0, count, samples);
        ptt_free_audio(samples);
        const jboolean accepted = env->CallBooleanMethod(sink, onAudio, chunk);
        env->DeleteLocalRef(chunk);
        if (env->ExceptionCheck() || !accepted) {
            env->ExceptionClear();
            success = false;
        }
    }
    engine->stream.store(nullptr);
    ptt_stream_end(stream);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiobookreader_tts_NativePocketTts_nativeStop(JNIEnv*, jobject, jlong value) {
    auto* engine = engineFrom(value);
    if (engine) {
        if (void* stream = engine->stream.load()) ptt_stream_cancel(stream);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiobookreader_tts_NativePocketTts_nativeDestroy(JNIEnv*, jobject, jlong value) {
    auto* engine = engineFrom(value);
    if (!engine) return;
    if (void* stream = engine->stream.load()) ptt_stream_cancel(stream);
    std::lock_guard<std::mutex> guard(engine->synthesisMutex);
    ptt_destroy(engine->tts);
    delete engine;
}
