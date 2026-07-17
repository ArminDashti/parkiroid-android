#include "yolov8_detector.h"

#include <android/log.h>
#include <cpu.h>

#include <algorithm>
#include <cmath>
#include <cfloat>
#include <vector>

namespace {

struct ObjectProposal {
    float x0;
    float y0;
    float x1;
    float y1;
    int label;
    float prob;
};

float intersection_area(const ObjectProposal& a, const ObjectProposal& b) {
    const float x0 = std::max(a.x0, b.x0);
    const float y0 = std::max(a.y0, b.y0);
    const float x1 = std::min(a.x1, b.x1);
    const float y1 = std::min(a.y1, b.y1);
    const float width = x1 - x0;
    const float height = y1 - y0;
    if (width <= 0.f || height <= 0.f) {
        return 0.f;
    }
    return width * height;
}

void qsort_descent_inplace(std::vector<ObjectProposal>& objects, int left, int right) {
    int i = left;
    int j = right;
    const float pivot = objects[(left + right) / 2].prob;

    while (i <= j) {
        while (objects[i].prob > pivot) {
            i++;
        }
        while (objects[j].prob < pivot) {
            j--;
        }
        if (i <= j) {
            std::swap(objects[i], objects[j]);
            i++;
            j--;
        }
    }

    if (left < j) {
        qsort_descent_inplace(objects, left, j);
    }
    if (i < right) {
        qsort_descent_inplace(objects, i, right);
    }
}

void qsort_descent_inplace(std::vector<ObjectProposal>& objects) {
    if (objects.empty()) {
        return;
    }
    qsort_descent_inplace(objects, 0, static_cast<int>(objects.size()) - 1);
}

void nms_sorted_bboxes(
    const std::vector<ObjectProposal>& objects,
    std::vector<int>& picked,
    float nms_threshold) {
    picked.clear();
    const int count = static_cast<int>(objects.size());
    std::vector<float> areas(count);
    for (int i = 0; i < count; i++) {
        areas[i] = (objects[i].x1 - objects[i].x0) * (objects[i].y1 - objects[i].y0);
    }

    for (int i = 0; i < count; i++) {
        const ObjectProposal& a = objects[i];
        int keep = 1;
        for (int picked_index : picked) {
            const ObjectProposal& b = objects[picked_index];
            if (a.label != b.label) {
                continue;
            }
            const float inter_area = intersection_area(a, b);
            const float union_area = areas[i] + areas[picked_index] - inter_area;
            if (union_area > 0.f && inter_area / union_area > nms_threshold) {
                keep = 0;
                break;
            }
        }
        if (keep) {
            picked.push_back(i);
        }
    }
}

std::vector<unsigned char> argb_to_rgb(const int* argb_pixels, int pixel_count) {
    std::vector<unsigned char> rgb(static_cast<size_t>(pixel_count) * 3);
    for (int i = 0; i < pixel_count; i++) {
        const int pixel = argb_pixels[i];
        rgb[static_cast<size_t>(i) * 3 + 0] = static_cast<unsigned char>((pixel >> 16) & 0xFF);
        rgb[static_cast<size_t>(i) * 3 + 1] = static_cast<unsigned char>((pixel >> 8) & 0xFF);
        rgb[static_cast<size_t>(i) * 3 + 2] = static_cast<unsigned char>(pixel & 0xFF);
    }
    return rgb;
}

/**
 * YOLO26 Ultralytics NCNN export: out0 is (4+num_class) x num_anchors,
 * boxes as cxcywh in letterbox pixels, class scores already sigmoided.
 *
 * ncnn may store that tensor as any of:
 *   - c=84, h=1, w=8400  (most common after Concat on axis 0)
 *   - h=84, w=8400, c=1
 *   - h=8400, w=84, c=1  (transposed)
 */
void generate_proposals_decoded(
    const ncnn::Mat& output,
    float prob_threshold,
    std::vector<ObjectProposal>& objects) {
    objects.clear();

    enum class Layout { Channels, Rows, Cols };
    Layout layout = Layout::Channels;
    int num_channels = 0;
    int num_anchors = 0;

    if (output.c >= 5 && output.c <= 512) {
        // Preferred: channels = 4+num_class, spatial = anchors
        num_channels = output.c;
        num_anchors = output.w * output.h;
        layout = Layout::Channels;
    } else if (output.h >= 5 && output.h <= 512 && output.w >= output.h) {
        num_channels = output.h;
        num_anchors = output.w;
        layout = Layout::Rows;
    } else if (output.w >= 5 && output.w <= 512 && output.h >= output.w) {
        num_channels = output.w;
        num_anchors = output.h;
        layout = Layout::Cols;
    } else {
        __android_log_print(
            ANDROID_LOG_ERROR,
            "DoganNcnn",
            "Unrecognized out0 shape dims=%d c=%d h=%d w=%d",
            output.dims,
            output.c,
            output.h,
            output.w);
        return;
    }

    const int num_class = num_channels - 4;
    if (num_class <= 0 || num_anchors <= 0) {
        return;
    }

    for (int i = 0; i < num_anchors; i++) {
        float cx;
        float cy;
        float bw;
        float bh;
        int label = -1;
        float score = -FLT_MAX;

        if (layout == Layout::Channels) {
            cx = output.channel(0)[i];
            cy = output.channel(1)[i];
            bw = output.channel(2)[i];
            bh = output.channel(3)[i];
            for (int c = 0; c < num_class; c++) {
                const float candidate = output.channel(4 + c)[i];
                if (candidate > score) {
                    score = candidate;
                    label = c;
                }
            }
        } else if (layout == Layout::Rows) {
            cx = output.row(0)[i];
            cy = output.row(1)[i];
            bw = output.row(2)[i];
            bh = output.row(3)[i];
            for (int c = 0; c < num_class; c++) {
                const float candidate = output.row(4 + c)[i];
                if (candidate > score) {
                    score = candidate;
                    label = c;
                }
            }
        } else {
            const float* row = output.row(i);
            cx = row[0];
            cy = row[1];
            bw = row[2];
            bh = row[3];
            for (int c = 0; c < num_class; c++) {
                const float candidate = row[4 + c];
                if (candidate > score) {
                    score = candidate;
                    label = c;
                }
            }
        }

        if (label < 0 || score < prob_threshold) {
            continue;
        }

        ObjectProposal object;
        object.x0 = cx - bw * 0.5f;
        object.y0 = cy - bh * 0.5f;
        object.x1 = cx + bw * 0.5f;
        object.y1 = cy + bh * 0.5f;
        object.label = label;
        object.prob = score;
        objects.push_back(object);
    }
}

}  // namespace

