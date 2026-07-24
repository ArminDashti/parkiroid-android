# Module: Detection overlay

Draw on-device YOLO detections as bounding boxes on the Preview screen and on history frame thumbnails/viewer. Watchman Preview also shows live jolt/sound via `SensorHudBridge`.

## Behavior

- `DetectionOverlayView` maps image-space boxes to view coordinates with centerCrop (`max` scale); history viewer uses matching `centerCrop` ImageView
- Colors: **green** for `car`, **red** for `person`; label format `label XX%`
- Spotter tap-to-watch enabled on CameraActivity when mode is Spotter
- Live overlays published via `DetectionOverlayBridge` from `CaptureService`
- Preview attaches the CaptureService continuous-monitoring camera surface while the Preview listener is attached
- While Preview listener is attached, analysis FPS is floored to ~2 for testing; outside Preview/recording the camera is duty-cycled
- `SensorHudBridge` publishes jolt m/s² and sound RMS for Watchman HUD

## Settings

- Preview always draws boxes when detections exist
- DataStore key `show_bounding_boxes` remains stored but is no longer exposed in General UI
- On-device detection runs when mode ≠ OFF
