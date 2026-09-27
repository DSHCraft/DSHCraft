# DSHCraft - Manual Smoke Tests

## 2026-09-27 实例选择本地 DSH Core

- 自动化已执行：缓存扫描仅返回完整版本目录，缺少 CLI 的目录和 `.installing-*` 暂存目录不进入列表；全量 HMCL 测试通过。
- 窗口待测：实例编辑页点击“选择已下载 DSH 版本”，确认只显示本地缓存版本；选中后实例版本字段更新。点击“浏览 DSH 版本”应进入在线目录，安装后返回本地选择器可见该版本。

## 2026-09-27 DSH 实例交付回归

- 自动化已执行：完整 HMCL 单元测试通过；Core `0.1.5-rc.3` 隔离安装与自检通过；生产启动命令实际启动 Web Profile 并输出 loopback URL；删除目录测试确认只清理 `dsh-home`、保留 Workspace 和相邻实例。
- 窗口待测：在本轮最新预览中新增一个 `web` 实例并启动，确认 Core 安装后进入控制台、输出 URL；点击停止确认 URL 失效；删除实例时确认提示说明 DSH_HOME 将删除且 Workspace 保留；确认后检查只删除该实例 DSH_HOME。

## 2026-09-27 首次实例启动回归

- 已执行：Core `0.1.5-rc.3` 安装到隔离临时 runtime，DSh `--help` 自检成功；内置 Web Profile 首次启动后输出 `127.0.0.1` loopback URL。真实 Java DshModServiceTest 套件通过；本地运行命令、URL 与凭据日志回归通过。
- 待手测：使用新构建的 HMCL 窗口，创建默认 `web` Profile 的新实例并点击启动，确认 Core 安装完自动打开控制台、日志显示 URL，停止可释放端口。原先已打开的预览 JAR 是旧构建，需重启新版。

## 2026-09-27 Java/HMCL 实例进程停止

- [ ] 启动一个已安装 Core 的实例，确认控制台显示 stdout/stderr、运行状态和本机 Web URL。
- [ ] 点击停止，确认子进程/父进程退出、运行状态变为停止、Web 打开按钮失效且日志显示停止完成。
- [ ] 再启动后删除当前实例，确认实例从列表移除且 DSH 进程退出；Workspace 文件保持不变。
- [ ] 停止一个已自然退出的实例，确认操作不会报错，旧 Web URL 不再可用。

## 2026-09-27 签名更新子进程交接

- 已执行：离线替换/交接测试 7 项与签名清单测试 4 项均通过、0 跳过/失败；覆盖可信旧启动器副本和签名材料暂存、助手启动失败清理、替换后重启失败恢复旧文件。增量 EXE 构建通过。
- 已执行：对重建 Windows EXE 通过 `java -jar` 实际传入完整 `--apply-dshcraft-update` 参数；因缺 DSHCraft 生产公钥而在文件操作前拒绝，目标文件不存在。
- 待完成：从现有 HMCL 设置页触发父进程退出与助手交接，使用生产公钥和真实 GitHub Release 完成更新/重启/回滚验收。测试密钥只存在于一次性单元测试中，没有发布。

## 2026-09-27 签名文件离线替换与回滚

- 已执行：`DshCraftUpdateInstallerTest` 3 项通过、0 跳过/失败。临时目录里使用一次性签名密钥验证：成功替换保留旧文件、备份后故障恢复旧文件、错误签名不改动目标。增量 `:HMCL:makeExecutables --offline` 通过。
- 待完成：把已签名文件的离线替换接入运行中的 DSHCraft 更新入口，验证 Windows 正在运行的 EXE 退出后的替换、重启和失败回滚；目前没有生产公钥或真实 GitHub Release。不能把临时文件测试当作正式自动更新验收。

## 2026-09-27 签名更新下载链路

- 已执行：`DshCraftSignedUpdateTest` 4 项通过、0 跳过/失败。新增本机 HTTP 测试端点完成清单/签名获取、验签、文件下载与 SHA-256 比对；篡改文件被拒绝且最终目标不存在。增量 `:HMCL:makeExecutables --offline` 通过。
- 待完成：生产公钥、真实 GitHub Release 签名源、窗口内更新入口、安装/回滚和合法签名安装实测。当前只是已验证的下载模块，没有自动安装权限或完成态。

## 2026-09-27 DSHCraft 签名更新校验基础

- 已执行：`DshCraftSignedUpdateTest` 3 项通过、0 跳过/失败；一次性 Ed25519 密钥验证正确清单与文件，清单篡改、错误公钥、错误仓库地址和文件篡改均被拒绝。增量 `:HMCL:makeExecutables --offline` 通过。
- 待完成：生产公钥内置、签名清单和文件的在线获取、安装/回滚及合法签名安装实测。当前没有生产密钥，缺钥时校验模块直接拒绝；不能把这些单元测试说成自动更新已完成。

