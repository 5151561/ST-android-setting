FROM ghcr.io/cirruslabs/android-sdk:36-ndk

ARG NODE_VERSION=24.13.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        python3 \
        ninja-build \
        make \
        g++ \
        xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" -o /tmp/node.tar.xz \
    && tar -xJf /tmp/node.tar.xz -C /opt \
    && ln -s "/opt/node-v${NODE_VERSION}-linux-x64" /opt/node \
    && rm /tmp/node.tar.xz

ENV PATH="/opt/node/bin:${PATH}"

# Gradle 由项目的 ./gradlew wrapper 提供（版本见 gradle-wrapper.properties），
# 不在镜像里预装，避免与 wrapper 版本不同步。

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager "build-tools;34.0.0"

WORKDIR /workspace
