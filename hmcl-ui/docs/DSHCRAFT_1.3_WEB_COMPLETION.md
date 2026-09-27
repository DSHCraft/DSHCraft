# DShCraft 1.3 — web/manage-layer completion

This iteration moves the browser-manageable functionality from the older launcher prototype back into the direct HMCL source fork without restoring the rejected React imitation UI.

## Completed in the HMCL-native source layer

- Provider profiles: ID, type, Base URL, default model and API-key environment variable name.
- Real OpenAI-compatible model discovery through `/models` with `/v1/models` fallback.
- Agent instance metadata: DSh version, Profile, Profile template, web port, model override, extension IDs, Workspace and description.
- Real npm metadata query for `@deepseek-ai/dsh`, including `latest`, `next`, `alpha` and recent versions.
- Remote HTTP(S) extension-catalog import for Plugin / MCP / Skill metadata.
- `.dshpack` v1 import/export compatible with the older browser shape; secrets, Workspace and sessions stay out of packs.
- Full sanitized JSON configuration backup/restore with validation before state replacement.
- Browser-level instance validation for Provider references, DSh version, Profile name, port and extension references.
- Secret-redacted diagnostics export.
- Per-instance isolated `DSH_HOME` path under the HMCL user-data directory.
- Backward-compatible loading of the 1.2 properties file; new fields receive safe defaults.
- Provider secrets remain environment-driven; the secret value is not persisted by this layer.

## UI de-duplication completed

- Removed `AgentNativeListPage`.
- Extracted the real HMCL `GameListPage.GameListSkin` into shared `SearchableListPage<T>`.
- Extracted the real HMCL `GameListCell` visual shell into shared `TwoLineActionListCell<T>`.
- Minecraft and Agent list pages now consume the same physical list/search/cell implementations.
- 247 protected HMCL CSS/theme/background/animation/construct/window/personalization files remain byte-for-byte unchanged.

## Intentionally desktop-only / still pending runtime E2E

These operations require native filesystem/process/security integration and should not be faked in the management layer:

- Installing/removing `@deepseek-ai/dsh` runtimes with pnpm/Corepack.
- Executing `dsh plugin --profile ... add/remove/update` against a real isolated profile.
- OS Keychain storage for Provider secrets.
- Native process stdout/stderr capture, port/process supervision and crash safe-mode behavior.
- Signed launcher self-update.
- `.dshpack` OS file association / `dsh://` deep links.
- Final Windows/macOS/Linux Gradle/JavaFX build + real DSh E2E.

## Sandbox limitation

The Gradle wrapper cannot resolve `services.gradle.org` here, and the unmodified HMCL baseline fails at the same distribution-download step. No binary-build success is claimed from this environment.