## 2026-09-27 旧 HMCL 更新入口隔离

- 已执行：`DshCraftUpdateIsolationTest` 两项通过、0 跳过/失败。直接给 DSHCraft 传入 `--apply-to` 不会覆写一次性目标文件；正常启动路径在处理 HMCL 旧版迁移/强制更新文件前退出该逻辑。增量 `:HMCL:makeExecutables --offline` 通过。
- 已执行：用重编译的 Windows EXE 实际传入 `--apply-to` 和一次性目标文件；进程在 20 秒内以退出码 0 结束，目标 SHA-256 前后相同。此项仅验证旧入口拒绝，不验证新更新安装。
- 待完成：DSHCraft 自己的生产公钥、签名更新源和合法签名安装实测。这个修复只阻断旧 HMCL 更新链路，不能当作自动更新已实现。

## 2026-09-27 二进制依赖许可检查

- 已执行：离线读取 `:HMCL:dependencies --configuration runtimeClasspath`，逐项核对本机缓存 POM 的许可字段；只读检查当前 shaded JAR 中 ANTLR、NanoHTTPD、CommonMark、JNA 的类与许可入口。详见 `BINARY_LICENSE_AUDIT.md`。
- 已补入上游 ANTLR 4.11.1 对应许可文本；增量 `:HMCL:makeExecutables --offline` 通过，重建 JAR 中的文本与源码文件换行规范化后完全一致。仍需核对其余压缩后实际重分发的库、原生组件及声明。依赖树和 POM 字段不能单独证明二进制发布合规；没有发布或推送。

## 2026-09-27 本地 stdio MCP

- 已执行：新增真实隔离 Core `0.1.5-rc.2` 测试，用启动器参数连接一次性 Node stdio 服务；服务收到 MCP `initialize`。首次运行发现 Windows 模块回退加载器被子进程继承但缺环境变量时崩溃；修复后仅重跑该用例通过。先前同组其余 8 项已通过，其中包含 Core 配置组合与 HTTP Bearer 握手。
- 待验证：真实 HMCL 窗口添加/启用/停用 stdio MCP 的点击路径，以及具体第三方 MCP 服务的工具调用。测试不下载或执行第三方 MCP 包；当前产物仍未生产签名。

## 2026-09-26 MCP Bearer Token 与真实握手

- 已执行：隔离 Core `0.1.5-rc.2` 使用启动器生成的参数和覆盖层启动，向本机 MCP 测试端点发送了正确的 `Authorization: Bearer` 请求头；此前发现并修复 `--patch` 放在 `--port` 后会被 Web 子命令拒绝的问题。
- 已执行：MCP 6 项、系统凭据 4 项、仓库/输出 6 项定向测试均通过，0 失败/跳过；包括 Provider 与 MCP 凭据目标隔离、配置导出只保留环境变量引用、控制台同时脱敏两类密钥。增量 `makeExecutables` 成功，但缺生产签名密钥。
- 待验证：真实 HMCL 窗口保存/删除 Token、连接/断开 MCP 的点击路径和真实第三方服务器工具调用。只实现 Bearer HTTP，stdio 与其他认证方式未完成。

## 2026-09-26 Java/HMCL MCP 实例覆盖层

- 已执行：`AgentMcpServiceTest` 共 4 项通过，含真实隔离 Core `0.1.5-rc.2 --dump-config` 验证生成的 MCP 客户端行与 URL 被 DSH 解析；覆盖实例隔离、无凭据 URL 校验和防止覆盖外部编辑。
- 已执行：后续一次定向 MCP/Skill 测试与 EXE 增量构建通过。新 EXE 能启动并暴露 DSHCraft JavaFX 窗口及首次使用声明；桌面自动化在唤起该窗口时连续返回 `failed to activate captured window`，按 computer-use 恢复规则停止，未继续盲点按钮。此轮不能宣称窗口点击通过。
- 待验证：真实 HMCL 窗口中的连接/断开点击、可用 MCP 服务器握手与工具调用。当前只支持无凭据 Streamable HTTP；stdio 和带凭据的服务器还未完成。

## 2026-09-26 Java/HMCL 技能导入

- 已执行：`AgentSkillServiceTest` 定向测试通过，覆盖实例隔离、重复安装拒绝、缺失或无效 SKILL.md；`makeExecutables` 增量构建通过。
- 待验证：在真实 HMCL 窗口中从「下载 → 技能」选择文件夹后，启动所选 DSH 实例并确认技能进入目录。当前只支持本地技能目录导入，不代表在线技能市场或 MCP 安装完成。

## 2026-09-26 Java 包内许可声明

