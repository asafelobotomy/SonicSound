#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "VlcPcmTap"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

using libvlc_media_player_t = struct libvlc_media_player_t;

using libvlc_audio_play_cb = void (*)(void *data, const void *samples, unsigned count, int64_t pts);
using libvlc_audio_pause_cb = void (*)(void *data, int64_t pts);
using libvlc_audio_resume_cb = void (*)(void *data, int64_t pts);
using libvlc_audio_flush_cb = void (*)(void *data, int64_t pts);
using libvlc_audio_drain_cb = void (*)(void *data);
using libvlc_audio_set_volume_cb = void (*)(void *data, float volume, bool mute);
using libvlc_audio_setup_cb = int (*)(void **data, char *format, unsigned *rate, unsigned *channels);
using libvlc_audio_cleanup_cb = void (*)(void *data);

using set_callbacks_fn = void (*)(
    libvlc_media_player_t *,
    libvlc_audio_play_cb,
    libvlc_audio_pause_cb,
    libvlc_audio_resume_cb,
    libvlc_audio_flush_cb,
    libvlc_audio_drain_cb,
    void *);
using set_format_callbacks_fn = void (*)(
    libvlc_media_player_t *,
    libvlc_audio_setup_cb,
    libvlc_audio_cleanup_cb);
using set_volume_callback_fn = void (*)(libvlc_media_player_t *, libvlc_audio_set_volume_cb);

JavaVM *g_vm = nullptr;
jclass g_bridge_class = nullptr;
jmethodID g_setup_mid = nullptr;
jmethodID g_play_mid = nullptr;
jmethodID g_pause_mid = nullptr;
jmethodID g_resume_mid = nullptr;
jmethodID g_flush_mid = nullptr;
jmethodID g_cleanup_mid = nullptr;
jmethodID g_volume_mid = nullptr;

set_callbacks_fn g_set_callbacks = nullptr;
set_format_callbacks_fn g_set_format_callbacks = nullptr;
set_volume_callback_fn g_set_volume_callback = nullptr;

std::mutex g_attach_mu;
libvlc_media_player_t *g_attached_mp = nullptr;

struct TapState {
    unsigned channels = 2;
    unsigned rate = 44100;
    // Reused direct buffer for play() JNI upcalls.
    jbyteArray play_array = nullptr;
    jint play_array_cap = 0;
};

TapState g_tap;

void *open_libvlc() {
    // Android loads JNI libs with RTLD_LOCAL, so RTLD_DEFAULT cannot see libvlc
    // exports. Open by SONAME (already mapped after System.loadLibrary("vlc")).
    void *handle = dlopen("libvlc.so", RTLD_NOW | RTLD_NOLOAD);
    if (!handle) {
        handle = dlopen("libvlc.so", RTLD_NOW);
    }
    if (!handle) {
        LOGE("dlopen(libvlc.so) failed: %s", dlerror());
    }
    return handle;
}

bool resolve_symbols() {
    if (g_set_callbacks && g_set_format_callbacks && g_set_volume_callback) {
        return true;
    }
    void *handle = open_libvlc();
    if (!handle) {
        return false;
    }
    g_set_callbacks = reinterpret_cast<set_callbacks_fn>(
        dlsym(handle, "libvlc_audio_set_callbacks"));
    g_set_format_callbacks = reinterpret_cast<set_format_callbacks_fn>(
        dlsym(handle, "libvlc_audio_set_format_callbacks"));
    g_set_volume_callback = reinterpret_cast<set_volume_callback_fn>(
        dlsym(handle, "libvlc_audio_set_volume_callback"));
    // Keep handle open for the process lifetime (libvlc stays mapped anyway).
    if (!g_set_callbacks || !g_set_format_callbacks || !g_set_volume_callback) {
        LOGE(
            "Failed to resolve libvlc audio callback symbols (cb=%p fmt=%p vol=%p): %s",
            reinterpret_cast<void *>(g_set_callbacks),
            reinterpret_cast<void *>(g_set_format_callbacks),
            reinterpret_cast<void *>(g_set_volume_callback),
            dlerror());
        return false;
    }
    LOGI("Resolved libvlc audio callback symbols");
    return true;
}

