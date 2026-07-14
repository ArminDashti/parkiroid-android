#ifndef DOGAN_YOLOV8_DETECTOR_H
#define DOGAN_YOLOV8_DETECTOR_H

#include <net.h>
#include <string>
#include <vector>

struct DetectionBox {
    float x0;
    float y0;
    float x1;
    float y1;
    int label;
    float prob;
};

class YoloV8Detector {
public:
    bool load(const std::string& param_path, const std::string& bin_path);
    void unload();
    bool is_loaded() const;

    std::vector<DetectionBox> detect(
        const int* argb_pixels,
        int width,
        int height,
        float confidence_threshold) const;

private:
    mutable ncnn::Net net_;
    bool loaded_ = false;
    int target_size_ = 640;
};

#endif