- 已执行：使用既有 Gradle 缓存增量构建 `:HMCL:shadowJar --offline` 与 `:HMCL:makeExecutables --offline` 成功；检查 JAR 内 `META-INF/dshcraft/` 下 GPL、项目声明、第三方声明和 JFoenix MIT 文本 4 项均存在且非空。
- 已执行：重新生成源码预备 ZIP，共 1671 项，包含 JFoenix MIT 文本；扫描未发现生成目录或可执行文件。仓库远端目前为空，未推送或发布。
- 待验证：逐一核对其余实际重分发依赖的许可证及通知文本；正式签名与 Windows 桌面图标缓存刷新后的显示。此项不改变界面。

## 2026-09-26 图标与源码开源包

- 已执行：原方形图标底边中央不透明，确认资源本身被裁切；新图 1254×1254 底边透明。增量 `:HMCL:makeExecutables` 通过，提取的 Windows EXE 48×48 图标底边中央透明。
- 已执行：源码预备 ZIP 共 1669 个条目，含 GPL 许可证、Gradle wrapper、新图标和第三方声明，不含构建目录或嵌套上游发布工作流。敏感标记/本机路径针对性扫描无命中。
- 待验证：Windows 任务栏/桌面缓存刷新后的肉眼效果；完整二进制依赖许可清单、生产签名和正式 Release 发布。源码 ZIP 是预备包，不代表这些门槛已经通过。

## 2026-09-26 Java/HMCL API 接入

- 已执行：针对 Provider 配置的 5 项单元测试通过，覆盖协议切换更新托管 YAML、手工编辑保护与凭据环境变量引用；之后一次增量 `:HMCL:makeExecutables` 成功。构建提示缺少签名密钥，不能作为正式签名版发布。
- 待验证：真实 HMCL 窗口逐个选择预设、系统凭据输入、生产服务端模型发现/请求；旧版非托管 `settings.yaml` 的迁移提示需人工确认。没有重复运行整套测试。

## 2026-09-26 Java/HMCL 整合包提交失败清理

- 已执行：`AgentPackServiceTest` 与完整 `:HMCL:build` 通过；测试确认失败清理只删除新实例目录，不删除相邻实例，路径穿越 ID 被拒绝。
- 已执行：使用已安装的隔离 Core `0.1.5-rc.2`，一次性运行真实 DSH Profile 整合包集成测试，5 项通过、0 跳过、0 失败；包含外部包成功安装及不存在版本失败清理。
- 待验证：真实 Java/HMCL 窗口的整合包最终校验失败提示和清理路径。构建提示缺少签名密钥，本轮产物不是已签名正式版。

## 2026-09-26 HMCL-only UI cleanup

- 已执行：关闭 Tauri 测试窗口；Java/HMCL 预览成功启动（PID 2792），桌面启动入口现指向 Java/HMCL。React/Tauri 界面源码和启动入口已移除，清理前已留可恢复源码归档。
- 已执行：本轮 `:HMCL:build` 成功；检查确认 React/Standalone UI 源码 0 个、Tauri 进程 0 个、旧 UI 启动入口 0 个，Java 预览仍在运行。
- 待验证：实例、下载、插件、Provider、整合包等按钮的真实点击。旧的 npm/Tauri 测试历史记录仅供追溯，不代表当前桌面产品验收。

> 核心原则：只把真正执行过的项目标记为通过。浏览器测试与桌面 E2E 分开记录。

## 2026-09-26 Provider 系统凭据链路

- 已执行：两项可选 Windows 系统凭据库测试使用一次性 ID/测试 Key，保存、读取、覆盖、删除均通过；本机 HTTP 模型发现测试确认选中 Provider 的 Key 从凭据库进入请求，另一个 Provider 的 Key 没有进入请求。测试结束清理凭据。无头桌面 IPC 模拟点击测试确认后端返回 `ok: false` 时不把凭据标记为已保存，也不误删 Provider。
- 已执行：`npm run test:ui`、`npm run check`、`npm run test:unit`（14 项 Pack/Provider/browser、4 项 updater）、`npm run build`、`cargo check`、`cargo test --lib`（23 项通过、8 项可选默认跳过）、`cargo fmt -- --check`。
- 待验证：当前真实 Tauri 窗口输入密钥后的保存、切换、模型探测与启动流程；外部生产 API 仍未联调。本轮未打开桌面窗口或更动视觉布局。

## 2026-09-26 API 端点预设

- 已执行：`npm run check`、`npm run test:unit`（14 项 Pack/Provider/browser、4 项 updater）、`npm run build`、`npm run test:ui`。无头点击测试确认官方 Responses 预设固定官方 URL、OpenRouter Responses 预设填入正确地址与协议、切换预设清空尚未保存的 Key、含 URL 凭据的自定义地址被拒绝且不写入状态。
- 待验证：真实桌面窗口中的系统凭据保存/读取、DeepSeek 官方 Chat/Responses 以及其他生产 API 的实际 DSH 请求；不要把预设地址正确等同于所有模型/工具兼容。此轮未打开桌面窗口，未改 HMCL/Java 布局。

