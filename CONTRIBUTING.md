# Contributing to DSHCraft

The desktop product is the Java/HMCL fork in `hmcl-ui/`. Do not create another React/Tauri or browser UI for this product.

Before sending a change:

1. Read the root `AGENTS.md` and `hmcl-ui/AGENTS.md`.
2. Keep DSH instances isolated under their own `DSH_HOME`.
3. Keep API keys in the OS credential store or environment references. Never put them in properties, packs, logs, tests, screenshots or issues.
4. Keep external Mods on DSH's `dsh plugin --profile` workflow.
5. Preserve HMCL copyright/GPL notices and publish the corresponding source with binary releases.

For Java/HMCL changes on Windows:

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'dshcraft-gradle-review'
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
.\gradlew.bat :HMCL:build --no-daemon
```

Describe the Java/HMCL workflow you changed and the real checks you ran. Do not call a source compile or a stale preview a successful desktop workflow test.
