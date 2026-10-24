# 测试策略与运行指南 (`docs/testing.md`)

本项目（`:app` 单模块，AGP 9.2.1 / 内置 Kotlin 2.3.10 / Hilt 2.60.1 / 100% Jetpack Compose）采用分层测试策略，覆盖纯 JVM 单测、Robolectric Compose 行为测试、Compose Preview 主机端截图测试以及 Dropshots 设备端截图测试。

---

## 1. 测试分层与目录结构

| 测试类型 | 源码位置 | 核心工具链 | 是否需要设备/模拟器 |
| :--- | :--- | :--- | :--- |
| **纯单元测试 & 契约测试** | `app/src/test/java/` | JUnit 4、`kotlinx-coroutines-test`、MockK、Rust JNI 宿主动态库 | 否（本地 JVM） |
| **Compose 行为测试（本地）** | `app/src/test/java/` | Robolectric (`sdk = 36`)、`compose-ui-test-junit4`、`HiltTestApplication`、`HiltTestActivity` | 否（本地 JVM） |
| **Compose Preview 截图测试** | `app/src/screenshotTest/kotlin/` | `com.android.compose.screenshot` (`0.0.1-alpha16`) | 否（本地 JVM 渲染） |
| **Instrumented & Dropshots 截图测试** | `app/src/androidTest/java/` | AndroidX Test、`HiltAndroidRule` (`STTestRunner`)、Dropshots (`0.6.0`) | 是（一致的设备/模拟器） |

---

## 2. 常用命令速查

### 2.1 运行本地单元测试（含 MockK 单测、契约测试与 Robolectric Compose 测试）

```bash
# 运行全部本地单元测试
./gradlew testDebugUnitTest

# 运行指定测试类
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.ui.screens.WorldPersonaSecretsViewModelsTest"
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.ui.screens.STChatListScreenTest"
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.ui.screens.STApiConnectionScreenTest"
```

> **说明**：所有 `Test` 任务默认依赖 `buildRustJniHost`，会自动在宿主机编译 `rust/st-JNI` 动态库并注入 `java.library.path`。Robolectric 首次运行会自动从 Maven 下载对应 API 36 的 `android-all` jar。

### 2.2 生成 JaCoCo 覆盖率报告

```bash
./gradlew jacocoTestReport
```

- HTML 报告输出：`app/build/reports/jacoco/jacocoTestReport/html/index.html`
- XML 报告输出：`app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`

### 2.3 Compose Preview 截图测试（主机端）

```bash
# 录制 / 更新参考截图
./gradlew updateDebugScreenshotTest

# 校验当前 UI 与参考截图是否一致
./gradlew validateDebugScreenshotTest
```

- **参考图目录**：`app/src/screenshotTestDebug/reference/`
- **差异报告目录**：`app/build/reports/screenshotTest/preview/debug/`
- **版本说明**：当前项目使用 AGP `9.2.1`（`< 9.5.0-alpha03`），因此采用独立插件 `com.android.compose.screenshot:0.0.1-alpha16` 并在 `gradle.properties` 中启用 `android.experimental.enableScreenshotTest=true`。该独立插件已标记废弃，后续若将 AGP 升级至 `9.5.0-alpha03+`，应迁移至 AGP 内置 `testSuites` DSL。

### 2.4 Instrumented 测试与 Dropshots 设备截图测试

```bash
# 仅编译 androidTest（无需设备）
./gradlew compileDebugAndroidTestKotlin

# 在连接的设备/模拟器上运行校验
./gradlew connectedDebugAndroidTest

# 录制 / 更新 Dropshots 设备端参考截图
./gradlew connectedDebugAndroidTest -Pdropshots.record
```

- **Dropshots 参考图目录**：`app/src/androidTest/screenshots/`
- **注意**：Dropshots 参考截图必须在与校验环境完全一致的模拟器配置（分辨率、DPI、系统镜像版本）下录制。

---

## 3. 编写规范与最佳实践

1. **Hilt 测试基建**：
   - 本地 Robolectric 测试通过 `app/src/test/resources/robolectric.properties` 自动配置 `application = dagger.hilt.android.testing.HiltTestApplication` 与 `sdk = 36`。
   - 设备端 `androidTest` 通过 `STTestRunner` 启动 `HiltTestApplication`。
   - Compose 测试统一使用 `@HiltAndroidTest` + `HiltAndroidRule(this)` (`order = 0`) + `createAndroidComposeRule<HiltTestActivity>()` (`order = 1`)。`HiltTestActivity` 定义于 `app/src/debug/` 源集并注册在 `src/debug/AndroidManifest.xml` 中，不污染 `main` 生产源码。
2. **Compose 语义匹配器优先**：
   - 优先使用 `onNodeWithText`、`onNodeWithContentDescription`、`hasClickAction` 等无障碍语义匹配器。
   - **仅当定位同一节点需要组合超过 3 个匹配器时**，才允许在生产组件上添加 `Modifier.testTag(...)`。
3. **状态恢复与多形态适配验证**：
   - 页面级瞬态 UI 状态（如筛选 chip、分栏模式）应使用 `rememberSaveable`，并通过 `composeRule.activityRule.scenario.recreate()` 验证配置变更后的状态恢复。
   - 使用 `DeviceConfigurationOverride`（`ForcedSize`、`FontScale`）测试宽屏（如 `900.dp`）与大字号（`1.5f`）下的布局健壮性。
4. **导航测试**：
   - 项目使用 Jetpack Navigation 3 (`STNavigator` + `STNavKey`)，在 `STNavigatorTest` 中验证返回栈入栈、去重、`switchRootTab` 与 `openChat` 折叠逻辑（项目未配置深链 deeplink）。