## 2026-09-26 整合包回滚目录边界

- 已执行：Rust 临时文件系统测试调用生产使用的删除函数，确认只清理目标实例，重复清理安全，`../workspace` 被拒绝，邻近实例与独立 Workspace 文件不受影响。另运行本机 Core `0.1.5-rc.2` 的可选真实 DSH 测试：临时 Profile 安装不存在的本地插件实际失败并留下部分目录，回滚后目标目录消失而 Workspace 保留。`cargo fmt -- --check`、`cargo check`、`cargo test --lib` 通过（23 项常规测试、6 项可选真实 DSH 测试默认跳过；上述可选测试单独通过）。
- 待验证：当前真实 Tauri 窗口中整合包插件安装失败后的目录清理、日志和 Launcher 状态一致性；本轮没有启动桌面 UI。

## 2026-09-26 整合包导出可移植性

- 已执行：单元测试验证本地 `file:` Mod、缺失 Mod 元数据会阻止导出，公开 npm Package Spec 可导出；`npm run check`、`npm run test:unit`（11 项 Pack/browser、4 项 updater）、`npm run build`、`npm run test:ui` 通过。无头浏览器的正常导出按钮路径仍通过；未打开桌面窗口或改动 HMCL 布局。
- 待验证：真实桌面窗口中尝试导出含本地路径的 Mod，确认不产生文件且显示可理解的失败日志；随后导出公开 npm Mod 并在独立实例导入、安装。

## 2026-09-26 整合包 Mod 身份冲突

- 已执行：`npm run check`、`npm run test:unit`（10 项 Pack/browser、4 项 updater）、`npm run build`、`npm run test:ui`。无头浏览器验证正常 `.dshpack` 可导入并创建配置模板；伪造内置 `filesystem` 的同 ID、不同 package spec 元数据及不存在的“内置 Mod”均被拒绝，未写入整合包。无安装包 spec 的新增外部 Mod 在单元测试中被拒绝。桌面 UI 未启动，HMCL 布局未修改。
- 待验证：当前真实桌面窗口中导入含合法外部 Mod 的 Pack、冲突报错日志与安装回滚。无头浏览器验证不能替代实际桌面验收。

## 2026-09-26 签名更新本地验证

- 已执行：不带 `v` 的 Release 标签清单链接回归测试；`npm run test:updater-signature:e2e` 使用一次性密钥签名小型测试文件，独立验证正确签名，并拒绝篡改内容、错误公钥与错误版本。测试脚本清理临时密钥和文件。
- 已执行：`npm run check`、`npm run test:unit`（7 项 Pack/browser、4 项 updater）、`npm run build`、`cargo check`、Rust 常规 22 项、格式检查与 HMCL UI 基线 247/247 通过。
- 未完成：仓库生产签名密钥/公钥配置、正式 Release 的各平台签名产物和发布清单、由旧安装版本到新版本的合法更新安装与重启验收。本地小文件签名测试不等于安装器更新通过。

## 2026-09-26 Pack 元数据和 Plugin 隔离生命周期

- 已执行：`.dshpack` 与 Launcher 状态共用的插件 spec 过滤测试，覆盖 URL 凭据片段、pnpm 选项与公开 Git 引用；Provider URL 片段在状态保存前被移除。`npm run test:unit` 的 10 项测试通过。
- 已执行：可选 Rust 集成测试在临时 `DSH_HOME` 中使用本机 Core `0.1.5-rc.2` 和 pnpm `11.21.0` 实际添加/移除 `is-number@7.0.0`，Profile `package.json` 依赖随操作变化。普通 Rust 22 项通过，另 5 项可选集成测试默认跳过；`cargo check`、格式检查、`npm run check`、`npm run build`、项目验证及 HMCL UI 基线 247/247 通过。
- 待验证：当前 Tauri 窗口的 `.dshpack` 导入、整包安装失败回滚、按钮行为和 Provider 系统凭据流程。本轮未启动图形界面。

## 2026-09-26 三种 Provider 协议真实 DSH 请求

