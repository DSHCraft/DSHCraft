# Verification status

## Passed in this environment

- User-supplied HMCL source unpacked successfully and retained as the baseline.
- Protected HMCL UI files: **247 checked, 0 changed** (`python3 tools/verify_hmcl_ui_parity.py`).
- Combined Agent verifier passes: `python3 tools/verify_dshcraft_agent.py`.
- No added/modified React, TSX, JSX, Vue, Svelte, Tauri, Vite, CSS, SCSS, or SASS UI implementation.
- `AgentNativeListPage` is removed.
- HMCL's original `GameListPage.GameListSkin` structure is extracted once into `SearchableListPage<T>` and consumed by both Minecraft and Agent lists.
- HMCL's original `GameListCell` structure is extracted once into `TwoLineActionListCell<T>` and consumed by both Minecraft and Agent rows.
- Agent English/Simplified-Chinese key sets are aligned and every `agent.*` key referenced by Java exists in both bundles.
- `javac` parser scan reports no parser-level syntax diagnostics in changed/new Java files. Full semantic compilation is blocked by unavailable JavaFX/Gradle dependencies in this sandbox.
- Provider model discovery, npm DSh catalog, remote extension catalog import, `.dshpack`, sanitized backup/restore, validation, diagnostics and isolated `DSH_HOME` are wired into HMCL-native pages.
- Secrets are not written into `.dshpack`, JSON backups or diagnostics; only API-key environment-variable names are persisted/exported.

## Not claimed

A complete Gradle/JavaFX binary build was **not** completed in this sandbox. The original, untouched HMCL source itself fails before configuration because Gradle 9.7.1 cannot be downloaded: DNS resolution for `services.gradle.org` fails.

Therefore this package claims verified source-level direct HMCL reuse and static checks, not a fabricated runtime screenshot or binary-build result.
