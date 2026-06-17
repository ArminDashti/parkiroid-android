#!/usr/bin/env python3
"""Export Ultralytics YOLO26n to ONNX for Parkiroid on-device detection."""

from pathlib import Path

from ultralytics import YOLO

ASSET_PATH = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "yolo26n.onnx"


def main() -> None:
    model = YOLO("yolo26n.pt")
    exported = Path(model.export(format="onnx", imgsz=640))
    ASSET_PATH.parent.mkdir(parents=True, exist_ok=True)
    ASSET_PATH.write_bytes(exported.read_bytes())
    print(f"Wrote {ASSET_PATH}")


if __name__ == "__main__":
    main()