- 已执行：本机 Core `0.1.5-rc.2` 下运行 4 项可选集成测试（`cargo test --lib -- --ignored --test-threads=1`），隔离 Web 无浏览器启动，以及 Chat Completions、Responses、Anthropic Messages 的 Headless 回环 SSE 请求全部通过。三种请求均到达正确路径，携带当前 Provider 的一次性测试 Key，并输出 `OK`；前一个 Provider 路由保留在配置中，进程输出未包含新旧测试 Key。
- 已修复：Anthropic Base URL 末尾的 `/v1` 不再重复拼接为 `/v1/v1/messages`。普通 Rust 测试 22 项通过；`cargo check`、格式检查、`npm run check`、`npm run build` 与 HMCL UI 基线 247/247 均通过。
- 仍需：真实 Tauri 窗口中的 OS 凭据读取及切换、外部生产 API、不同代理对 SSE 的兼容性。此轮未启动图形界面。

## 2026-09-26 Provider 凭据路由隔离

- 初次凭据隔离验证时，`cargo test --lib` 有 22 项通过、2 项可选集成测试默认跳过；随后已扩展为上节的 4 项可选测试。`cargo check`、`cargo fmt -- --check`、`npm run check`、`npm run build`、`node scripts/verify-project.mjs` 通过。单元测试验证不同 Provider 的 `apiKeyEnv` 不同，且本地无 Key 端点的占位值不写入 `settings.yaml`。
- 已执行可选集成测试：设置 `DSHCRAFT_TEST_RUNTIME_DIR` 指向已安装的 DSH Core `0.1.5-rc.2` 后，隔离 Web Profile 无浏览器启动成功；隔离 Headless Profile 在先写入旧 Provider 路由后，向回环 Chat Completions 假端点发送了请求，假端点收到当前 Provider 的测试 Key，运行输出没有包含新旧测试 Key。
- Responses 与 Anthropic Messages 的回环真实请求已在上节通过；仍待完整 Tauri 窗口中的 Provider 切换与 OS 凭据读取、旧端点不会在其他调用路径收到新 Key，以及外部生产 API 联调。本轮未启动图形界面。

## 2026-09-26 Plugin spec 底层校验

- `cargo check`、`cargo test --lib`（20 项通过）、`cargo fmt -- --check` 已执行。单元测试验证 URL 凭据、查询 token、敏感引用标记、pnpm 选项和控制字符在调用 DSH 前被拒绝；常规 npm/GitHub/file/Git SSH spec 保持可用。
- 仍需在真实 Tauri 临时实例中验证合法插件安装与含凭据 spec 的拒绝路径；本轮未启动新界面进行该测试。

## 2026-09-25 DSHCraft brand and legal notices

- Automated/build verification: `:HMCL:makeExecutables --rerun-tasks --no-daemon` completed on Windows. The generated executable's associated icon was extracted and visually confirmed as the DSHCraft mark. The same mark is used for JavaFX stages and macOS Dock; Debian packaging already points at `image/dshcraft.png`.
- Agreement version was raised from 1 to 2 so prior HMCL agreement acceptance does not suppress the DSHCraft disclaimer. The document disclaims affiliation, describes API costs and third-party package/extension risk, and says DSH_HOME isolation is not a security sandbox.
- Still requires a user-window check: launch the new snapshot and confirm the taskbar/tray icon, first-run disclaimer link, About source notices, Help, and Feedback links. Do not remove HMCL GPL copyright, source notices, or license obligations.

## 2026-09-25 React/Tauri updater feed

- Automated: `npm run check`, `npm run test:unit`, `cargo fmt --manifest-path src-tauri/Cargo.toml -- --check`, `cargo test --manifest-path src-tauri/Cargo.toml --lib` passed. The new manifest tests cover three platform targets, release tag URLs, missing signatures, placeholder signatures and invalid versions.
- Release workflow is ready but not production-verified. A maintainer must configure repository secret `TAURI_SIGNING_PRIVATE_KEY`, secret `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`, and repository variable `DSHCRAFT_UPDATER_PUBKEY`, then publish a GitHub Release. The workflow independently verifies each `.sig` before uploading `latest.json`.
- Still required for completion: execute a legitimate update from an older installed DSHCraft build to the new signed release, confirm signature acceptance, version-bound signature enforcement, restart/exit behavior, rollback/error handling, and cross-platform artifacts.

## 2026-09-25 React/Tauri Plugin manager

- Automated build/type/unit checks pass after adding bulk Plugin/Bundle controls and details.
- Manual desktop check required: open Downloads → Mods, select multiple package-backed entries, use Enable/Disable, run Check Updates, open a detail row, and confirm Profile `package.json`, process restart-required state, and logs reflect real DSH CLI operations.

## 2026-09-25 HMCL Provider 与下载源

