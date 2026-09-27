# DSHCraft

DSHCraft 是基于 HMCL/JavaFX 的 DeepSeek Harness (`dsh`) 桌面启动器。最终桌面界面和运行时位于 [`hmcl-ui/`](hmcl-ui/)；原先另一套 React/Tauri 窗口已按用户要求从可用源码与启动入口移除，不应再运行或发布。

它把 DSH Core 版本映射为启动器版本，把隔离的 `DSH_HOME` 映射为实例，并管理 Provider、Profile、Plugin/Bundle、MCP、Skills、Tools、`.dshpack` 和 DSH 进程控制台。界面沿用 HMCL 的窗口、导航与页面组件。DSHCraft 是 GPL-3.0-or-later 的 HMCL 衍生项目；保留上游版权声明，并在发布时提供对应源码。

## Windows 构建与启动

```powershell
cd hmcl-ui
.\gradlew.bat :HMCL:build --no-daemon
cd ..
.\START_HERE.cmd
```

构建产物位于 `hmcl-ui/HMCL/build/libs/`。`START_HERE.cmd` 只启动 Java/HMCL 的 Windows 可执行文件。需要隔离用户数据做测试时，可运行 `scripts/start-hmcl-preview.ps1`；预览不会把生产签名与更新配置自动补齐。

当前 `1.3.0-SNAPSHOT` 可执行文件是本地未签名测试产物，不是正式发布版。源码仓库为 [DSHCraft/DSHCraft](https://github.com/DSHCraft/DSHCraft)。源码开放和二进制 Release 是分开的步骤；二进制发布仍需完成许可审计、生产签名和桌面验收。旧 HMCL 命名产物、React/Tauri 缓存和本机运行时不得混入发布。

项目状态与未完成项见 [`LOCAL_HANDOFF.md`](LOCAL_HANDOFF.md)、[`MANUAL_TESTS.md`](MANUAL_TESTS.md)。开源和第三方声明见 [`OPEN_SOURCE.md`](OPEN_SOURCE.md)、[`NOTICE.md`](NOTICE.md)、[`DISCLAIMER.md`](DISCLAIMER.md)。

## 源码开源预备包

在 Windows 上运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-hmcl-source-package.ps1`，会生成 `artifacts/DSHCraft-1.3.0-source.zip`，用于源码归档和审查。GitHub 仓库以 Git 源码和提交历史为主；ZIP 及运行二进制不会随源码提交。上传前先检查 [`OPEN_SOURCE.md`](OPEN_SOURCE.md) 中的源码发布清单。