bool YoloV8Detector::load(const std::string& param_path, const std::string& bin_path) {
    unload();

    net_.clear();
    net_.opt = ncnn::Option();
    net_.opt.num_threads = ncnn::get_big_cpu_count();
    net_.opt.use_vulkan_compute = false;
    // Unpacked blobs so out0 is readable as h=84,w=8400 (or c=84) without elempack surprises.
    net_.opt.use_packing_layout = false;

    if (net_.load_param(param_path.c_str()) != 0) {
        return false;
    }
    if (net_.load_model(bin_path.c_str()) != 0) {
        net_.clear();
        return false;
    }

    loaded_ = true;
    return true;
}

void YoloV8Detector::unload() {
    if (loaded_) {
        net_.clear();
        loaded_ = false;
    }
}

bool YoloV8Detector::is_loaded() const {
    return loaded_;
}

std::vector<DetectionBox> YoloV8Detector::detect(
    const int* argb_pixels,
    int width,
    int height,
    float confidence_threshold) const {
    std::vector<DetectionBox> results;
    if (!loaded_ || argb_pixels == nullptr || width <= 0 || height <= 0) {
        return results;
    }

    const float nms_threshold = 0.45f;
    const int target = target_size_;

    // Letterbox into a fixed 640x640 canvas (YOLO26 anchors assume this size).
    const float scale = std::min(
        static_cast<float>(target) / static_cast<float>(width),
        static_cast<float>(target) / static_cast<float>(height));
    const int resized_w = static_cast<int>(std::round(static_cast<float>(width) * scale));
    const int resized_h = static_cast<int>(std::round(static_cast<float>(height) * scale));
    const int wpad = target - resized_w;
    const int hpad = target - resized_h;
    const int pad_left = wpad / 2;
    const int pad_top = hpad / 2;

    const std::vector<unsigned char> rgb = argb_to_rgb(argb_pixels, width * height);
    ncnn::Mat input = ncnn::Mat::from_pixels_resize(
        rgb.data(),
        ncnn::Mat::PIXEL_RGB,
        width,
        height,
        resized_w,
        resized_h);

    ncnn::Mat padded_input;
    ncnn::copy_make_border(
        input,
        padded_input,
        pad_top,
        hpad - pad_top,
        pad_left,
        wpad - pad_left,
        ncnn::BORDER_CONSTANT,
        114.f);

    const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    padded_input.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor extractor = net_.create_extractor();
    extractor.set_num_threads(ncnn::get_big_cpu_count());
    if (extractor.input("in0", padded_input) != 0) {
        return results;
    }

    ncnn::Mat output;
    if (extractor.extract("out0", output) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "DoganNcnn", "Failed to extract out0");
        return results;
    }

    static bool logged_shape = false;
    if (!logged_shape) {
        logged_shape = true;
        __android_log_print(
            ANDROID_LOG_INFO,
            "DoganNcnn",
            "out0 shape dims=%d c=%d h=%d w=%d",
            output.dims,
            output.c,
            output.h,
            output.w);
    }

    std::vector<ObjectProposal> proposals;
    generate_proposals_decoded(output, confidence_threshold, proposals);
    qsort_descent_inplace(proposals);

    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    results.reserve(picked.size());
    for (int index : picked) {
        const ObjectProposal& proposal = proposals[index];
        float x0 = (proposal.x0 - static_cast<float>(pad_left)) / scale;
        float y0 = (proposal.y0 - static_cast<float>(pad_top)) / scale;
        float x1 = (proposal.x1 - static_cast<float>(pad_left)) / scale;
        float y1 = (proposal.y1 - static_cast<float>(pad_top)) / scale;

        x0 = std::max(0.f, std::min(x0, static_cast<float>(width - 1)));
        y0 = std::max(0.f, std::min(y0, static_cast<float>(height - 1)));
        x1 = std::max(0.f, std::min(x1, static_cast<float>(width - 1)));
        y1 = std::max(0.f, std::min(y1, static_cast<float>(height - 1)));

        DetectionBox box;
        box.x0 = x0;
        box.y0 = y0;
        box.x1 = x1;
        box.y1 = y1;
        box.label = proposal.label;
        box.prob = proposal.prob;
        results.push_back(box);
    }

    return results;
}