- 自动化：`DshModServiceTest`、`AgentRepositoryOutputTest` 通过；覆盖 npm 源白名单/自定义 URL 校验、DSH Responses 配置生成和 API Key 环境变量引用，未写入密钥值。
- 待手动：在实际 HMCL 设置页分别选择目录源与包下载源，刷新 Core 目录；用 npmmirror/Huawei/Tencent 镜像安装临时 Core/测试 Mod，确认错误时显示所选源失败，不静默回退。
- 待手动：真实窗口新增 Provider，应用 DeepSeek 官方预设并保存测试 Key 到 Windows Credential Manager；分别验证 Chat Completions、Responses、Anthropic Messages Profile 启动和切换服务商时旧凭据删除。不得使用真实生产 Key。
- 未完成：Plugin/Bundle 批量工具栏、Mod 详情/来源链接及 MCP/Skill/Tool 可浏览市场；目前不应在验收时标记通过。

## 2026-09-24 Logo 与预览窗口回归

- 修复 Logo 巨大覆盖窗口：`AgentAboutPane` 的 HMCL `LineButton` 现在使用 48px 固定 leading 图像；标题栏和列表图标使用固定尺寸容器。最新快照窗口实测显示正常小 Logo。
- Java 预览使用 `scripts/start-hmcl-preview.ps1` 创建只读 JAR 快照，避免 Gradle 在运行中替换 JAR 造成旧类/新类混载。

## 2026-09-24 HMCL 下载页与空白区修复实测

- 真实最新 Java 窗口通过：侧栏只有一个“下载”入口；右侧显示 DSH Core 的 26 个 npm 版本、latest/next/alpha 标签、已安装状态和下载按钮。点击 Core/MCP/Skill/Tool 分类不会出现截图中的空白大面板；Core 空目录加载失败会显示错误和可点击重试。
- Java `.dshpack` 现在先验证和暂存 Core/Plugin 安装，Profile 依赖核对成功后才提交实例；本地路径、凭据 URL 和伪造 MCP/Skill/Tool 安装会被拒绝。完整 `:HMCL:build` 通过。

## 2026-09-24 Java/HMCL 最终界面与凭据实测

- 最终桌面 UI 已由用户确认为 `hmcl-ui/` 的 GPL Java/HMCL 窗口。最新构建在隔离 `hmcl.home` 中真实打开，Provider 编辑页和 HMCL 样式密码弹窗已截屏核对；未在预览窗口输入真实密钥。
- Windows Credential Manager 的一次性 Key 保存、读取、删除，以及使用已存 Key 的本机 Anthropic 模型发现通过：`AgentSecretStoreTest` 3 项、0 跳过。`:HMCL:build` 通过。
- 待测和待实现：从 Java 窗口启动/停止 DSH 后的日志与 Web URL、Java `.dshpack` 的实际包安装、非 Windows OS 凭据库，以及签名更新。现有 Tauri 验收记录属于独立实现，不能替代 Java 产品验收。

## 2026-09-23 依赖实例克隆启动实测

- 63 的独立启动部分通过：一次性源实例安装 `is-number@7.0.0`，Rust IPC 克隆后确认副本依赖，副本 Web Profile 独立启动、输出环回 URL 和 stdout，停止后端口可重新绑定。测试清理了源/副本数据与临时 Workspace。
- 复测命令为 `npm run test:desktop-clone:e2e`，需运行中的 Tauri 开发窗口与 CDP 9222；其他 Profile 模板和平台尚未验证。

## 2026-09-23 Provider 凭据迁移实测

- 52、53、54 的一次性测试通过：真实 Tauri 凭据库保存旧配置和旧备份中的 Provider Key，状态与日志均不含测试密钥；无效 Provider ID 导致迁移失败时清除本地明文并提示重新输入。测试后删除一次性凭据并恢复页面状态。
- `npm run test:desktop-provider:e2e` 可复测，需运行中的 Tauri 开发窗口与 CDP 9222。多 Provider 备份导入已改为 Rust 批量预校验和写入失败恢复；有效/无效 ID 混合批次的整批拒绝已实测，OS 凭据库在写入中途故障的恢复分支尚未故障注入。

## 2026-09-23 React/Tauri 整合包实测

- 65 通过：在真实 Tauri 窗口中导入两份一次性 `.dshpack`；成功包把 `is-number@7.0.0` 安装进隔离 Profile，失败包触发后端回滚，实例状态和目录均未残留。测试后恢复原页面状态并删除成功测试实例。
- 同时修复成功安装时导入模板/Mod 元数据丢失；`npm run check`、`npm run build`、`npm run test:unit`（4 项）和 `npm run test:ui` 通过。复测命令是 `npm run test:desktop-pack:e2e`，需要已启动并开放 CDP 9222 的桌面开发窗口。
- 真实签名更新安装、正式签名密钥和跨平台发行构建仍未通过验收。
- 66 和 68 的真实 Tauri updater 拒绝路径通过：无更新返回、较新版本识别、错误公钥拒绝，安装器未执行。所用 NSIS 文件是旧的一次性签名测试产物；67 的合法更新安装仍未通过。

## 2026-09-23 HMCL-native 子工程

