#!/usr/bin/env bash
set -euo pipefail

package_id="com.dogan"
launcher_activity="com.dogan/.MainActivity"

usage() {
  cat <<'EOF'
Install Dogan on a USB-connected Android device via ADB.

Usage:
  ./scripts/install-apk.sh [options]

Options:
  --apk PATH                APK to install (default: newest export/build output)
  --variant debug|release   Variant when resolving/building (default: debug)
  --build                   Build via export-apk.sh before installing
  --clean                   With --build, run a clean build first
  --serial ID               Target a specific adb device serial
  --launch                  Start Dogan after install
  --uninstall-first         Uninstall com.dogan before installing
  -h, --help                Show this help

Examples:
  ./scripts/install-apk.sh
  ./scripts/install-apk.sh --build --launch
  ./scripts/install-apk.sh --apk ./exports/dogan-debug-v1.0.0.apk
EOF
}

apk_path=""
variant="debug"
do_build="false"
do_clean="false"
serial=""
do_launch="false"
uninstall_first="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      apk_path="$2"
      shift 2
      ;;
    --variant)
      variant="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
      shift 2
      ;;
    --build)
      do_build="true"
      shift
      ;;
    --clean)
      do_clean="true"
      shift
      ;;
    --serial)
      serial="$2"
      shift 2
      ;;
    --launch)
      do_launch="true"
      shift
      ;;
    --uninstall-first)
      uninstall_first="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "$variant" != "debug" && "$variant" != "release" ]]; then
  echo "Invalid variant: $variant (use debug or release)" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"

resolve_android_sdk_dir() {
  local candidates=()
  local value sdk_dir local_properties

  for value in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    if [[ -n "$value" ]]; then
      candidates+=("$value")
    fi
  done

  candidates+=(
    "$HOME/Library/Android/sdk"
    "$HOME/Android/Sdk"
    "/opt/android-sdk"
  )

  local_properties="$project_root/local.properties"
  if [[ -f "$local_properties" ]]; then
    sdk_dir="$(grep -E '^sdk\.dir=' "$local_properties" | head -n1 | cut -d= -f2- | sed 's/\\\\/\//g; s/\\:/:/g')"
    if [[ -n "$sdk_dir" ]]; then
      candidates+=("$sdk_dir")
    fi
  fi

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -d "$candidate/platform-tools" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

resolve_adb_path() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  local sdk_dir
  sdk_dir="$(resolve_android_sdk_dir || true)"
  if [[ -n "$sdk_dir" && -x "$sdk_dir/platform-tools/adb" ]]; then
    echo "$sdk_dir/platform-tools/adb"
    return 0
  fi
  return 1
}

run_adb() {
  if [[ -n "$serial" ]]; then
    "$adb_path" -s "$serial" "$@"
  else
    "$adb_path" "$@"
  fi
}

list_adb_devices() {
  run_adb devices | awk 'NR>1 && $1 != "" { print $1 "\t" $2 }'
}

wait_for_adb_device() {
  echo "Looking for a USB-debugging device..."
  local deadline=$((SECONDS + 60))
  local line device_serial state ready_count ready_serials unauthorized_count

  while (( SECONDS < deadline )); do
    mapfile -t device_lines < <(list_adb_devices || true)

    if [[ -n "$serial" ]]; then
      state=""
      for line in "${device_lines[@]:-}"; do
        device_serial="${line%%$'\t'*}"
        state="${line#*$'\t'}"
        if [[ "$device_serial" == "$serial" ]]; then
          if [[ "$state" == "device" ]]; then
            echo "  Using device: $device_serial"
            return 0
          fi
          if [[ "$state" == "unauthorized" ]]; then
            echo "  Device unauthorized - accept the USB debugging prompt on the phone."
          else
            echo "  Device $device_serial is $state; waiting..."
          fi
          break
        fi
      done
      if [[ -z "$state" ]]; then
        echo "  Waiting for device $serial ..."
      fi
    else
      ready_count=0
      unauthorized_count=0
      ready_serials=()
      for line in "${device_lines[@]:-}"; do
        device_serial="${line%%$'\t'*}"
        state="${line#*$'\t'}"
        if [[ "$state" == "device" ]]; then
          ready_serials+=("$device_serial")
          ready_count=$((ready_count + 1))
        elif [[ "$state" == "unauthorized" ]]; then
          unauthorized_count=$((unauthorized_count + 1))
        fi
      done

      if (( ready_count == 1 )); then
        serial="${ready_serials[0]}"
        echo "  Using device: $serial"
        return 0
      fi
      if (( ready_count > 1 )); then
        echo "Multiple devices connected: ${ready_serials[*]}" >&2
        echo "Re-run with --serial <id>, e.g.:" >&2
        echo "  ./scripts/install-apk.sh --serial ${ready_serials[0]}" >&2
        exit 1
      fi
      if (( unauthorized_count > 0 )); then
        echo "  Device unauthorized - accept the USB debugging prompt on the phone."
      else
        echo "  No device yet. Enable Developer options > USB debugging, then plug in USB."
      fi
    fi

    sleep 2
  done

  echo "No ready ADB device found within 60 seconds." >&2
  echo "Checklist:" >&2
  echo "  1. Enable Developer options and USB debugging on the phone" >&2
  echo "  2. Connect via USB (not charge-only)" >&2
  echo "  3. Accept the \"Allow USB debugging?\" prompt" >&2
  echo "  4. Run: adb devices  (should show \"device\", not \"unauthorized\")" >&2
  exit 1
}

