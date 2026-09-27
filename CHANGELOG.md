# Changelog

## DSH 启动失败诊断 - 2026-09-27

- 启动异常和启动后非零退出现在使用统一的 HMCL 风格诊断窗口。
- 支持复制脱敏日志、导出 UTF-8 日志文件和打开控制台，并提示不要发送截图。

## DSH instance workspace and configurable Pack export - 2026-09-27

- Reworked DSH Instances into an HMCL-style two-pane workspace: instances remain on the left and the selected instance's Core/Profile management appears on the right.
- Added Pack export to the instance row menu and made export fields selectable. Core, Profile, model, extensions and optional Provider routing can be chosen per export.
- API keys, Workspace paths, sessions and runtime files remain excluded; API keys are never exportable and Provider routing is off by default.
- Widened the export options dialog for the multi-field workflow and made Pack writes atomic.

## Runtime safety and bounded network input - 2026-09-27

- Reject persisted instance environment entries that could contain secrets or alter Node, Java, DSH, or pnpm execution.
- Reject Provider API-key environment references that target launcher or runtime control variables.
- Bound Provider model, npm metadata, and extension-catalog responses before JSON parsing.
- Clear stale process state when an instance has already exited before Stop is pressed.

## Public source repository - 2026-09-27

- Published the Java/HMCL source to `DSHCraft/DSHCraft` on GitHub. GitHub Actions Java/HMCL CI passes on Linux, and a clean Windows build passes locally.
- Corrected the Windows icon helper path, preserved the Gradle wrapper executable bit, and narrowed the root `.gitignore` so HMCL's `ui/instances` sources are included.
- No binary Release was created; executable signing and redistributed dependency license review remain open.

## Select installed DSH Core per instance - 2026-09-27

- Changed the instance Core selector to list only complete versions in the launcher-managed runtime cache. Added a separate action to browse the online release catalog; install still targets the isolated launcher runtime.
- Incomplete folders, symlinked runtimes and install staging directories are excluded. Full HMCL tests passed (271 tests, 0 failures, 9 optional skips), and the offline Windows executable build passed. The artifact remains unsigned.

## DSH instance workflow hardening - 2026-09-27

- Fixed first launch of shipped DSH profiles by avoiding an invalid `--from-default-profile` target. Core `0.1.5-rc.3` installed in isolation and its Web profile announced a loopback URL.
- Stop clears the session URL and waits for the managed process; deleting the selected running instance stops it first. Instance deletion now asks for confirmation and removes only the managed `DSH_HOME`, preserving Workspace and neighboring instance data. Symlinks and traversal IDs are rejected.
- Full HMCL tests and offline Windows executable build passed. The UI click path could not be exercised because the desktop automation bridge is unavailable. No Git metadata exists in this workspace, and the artifact remains unsigned.

## First DSH launch fix - 2026-09-27

- Fixed first launch of the shipped `web` profile: the launcher no longer passes that same shipped profile as a custom target to `--from-default-profile`. A disposable Core `0.1.5-rc.3` install and actual Web launch produced a loopback URL; the DSH service suite passed with two opt-in install tests skipped.
- Escaped HMCL dialog error text as XML so failures containing angle-bracketed Java frames remain readable. The offline Windows executable build passed; the artifact still has no production signature key.

## Java/HMCL instance process lifecycle - 2026-09-27

- Stopping a managed DSH process now clears its session URL, terminates known descendants, waits up to five seconds for the parent process, and updates the launcher status only after confirmed exit. Timeout and interruption are reported in the console.
- Removing the selected running instance now requests the same stop flow before removing its launcher metadata. The offline Windows executable build passed; JUnit execution was blocked by a Maven Central TLS handshake failure for the JUnit platform launcher. No production-signed release was produced.

## Signed update helper handoff - 2026-09-27

