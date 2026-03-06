#!/usr/bin/env python3
"""Apply stats_lib patches P7,P11 to retail libmmcamera2_stats_lib.so"""
import re, sys

path = sys.argv[1] if len(sys.argv) > 1 else 'libmmcamera2_stats_lib.so'
with open(path, 'rb') as f:
    data = f.read()

patches = [
    # P7: min_stable_cnt fix — BNE→B so telephoto always gets min_stable_cnt=3
    ("P7", re.escape(b'\x29\x08\xd1\x0b\xf1'),
           b'\x29\x08\xe0\x0b\xf1'),

    # P11a: af_haf_fine_search direction formula operand 0x00→0x0a
    ("P11a", re.escape(b'\x20\x0a\xea\x00'),
             b'\x20\x0a\xea\x0a'),

    # P11b: force initial direction=2 (toward infinity)
    ("P11b", re.escape(b'\x00\xe0\x00\x25\xda\xf8'),
             b'\x00\xe0\x02\x25\xda\xf8'),
]

for name, pattern, replacement in patches:
    m = re.search(pattern, data)
    if m:
        data = data[:m.start()] + replacement + data[m.end():]
        print(f"  {name}: patched at 0x{m.start():x}")
    else:
        print(f"  {name}: PATTERN NOT FOUND!")

with open(path, 'wb') as f:
    f.write(data)
print(f"Wrote {path}")
