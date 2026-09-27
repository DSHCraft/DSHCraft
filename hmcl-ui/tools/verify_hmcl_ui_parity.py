#!/usr/bin/env python3
from pathlib import Path
import hashlib, sys
root=Path(__file__).resolve().parents[1]
manifest=root/'DSHCRAFT_UI_BASELINE.sha256'
failed=[]
checked=0
for raw in manifest.read_text(encoding='utf-8').splitlines():
    if not raw.strip(): continue
    expected, rel=raw.split('  ',1)
    path=root/rel
    if not path.is_file():
        failed.append((rel,'missing'))
        continue
    actual=hashlib.sha256(path.read_bytes()).hexdigest()
    checked+=1
    if actual!=expected: failed.append((rel,actual))
print(f'checked={checked} protected={checked+sum(1 for _,v in failed if v=="missing")} failures={len(failed)}')
for rel,value in failed: print(f'FAIL {rel}: {value}')
sys.exit(1 if failed else 0)