- Added DSHCraft-only `--apply-dshcraft-update` entry before HMCL's legacy update logic. It requires the embedded DSHCraft public key, verifies signed feed and candidate bytes, waits for the parent process to exit, performs offline replacement and restarts the installed file; a failed process start restores the backup.
- Parent-side code now stages a copy of the current launcher as a trusted helper, preserves exact signed feed bytes, and starts that helper with update-only arguments. Installer/handoff tests 7/7 and signed-feed tests 4/4 passed. The rebuilt EXE rejected an actual helper invocation without a production public key and left the target absent. The UI does not yet trigger this handoff, and no production signed install was run.

## Signed offline replacement transaction - 2026-09-27

- Added a Java/HMCL offline replacement routine that re-verifies the signed DSHCraft manifest and candidate file, stages the replacement alongside the target, preserves the old launcher as a backup, and restores it if the new-file move fails.
- Three disposable-filesystem tests passed: successful replacement with retained backup, failure after backup with rollback, and invalid signature with no target mutation. The EXE was rebuilt. This routine is not wired to a running launcher or UI and has no production key; it is not yet a complete updater.

## Signed release retrieval and artifact download - 2026-09-27

- Extended the fork-specific updater verifier to fetch a bounded JSON feed and detached signature from fixed DSHCraft GitHub Release paths, then download a manifest-authenticated artifact to a fresh destination only after SHA-256 verification. Partial or mismatched downloads are discarded.
- Four disposable-key tests passed, including a loopback fetch→verify→download positive path and a tampered download that left no final file. The Windows EXE was rebuilt. Production key, UI integration, installation/rollback and legal signed-update test remain open.

## DSHCraft signed-update verification foundation - 2026-09-27

- Added a fork-specific Ed25519 verifier for exact release-manifest bytes and SHA-256 artifact bytes. It accepts only the matching DSHCraft GitHub release tag/URLs and fails closed when a DSHCraft public key is not embedded; HMCL's signing key and SHA-1 feed are not reused.
- Three disposable-key tests passed: valid manifest/artifact, changed manifest or wrong key, and wrong repository or altered artifact. Replaced the stale Tauri/minisign updater example with the Java feed schema and rebuilt the EXE. Fetching, installing, production signing and a legal update test remain unimplemented.

## DSHCraft update-entry isolation - 2026-09-27

- The Java/HMCL fork now rejects legacy `--apply-to` arguments before HMCL migration or file operations and skips HMCL's old force-update cleanup on normal DSHCraft startup. DSHCraft's HMCL update check and UI update action remain disabled.
- Two focused isolation tests passed, including an existing target-file preservation check. The Java executable was rebuilt. This is a fail-closed boundary, not a signed DSHCraft updater; production key/feed and a legal signed installation test remain open.

## Java binary dependency inventory - 2026-09-27

- Recorded the current Gradle runtime dependency tree and checked selected package classes and license entries against the rebuilt shaded JAR. The ANTLR runtime is present without a matching identified notice; NanoHTTPD, CommonMark and JNA have embedded license files whose scope still needs final verification.
- Added `BINARY_LICENSE_AUDIT.md` to the source-preparation archive. This narrows the binary release gate but does not clear it or authorize publishing the unsigned executable.
- Added the upstream ANTLR 4.11.1 BSD 3-clause notice to the source license directory and Java resource packaging; other dependency and native-component notices remain under review.

## Java/HMCL local stdio MCP - 2026-09-27

- Added explicit `stdio:{"command":"...","args":[...]}` descriptors to the existing HMCL MCP editor. The launcher maps them to DSH's stdio transport without a shell and warns before enabling a local executable; descriptor environment maps and MCP bearer tokens are rejected.
- Fixed the Windows DSH module-fallback loader so a spawned Node MCP server remains startable when DSH does not forward the loader's runtime-package environment variable. A disposable live Core `0.1.5-rc.2` stdio fixture received `initialize` after the fix.
- The isolated Core composition check and seven other MCP tests passed in the first run; the new live stdio test exposed the loader bug and failed once, then passed when rerun alone after the fix. No HMCL layout changed. Real window click-through and third-party stdio interoperability are still pending.

