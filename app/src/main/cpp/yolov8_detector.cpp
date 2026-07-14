#include "yolov8_detector.h"

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

float sigmoid(float value) {
    return 1.f / (1.f + std::exp(-value));
}

void generate_proposals(
    const ncnn::Mat& pred,
    int stride,
    const ncnn::Mat& in_pad,
    float prob_threshold,
    std::vector<ObjectProposal>& objects) {
    const int w = in_pad.w;
    const int h = in_pad.h;
    const int num_grid_x = w / stride;
    const int num_grid_y = h / stride;
    const int reg_max = 16;
    const int num_class = pred.w - reg_max * 4;

    for (int y = 0; y < num_grid_y; y++) {
        for (int x = 0; x < num_grid_x; x++) {
            const ncnn::Mat pred_grid = pred.row_range(y * num_grid_x + x, 1);

            int label = -1;
            float score = -FLT_MAX;
            const ncnn::Mat pred_score = pred_grid.range(reg_max * 4, num_class);
            for (int k = 0; k < num_class; k++) {
                const float candidate = pred_score[k];
                if (candidate > score) {
                    label = k;
                    score = candidate;
                }
            }
            score = sigmoid(score);
            if (score < prob_threshold) {
                continue;
            }

            ncnn::Mat pred_bbox = pred_grid.range(0, reg_max * 4).reshape(reg_max, 4);
            {
                ncnn::Layer* softmax = ncnn::create_layer("Softmax");
                ncnn::ParamDict params;
                params.set(0, 1);
                params.set(1, 1);
                softmax->load_param(params);

                ncnn::Option option;
                option.num_threads = 1;
                option.use_packing_layout = false;
                softmax->create_pipeline(option);
                softmax->forward_inplace(pred_bbox, option);
                softmax->destroy_pipeline(option);
                delete softmax;
            }

            float pred_ltrb[4] = {0.f, 0.f, 0.f, 0.f};
            for (int k = 0; k < 4; k++) {
                float distance = 0.f;
                const float* distance_after_softmax = pred_bbox.row(k);
                for (int bin = 0; bin < reg_max; bin++) {
                    distance += static_cast<float>(bin) * distance_after_softmax[bin];
                }
                pred_ltrb[k] = distance * static_cast<float>(stride);
            }

            const float center_x = (static_cast<float>(x) + 0.5f) * static_cast<float>(stride);
            const float center_y = (static_cast<float>(y) + 0.5f) * static_cast<float>(stride);

            ObjectProposal object;
            object.x0 = center_x - pred_ltrb[0];
            object.y0 = center_y - pred_ltrb[1];
            object.x1 = center_x + pred_ltrb[2];
            object.y1 = center_y + pred_ltrb[3];
            object.label = label;
            object.prob = score;
            objects.push_back(object);
        }
    }
}

void generate_proposals(
    const ncnn::Mat& pred,
    const std::vector<int>& strides,
    const ncnn::Mat& in_pad,
    float prob_threshold,
    std::vector<ObjectProposal>& objects) {
    const int w = in_pad.w;
    const int h = in_pad.h;
    int pred_row_offset = 0;
    for (int stride : strides) {
        const int num_grid_x = w / stride;
        const int num_grid_y = h / stride;
        const int num_grid = num_grid_x * num_grid_y;
        generate_proposals(pred.row_range(pred_row_offset, num_grid), stride, in_pad, prob_threshold, objects);
        pred_row_offset += num_grid;
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

}  // namespace

bool YoloV8Detector::load(const std::string& param_path, const std::string& bin_path) {
    unload();

    net_.clear();
    net_.opt = ncnn::Option();
    net_.opt.num_threads = ncnn::get_big_cpu_count();
    net_.opt.use_vulkan_compute = false;

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
    const std::vector<int> strides = {8, 16, 32};
    const int max_stride = 32;

    int resized_w = width;
    int resized_h = height;
    float scale = 1.f;
    if (width > height) {
        scale = static_cast<float>(target_size_) / static_cast<float>(width);
        resized_w = target_size_;
        resized_h = static_cast<int>(static_cast<float>(height) * scale);
    } else {
        scale = static_cast<float>(target_size_) / static_cast<float>(height);
        resized_h = target_size_;
        resized_w = static_cast<int>(static_cast<float>(width) * scale);
    }

    const std::vector<unsigned char> rgb = argb_to_rgb(argb_pixels, width * height);
    ncnn::Mat input = ncnn::Mat::from_pixels_resize(
        rgb.data(),
        ncnn::Mat::PIXEL_RGB,
        width,
        height,
        resized_w,
        resized_h);

    const int wpad = (resized_w + max_stride - 1) / max_stride * max_stride - resized_w;
    const int hpad = (resized_h + max_stride - 1) / max_stride * max_stride - resized_h;
    ncnn::Mat padded_input;
    ncnn::copy_make_border(
        input,
        padded_input,
        hpad / 2,
        hpad - hpad / 2,
        wpad / 2,
        wpad - wpad / 2,
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
        return results;
    }

    std::vector<ObjectProposal> proposals;
    generate_proposals(output, strides, padded_input, confidence_threshold, proposals);
    qsort_descent_inplace(proposals);

    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    results.reserve(picked.size());
    for (int index : picked) {
        const ObjectProposal& proposal = proposals[index];
        float x0 = (proposal.x0 - static_cast<float>(wpad) / 2.f) / scale;
        float y0 = (proposal.y0 - static_cast<float>(hpad) / 2.f) / scale;
        float x1 = (proposal.x1 - static_cast<float>(wpad) / 2.f) / scale;
        float y1 = (proposal.y1 - static_cast<float>(hpad) / 2.f) / scale;

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
