#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "DoganNcnn"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static bool g_model_loaded = false;

extern "C" JNIEXPORT jstring JNICALL
Java_com_dogan_NcnnNative_nativeVersion(JNIEnv *env, jobject /* thiz */) {
    return env->NewStringUTF("dogan-ncnn-stub-1.0");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dogan_NcnnNative_loadModel(JNIEnv *env, jobject /* thiz */,
                                    jstring param_path, jstring bin_path) {
    const char *param = env->GetStringUTFChars(param_path, nullptr);
    const char *bin = env->GetStringUTFChars(bin_path, nullptr);
    LOGI("Loading NCNN model param=%s bin=%s", param, bin);
    g_model_loaded = true;
    env->ReleaseStringUTFChars(param_path, param);
    env->ReleaseStringUTFChars(bin_path, bin);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_dogan_NcnnNative_detect(JNIEnv *env, jobject /* thiz */,
                                 jintArray pixels, jint width, jint height,
                                 jfloat confidence_threshold) {
    // Stub: returns empty detection array. Link Tencent NCNN for production inference.
    jfloatArray result = env->NewFloatArray(0);
    return result;
}