## Java/HMCL MCP bearer-token integration - 2026-09-26

- Added a separate Windows Credential Manager target for each MCP bearer token. The MCP overlay contains only a `process.env` reference; the selected instance receives the token in its process environment on launch, and console output redacts Provider and MCP secrets.
- Fixed DSH Web launch argument order: `--patch` must precede Web app flags such as `--port`, or DSH forwards it to the inner app as an unknown option.
- A real isolated Core `0.1.5-rc.2` contacted a loopback MCP fixture with the expected bearer header. Six MCP tests, four credential-store tests and six repository/output tests passed; the Windows executable was rebuilt. Real UI clicks and production server interoperability remain open.

## Java/HMCL instance-local MCP overlay - 2026-09-26

- MCP entries in the existing Downloads page can now be connected to or disconnected from the selected instance. DSHCraft writes a separate hash-checked `--patch` overlay in that instance's DSH_HOME for credential-free Streamable HTTP endpoints; the user's Profile patch is not edited.
- Rejects URL credentials, query strings and fragments. Targeted tests passed, including composition through installed DSH Core `0.1.5-rc.2 --dump-config`. Stdio transport and authenticated MCP servers remain open.
- A follow-up safety check rejects symbolic links in the instance-owned DSH_HOME path before MCP patch deletion or Skill copying.

## Java/HMCL instance-local Skill import - 2026-09-26

- The existing Skills Downloads category can now choose a local SKILL.md folder and copy it into only the selected instance's `DSH_HOME/skills`. It rejects invalid names/manifests, symlinks, oversized bundles and existing destinations, and stages the copy before making it visible.
- The existing HMCL editor/list layout is unchanged. This is local import, not an online Skill catalog or MCP server configuration.

## Embedded binary notices - 2026-09-26

- Embedded DSHCraft/HMCL GPL, source notice, third-party notice, and the bundled JFoenix MIT text into the Java launcher under `META-INF/dshcraft/`; the same JFoenix license is included in the source package.
- Verified the four entries in the rebuilt JAR. This does not complete the remaining Gradle dependency license audit or make the unsigned executable a production release.

## Complete app icon and source-release preparation - 2026-09-26

- Replaced the clipped square icon with a complete transparent mark based on the supplied DSHCraft artwork. Updated the app and package icon inputs and rebuilt the Java/Windows executable; the extracted EXE icon now has transparent padding at the bottom.
- Prepared a reproducible Java/HMCL source ZIP with legal notices and excluded caches/upstream automation. Binary signing and full third-party notices remain open release gates.

## Java/HMCL Provider protocol presets and Profile sync - 2026-09-26

- Split known Provider presets by actual Chat Completions, Responses and Messages protocols in the existing HMCL dialog. Launcher-owned DSH Profile YAML now tracks endpoint/protocol changes while protecting manually edited files. No visual layout changed.

## Java/HMCL Pack failure cleanup - 2026-09-26

- Failed `.dshpack` final verification now discards the staged DSH instance directory. Added instance-ID validation and sibling-preserving cleanup tests. No HMCL visual component changed.

## HMCL-only desktop UI - 2026-09-26

- Removed the unwanted React/Tauri desktop UI source and its launch/release entrypoints. `START_HERE.cmd` now opens the existing Java/HMCL window only. Kept an archived source snapshot for recovery; no Java/HMCL visual component was restyled.

## Provider credential-store integration proof - 2026-09-26

- Added disposable real-OS credential tests for separate Provider IDs, replacement and deletion. A local HTTP fixture verifies desktop model discovery retrieves only the selected Provider's key from the OS store when Launcher state contains no key.
- Account credential save/delete actions now respect a `RuntimeResult` with `ok: false`; a failed backend operation no longer claims a key was saved or removes the Provider from Launcher state. No visual layout changed.

