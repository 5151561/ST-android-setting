# 里程碑 M3：内核迁移与核心稳定性规划

M3 阶段的焦点从单纯的业务原生化，全面转向了对应用**内核稳定性、容灾策略、平台合规以及生产级数据备份体系**的极限打磨。

---

## 1. 生产级备份、恢复与导入预检查

为了应对用户迁移数据时可能遇到的版本偏差和包破损，M3 对 `NodeBackup` 进行了重大安全增强。

### 1.1 `manifest.yaml` 指纹文件
在导出备份包时，App 内部会自动压入一个轻量的 `manifest.yaml` 元数据，用于追溯数据源：
```yaml
app_version: "v0.4.0-dev"
st_commit: "e3f41666c"
export_time: "2026-05-27T17:56:00Z"
data_size_bytes: 450123
has_secrets: true
```
* 记录了导出时的内核 Commit、App 构建版本、压缩时的大小指纹。
* 自动扫描 `secrets.json` 状态。若由于 SillyTavern 本地设置（`allowKeysExposure=false`）导致没有备份 API Key，会在清单中生成明显警告以提示用户。

### 1.2 零盲区导入预检查与覆盖确认（Overlay Checklist）
当用户点击“导入数据”并选中任意 zip/tar.gz 压缩包后，**App 绝对不会进行静默解压覆盖**，而是会开启临时内存解密沙箱：
1. **内容物扫描**：扫描是否存在 `settings.json`、`characters/`、`chats/`、`worlds/`、`groups/`、`User Avatars/`、`secrets.json` 等标志性 ST 目录。
2. **生成二次确认覆盖弹窗**：在屏幕上直观向用户列出拟覆盖的实体数量：
   * 角色数量：`XX` 个
   * 历史对话：`XX` 条
   * 世界书配置：`XX` 个
   * 是否带入 secrets API Key 等
3. **恶意覆盖拦截**：如果是多用户备份、或者格式损毁不含任何合法 ST 元数据的包，导入模块会直接强力拒绝并给出清晰原因，保护应用不进入黑洞状态。

---

## 2. 设置快照（Settings Snapshot）系统

为了防止上游内核同步升级时可能产生的配置格式冲突，M3 深度接入了 SillyTavern 本地的 Settings 快照 API。

* **功能路径**：“工具” -> **管理 ST (Manage ST)**。
* **核心动作**：
  * **一键创建快照**（`/api/settings/make-snapshot`）
  * **刷新与只读查看**（`/api/settings/get-snapshots` / `/api/settings/load-snapshot`）
  * **快照还原确认**（`/api/settings/restore-snapshot`）
* **快照回滚机制**：在执行大规模数据导入或上游 Git 同步前，App 会在 UI 强力弹窗建议**“先创建设置快照或导出完整备份”**，为用户留出绝对安全的后路。

---

## 3. 容灾与非主动退出恢复 (Crash Recovery)

当嵌入式的 Node.js 二进制包在运行中遭遇 OOM（内存溢出）、系统因能耗强杀、或者端口占用导致意外挂掉时，App 提供了一整套防白屏保护链：

* **非主动退出捕获**：`NodeService` 会对拉起的 native 进程进行 `waitForExitAsync` 挂载监听。一旦发现进程非人为异常中断，系统服务会捕获异常状态码并记录到 `service.log` 中：
  ```text
  [2026-05-28 01:50:00] unexpected exit: Node 异常退出，退出码 137
  ```
* **主屏降级处理**：首页卡片和内置 WebView 页面会自动感知状态扭转，切入 `ERROR` 异常卡片态，绝不展示系统底层黑屏或应用白屏。
* **一键重启与调试**：异常卡片会提供高亮按钮支持“一键重启 Node 服务”或“查看/导出诊断包”。

---

## 4. 端口主动探测与自动避让 (Port Conflict Resolution)

如果手机中其他应用（例如 Termux 开启的本地服务）占用了 SillyTavern 的默认端口，会导致 Node 服务启动陷入死循环崩溃。

* **主动嗅探**：在拉起 `NodeService` 前，App 会优先调用本地 Socket 网络探针，对目标配置端口进行非阻塞连接探测。
* **异常拦截**：若发现该端口早已处于 LISTEN 监视状态，Node 服务将**拒绝强制拉起**，直接进入 `ERROR` 状态。
* **引导防错**：页面会弹出说明弹窗告知用户发生了“端口被占用”，允许用户一键停止冲突占用或是在设置中无痛修改目标运行端口，从而避免启动死锁。

---

## 5. 日志与全脱敏诊断包导出 (Diagnostics Export)

当用户遇到使用难题希望向开发者提 Issue 或是自查问题时，可以通过“日志 (Logs)”页面一键导出诊断包。为了保护用户的极端隐私，诊断包采用了**最高等级的脱敏物理隔离规范**：

### 🛠️ 诊断包导出物理清单与脱敏规则

```
sillytavern-diagnostics-[date].zip
 ├── summary.yaml           # 运行状况摘要（脱敏端口、版本、App 运行态）
 ├── data-summary.yaml      # 数据量统计（例如：角色卡 15 张，不包含角色名字与内容）
 ├── config_redacted.yaml   # 脱敏后的 config.yaml
 ├── package.json           # 当前运行的内核依赖包清单
 └── logs/                  # 运行时日志目录
      ├── service.log       # 服务前台拉起/端口校验记录
      ├── stdout.log        # Node 服务标准输出日志
      └── stderr.log        # Node 服务错误输出日志
```

1. **零个人隐私数据泄露**：诊断包**绝对不会打包** `secrets.json`、`settings.json`、角色卡图片（`characters/`）、或者聊天历史 JSONL（`chats/`）。
2. **配置文件彻底脱敏**：
   * 自动抹除 `config.yaml` 里的任何个人鉴权字段或代理凭证。
   * **URL UserInfo 凭据二次脱敏**：编写了专门的脱敏过滤器（经 `DiagnosticsExportTest` 严密验证），若配置文件中包含代理服务器账户密码，例如：
     `socks5://my_username:my_pass@127.0.0.1:1080`
     将自动且不可逆地抹平为：
     `socks5://[redacted]@127.0.0.1:1080`
