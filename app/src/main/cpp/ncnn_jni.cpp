#include <jni.h>

#include <android/log.h>
#include <string>
#include <vector>

#include "yolov8_detector.h"

#define LOG_TAG "DoganNcnn"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static YoloV8Detector g_yolo_detector;
static std::string g_loaded_model_id;
static bool g_model_loaded = false;

static bool is_supported_yolo_model(const std::string& model_id) {
    return model_id.rfind("yolo26", 0) == 0 || model_id.rfind("yolov8", 0) == 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dogan_NcnnNative_nativeVersion(JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF("dogan-ncnn-20240820");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dogan_NcnnNative_loadModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring param_path,
    jstring bin_path,
    jstring model_id) {
    const char* param = env->GetStringUTFChars(param_path, nullptr);
    const char* bin = env->GetStringUTFChars(bin_path, nullptr);
    const char* model = env->GetStringUTFChars(model_id, nullptr);

    const std::string model_id_value = model != nullptr ? model : "";
    const bool supported = is_supported_yolo_model(model_id_value);

    LOGI("Loading NCNN model id=%s param=%s bin=%s", model, param, bin);

    g_yolo_detector.unload();
    g_model_loaded = false;
    g_loaded_model_id.clear();

    bool ok = false;
    if (supported) {
        ok = g_yolo_detector.load(param, bin);
        if (!ok) {
            LOGE("Failed to load YOLO NCNN model %s", model);
        }
    } else {
        LOGE("Model %s is not supported by the NCNN runtime yet", model);
    }

    if (ok) {
        g_model_loaded = true;
        g_loaded_model_id = model_id_value;
    }

    env->ReleaseStringUTFChars(param_path, param);
    env->ReleaseStringUTFChars(bin_path, bin);
    env->ReleaseStringUTFChars(model_id, model);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_dogan_NcnnNative_detect(
    JNIEnv* env,
    jobject /* thiz */,
    jintArray pixels,
    jint width,
    jint height,
    jfloat confidence_threshold) {
    if (!g_model_loaded || !g_yolo_detector.is_loaded() || pixels == nullptr || width <= 0 || height <= 0) {
        return env->NewFloatArray(0);
    }

    const jsize pixel_count = env->GetArrayLength(pixels);
    if (pixel_count < width * height) {
        return env->NewFloatArray(0);
    }

    jint* pixel_data = env->GetIntArrayElements(pixels, nullptr);
    const std::vector<DetectionBox> detections = g_yolo_detector.detect(
        pixel_data,
        width,
        height,
        confidence_threshold);
    env->ReleaseIntArrayElements(pixels, pixel_data, JNI_ABORT);

    const jsize output_length = static_cast<jsize>(detections.size() * 6);
    jfloatArray result = env->NewFloatArray(output_length);
    if (output_length == 0) {
        return result;
    }

    std::vector<jfloat> flat;
    flat.reserve(static_cast<size_t>(output_length));
    for (const DetectionBox& detection : detections) {
        flat.push_back(static_cast<jfloat>(detection.label));
        flat.push_back(detection.prob);
        flat.push_back(detection.x0);
        flat.push_back(detection.y0);
        flat.push_back(detection.x1);
        flat.push_back(detection.y1);
    }

    env->SetFloatArrayRegion(result, 0, output_length, flat.data());
    return result;
}
