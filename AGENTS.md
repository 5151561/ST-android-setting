# AGENTS.md

请始终使用中文交流。

## 项目概览与架构指南

- 项目详细说明、架构分层与开发约定请参见 [CLAUDE.md](CLAUDE.md) 与 [docs/architecture.md](docs/architecture.md)。
- 原生聊天运行时退出 WebView 的状态说明请参见 [docs/native-chat-runtime-exit-status.md](docs/native-chat-runtime-exit-status.md)。

## 测试策略与验证

- 完整测试栈说明（Version Catalog、MockK 单测、Robolectric Compose 行为测试、Compose Preview Screenshot Testing、Dropshots、JaCoCo 覆盖率报告）请参见 [docs/testing.md](docs/testing.md)。
- 常用测试命令：
  - `./gradlew testDebugUnitTest` — 运行全部本地单测与 Robolectric Compose 测试
  - `./gradlew updateDebugScreenshotTest` / `./gradlew validateDebugScreenshotTest` — 更新/校验 Compose Preview 截图测试
  - `./gradlew jacocoTestReport` — 生成 JaCoCo 单元测试覆盖率报告
