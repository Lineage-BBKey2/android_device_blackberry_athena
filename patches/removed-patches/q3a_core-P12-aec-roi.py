#!/usr/bin/env python3
"""Apply P12 AEC_ROI NOP to libmmcamera2_q3a_core.so
NOP aec_set_roi function to prevent touch AE overexposure.
Offset 0xf3c8: 80b5 88b0 → 0020 7047 (movs r0,#0; bx lr)"""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else 'libmmcamera2_q3a_core.so'
with open(path, 'rb') as f:
    data = f.read()

off = 0xf3c8
old = b'\x80\xb5\x88\xb0'  # push {r7,lr}; sub sp,#0x20
new = b'\x00\x20\x70\x47'  # movs r0,#0; bx lr

if data[off:off+4] == old:
    data = data[:off] + new + data[off+4:]
    print(f"  P12: patched at 0x{off:x}")
elif data[off:off+4] == new:
    print(f"  P12: already patched at 0x{off:x}")
else:
    print(f"  P12: unexpected bytes at 0x{off:x}: {data[off:off+4].hex()}")

with open(path, 'wb') as f:
    f.write(data)
print(f"Wrote {path}")
