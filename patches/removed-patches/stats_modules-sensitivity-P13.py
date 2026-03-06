#!/usr/bin/env python3
"""Apply stats_modules patches (sensitivity + P13 crash fix) to retail libmmcamera2_stats_modules.so"""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else 'libmmcamera2_stats_modules.so'
with open(path, 'rb') as f:
    data = f.read()

patches = [
    # Sensitivity: inject actuator_sensitivity=1.0 when EEPROM provides 0.0
    ("sensitivity",
     b'\x14\xd1\x0d\xf5\x40\x5e\xbe\xf8\x2c\x40\x05\xe1',
     b'\x14\xd1\xb7\xee\x00\x8a\x00\xbf\x00\xbf\x14\xe0'),

    # P13: crash fix — NOP two ldr r1,[r0,#0xc] at 0x182a4 and 0x182e8
    # These dereference NULL stats_data[8] during scene change → SIGSEGV
    # Pattern-based: find the two crash instructions
]

for name, old, new in patches:
    idx = data.find(old)
    if idx >= 0:
        data = data[:idx] + new + data[idx+len(old):]
        print(f"  {name}: patched at 0x{idx:x}")
    else:
        print(f"  {name}: PATTERN NOT FOUND!")

# P13: direct offset patches (no unique pattern, use known offsets)
# 0x182a4: NOP ldr r1,[r0,#0xc]  (68 41 → 00 bf 00 bf)
# 0x182e8: NOP ldr r1,[r0,#0xc]  (68 41 → 00 bf 00 bf)
p13_offsets = [0x182a4, 0x182e8]
for off in p13_offsets:
    if data[off:off+2] == b'\xc1\x68':  # ldr r1,[r0,#0xc] = 68c1 in little-endian thumb
        data = data[:off] + b'\x00\xbf' + data[off+2:]
        print(f"  P13: NOP at 0x{off:x}")
    elif data[off:off+2] == b'\x00\xbf':
        print(f"  P13: already NOP at 0x{off:x}")
    else:
        print(f"  P13: unexpected bytes at 0x{off:x}: {data[off:off+2].hex()}")

with open(path, 'wb') as f:
    f.write(data)
print(f"Wrote {path}")
