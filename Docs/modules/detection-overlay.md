# Module: Detection overlay

Draw on-device YOLO detections as bounding boxes on the Camera eye preview and on history frame thumbnails.

## Behavior

- `DetectionOverlayView` maps image-space boxes to view coordinates
- Spotter tap-to-watch enabled on CameraActivity when mode is Spotter
- Live overlays published via `DetectionOverlayBridge` from `CaptureService`

## Settings

- DataStore key: `show_bounding_boxes` (general settings preference)
- On-device detection is always on; boxes always draw on CameraActivity when detections exist