## Provider endpoint presets - 2026-09-26

- The existing account modal now offers verified DeepSeek Official, OpenAI, OpenRouter, Gemini OpenAI-compatible, Anthropic, Ollama and vLLM endpoint presets, grouped by Chat Completions, Responses and Messages where supported. Official setup no longer asks users to edit the Base URL or protocol manually; custom endpoints remain available.
- Switching presets clears an unsaved API key, and custom Base URLs reject embedded credentials, query parameters and fragments before a Provider can be saved. The main account page and HMCL/Java UI layout were not changed.

## Isolated instance rollback deletion proof - 2026-09-26

- The Tauri instance-deletion command now uses a shared, ID-validated directory-removal helper. A filesystem test exercises the production helper against a disposable instance tree and verifies sibling instances and the separate workspace remain intact; no desktop UI changed.
- An optional real-Core test drives `dsh plugin --profile web add` with a nonexistent local package in a disposable `DSH_HOME`, confirms the command fails after creating instance data, and then verifies rollback removes only that instance tree.

## Portable Pack export preflight - 2026-09-26

- The Pack export action now refuses to download an archive when a referenced Mod is missing metadata or its external Package Spec cannot be safely shared. This avoids a seemingly successful `.dshpack` that necessarily fails on import elsewhere. No visual layout or Java/HMCL UI changed.

## Pack import Mod identity validation - 2026-09-26

- `.dshpack` import now rejects a Mod ID already present in the launcher when its package spec, kind or built-in identity differs. It also rejects missing Mod references instead of silently using unrelated local metadata. The Pack page layout and HMCL UI were not changed.
- Unknown Mods can no longer claim to be built-ins or omit an installable Package Spec. Unsafe specs filtered during normalization therefore fail at import rather than creating a broken Pack template.
- Added unit and headless browser regressions for valid imports and conflicting IDs.

## Signed updater feed and disposable signature proof - 2026-09-26

- Feed URLs now use the exact GitHub Release tag, so both `v0.4.1` and `0.4.1` tags point to their actual assets. The release build override explicitly retains `requireSignedVersion: true`.
- Added `npm run test:updater-signature:e2e`: it creates disposable keys, signs a tiny fixture with a bound version, independently verifies it, and confirms tampering, a wrong key and a wrong announced version are rejected. This does not install an update or substitute for production signing.

## Pack metadata sanitization and isolated Plugin lifecycle - 2026-09-26

- `.dshpack` import/export and Launcher-state persistence now share package-spec validation. Credential-bearing URL fragments, URL userinfo/query credentials, pnpm-leading options and control characters are dropped; public Git refs remain valid. Provider URLs lose fragments before persistence.
- The Rust Plugin command now has a path-based internal seam used by both production and an optional integration test. With Core `0.1.5-rc.2`, `dsh plugin --profile web` added and removed `is-number@7.0.0` in a disposable `DSH_HOME`, with the Profile dependency changing accordingly. No UI source was changed.

## Three Provider protocols verified against real DSH - 2026-09-26

- Added isolated real-DSH Headless request fixtures for OpenAI Chat Completions, OpenAI Responses and Anthropic Messages. Each checks the wire path, selected Provider test credential, successful answer, and absence of both selected and previous test keys from process output.
- Anthropic route configuration now removes a terminal `/v1` from its Base URL because the Anthropic SDK appends `/v1/messages` itself. OpenAI protocol Base URLs remain unchanged. This fixes requests accidentally going to `/v1/v1/messages`.
- No UI source files were changed.

## Provider credential route isolation - 2026-09-26

- The Rust backend gives each nonofficial Provider route a distinct `apiKeyEnv` name, so switching Providers cannot feed the newly selected key into an older route. It removes the legacy shared key variable from launched DSH processes. Keyless local endpoints receive a process-only placeholder; secrets are not written to `settings.yaml`.
- Added optional real-DSH integration checks: an isolated Web profile boots without opening a browser, and an isolated Headless profile completes a request against a loopback Chat Completions fixture using the selected Provider key after switching routes.
- This is backend-only; no HMCL or React UI was changed.