- 通过：工作区 `:HMCL:build`；隔离 Profile 的真实 npm Mod 安装、更新、删除；隔离 Core 启动 Web Profile 并输出本地 URL。JUnit 报告 8 项测试、1 项跳过、0 失败；跳过的是本轮未重跑的从零下载 Core 用例。
- 待测：从 HMCL 窗口点击启动/停止后的完整 UI 流程、启动日志呈现、桌面 `.dshpack` 安装，以及其他页面截图对比。Windows Provider 凭据库与当前 HMCL Provider 页面已在本页顶部的后续实测中通过。

## 2026-09-22 实测记录

- 通过：24、25、26、27、28、31、33、34、35、36。
- 部分通过：29、30、37。React 主要页面已用 Chrome/Playwright 在 1204x736 渲染且无横向溢出；Tauri 窗口真实打开并正常响应，未观察到 capability 错误，但尚未逐个点击所有原生 dialog/opener/updater/credential 操作。
- 未执行：签名更新、`.dshpack` 全流程。Launcher-exit 子进程清理在后续续测中已通过。
- Windows Cargo 需用 `--config 'http.proxy=""'` 绕过用户目录中未运行的 `127.0.0.1:7897` 代理；没有修改全局 Cargo 配置。

后端续测结果：

- 通过：38、40、41、42、43、44、46、47、48、49、51、52、55、57（本机 HTTP fixture）、59、60、62、64。
- 另通过：Core 安装失败后的 staging 回滚、全新 Profile 的真实 `dsh plugin add`、带 `is-number@7.0.0` 依赖的 Profile 克隆重建、损坏 package 时的克隆回滚、一次性 Profile 的真实外部依赖 `dsh plugin update`，以及损坏 `settings.yaml` 不覆盖的 Rust 单测。
- 部分通过：58（add/list/remove、空 Profile update 和小型外部依赖 update；大型 Codex Bundle update 曾挂起，尚未用新执行器重测）、63（带依赖的克隆重建通过；副本独立启动未测）。
- 未执行：45 的 cwd 外部观测、50、53、54、61、65-70、真实远程 Provider 模型发现，以及 `.dshpack` 桌面成功安装全流程。
- `.dshpack` 浏览器下载/导入/配置模板/非法包拒绝通过；当时桌面插件安装失败仅为模拟测试。65 的真实桌面成功和失败回滚已在本页顶部的后续实测中通过。
- 浏览器和 Standalone 不再把本机 Core/插件操作报告为成功，临时 Key 不再写入 localStorage/备份；`npm run test:unit` 4 项和 `npm run test:ui` 3 组通过。
- 一次性测试 Provider 凭据已删除；一次性 Launcher 实例目录已删除。用于验证“删除实例不删除 Workspace”的两个空 Workspace 目录按预期仍存在于系统 Temp。
- 单独提供的 DShCraft/HMCL Java 包通过 247 文件 UI 哈希核验和 Agent/i18n 静态核验，但真实 `:HMCL:compileJava` 因 3 处不存在的 `SVG.CODE` 编译失败；没有可启动 UI 截图，未声称视觉验收通过。
- 使用一次性签名密钥完成 Windows NSIS release 构建；独立验签验证原始安装器通过、篡改/错公钥拒绝、签名绑定 `0.4.0`。67/68 仅签名构建与离线验签部分通过，未执行真实安装/升级；70 尚未审计二进制是否含私钥。该测试包不可作为正式版分发。

## A. Standalone / Browser

1. 直接打开 `standalone/index.html`，确认无需 npm 即可进入。
2. 首页、实例、账户、插件、整合包、下载、更新、诊断、日志、设置页面均可切换。
3. 新建实例并编辑 DSh 版本、Profile、端口、Provider、模型、Workspace。
4. 复制实例，确认新 ID/端口独立。
5. 删除实例，当前实例指针正确回退。
6. 添加官方 / 第三方 / 本地 Provider。
7. Provider `/models` 或 `/v1/models` 探测成功时写入可用模型；CORS/HTTP 失败只提示错误。
8. 切换实例 Mods，数量和兼容性提示正确。
9. 从当前实例保存本地 Agent Pack 模板。
10. 删除 Pack 模板不影响已经创建的实例。
11. 导出 `.dshpack`，确认不包含 API Key、Workspace、会话。
12. 导入 `.dshpack`，确认引用的 Mod 元数据齐全，否则拒绝。
13. 安装 Pack 为新实例并自动选中。
14. 刷新更新时直接查询 npm Registry，读取真实 `@deepseek-ai/dsh` 历史版本与 `dist-tags`。
15. Latest / Next / Alpha 自动更新候选只来自当前 dist-tag 指向版本。
16. 自定义 Core Manifest 可覆盖展示目录。
17. 自定义 Plugin Registry 可更新已知 Mod，也能发现并加入未知 Mod 条目。
18. 诊断页能发现重复实例 ID、重复 Web 端口、缺失 Provider、未知 Mod、Core 不兼容和非法更新源 URL。
19. 导出诊断报告，确认只记录 `credentialPresent`，不记录 API Key。
20. 导出 Launcher JSON，确认 API Key 被清空。
21. 导入非法实例 ID / Profile / Web Port 时，React 正式版 `normalizeState` 必须拒绝或安全迁移。
22. 主题、更新通道、设置与 localStorage 持久化正常。
23. 重置恢复默认示例。
24. `node --check standalone/app.js` 通过。