resolve_apk_path() {
  if [[ -n "$apk_path" ]]; then
    if [[ ! -f "$apk_path" ]]; then
      echo "APK not found: $apk_path" >&2
      exit 1
    fi
    # shellcheck disable=SC2005
    echo "$(cd "$(dirname "$apk_path")" && pwd)/$(basename "$apk_path")"
    return 0
  fi

  local newest="" candidate mtime newest_mtime=0
  local search_roots=(
    "$project_root/exports"
    "$project_root/app/build/outputs/apk/$variant"
  )

  for root in "${search_roots[@]}"; do
    [[ -d "$root" ]] || continue
    if [[ "$root" == */exports ]]; then
      shopt -s nullglob
      for candidate in "$root"/dogan-"$variant"-*.apk; do
        mtime="$(stat -c %Y "$candidate" 2>/dev/null || stat -f %m "$candidate")"
        if (( mtime >= newest_mtime )); then
          newest_mtime=$mtime
          newest="$candidate"
        fi
      done
      shopt -u nullglob
    else
      shopt -s nullglob
      for candidate in "$root"/*.apk; do
        mtime="$(stat -c %Y "$candidate" 2>/dev/null || stat -f %m "$candidate")"
        if (( mtime >= newest_mtime )); then
          newest_mtime=$mtime
          newest="$candidate"
        fi
      done
      shopt -u nullglob
    fi
  done

  if [[ -z "$newest" ]]; then
    echo "No $variant APK found under exports/ or app/build/outputs/apk/$variant/." >&2
    echo "Build one first:" >&2
    echo "  ./scripts/export-apk.sh" >&2
    echo "or install with a build step:" >&2
    echo "  ./scripts/install-apk.sh --build" >&2
    exit 1
  fi

  echo "$newest"
}

adb_path="$(resolve_adb_path || true)"
if [[ -z "$adb_path" ]]; then
  echo "adb not found. Install Android SDK Platform-Tools and either:" >&2
  echo "  - add platform-tools to PATH, or" >&2
  echo "  - set ANDROID_HOME to your SDK path" >&2
  exit 1
fi

echo "Using adb: $adb_path"

if [[ "$do_build" == "true" ]]; then
  export_script="$script_dir/export-apk.sh"
  if [[ ! -x "$export_script" && ! -f "$export_script" ]]; then
    echo "Export script not found: $export_script" >&2
    exit 1
  fi
  chmod +x "$export_script" 2>/dev/null || true

  export_args=(--variant "$variant")
  if [[ "$do_clean" == "true" ]]; then
    export_args+=(--clean)
  fi

  echo "Building $variant APK via export-apk.sh..."
  "$export_script" "${export_args[@]}"
fi

apk_path="$(resolve_apk_path)"
echo "APK: $apk_path"

wait_for_adb_device

if [[ "$uninstall_first" == "true" ]]; then
  echo "Uninstalling $package_id (if present)..."
  run_adb uninstall "$package_id" || true
fi

echo "Installing (adb install -r)..."
install_output="$(run_adb install -r "$apk_path" 2>&1 || true)"
echo "$install_output"

if ! grep -qE '^Success$' <<<"$install_output"; then
  echo "Install failed." >&2
  exit 1
fi

echo "Install complete."

if [[ "$do_launch" == "true" ]]; then
  echo "Launching $launcher_activity..."
  run_adb shell am start \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER \
    -n "$launcher_activity"
fi

echo
echo "Done. Device is ready with Dogan installed over USB debugging."
