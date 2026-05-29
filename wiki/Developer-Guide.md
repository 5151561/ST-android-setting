# 开发者指南

本页面专为开发贡献者与高级极客编写，详细说明开发环境搭建、自动化编译、真实的 API 契约测试、真机调试以及如何安全地与 SillyTavern 上游源码进行同步的流程。

---

## 1. 环境与编译

### 1.1 开发前置要求
* **JDK 版本**：必须是 **JDK 17**。可在全局或 VS Code 偏好中通过 `org.gradle.java.home` 指定。
* **Android SDK**：支持 `minSdk = 26` (Android 8.0) 且 `targetSdk = 36` (Android 16+) 的最新 SDK。
* **构建宿主**：常规 Android APK 编译支持 Windows/macOS/Linux；但若要使用 Docker 交叉编译 Node.js 本身，必须在 Linux 宿主上进行。

### 1.2 常用构建指令
* **拉取子模块**：由于项目将 Node.js 源码与 SillyTavern 源码挂载为 Git Submodule，克隆后必须立即更新：
  ```bash
  git submodule update --init --recursive
  ```
* **一键编译 Debug APK**：
  ```bash
  ./gradlew assembleDebug
  ```
  编译完成后，输出产物位于：
  `app/build/outputs/apk/debug/app-debug.apk`

* **运行单元测试套件**：
  ```bash
  ./gradlew test
  ```
  单测试类执行：
  ```bash
  ./gradlew testDebugUnitTest --tests "io.github.sanitised.st.api.TavernCoreClientTest"
  ```

* **Docker 完整打包编译 (Linux)**：
  如果您修改了嵌入式 Node 运行内核，希望从 C++ 源码交叉编译出 Android arm64 适用的 `libnode.so`：
  ```bash
  ./ci/scripts/build_apk_docker.sh
  ```
  > [!TIP]
  > 首次全编译涉及 Node 源码环境初始化，通常耗时约 2 到 3 小时；后续构建基于缓存增量进行，速度会有极大提升。

---

## 2. 契约测试与真机调试

在传统的 mock 单元测试外，为了确保我们在安卓端开发的 API 适配层与 SillyTavern 真实的 Node.js 服务行为完全一致（字段无遗漏、序列化无错误），项目引入了**真实 ST 契约测试 (`TavernCoreRealContractTest`)**。

### 2.1 契约测试工作原理与 adb 转发
我们将物理测试手机与本地 PC 通过 adb 连接，利用端口转发让跑在本地电脑 JVM 里的单测用例直接去读写真机上真实拉起的 SillyTavern 服务：

```
+--------------------------+                  +-------------------------+
|        PC (JVM)          |                  |    Android Device       |
|                          |    adb forward   |                         |
|  TavernCoreContractTest  | ==============>  |  SillyTavern Service   |
|  (Tries localhost:18000) |                  |     (Port 8000)         |
+--------------------------+                  +-------------------------+
```

1. **启用手机的无线调试或 USB 调试**。
2. **建立 adb 端口映射**：
   通过 adb 将本地电脑的 `18000` 端口流量导向真机内部运行的 `8000` 端口。
   ```bash
   # 查看当前调试设备，获取设备标识
   adb devices
   # 绑定映射
   adb forward tcp:18000 tcp:8000
   ```

### 2.2 启动真实契约测试
为了确保普通全量编译或 CI 自动测试时不会误伤开发者的真实本地数据，契约测试在默认情况下是**静默跳过**的。当且仅当配置了环境变量 `ST_CONTRACT_BASE_URL` 时，它才会真正工作：

```bash
# 在 adb forward 建立后，执行契约测试
ST_CONTRACT_BASE_URL=http://127.0.0.1:18000/ ./gradlew testDebugUnitTest --tests 'io.github.sanitised.st.api.TavernCoreRealContractTest'
```

### 2.3 安全与沙箱保真设计
测试套件拥有非常完善的自清理逻辑，以确保测试不会污染真机用户的日常资产：
* **命名隔离**：测试中新建的所有临时角色卡均前缀为 `STContract_*`。
* **数据保真验证**：测试专门读取了一个高度复杂的外部未知字段 JSON 属性，验证 multipart 保存上传后，这些非 ST 识别属性在后端保存时依然保真（不丢失、不损坏）。
* **清理收尾**：用例在 `tearDown` 周期中会自动删除所有 `STContract_*` 临时角色，并将 tag settings 等全局配置文件物理性还原。

---

## 3. 上游同步与发布流程

SillyTavern 的前端和接口演进非常迅速。为了避免更新子仓库导致应用崩溃，在每一次**同步子模块**或**准备发布 Release 包**前，必须严格依照以下清单进行合规检查与回归：

### 📋 发布与同步回归清单

| 阶段步骤 | 执行动作 | 验收标准与防线 |
|---|---|---|
| **1. 基线记录** | 登记当前 App 的 commit hash、SillyTavern 的旧/新 commit，以及计划升级的 App versionCode。 | 建立回滚锚点。 |
| **2. 同步源码** | 执行子模块更新或拉取指定 tag；如果 `package-lock.json` 发生重大变动，须重新导出依赖清单。 | 子仓库状态干净（无本地修改）。 |
| **3. 接口审计** | 复核 ST `src/endpoints/` 目录下的核心文件变化：<br>- `settings.js` 的 parse/save 参数形态<br>- `worldinfo.js` 与 `avatars.js` 的接口定义变化。 | 若入参/响应属性改名，须同步重构 `TavernCoreClient`。 |
| **4. 自动化回归** | 本地执行全量单测与打包：<br>`./gradlew testDebugUnitTest assembleDebug` | 全部单元测试无红通过，Debug 包构建无错误。 |
| **5. 真实契约测试** | 按本页第 2 节方式连接手机，跑通 `TavernCoreRealContractTest`。 | 临时卡创建、保存、头像替换等真实端点返回 200。 |
| **6. 容灾与诊断回归** | 启动 Node 后，通过 adb 手动强杀 `libnode.so`。检查日志页是否成功显示“非主动退出异常（Code 137）”，导出诊断 `.zip` 包。 | 诊断包内必须不包含 `secrets.json` 或用户私密数据，且 config userinfo 敏感信息已完成脱敏。 |
| **7. 数据恢复 smoke** | 卸载 App 并干净安装，尝试导入上一步导出的 App 完整备份包。同时下载一个官方 UI 导出的备份，验证导入预检查的“覆盖清单”是否精确识别 settings、characters 和 worlds 数量。 | 识别成功；对无法还原的多用户包自动拦截并提示。 |
| **8. WebView smoke** | 启动 Chat WebView，确认 health check 轮询通过、最近聊天与最近角色展示无阻、网页文件选择器能够调起原生图片裁剪。 | 聊天发送与 SSE 流式生成工作正常。 |
| **9. 协议合规检查** | 检查 App 的“法律信息”页面。确认 **AGPL-3.0 许可证**、Node.js 及主要前端库版权声明正常展示。 | 确保分发许可无诉讼风险。 |

> [!CAUTION]
> **强力回滚机制**：以上 9 个步骤中若有**任何一项**未通过（例如发现上游接口改版导致契约单测报错，或者强杀服务后无法导出脱敏诊断），**必须无条件回滚** `SillyTavern` 子模块至上一次可发布的安全 commit，并阻断发布流程，直至相关适配代码修改完毕并重新通过回归。