## Plugin package spec validation - 2026-09-26

- The Rust desktop backend now rejects plugin specs that could pass pnpm options or embed URL credentials into a Profile or command output. The DSH plugin command and launcher UI are unchanged.

## Delivery target clarification - 2026-09-25

- Current delivery target is the separate React/Tauri/Rust app. The GPL Java/HMCL tree remains an independent reference package with its license and notices intact; it is not integrated into the Tauri product.

## Tauri updater feed hardening - 2026-09-25

- Added compiled updater defaults and a `launcher_update_config` IPC command. User-configured endpoint/public key values override defaults; an empty production public key remains fail-closed.
- Added a signed release workflow for Windows NSIS, Linux AppImage and macOS ARM64. It requires production signing secrets, independently verifies all platform signatures, generates Tauri `latest.json`, and uploads assets to the GitHub Release.
- Added manifest tests for platform completeness, release-tag URLs, invalid versions, missing signatures and placeholder signatures.

## React/Tauri plugin manager actions - 2026-09-25

- Added HMCL-style Plugin/Bundle list actions to the delivered React/Tauri UI: select-all, clear selection, bulk enable/disable, bulk Profile update, row details, package spec, compatibility state and source links.
- Bulk actions still execute through the existing DSH `plugin --profile` runtime adapter and preserve isolated Profile/restart-required behavior; built-in MCP/Skill/Tool capabilities remain non-installable.

## DSHCraft legal identity and application icon - 2026-09-25

- Added the DSHCraft terms/disclaimer covering third-party API billing and availability, package mirror/plugin risks, credential handling, and the fact that per-instance DSH_HOME is isolation rather than a security sandbox.
- Updated the first-run agreement version so existing installs see the DSHCraft disclaimer rather than an HMCL EULA. About, help, feedback, and release links now lead to DSHCraft or official DSH resources; HMCL upstream GPLv3 attribution remains explicit.
- Replaced JavaFX window and macOS Dock icons with the DSHCraft mark and added a Windows build step that embeds seven DSHCraft icon sizes into the launcher PE stub before the JAR is appended. Linux packages already use the DSHCraft icon.

## HMCL API onboarding and npm sources - 2026-09-25

- Added separate selectable npm catalog and package registries, with official npm, npmmirror, Huawei Cloud, Tencent and validated custom URL options. Catalog requests, isolated Core installs, DSH plugin operations and `.dshpack` installs use their respective configured source.
- Added DeepSeek Official as the default Provider preset, plus DeepSeek, OpenAI, Anthropic, Gemini-compatible, OpenRouter, Ollama and custom endpoint presets. Provider protocol is explicit (`openai-completions`, `openai-responses`, or `anthropic-messages`) and new empty Profiles receive a credential-reference-only DSH routing YAML; existing Profile settings are never overwritten.
- Reviewed the user-supplied MIT-licensed `dsh-desktop-master` as a behavioral reference. Its separation of DSH plugin operations, desktop market adapters and upstream settings contracts informed the boundaries; no source was copied.
- The screenshot-requested Plugin batch toolbar (select all, enable/disable, update), full Mod detail/source view and functional MCP/Skill/Tool catalogs remain incomplete and are not represented as finished.

## Preview stability and Logo sizing - 2026-09-24

- Added an immutable-JAR HMCL preview launcher so Gradle rebuilds cannot corrupt a running preview.
- Fixed the DSHCraft About Logo being rendered at its source 700px size inside an HMCL settings row; it is now constrained to the intended 48px row size.

## Open source preparation and branding - 2026-09-24

