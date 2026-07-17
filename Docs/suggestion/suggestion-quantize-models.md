# Suggestion: Quantize embedded models

FP32 YOLO26 medium alone is ~78 MB. Exporting int8 NCNN variants would cut APK size substantially while keeping on-device detection. Effort: medium (re-export + validate mAP on device scenes).