## B. React Web development build

25. `npm install --no-audit --no-fund` 成功并生成锁文件。
26. `npm run verify` 无失败。
27. `npm run check` 通过。
28. `npm run build` 通过。
29. `npm run dev` 启动后，React UI 与 Standalone 关键能力一致。
30. 浏览器控制台无持续异常、无限 state loop 或明显 hydration/render 错误。

## C. Tauri Desktop - environment / build

31. `scripts/windows/check-env.ps1` 正确识别 Node/npm/pnpm/Corepack/Rust/Cargo 与 MSVC Build Tools。
32. 当前 DSh Node 要求与上游实际要求重新核验；不兼容版本必须在安装/启动前拦截。
33. `cargo fmt --check` 通过。
34. `cargo check --manifest-path src-tauri/Cargo.toml` 通过。
35. `cargo test --manifest-path src-tauri/Cargo.toml --lib` 通过。
36. `npm run desktop` 打开真实 Tauri 窗口。
37. Tauri dialog/opener/updater/event/credential 功能无 capability/permission 错误。

## D. DSh Core / process lifecycle

38. Runtime Probe 正确报告 Node/pnpm/Corepack/AppData。
39. 缺 pnpm 且存在 Corepack 时可启用 pnpm。
40. npm Registry 返回版本与 dist-tags。
41. 安装一个真实 DSh 版本到 `<AppData>/runtimes/<version>`。
42. 安装后 DSh CLI 自检成功；失败时 staging 不得提交。
43. 创建独立实例后只使用 `<AppData>/instances/<id>/dsh-home`，不得改用户全局 `~/.dsh`。
44. Web Profile 启动前检测端口占用。
45. DSh 进程 cwd 为实例 Workspace。
46. stdout/stderr 实时进入 Launcher 日志。
47. 捕获 DSh loopback Web/auth URL 并安全打开；非 loopback URL 必须拒绝。
48. Stop 只终止对应托管子进程。
49. Launcher 退出时回收托管子进程。
50. 启动后最小化开关实际生效。
51. Safe Mode 使用干净官方模板 Profile，不污染原 Profile。

## E. Provider / secrets

52. Desktop Provider Key 写入系统凭据库，Launcher 状态不保留明文。
53. 旧版明文 Key 可迁移到系统凭据库，成功后清空本地状态。
54. 导入旧备份时先迁移 Key，再保存状态。
55. `settings.yaml` 只包含环境变量引用，不包含明文 Key。
56. 损坏的 `settings.yaml` 导致明确错误且原文件不被覆盖。
57. Rust 模型发现支持 OpenAI-compatible 与 Anthropic-compatible 鉴权头。

## F. Mods / Profiles

58. `dsh plugin --profile ... add/remove/update` 在目标实例的隔离 DSH_HOME 中执行。
59. 官方 `@deepseek-ai/dsh-*` Bundle/Core 版本规则与当前上游重新核验并通过 E2E。
60. 同步 Profile `package.json` 时未知 Bundle 自动建档。
61. 运行中改外部 Bundle 后出现“需重启”，停止/重启成功后清除。
62. 非法/路径逃逸 Profile 名被 Rust 后端拒绝。
63. Clone 跳过 `node_modules` 并重建 Profile 依赖；副本可独立运行。
64. Delete 不删除用户 Workspace，运行中实例禁止删除。
65. Pack 安装中途失败会回滚新实例 DSH_HOME。

## G. Updater / release

66. Tauri updater 无更新时正常返回。
67. 合法签名 artifact 可安装。
68. 错误 pubkey/signature 必须失败，没有“忽略签名”开关。
69. Windows/macOS/Linux CI 至少 `cargo check` + Rust unit tests。
70. Release 构建不包含 Tauri 签名私钥。

## H. Stable release blockers

以下任一未通过，不建议标记稳定版：

- Windows / macOS / Linux 至少完成目标平台真实 Tauri 构建
- 真实 DSh Core install / launch / stop E2E
- Provider secret store E2E
- Mod add/remove/update E2E
- Clone/Delete/Safe Mode E2E
- Launcher 签名更新 E2E
