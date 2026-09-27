# HMCL UI parity report

Base: user-supplied `HMCL-main.zip`.

## Hard-protected upstream UI

- Protected files: **247**
- Changed protected files: **0**
- Protected scope: HMCL CSS, themes, built-in images, theme engine, animation, construct controls, decorator/window code, image layer, `PersonalizationPage`, `ThemePackManagementPage`.
- Result: **byte-for-byte unchanged**.

## Structural UI reuse

DShCraft no longer keeps a copied `AgentNativeListPage` implementation.

The original HMCL instance-list implementation was factored into two shared Java UI primitives while preserving the same node hierarchy and style classes:

- `SearchableListPage<T>`: the original `GameListPage.GameListSkin` toolbar/search/fade/spinner/list/placeholder shell.
- `TwoLineActionListCell<T>`: the original `GameListCell` `md-list-cell` / radio / 32 px image / `TwoLineListItem` / right actions / ripple / popup geometry shell.

Both Minecraft's real `GameListPage`/`GameListCell` and DShCraft's Agent list adapter now consume those **same physical implementations**. The Agent layer supplies only data bindings and business callbacks.

Protected HMCL theme/background/CSS resources remain unchanged, so imported HMCL themes and personalization behavior continue to flow through the original HMCL systems rather than a DShCraft imitation layer.

## Business UI added on top of HMCL controls

The Agent pages use HMCL-native settings rows, dialogs, file choosers and list pages for:

- Provider editing and `/models` discovery
- DSh npm version catalog and version selection
- Agent Profile / Profile template / web port / Workspace metadata
- Plugin / MCP / Skill metadata and remote JSON catalog import
- `.dshpack` import/export
- Sanitized full configuration backup/restore
- Browser-level configuration validation
- Sanitized diagnostics export
- Isolated per-instance `DSH_HOME`

No React/TSX/JSX/Vue/Svelte/Tauri/Vite UI or parallel CSS theme implementation was introduced.

## Build limitation in this environment

The original, unmodified HMCL source was tested first. Gradle wrapper fails before project configuration because `services.gradle.org` cannot resolve in the container. Therefore this environment cannot truthfully claim a completed Gradle/JavaFX binary build.

The release is checked with the protected-file hash verifier, i18n consistency checks, source-diff checks and `javac` parser scanning. A normal networked machine should still run the full Gradle build before publishing binaries.
