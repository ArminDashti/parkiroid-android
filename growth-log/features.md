# Growth log — features

- USB/ADB install: root `install-on-phone-directly.ps1` rebuilds from current sources by default (use `-SkipBuild` for an existing APK); `scripts/install-apk.sh` on macOS/Linux.
- Battery defaults: mode OFF, recording off, Preview does not auto-enable a mode, image upload on-demand.
- Camera duty-cycle: CaptureService opens the camera only for each detection still unless Preview or recording needs continuous bind.
