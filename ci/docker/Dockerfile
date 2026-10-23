FROM ghcr.io/cirruslabs/android-sdk:36-ndk

ARG RUST_TOOLCHAIN=stable

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        python3 \
        rsync \
        pkg-config \
        libssl-dev \
        build-essential \
        xz-utils \
    && rm -rf /var/lib/apt/lists/*

ENV RUSTUP_HOME="/opt/rustup" \
    CARGO_HOME="/opt/cargo" \
    PATH="/opt/cargo/bin:${PATH}"

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y \
        --default-toolchain "${RUST_TOOLCHAIN}" \
        --profile minimal \
        --target aarch64-linux-android,x86_64-linux-android \
    && chmod -R a+rX "${RUSTUP_HOME}" "${CARGO_HOME}"

# Gradle 由项目的 ./gradlew wrapper 提供（版本见 gradle-wrapper.properties），
# 不在镜像里预装，避免与 wrapper 版本不同步。

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager "build-tools;34.0.0"

WORKDIR /workspace
