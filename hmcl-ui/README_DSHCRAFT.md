# DShCraft — HMCL direct UI fork

This tree is built directly on the user-supplied `HMCL-main.zip`. It is **not** a React/Tauri recreation and it does not ship a parallel hand-written HMCL-like CSS/theme system.

## UI rule

HMCL is the UI implementation. DShCraft only replaces business meaning and bindings:

- HMCL account identity -> Provider identity
- HMCL current game -> current Agent instance
- HMCL instance list -> Agent instance list
- HMCL download/component navigation -> Plugin / MCP / Skill navigation
- HMCL launch button -> Agent process launch
- HMCL launcher settings shell -> Agent general settings + original HMCL `PersonalizationPage` + About

The following upstream UI layers are hash-protected and byte-for-byte unchanged: CSS, theme packs, built-in images, theme engine, animation, construct controls, decorator/window code, image layer, `PersonalizationPage`, and `ThemePackManagementPage`.

Run:

```bash
python3 tools/verify_hmcl_ui_parity.py
```

Expected result:

```text
checked=247 protected=247 failures=0
```

See `docs/UI_PARITY_REPORT.md` and `VERIFY.md` for the exact audit and the build limitation of this environment.

## 1.3 management-layer status

The HMCL-native Agent layer now includes Provider model discovery, real npm DSh version discovery, Profile/Workspace metadata, remote extension catalog import, `.dshpack` import/export, sanitized backup/restore, configuration validation, diagnostics export and isolated per-instance `DSH_HOME`.

The old copied `AgentNativeListPage` has been removed. Minecraft and Agent lists now share the extracted HMCL `GameListPage.GameListSkin` implementation (`SearchableListPage`) and the extracted HMCL `GameListCell` shell (`TwoLineActionListCell`).

See `docs/DSHCRAFT_1.3_WEB_COMPLETION.md` for the exact completed/pending boundary.

## Build

On a development machine with Java 17+ and Gradle wrapper access:

```bash
./gradlew :HMCL:build
```

The Windows Java/HMCL build has passed locally. Production packaging still requires release signing configuration and third-party binary notice review.

## License

HMCL and this derivative remain GPL-3.0-or-later. Original copyright and license notices are preserved.