- Added the supplied DSHCraft artwork as the Java/HMCL product logo and square mark; Agent title bars, Providers, instances, Mods and Core rows use the DSHCraft mark instead of the HMCL Minecraft icon.
- Added root GPL notice, `NOTICE.md`, `OPEN_SOURCE.md`, `CONTRIBUTING.md`, `SECURITY.md` and a Java/HMCL build job to prepare a source release while preserving the separate React/Tauri boundary.

## Unified HMCL Downloads and Pack safety - 2026-09-24

- Added a shared HMCL Downloads page for DSH Core, Packs, Plugin/Bundle, MCP, Skills and Tools.
- Fixed the blank Core pane caused by an empty failure string and a hidden category sidebar; real npm Core versions now render in the shared searchable list.
- Added Windows-root HTTPS trust for Java npm/Provider requests and tightened Java `.dshpack` staging, validation and rollback.

## Java/HMCL product direction and credentials - 2026-09-24

- Confirmed the GPL Java/HMCL fork as DSHCraft's final desktop UI and recorded its separation from the existing React/Tauri implementation.
- Added Windows Credential Manager storage, a masked HMCL Provider dialog, credential deletion and stored-key use during launch and model discovery.
- Made stable Provider, instance and Mod IDs read-only in HMCL editors. Verified the real HMCL window and password dialog, the full Java build, Credential Manager operations and authenticated loopback model discovery.

## Clone lifecycle verification - 2026-09-23

- Added a real Tauri E2E test that clones an instance with an installed Profile dependency, boots the clone independently, confirms its loopback URL and stdout, then stops it and checks port release.

## Provider migration verification - 2026-09-23

- Delayed desktop state persistence until legacy Provider Key migration completes; failed migration now removes the local plaintext and prompts for re-entry.
- Added real Tauri migration tests for a legacy local Key, an imported legacy backup, and a rejected credential-store write without logging or persisting test secrets.
- Backup imports now use a Rust batch credential operation that pre-validates all Provider IDs and attempts to restore prior credentials if any write fails.

## Real desktop Pack verification - 2026-09-23

- Exercised actual `.dshpack` UI import, isolated Profile dependency installation, and failed-install backend rollback through the running Tauri window.
- Fixed successful Pack installation discarding newly imported Pack templates and Mod metadata during a concurrent state update.
- Added a repeatable WebView2/Tauri desktop Pack E2E script with test-instance cleanup and localStorage restoration.

## HMCL-native runtime continuation - 2026-09-23

- Added three npm-verified official Bundles to the new-user HMCL-native Mod catalog and reconciled installed state with each selected Profile on load.
- Connected HMCL-native launch to the isolated installed DSH Core, custom Profile initialization, Web port and Windows module fallback; removed the obsolete executable field from the instance editor.
- Stop now terminates the managed process tree instead of only the direct Node process.
- Exercised real disposable Mod install/update/remove and isolated Web startup, and passed the Java full build.

## Pack and persistence continuation - 2026-09-23

- Updated Tauri CLI to 2.11.5 and required updater signatures to bind their announced version; a disposable signed NSIS release passed independent valid/tampered/wrong-key/version checks. Actual updater installation and production signing remain unverified.
- Added strict `.dshpack` normalization and defined-field export; rejects malformed IDs/versions and drops credential-bearing/local package specs.
- Browser and Standalone now create Pack configuration templates without pretending to install Core or external Mods; launch/update/install controls reflect desktop-only capabilities.
- Redacted persisted Launcher state and backups with defined-field serialization, including unknown legacy fields and URL-embedded credentials. Browser Provider keys remain session-only; desktop keys still use the OS credential store.
- Added repeatable Pack unit and Playwright browser tests, including simulated desktop Pack rollback, plus Rust tests for malformed YAML preservation and secret-free provider settings (13 Rust tests total).

## Backend continuation

