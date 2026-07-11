# 研发文档索引

本目录保存 ST-android-setting 的研发档案、迁移方案、阶段进度和详细设计。面向用户和贡献者的完整阅读入口在 [Wiki 首页](../wiki/Home.md)；本文件负责说明 `docs/` 内每份材料的用途、状态和权威来源。

## 文档分工

| 位置 | 定位 | 读者 |
|---|---|---|
| [README.md](../README.md) | 项目简介、安装、构建和快速链接 | 新用户、首次访问者 |
| [wiki/](../wiki/Home.md) | 完整知识库，包含用户指南、开发者指南、架构概览和里程碑导航 | 用户、贡献者、维护者 |
| [docs/](README.md) | 研发档案，包含详细方案、迁移记录、审计记录和阶段进度 | 贡献者、维护者 |
| [out/test-reports/](../out/test-reports) | 一次性测试报告和真机验证记录 | 维护者 |

## 推荐阅读顺序

1. 新用户先看 [README.md](../README.md)，再看 [用户指南](../wiki/User-Guide.md)。
2. 开发者先看 [开发者指南](../wiki/Developer-Guide.md)，再看 [当前架构详版](architecture.md)。
3. 需要理解 Chat 原生化现状时，先看 [Native Chat Runtime Exit Status](native-chat-runtime-exit-status.md)，再按需回看历史迁移方案。
4. 需要追溯某个里程碑决策时，从 Wiki 里程碑页进入，再回到本目录阅读详细方案。

## 当前权威来源

| 主题 | 当前权威来源 | 补充或历史材料 |
|---|---|---|
| 项目总体架构 | [architecture.md](architecture.md) | [wiki/Architecture.md](../wiki/Architecture.md) 是面向阅读的概览 |
| Chat 当前运行时状态 | [native-chat-runtime-exit-status.md](native-chat-runtime-exit-status.md) | [native-chat-webview-exit-plan.md](native-chat-webview-exit-plan.md)、[chat-interface-migration.md](chat-interface-migration.md) 为历史方案 |
| Chat Phase 进度 | [native-chat-phase0-audit.md](native-chat-phase0-audit.md)、[native-chat-phase1-progress.md](native-chat-phase1-progress.md)、[native-chat-phase2-progress.md](native-chat-phase2-progress.md) | 用于追溯 TDD 和阶段验收记录 |
| 角色管理迁移 | [M2-character-management-migration.md](M2-character-management-migration.md) | [wiki/Milestone-M2-Characters.md](../wiki/Milestone-M2-Characters.md) 是摘要版 |
| 群聊迁移 | [group_chat_migration_plan.md](group_chat_migration_plan.md) | 当前作为群聊 REST 和原生生成路线记录 |
| M3 稳定性与源码迁移 | [M3-sillytavern-source-migration-plan.md](M3-sillytavern-source-migration-plan.md) | [wiki/Milestone-M3-Core-Stability.md](../wiki/Milestone-M3-Core-Stability.md) 是摘要版 |
| 产品范围和早期 PRD | [PRD-native-settings.md](PRD-native-settings.md) | 保留为产品定位、原生页面策略和里程碑源材料 |
| P3 UI 规格 | [P3-ui-design-spec.md](P3-ui-design-spec.md) | 用于 Reasoning、Tool Calls、Quick Replies 等 UI 细节追溯 |
| 设计原型落地评估 | [sillytavern-prototype-landing-report.md](sillytavern-prototype-landing-report.md) | 保留为原型与 Android Native 落地对照 |

## 文档状态

- **当前权威**：描述当前实现或当前维护口径，重复内容以它为准。
- **摘要版**：面向 Wiki 阅读，帮助快速理解；细节以对应 `docs/` 文档为准。
- **历史方案**：保留迁移背景、设计取舍和旧实现记录，不代表当前架构。
- **阶段进度**：保留 TDD、验收、审计和阶段收尾记录。

## 维护规则

1. 新增面向读者的说明优先放到 `wiki/`，再从 `README.md` 或本索引链接过去。
2. 新增详细方案、审计记录、阶段进度优先放到 `docs/`，并在本文件登记状态。
3. 如果 Wiki 与 `docs/` 重复，Wiki 写摘要和入口，`docs/` 保留细节和历史。
4. 废弃方案不要直接删除，先在文档开头标注“历史方案”并指向当前权威来源。
5. 移动文件前先检查 README、Wiki、docs 索引和测试报告里的链接，避免断链。
