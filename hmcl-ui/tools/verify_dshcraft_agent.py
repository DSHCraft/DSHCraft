#!/usr/bin/env python3
"""Source-level verification for the DShCraft direct-HMCL Agent layer."""
from __future__ import annotations

from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "HMCL/src/main/java/org/jackhuang/hmcl"
LANG = ROOT / "HMCL/src/main/resources/assets/lang"

failures: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def keys(path: Path) -> set[str]:
    result: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.lstrip().startswith(("#", "!")) or "=" not in line:
            continue
        result.add(line.split("=", 1)[0])
    return result


require(not (JAVA / "ui/agent/AgentNativeListPage.java").exists(), "AgentNativeListPage must stay removed")
require((JAVA / "ui/SearchableListPage.java").exists(), "shared SearchableListPage missing")
require((JAVA / "ui/TwoLineActionListCell.java").exists(), "shared TwoLineActionListCell missing")

checks = {
    JAVA / "ui/instances/GameListPage.java": "extends SearchableListPage<GameListItem>",
    JAVA / "ui/instances/GameListCell.java": "extends TwoLineActionListCell<GameListItem>",
    JAVA / "ui/agent/AgentListPage.java": "extends SearchableListPage<T>",
}
for path, needle in checks.items():
    require(needle in path.read_text(encoding="utf-8"), f"{path.name} is not using shared HMCL UI: {needle}")

required_services = [
    "AgentBackupService.java",
    "AgentDiagnostics.java",
    "AgentNetworkService.java",
    "AgentPackService.java",
]
for name in required_services:
    require((JAVA / "agent" / name).exists(), f"missing Agent service: {name}")

en = keys(LANG / "I18N.properties")
zh = keys(LANG / "I18N_zh_Hans.properties")
agent_en = {key for key in en if key.startswith("agent.")}
agent_zh = {key for key in zh if key.startswith("agent.")}
require(agent_en == agent_zh, f"Agent i18n key mismatch: EN-only={sorted(agent_en-agent_zh)} ZH-only={sorted(agent_zh-agent_en)}")

refs: set[str] = set()
for path in JAVA.rglob("*.java"):
    refs.update(re.findall(r'i18n\("(agent\.[^"]+)"', path.read_text(encoding="utf-8", errors="replace")))
require(refs <= agent_en, f"missing English Agent i18n keys: {sorted(refs-agent_en)}")
require(refs <= agent_zh, f"missing Chinese Agent i18n keys: {sorted(refs-agent_zh)}")

parity = subprocess.run(
    [sys.executable, str(ROOT / "tools/verify_hmcl_ui_parity.py")],
    cwd=ROOT,
    capture_output=True,
    text=True,
)
if parity.returncode != 0:
    failures.append("protected HMCL UI parity failed: " + (parity.stdout + parity.stderr).strip())

print(f"agent_keys={len(agent_en)} refs={len(refs)} protected={parity.stdout.strip()}")
if failures:
    for failure in failures:
        print("FAIL:", failure)
    raise SystemExit(1)
print("DShCraft Agent source verification: PASS")