JNIEnv *env_for_thread(bool *attached) {
    *attached = false;
    if (!g_vm) return nullptr;
    JNIEnv *env = nullptr;
    const jint status = g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (status == JNI_OK) return env;
    if (status == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) return nullptr;
        *attached = true;
        return env;
    }
    return nullptr;
}

void detach_if_needed(bool attached) {
    if (attached && g_vm) g_vm->DetachCurrentThread();
}

int setup_cb(void **data, char *format, unsigned *rate, unsigned *channels) {
    // Request signed 16-bit native endian — simplest for AudioTrack + FFT.
    std::strncpy(format, "S16N", 4);
    format[4] = '\0';
    g_tap.rate = *rate;
    g_tap.channels = *channels == 0 ? 2 : *channels;
    *channels = g_tap.channels;

    LOGI("setup_cb rate=%u ch=%u", g_tap.rate, g_tap.channels);

    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env || !g_bridge_class || !g_setup_mid) {
        detach_if_needed(attached);
        return -1;
    }
    const jboolean ok = env->CallStaticBooleanMethod(
        g_bridge_class, g_setup_mid, static_cast<jint>(g_tap.rate), static_cast<jint>(g_tap.channels));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        detach_if_needed(attached);
        return -1;
    }
    detach_if_needed(attached);
    *data = &g_tap;
    return ok ? 0 : -1;
}

void cleanup_cb(void * /*data*/) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (env && g_bridge_class && g_cleanup_mid) {
        env->CallStaticVoidMethod(g_bridge_class, g_cleanup_mid);
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (g_tap.play_array) {
            env->DeleteGlobalRef(g_tap.play_array);
            g_tap.play_array = nullptr;
            g_tap.play_array_cap = 0;
        }
    }
    detach_if_needed(attached);
}

void play_cb(void *data, const void *samples, unsigned count, int64_t /*pts*/) {
    // After cleanup, VLC may still deliver a play with a stale/null opaque pointer
    // until setup runs again — always fall back to the process-wide tap state.
    auto *state = static_cast<TapState *>(data);
    if (!state) state = &g_tap;
    if (!samples || count == 0) return;

    const unsigned channels = state->channels == 0 ? 2 : state->channels;
    const jint bytes = static_cast<jint>(count * channels * sizeof(int16_t));
    if (bytes <= 0) return;

    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env || !g_bridge_class || !g_play_mid) {
        detach_if_needed(attached);
        return;
    }

    if (!state->play_array || state->play_array_cap < bytes) {
        if (state->play_array) env->DeleteGlobalRef(state->play_array);
        jbyteArray local = env->NewByteArray(bytes);
        if (!local) {
            detach_if_needed(attached);
            return;
        }
        state->play_array = static_cast<jbyteArray>(env->NewGlobalRef(local));
        env->DeleteLocalRef(local);
        state->play_array_cap = bytes;
        if (!state->play_array) {
            detach_if_needed(attached);
            return;
        }
    }

    env->SetByteArrayRegion(state->play_array, 0, bytes, reinterpret_cast<const jbyte *>(samples));
    env->CallStaticVoidMethod(
        g_bridge_class,
        g_play_mid,
        state->play_array,
        bytes,
        static_cast<jint>(channels),
        static_cast<jint>(count));
    if (env->ExceptionCheck()) env->ExceptionClear();
    detach_if_needed(attached);
}

void pause_cb(void * /*data*/, int64_t /*pts*/) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (env && g_bridge_class && g_pause_mid) {
        env->CallStaticVoidMethod(g_bridge_class, g_pause_mid);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    detach_if_needed(attached);
}

void resume_cb(void * /*data*/, int64_t /*pts*/) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (env && g_bridge_class && g_resume_mid) {
        env->CallStaticVoidMethod(g_bridge_class, g_resume_mid);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    detach_if_needed(attached);
}

