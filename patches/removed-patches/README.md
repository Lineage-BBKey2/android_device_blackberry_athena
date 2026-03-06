# Removed Camera Patches (stripped 2026-03-06)

All binary patches removed from extract-files.py to establish clean retail baseline.
Retail blobs: ACQ160 OREO (BlackBerry Key2)

## q3a_core patches (libmmcamera2_q3a_core.so)

### P1 — AF_INIT range check NOP (0x8321e)
- NOP bhi.w that skips AF_INIT for non-CAF modes (AUTO=1, MACRO=2)
- Enables tap-to-focus in AUTO/MACRO modes on HAL3

### P2 — af_state=1 safety net (0x83b3c)
- Force af_state=1 on mode transitions
- Safety net for mode change handling

### P3 — af_util_focus_mode_change range check (0x958da)
- NOP bhi that blocks focus mode change for non-CAF modes

### P6 — PDAF direction passthrough (trampoline 0x8c116 + cave 0xb69ba)
- Qualcomm bug: af_single_set_start_pos loads PDAF direction but ignores it
- Code cave checks depth_status==2, uses PDAF direction for HJ prescan

### P8 — 0xec20 cascade bypass (0x88dfe)
- B-always skips scene change stale flag check
- Prevents cascade from HJ backlash (lens never lands exactly at target)

### P10 — 0x3e94 cascade bypass (0x88cd6)
- BNE→B skips sparse PDAF stale flag
- Fires 1 frame after every SEARCH_DONE causing infinite cascade

## stats_lib patches (libmmcamera2_stats_lib.so)

### P7 — min_stable_cnt fix (0x18d3d)
- BNE→B so telephoto always gets min_stable_cnt=3
- Default returns 1 for telephoto = single PDAF frame triggers cascade

### P11 — af_haf_fine_search direction fix
- 0x18e26: direction formula operand 0x00→0x0a
- 0x194a4: force initial direction=2 (toward infinity)

## stats_modules patches (libmmcamera2_stats_modules.so)

### Sensitivity — PDAF actuator_sensitivity=1.0 fallback
- Injects default 1.0 when EEPROM provides 0.0
- Without this, PDAF depth processing skipped entirely

### P13 — crash fix (0x182a4 + 0x182e8)
- NOP two `ldr r1,[r0,#0xc]` that dereference NULL stats_data[8]
- Prevents SIGSEGV in CAM_AF thread during scene change

## Also previously applied but NOT in extract-files.py

### P12 — AEC_ROI NOP (q3a_core 0xf3c8)
- NOP aec_set_roi to prevent touch AE overexposure
- Was manual blob edit, never in extract-files.py

### P14 — Camera-selective 0x423c (q3a_core cave 0xb6a22)
- Hook at 0x88e00, cave handles per-camera 0x423c logic
- Was overwritten by P15

### P15 — Same-position detection (q3a_core cave 0xb6a22) — BROKEN
- Compared lens pos to block re-trigger if same position
- Bug: permanently blocks ALL CAF auto-refocus after few scene changes
