#!/usr/bin/env python3
"""Apply q3a_core patches P1,P2,P3,P6,P8,P10 to retail libmmcamera2_q3a_core.so"""
import re, sys

path = sys.argv[1] if len(sys.argv) > 1 else 'libmmcamera2_q3a_core.so'
with open(path, 'rb') as f:
    data = f.read()

patches = [
    # P1: af_core_set_param case 5 — NOP bhi.w at 0x8321e (AF_INIT for AUTO/MACRO)
    ("P1", re.escape(b'\x03\x38\x02\x28\x00\xf2\x17\x82\x28\x46\x84\xf7'),
           b'\x03\x38\x02\x28\x00\xbf\x00\xbf\x28\x46\x84\xf7'),

    # P2: af_state=1 safety net at 0x83b3c
    ("P2", re.escape(b'\x30\x68\x61\x68\xd0\xf8\x64\x01\x00\x29\x75\xd0\x60\xb1'),
           b'\x30\x68\x61\x68\xd0\xf8\x64\x01\x01\x21\x61\x60\x60\xb1'),

    # P3: af_util_focus_mode_change — NOP bhi at 0x958da
    ("P3", re.escape(b'\x41\x58\x03\x39\x02\x29\x0e\xd8\x72\xf7\x4a\xe8'),
           b'\x41\x58\x03\x39\x02\x29\x00\xbf\x72\xf7\x4a\xe8'),

    # P6: PDAF direction trampoline at 0x8c116
    ("P6-trampoline", re.escape(b'\x38\x74\xc8\xbf\x01\x23'),
                      b'\x2a\xf0\x50\xbc\x00\xbf'),

    # P6: Code cave at 0xb69ba
    ("P6-cave", re.escape(b'\x4c\x3e\x00\x00\x00\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00'),
              b'\x4c\x3e\x00\x00\x00\x00\x00\x01\x06\xb4\x41\xf2\xb0\x51\x79\x58\x0a\x69\x12\x68\x02\x2a\x02\xd1\x89\x68\x08\x68\x38\x74\x06\xbc\xca\x45\xc8\xbf\x01\x23\xd5\xf7\xa0\xbb'),

    # P8: 0xec20 cascade bypass at 0x88dfe — B-always
    ("P8", re.escape(b'\x01\x29\x03\xd0\x4e\xf6\x20\x41\x61\x58\xd1\xb1'),
           b'\x01\x29\x03\xd0\x4e\xf6\x20\x41\x61\x58\x1a\xe0'),

    # P10: 0x3e94 cascade bypass at 0x88cd6 — BNE→B
    ("P10", re.escape(b'\x43\xf6\x94\x65\x60\x59\x01\x28\x24\xd1'),
            b'\x43\xf6\x94\x65\x60\x59\x01\x28\x24\xe0'),
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
