#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="st-android-build:local"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${ROOT_DIR}"

docker build -f ci/docker/Dockerfile -t "${IMAGE_NAME}" .

DOCKER_ENV_ARGS=()
if [ -n "${VERSION_NAME:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "VERSION_NAME=${VERSION_NAME}")
fi
if [ -n "${VERSION_CODE:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "VERSION_CODE=${VERSION_CODE}")
fi
if [ -n "${RELEASE_KEYSTORE_B64:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "RELEASE_KEYSTORE_B64=${RELEASE_KEYSTORE_B64}")
fi
if [ -n "${RELEASE_STORE_PASSWORD:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "RELEASE_STORE_PASSWORD=${RELEASE_STORE_PASSWORD}")
fi
if [ -n "${RELEASE_KEY_ALIAS:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "RELEASE_KEY_ALIAS=${RELEASE_KEY_ALIAS}")
fi
if [ -n "${RELEASE_KEY_PASSWORD:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "RELEASE_KEY_PASSWORD=${RELEASE_KEY_PASSWORD}")
fi
if [ -n "${GITHUB_REF_TYPE:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "GITHUB_REF_TYPE=${GITHUB_REF_TYPE}")
fi
if [ -n "${GITHUB_REF_NAME:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "GITHUB_REF_NAME=${GITHUB_REF_NAME}")
fi
if [ -n "${GITHUB_REF:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "GITHUB_REF=${GITHUB_REF}")
fi
if [ -n "${GITHUB_RUN_NUMBER:-}" ]; then
  DOCKER_ENV_ARGS+=(-e "GITHUB_RUN_NUMBER=${GITHUB_RUN_NUMBER}")
fi

docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "${ROOT_DIR}:/workspace" \
  -w /workspace \
  "${DOCKER_ENV_ARGS[@]}" \
  "${IMAGE_NAME}" \
  bash -lc '\
    set -euo pipefail; \
    export HOME="/workspace/.home"; \
    export CARGO_HOME="${CARGO_HOME:-/workspace/.cargo}"; \
    export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle}"; \
    export ANDROID_USER_HOME="/workspace/.android"; \
    unset ANDROID_SDK_HOME ANDROID_PREFS_ROOT; \
    mkdir -p "$HOME" "$CARGO_HOME" "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"; \
    touch "$ANDROID_USER_HOME/repositories.cfg" 2>/dev/null || true; \
    BUILD_MODE="debug"; \
    if [ -n "${RELEASE_KEYSTORE_B64:-}" ]; then \
      mkdir -p ci/keystore; \
      echo "$RELEASE_KEYSTORE_B64" | base64 -d > ci/keystore/release.jks; \
      chmod 600 ci/keystore/release.jks; \
      export RELEASE_STORE_FILE="/workspace/ci/keystore/release.jks"; \
      if [ -z "${RELEASE_KEY_PASSWORD:-}" ] && [ -n "${RELEASE_STORE_PASSWORD:-}" ]; then \
        export RELEASE_KEY_PASSWORD="$RELEASE_STORE_PASSWORD"; \
      fi; \
      BUILD_MODE="release"; \
    fi; \
    NDK_ROOT="${ANDROID_NDK_HOME:-}"; \
    if [ -z "$NDK_ROOT" ]; then \
      NDK_ROOT="$(ls -d /opt/android-sdk/ndk/* 2>/dev/null | head -n1 || true)"; \
    fi; \
    if [ -z "$NDK_ROOT" ]; then \
      NDK_ROOT="$(ls -d /opt/android-sdk-linux/ndk/* 2>/dev/null | head -n1 || true)"; \
    fi; \
    if [ -z "$NDK_ROOT" ] || [ ! -d "$NDK_ROOT" ]; then \
      echo "Android NDK not found in container"; \
      exit 1; \
    fi; \
    TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"; \
    if [ ! -d "$TOOLCHAIN" ]; then \
      echo "NDK toolchain not found at $TOOLCHAIN"; \
      exit 1; \
    fi; \
    export ANDROID_NDK_HOME="$NDK_ROOT"; \
    export ANDROID_API=26; \
    export ANDROID_ABIS="${ANDROID_ABIS:-arm64-v8a}"; \
    bash ci/scripts/build_rust_jni.sh android; \
    python3 ci/scripts/check_elf_align.py "$TOOLCHAIN/bin/llvm-readelf" \
      app/src/main/jniLibs/arm64-v8a/libtauritavern_jni.so; \
    GRADLE_JAVA_HOME="${JAVA_HOME:-}"; \
    if [ -z "$GRADLE_JAVA_HOME" ] || [ ! -x "$GRADLE_JAVA_HOME/bin/java" ]; then \
      GRADLE_JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"; \
    fi; \
    chmod +x ./gradlew; \
    if [ "$BUILD_MODE" = "release" ]; then \
      ./gradlew :app:assembleRelease -Dorg.gradle.java.home="$GRADLE_JAVA_HOME" --stacktrace --no-daemon; \
    else \
      ./gradlew :app:assembleDebug -Dorg.gradle.java.home="$GRADLE_JAVA_HOME" --stacktrace --no-daemon; \
    fi; \
  '

APK_PATH=""
if [ -f "${ROOT_DIR}/app/build/outputs/apk/release/app-release.apk" ]; then
  APK_PATH="${ROOT_DIR}/app/build/outputs/apk/release/app-release.apk"
elif [ -f "${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk" ]; then
  APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -z "${APK_PATH}" ]; then
  printf '\nAPK not found\n'
  exit 1
fi

# 计算版本标签（tag / VERSION_NAME），去掉前导 v
VERSION_LABEL="${VERSION_NAME:-}"
if [ -z "${VERSION_LABEL}" ] && [ -n "${GITHUB_REF_NAME:-}" ]; then
  VERSION_LABEL="${GITHUB_REF_NAME#refs/tags/}"
fi
VERSION_LABEL="${VERSION_LABEL#v}"

# 无论 release 还是 debug，都把产物复制到 out/ 供 CI 上传 / 发布
mkdir -p "${ROOT_DIR}/out"
if [[ "${APK_PATH}" == */release/* ]]; then
  BUILD_SUFFIX=""
else
  BUILD_SUFFIX="-debug"
fi
if [ -n "${VERSION_LABEL}" ]; then
  RELEASE_NAME="ST-android-${VERSION_LABEL}${BUILD_SUFFIX}.apk"
else
  RELEASE_NAME="ST-android${BUILD_SUFFIX}.apk"
fi
cp -f "${APK_PATH}" "${ROOT_DIR}/out/${RELEASE_NAME}"
printf '\nAPK: %s\n' "${ROOT_DIR}/out/${RELEASE_NAME}"