- Applied 600-second Core install and 45-second self-check limits with staging rollback on failures; verified a missing Core version leaves no runtime/staging via real Tauri IPC.
- Exercised dependency-bearing instance clone, malformed-profile clone rollback, and external-dependency DSh plugin update with disposable desktop instances.
- Bounded plugin/clone command output to 1 MiB per stream, enforced deadlines while descendants hold output pipes, and grouped Windows commands in kill-on-close Job Objects.
- Rolled back partial clone directories on any Profile dependency rehydration failure, including timeout and malformed package metadata.
- Added local HTTP fixture tests for OpenAI-compatible and Anthropic-compatible model discovery/authentication.
- Inspected the separately supplied GPL HMCL/JavaFX derivative without merging it: source/hash checks passed, while real Java compilation failed on three nonexistent `SVG.CODE` references. No runnable UI was verified.

## 0.4.0 - 2026-09-21

### Windows continuation - 2026-09-22

- Reworked the launcher shell against the current HMCL source and supplied HMCL 3.16.4 screenshots: custom title bar, account/current-instance root navigation, empty home stage, update bubble and exact 230x57 split launch control.
- Reorganized Accounts, Instances, Downloads and Settings around HMCL's two-pane information architecture while preserving the existing React/Tauri boundaries.
- Unified Core, Agent Pack and Mods under Downloads; added an official-package-backed Collaboration view for Agent Team and subagent Bundles.
- Added real global defaults for new instances, local home backgrounds, background opacity, animation preference and Node/pnpm/Corepack management.
- Expanded Logs with stream filters, search, wrap, auto-scroll, copy, export, clear and managed-process stop.
- Added original cross-platform application icons and a source-cited `HMCL_FEATURE_MAPPING.md`; no HMCL source or assets were copied.
- Fixed TypeScript 7 CSS side-effect typing, missing Tauri application icons and Windows Vite `src-tauri/target` watcher failures.
- Verified npm install, TypeScript check, Vite build, Rust formatting/checks, 6 Rust unit tests and a real responsive Windows Tauri window.
- Fixed Windows PATH resolution to prefer executable `.CMD`/`.EXE` shims over non-executable extensionless pnpm/Corepack scripts.
- Made isolated Core/Profile installs work without Windows symlink privileges by using a local pnpm Store, hoisted linker, reviewed package-name build-script allowlist and a Launcher-scoped ESM resolution fallback for DSh Profile imports.
- Added a 180-second deadline to plugin/clone dependency operations. The original reader could still hang after direct child exit; the backend continuation above fixes this gap.
- E2E-verified real DSh Core install, Web launch/stop, stdout/stderr events, auth URL capture, port collision, Safe Mode, Credential Manager, secret-free provider settings, Bundle add/list/remove and instance clone/delete.
- Empty-Profile plugin update passed; a large Codex Bundle update still stalled. End-to-end verification of the corrected timeout remains pending.
- Fixed Launcher-exit cleanup by explicitly stopping full managed process trees during Tauri `ExitRequested`/`Exit`; E2E confirmed the child PID and Web port are released.

### Browser completion

- Browser Runtime now queries the real npm Registry packument for `@deepseek-ai/dsh` versions and current `dist-tags`.
- Custom Plugin Registry sync can discover previously unknown Mods instead of only patching known entries.
- Added a Diagnostics page with structural validation for duplicate IDs/ports, missing Providers/Mods, incompatible Core versions, invalid update URLs and credential presence.
- Diagnostics can export/copy a secret-redacted report suitable for issue reports or local-agent handoff.
- Agent Packs can now save the current instance as a local reusable template and delete templates in addition to `.dshpack` import/export/install.
- Standalone browser edition mirrors npm refresh, registry discovery, pack template management and diagnostic report export.

### Local work package

- Added root `START_HERE.cmd` / `START_HERE.sh` entry points.
- Added Windows environment check, dependency install, Web dev and Tauri desktop launch scripts.
- Added dependency-free `scripts/verify-project.mjs` project sanity checker.
- Added `AGENTS.md` with architecture invariants for local coding agents.
- Added `DESKTOP_CONTINUE_PROMPT.md` with an execution-first desktop continuation prompt.
- Added `LOCAL_HANDOFF.md` with exact verified/unverified boundaries and local command order.
- Added `WEB_COMPLETION.md` defining the intentional browser/desktop capability boundary.

