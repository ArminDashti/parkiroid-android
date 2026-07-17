# Module: ModelAssetManager

## Responsibility

Extract embedded YOLO26 NCNN `model.param` / `model.bin` from APK assets into `filesDir/models/{id}/` and load them via `NcnnNative`.

## Key APIs

- `ensureModelReady(id)` / `ensureSelectedModelReady(aiModel)` — copy from assets if missing
- `loadModel(aiModel)` — JNI load of param/bin paths
- `getLabelsForModel` — returns COCO-80 label list
- `AiModel` — `yolo26_nano` / `yolo26_small` / `yolo26_medium` (legacy yolov8_* maps to nano)

## Dependencies

- Android `AssetManager`
- `NcnnNative` / `dogan_ncnn`
- Build: `app/download_models.gradle` populates `assets/models/` from `ai-models/`
