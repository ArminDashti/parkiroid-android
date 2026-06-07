#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Build and export a Parkiroid APK to the exports folder.

Usage:
  ./scripts/export-apk.sh [--variant debug|release] [--output DIR] [--clean]

Options:
  --variant debug|release   Build variant (default: debug)
  --output DIR              Export folder (default: ./exports)
  --clean                   Run clean before assemble
  -h, --help                Show this help
EOF
}

variant="debug"
output_dir=""
clean="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --variant)
      variant="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
      shift 2
      ;;
    --output)
      output_dir="$2"
      shift 2
      ;;
    --clean)
      clean="true"
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
gradlew="$project_root/gradlew"

if [[ ! -x "$gradlew" ]]; then
  echo "Gradle wrapper not found or not executable: $gradlew" >&2
  exit 1
fi

if [[ -z "$output_dir" ]]; then
  output_dir="$project_root/exports"
fi

mkdir -p "$output_dir"

gradle_args=(--no-daemon)
if [[ "$clean" == "true" ]]; then
  gradle_args+=(clean)
fi

if [[ "$variant" == "release" ]]; then
  gradle_args+=(assembleRelease)
else
  gradle_args+=(assembleDebug)
fi

echo "Building ${variant} APK..."
(
  cd "$project_root"
  "$gradlew" "${gradle_args[@]}"
)

apk_dir="$project_root/app/build/outputs/apk/$variant"
if [[ ! -d "$apk_dir" ]]; then
  echo "APK output folder not found: $apk_dir" >&2
  exit 1
fi

source_apk="$(ls -t "$apk_dir"/*.apk 2>/dev/null | head -n1 || true)"

if [[ -z "$source_apk" || ! -f "$source_apk" ]]; then
  echo "No APK found in $apk_dir" >&2
  exit 1
fi

version_name="unknown"
build_gradle="$project_root/app/build.gradle.kts"
if [[ -f "$build_gradle" ]]; then
  version_name="$(grep -E 'versionName\s*=\s*"' "$build_gradle" | sed -E 's/.*versionName\s*=\s*"([^"]+)".*/\1/' | head -n1)"
  if [[ -z "$version_name" ]]; then
    version_name="unknown"
  fi
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
export_name="parkiroid-${variant}-v${version_name}-${timestamp}.apk"
destination_apk="$output_dir/$export_name"

cp "$source_apk" "$destination_apk"

echo
echo "Export complete."
echo "  Source:      $source_apk"
echo "  Destination: $destination_apk"
echo
echo "Install on a connected device:"
echo "  adb install -r \"$destination_apk\""

if [[ "$variant" == "release" && "$source_apk" == *unsigned* ]]; then
  echo
  echo "Note: Release APK is unsigned. Configure signing in app/build.gradle.kts"
  echo "      or sign manually before distributing."
fi