void flush_cb(void * /*data*/, int64_t /*pts*/) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (env && g_bridge_class && g_flush_mid) {
        env->CallStaticVoidMethod(g_bridge_class, g_flush_mid);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    detach_if_needed(attached);
}

void drain_cb(void * /*data*/) {}

void volume_cb(void * /*data*/, float volume, bool mute) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (env && g_bridge_class && g_volume_mid) {
        env->CallStaticVoidMethod(g_bridge_class, g_volume_mid, volume, mute ? JNI_TRUE : JNI_FALSE);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    detach_if_needed(attached);
}

void clear_callbacks(libvlc_media_player_t *mp) {
    if (!mp || !g_set_callbacks || !g_set_format_callbacks || !g_set_volume_callback) return;
    g_set_callbacks(mp, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr);
    g_set_format_callbacks(mp, nullptr, nullptr);
    g_set_volume_callback(mp, nullptr);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_sonicsound_playback_VlcPcmOutput_nativeInit(JNIEnv *env, jclass) {
    if (!resolve_symbols()) return JNI_FALSE;

    jclass local = env->FindClass("app/sonicsound/playback/VlcPcmOutput");
    if (!local) {
        LOGE("FindClass VlcPcmOutput failed");
        return JNI_FALSE;
    }
    if (g_bridge_class) env->DeleteGlobalRef(g_bridge_class);
    g_bridge_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);

    g_setup_mid = env->GetStaticMethodID(g_bridge_class, "nativeSetup", "(II)Z");
    g_play_mid = env->GetStaticMethodID(g_bridge_class, "nativePlay", "([BIII)V");
    g_pause_mid = env->GetStaticMethodID(g_bridge_class, "nativePause", "()V");
    g_resume_mid = env->GetStaticMethodID(g_bridge_class, "nativeResume", "()V");
    g_flush_mid = env->GetStaticMethodID(g_bridge_class, "nativeFlush", "()V");
    g_cleanup_mid = env->GetStaticMethodID(g_bridge_class, "nativeCleanup", "()V");
    g_volume_mid = env->GetStaticMethodID(g_bridge_class, "nativeVolume", "(FZ)V");
    if (!g_setup_mid || !g_play_mid || !g_pause_mid || !g_resume_mid || !g_flush_mid ||
        !g_cleanup_mid || !g_volume_mid) {
        LOGE("Missing JNI method IDs");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_sonicsound_playback_VlcPcmOutput_nativeAttach(JNIEnv *, jclass, jlong mediaPlayerPtr) {
    auto *mp = reinterpret_cast<libvlc_media_player_t *>(mediaPlayerPtr);
    if (!mp || !resolve_symbols()) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_attach_mu);
    if (g_attached_mp && g_attached_mp != mp) {
        clear_callbacks(g_attached_mp);
    }
    g_set_callbacks(mp, play_cb, pause_cb, resume_cb, flush_cb, drain_cb, &g_tap);
    g_set_format_callbacks(mp, setup_cb, cleanup_cb);
    g_set_volume_callback(mp, volume_cb);
    g_attached_mp = mp;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_sonicsound_playback_VlcPcmOutput_nativeDetach(JNIEnv *env, jclass, jlong mediaPlayerPtr) {
    auto *mp = reinterpret_cast<libvlc_media_player_t *>(mediaPlayerPtr);
    std::lock_guard<std::mutex> lock(g_attach_mu);
    if (mp && g_attached_mp == mp) {
        clear_callbacks(mp);
        g_attached_mp = nullptr;
    } else if (!mp && g_attached_mp) {
        clear_callbacks(g_attached_mp);
        g_attached_mp = nullptr;
    }
    if (g_tap.play_array && env) {
        env->DeleteGlobalRef(g_tap.play_array);
        g_tap.play_array = nullptr;
        g_tap.play_array_cap = 0;
    }
}
