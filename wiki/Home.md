# ST-android-setting Wiki 知识库

> [!NOTE]
> 本 Wiki 是项目的完整阅读入口。`README.md` 负责快速介绍和安装构建，`wiki/` 负责用户指南、开发者指南、架构概览和里程碑导航，`docs/` 保存更细的研发档案与历史方案。

ST-android-setting 是一个第三方 SillyTavern Android 客户端。它在设备本地运行嵌入式 Node.js + SillyTavern 服务端，并用 Jetpack Compose 与 Material 3 构建原生移动端体验。当前聊天路径已经完成 Native runtime exit：App 内聊天不再依赖隐藏 WebView runtime，当前实现以原生 `NativeChatScreen` 和 `NativeChatEngine` 为主。

---

## 项目愿景

* **开箱即用**：APK 内置 Node.js 运行时和 SillyTavern 源码，用户无需 Termux 即可在 Android 设备上启动本地服务。
* **原生移动体验**：首页、聊天、角色、工具、设置等页面逐步由 Compose 原生界面承接。
* **本地优先和隐私优先**：聊天、角色、设置都保存在本地；项目不内置遥测或分析上报。
* **可维护迁移**：保留 SillyTavern 后端能力，通过原生 API、契约测试和阶段文档追踪迁移边界。

---

## 快速入口

| 想了解什么 | 推荐入口 | 说明 |
|---|---|---|
| 安装、启动、迁移数据 | [用户与数据迁移指南](User-Guide) | 面向最终用户，覆盖 APK 安装、备份导入、自定义版本和后台保活 |
| 编译、测试、发布 | [开发者指南](Developer-Guide) | 面向贡献者，覆盖构建命令、真机契约测试、上游同步和发布检查 |
| 当前技术架构 | [技术架构与设计规范](Architecture) | Wiki 摘要版；详细实现以 `docs/architecture.md` 为准 |
| Chat 原生化现状 | [Chat 原生化技术规划](Milestone-Chat-Migration) | Wiki 导航页；当前状态以 `docs/native-chat-runtime-exit-status.md` 为准 |
| 角色管理迁移 | [M2 原生角色管理](Milestone-M2-Characters) | 角色列表、编辑、头像、聊天文件管理的原生化摘要 |
| 内核稳定性规划 | [M3 内核与稳定性](Milestone-M3-Core-Stability) | 备份恢复、诊断导出、端口避让和容灾能力摘要 |
| 研发档案索引 | [docs/README.md](https://github.com/5151561/ST-android-setting/blob/main/docs/README.md) | 详细方案、历史迁移记录和权威来源说明 |

---

## 推荐阅读路径

### 普通用户

1. 阅读 [README](https://github.com/5151561/ST-android-setting/blob/main/README.md) 了解项目定位和安装入口。
2. 阅读 [用户与数据迁移指南](User-Guide) 完成安装、首次启动和旧数据迁移。
3. 如需长时间后台聊天，阅读 [后台电池保活](User-Guide#4-电池保活与后台优化)。

### 开发者

1. 阅读 [开发者指南](Developer-Guide) 准备 JDK、Android SDK 和常用构建命令。
2. 阅读 [技术架构与设计规范](Architecture) 建立整体概念。
3. 阅读 [docs/architecture.md](https://github.com/5151561/ST-android-setting/blob/main/docs/architecture.md) 获取当前详细架构。
4. 修改 SillyTavern API 相关代码前，阅读真机契约测试章节和对应迁移文档。

### 维护者

1. 从 [docs/README.md](https://github.com/5151561/ST-android-setting/blob/main/docs/README.md) 确认当前权威文档。
2. 改动 Chat 路径前先读 [Native Chat Runtime Exit Status](https://github.com/5151561/ST-android-setting/blob/main/docs/native-chat-runtime-exit-status.md)。
3. 更新历史迁移方案时，在文档开头标注当前口径和历史状态，避免读者误把旧方案当成当前实现。

---

## 文档维护约定

* Wiki 写完整入口和可读摘要，帮助读者快速找到正确材料。
* `docs/` 写详细研发方案、审计记录、阶段进度和历史方案。
* 同一主题在 Wiki 和 `docs/` 同时存在时，Wiki 应指向当前权威来源，不重复维护大量细节。
* 过时方案保留为历史记录，但必须在开头说明当前口径。

---

## 如何同步本 Wiki

本目录采用 GitHub Wiki 兼容的扁平结构。如果拥有项目 Wiki 仓库写权限，可以按以下步骤同步：

1. 克隆项目 Wiki 仓库：

   ```bash
   git clone https://github.com/5151561/ST-android-setting.wiki.git
   ```

2. 将本项目 `wiki/` 目录下的所有文件覆盖复制到刚才克隆的 Wiki 根目录。

3. 提交并推送：

   ```bash
   git add .
   git commit -m "docs: sync repo wiki to github"
   git push origin master
   ```
