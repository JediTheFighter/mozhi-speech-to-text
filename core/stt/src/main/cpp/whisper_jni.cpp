#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include "whisper.h"

#define TAG "MozhiWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM *g_jvm = nullptr;
static std::mutex g_cb_mutex;
static jobject g_listener = nullptr;
static jmethodID g_on_segment = nullptr;
static std::atomic<bool> g_abort{false};

struct CallbackCtx {
    whisper_context *ctx;
};

static void emit_segments(JNIEnv *env, whisper_context *ctx, int n_new) {
    if (g_listener == nullptr || g_on_segment == nullptr) return;
    const int n = whisper_full_n_segments(ctx);
    const int start = n - n_new;
    std::string acc;
    for (int i = 0; i < n; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            acc += text;
        }
    }
    jstring jtext = env->NewStringUTF(acc.c_str());
    const bool is_partial = n_new > 0 && start >= 0;
    env->CallVoidMethod(g_listener, g_on_segment, jtext, is_partial ? JNI_TRUE : JNI_FALSE);
    env->DeleteLocalRef(jtext);
}

static void new_segment_callback(whisper_context *ctx, whisper_state *, int n_new, void *) {
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
    }
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    emit_segments(env, ctx, n_new);
}

static bool encoder_begin_callback(whisper_context *, whisper_state *, void *) {
    return !g_abort.load();
}

static bool abort_callback(void *) {
    return g_abort.load();
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_initContext(
        JNIEnv *env, jclass, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("initContext path=%s", path);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (!ctx) {
        LOGE("Failed to load whisper model");
        return 0;
    }
    LOGI("Loaded whisper model");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_freeContext(
        JNIEnv *, jclass, jlong ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx) whisper_free(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_setListener(
        JNIEnv *env, jclass, jobject listener) {
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    if (g_listener) {
        env->DeleteGlobalRef(g_listener);
        g_listener = nullptr;
        g_on_segment = nullptr;
    }
    if (listener) {
        g_listener = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        g_on_segment = env->GetMethodID(cls, "onSegment", "(Ljava/lang/String;Z)V");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_requestAbort(
        JNIEnv *, jclass) {
    LOGI("requestAbort");
    g_abort.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_clearAbort(
        JNIEnv *, jclass) {
    LOGI("clearAbort was=%d", (int) g_abort.load());
    g_abort.store(false);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_fullTranscribe(
        JNIEnv *env, jclass, jlong ptr, jfloatArray audio, jint threads, jstring language) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return env->NewStringUTF("");

    g_abort.store(false);
    jsize n = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = true;
    params.no_timestamps = true;
    params.max_tokens = 0;
    params.n_threads = threads > 0 ? threads : 4;
    params.offset_ms = 0;
    params.duration_ms = 0;
    params.temperature = 0.0f;
    params.temperature_inc = 0.2f;
    params.suppress_blank = false;
    params.suppress_nst = false;
    params.no_speech_thold = 1.0f;
    params.logprob_thold = -2.0f;
    params.entropy_thold = 2.8f;
    params.initial_prompt = nullptr;

    const char *lang = env->GetStringUTFChars(language, nullptr);
    params.language = lang;
    params.detect_language = false;
    params.new_segment_callback = new_segment_callback;
    params.encoder_begin_callback = encoder_begin_callback;
    params.abort_callback = abort_callback;

    LOGI("whisper_full n_samples=%d threads=%d lang=%s abort=%d",
         (int) n, (int) params.n_threads, lang, (int) g_abort.load());
    int rc = whisper_full(ctx, params, samples, n);
    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);

    if (rc != 0) {
        LOGE("whisper_full failed: %d abort=%d", rc, (int) g_abort.load());
        return env->NewStringUTF("");
    }

    std::string out;
    const int segs = whisper_full_n_segments(ctx);
            float max_nsp = 0.f;
    for (int i = 0; i < segs; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) out += text;
        const float nsp = whisper_full_get_segment_no_speech_prob(ctx, i);
        if (nsp > max_nsp) max_nsp = nsp;
    }
    LOGI("whisper_full ok segments=%d chars=%zu nsp=%.3f lang_id=%d text='%s'",
         segs, out.size(), max_nsp, whisper_full_lang_id(ctx), out.c_str());
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mozhi_core_stt_whisper_WhisperLib_systemInfo(JNIEnv *env, jclass) {
    return env->NewStringUTF(whisper_print_system_info());
}
