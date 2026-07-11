#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Build and export a Dogan APK to the exports folder.

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

get_java_major_version() {
  local java_exe="$1"
  local version_line
  version_line="$("$java_exe" -version 2>&1 | head -n1)"
  if [[ "$version_line" =~ version\ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  if [[ "$version_line" =~ version\ \"1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  echo 0
}

resolve_gradle_java_home() {
  local candidates=()
  local candidate java_exe major

  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    candidates+=("$JAVA_HOME")
  fi

  local search_roots=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/opt/android-studio/jbr"
    "/usr/lib/jvm"
    "/usr/local/opt/openjdk"
    "/usr/local/opt/openjdk@17"
    "/usr/local/opt/openjdk@21"
  )

  local root
  for root in "${search_roots[@]}"; do
    if [[ -x "$root/bin/java" ]]; then
      candidates+=("$root")
      continue
    fi
    if [[ -d "$root" ]]; then
      while IFS= read -r candidate; do
        candidates+=("$candidate")
      done < <(find "$root" -mindepth 1 -maxdepth 1 -type d 2>/dev/null)
    fi
  done

  for candidate in "${candidates[@]}"; do
    java_exe="$candidate/bin/java"
    if [[ ! -x "$java_exe" ]]; then
      continue
    fi
    major="$(get_java_major_version "$java_exe")"
    if [[ "$major" -ge 11 ]]; then
      echo "$candidate"
      return 0
    fi
  done

  return 1
}

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

gradle_java_home="$(resolve_gradle_java_home || true)"
if [[ -z "$gradle_java_home" ]]; then
  echo "No Java 11+ runtime found. Android Gradle Plugin 8.5 requires JDK 11 or newer." >&2
  echo "Install a JDK or set JAVA_HOME to a Java 11+ installation." >&2
  exit 1
fi

if [[ "${JAVA_HOME:-}" != "$gradle_java_home" ]]; then
  echo "Using Java from: $gradle_java_home"
  export JAVA_HOME="$gradle_java_home"
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
export_name="dogan-${variant}-v${version_name}-${timestamp}.apk"
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