### Packaging status

- Package/Launcher/Tauri version bumped to 0.4.0.
- This build environment still cannot run Rust/Tauri because Rust/Cargo is unavailable.
- npm dependency installation timed out in this environment, so a lockfile must be generated and the full TypeScript/Vite build rerun on the local development machine.

## 0.3.0 - 2026-09-21

### Desktop runtime

- Added real instance clone/delete against Launcher-managed `DSH_HOME` data.
- Cloned Profiles skip `node_modules` and rebuild dependencies with pnpm.
- Added Safe Mode using a clean Profile created from the selected official template.
- Added launch-after-success window minimization.
- Captures the authenticated `dsh web:` loopback URL from stdout/stderr and opens it through the system browser.
- Added loopback-only URL validation before handing a DSh URL to the OS.
- Added Web port collision preflight before process spawn.
- Missing DSh Core is installed automatically on launch; Corepack can bootstrap pnpm when needed.
- Added conservative Node compatibility checks before DSh install/start/plugin operations to avoid known silent CLI exits on unsupported Node versions.

### DSh / Mods

- Official `@deepseek-ai/dsh-*` Bundles are pinned to the selected Core version on install.
- Profile update aligns official Bundle versions to the selected DSh Core before normal pnpm update.
- Launch blocks exact-version official Bundle/Core mismatches with a repair instruction instead of starting a broken Profile.
- Profile synchronization now imports unknown dependencies found in the real Profile `package.json`.
- Plugin install failures surface pnpm ignored-build-script guidance without automatically trusting package build scripts.
- Modpack installation now prepares Node/pnpm/Core automatically and rolls back a partially-created instance if Bundle installation fails.

### Updates

- npm Registry `dist-tags` are now read directly.
- Historical DSh versions remain manually installable but are excluded from automatic-update target selection.
- Launcher/Core/Mods remain three independent update layers.

### Configuration / safety

- Third-party provider routes are written using DSh `llm-pi-ai.providers` and `agent-default-model`; API keys stay out of `settings.yaml` and are injected through the process environment.
- Existing malformed `settings.yaml` is never overwritten.
- Profile names and instance IDs are validated before filesystem use.
- Imported Launcher backups are normalized and validated; obsolete fake sampling/System-Prompt fields were removed from the formal instance model.
- Configuration export strips API keys.

### Validation in this environment

- TypeScript/TSX syntax transpile check: 19 files, 0 syntax errors.
- Standalone JavaScript: `node --check` passes.
- Project JSON files parse successfully.
- Rust/Tauri compilation still requires a Rust toolchain on a desktop build machine; the current execution environment does not include Cargo/Rustc.

### 0.3.0 follow-up
- Added portable `.dshpack` JSON import/export. Packs include Core/Profile/model and Mod metadata, but intentionally exclude API keys, Workspace paths and conversations.
- Added system file-manager actions for Workspace and the instance DSH_HOME.
- Pinned frontend dependencies to exact versions and added a cross-platform GitHub Actions check matrix.
- Added Rust unit tests for Node compatibility, path-safe IDs/Profile names, loopback Web URL extraction and official Bundle/Core version pinning.

- Desktop Provider secrets now use the native OS credential store through Rust keyring; legacy plaintext keys are migrated and removed from Launcher state after a successful write.

- Standalone demo now mirrors `.dshpack` import/export, Profile metadata and automatic-update-candidate semantics.
- Provider model discovery now uses Anthropic `x-api-key` / `anthropic-version` headers for `anthropic-messages` profiles instead of always sending Bearer auth.
- Managed DSh child processes are killed/waited when the desktop Launcher process state is dropped.
- Removed the obsolete browser-only provider helper after model discovery was consolidated into `RuntimeAdapter`.
