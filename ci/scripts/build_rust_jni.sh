#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CRATE_DIR="${ROOT_DIR}/rust/tt-android-jni"
JNI_LIBS_DIR="${ROOT_DIR}/app/src/main/jniLibs"
DEFAULT_SRC_DIR="${ROOT_DIR}/TauriTavern/default"
DEFAULT_ASSET_DIR="${ROOT_DIR}/app/src/main/assets/tauritavern_default"

export PATH="/opt/homebrew/opt/rustup/bin:$HOME/.cargo/bin:/opt/homebrew/bin:$PATH"

MODE="${1:-all}" # host | android | all
HOST_PROFILE="${CARGO_HOST_PROFILE:-${CARGO_PROFILE:-debug}}"
ANDROID_PROFILE="${CARGO_ANDROID_PROFILE:-${CARGO_PROFILE:-release}}"

# 1. Stage TauriTavern/default into app/src/main/assets/tauritavern_default
if [ -d "${DEFAULT_SRC_DIR}" ]; then
  mkdir -p "${DEFAULT_ASSET_DIR}"
  rsync -a --delete "${DEFAULT_SRC_DIR}/" "${DEFAULT_ASSET_DIR}/" 2>/dev/null || cp -R "${DEFAULT_SRC_DIR}/." "${DEFAULT_ASSET_DIR}/"
fi

# 2. Build host library for JVM unit tests (macOS .dylib / Linux .so)
if [ "${MODE}" = "host" ] || [ "${MODE}" = "all" ]; then
  HOST_FLAG=""
  HOST_DIR="debug"
  if [ "${HOST_PROFILE}" = "release" ]; then
    HOST_FLAG="--release"
    HOST_DIR="release"
  fi
  echo "==> Building tauritavern_jni for host (${HOST_PROFILE})..."
  (cd "${CRATE_DIR}" && cargo build ${HOST_FLAG})
  if [ "${HOST_DIR}" = "debug" ]; then
    mkdir -p "${CRATE_DIR}/target/release"
    for ext in dylib so dll; do
      if [ -f "${CRATE_DIR}/target/debug/libtauritavern_jni.${ext}" ]; then
        cp -f "${CRATE_DIR}/target/debug/libtauritavern_jni.${ext}" "${CRATE_DIR}/target/release/libtauritavern_jni.${ext}"
      fi
    done
  fi
fi

# 3. Build Android JNI libraries if requested and NDK is available
if [ "${MODE}" = "android" ] || [ "${MODE}" = "all" ]; then
  CARGO_FLAG=""
  PROFILE_DIR="debug"
  if [ "${ANDROID_PROFILE}" = "release" ]; then
    CARGO_FLAG="--release"
    PROFILE_DIR="release"
  fi
  NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
  if [ -z "${NDK_ROOT}" ] && [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}/ndk" ]; then
    NDK_ROOT="$(ls -d "${ANDROID_HOME}"/ndk/* 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  if [ -z "${NDK_ROOT}" ] && [ -d "$HOME/android-sdk/ndk" ]; then
    NDK_ROOT="$(ls -d "$HOME/android-sdk/ndk"/* 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  if [ -z "${NDK_ROOT}" ] && [ -d "/opt/android-sdk/ndk" ]; then
    NDK_ROOT="$(ls -d /opt/android-sdk/ndk/* 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  if [ -z "${NDK_ROOT}" ] && [ -d "/opt/android-sdk-linux/ndk" ]; then
    NDK_ROOT="$(ls -d /opt/android-sdk-linux/ndk/* 2>/dev/null | sort -V | tail -n1 || true)"
  fi

  if [ -z "${NDK_ROOT}" ] || [ ! -d "${NDK_ROOT}" ]; then
    if [ "${MODE}" = "android" ]; then
      echo "ERROR: Android NDK not found. Set ANDROID_NDK_HOME." >&2
      exit 1
    else
      echo "WARNING: Android NDK not found; skipping Android target cross-compilation."
      exit 0
    fi
  fi

  export ANDROID_NDK_HOME="${NDK_ROOT}"
  API_LEVEL="${ANDROID_API:-26}"
  ANDROID_ABIS="${ANDROID_ABIS:-arm64-v8a}"

  HOST_TAG=""
  for candidate in darwin-arm64 darwin-x86_64 linux-x86_64 linux-aarch64; do
    if [ -d "${NDK_ROOT}/toolchains/llvm/prebuilt/${candidate}" ]; then
      HOST_TAG="${candidate}"
      break
    fi
  done

  if [ -z "${HOST_TAG}" ]; then
    echo "ERROR: Could not locate NDK LLVM prebuilt toolchain under ${NDK_ROOT}" >&2
    exit 1
  fi

  TOOLCHAIN_BIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}/bin"
  export PATH="${TOOLCHAIN_BIN}:${PATH}"

  build_abi() {
    local abi="$1"
    local rust_target=""
    local clang_prefix=""
    case "${abi}" in
      arm64-v8a)
        rust_target="aarch64-linux-android"
        clang_prefix="aarch64-linux-android${API_LEVEL}"
        ;;
      x86_64)
        rust_target="x86_64-linux-android"
        clang_prefix="x86_64-linux-android${API_LEVEL}"
        ;;
      *)
        echo "Unsupported ABI: ${abi}" >&2
        exit 1
        ;;
    esac

    echo "==> Building tauritavern_jni for Android ABI ${abi} (${rust_target}, API ${API_LEVEL})..."
    rustup target add "${rust_target}" >/dev/null 2>&1 || true

    local env_target
    env_target="$(echo "${rust_target}" | tr '-' '_')"
    local env_target_upper
    env_target_upper="$(echo "${env_target}" | tr '[:lower:]' '[:upper:]')"

    export "CC_${env_target}=${TOOLCHAIN_BIN}/${clang_prefix}-clang"
    export "CXX_${env_target}=${TOOLCHAIN_BIN}/${clang_prefix}-clang++"
    export "AR_${env_target}=${TOOLCHAIN_BIN}/llvm-ar"
    export "CARGO_TARGET_${env_target_upper}_LINKER=${TOOLCHAIN_BIN}/${clang_prefix}-clang"
    # Ensure 16KB ELF page size alignment for Android 15+
    export "CARGO_TARGET_${env_target_upper}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384"

    (cd "${CRATE_DIR}" && cargo build --target "${rust_target}" ${CARGO_FLAG})

    mkdir -p "${JNI_LIBS_DIR}/${abi}"
    cp -f "${CRATE_DIR}/target/${rust_target}/${PROFILE_DIR}/libtauritavern_jni.so" \
      "${JNI_LIBS_DIR}/${abi}/libtauritavern_jni.so"
  }

  IFS=',' read -ra ABI_ARRAY <<< "${ANDROID_ABIS}"
  for abi in "${ABI_ARRAY[@]}"; do
    build_abi "$(echo "${abi}" | xargs)"
  done
fi
