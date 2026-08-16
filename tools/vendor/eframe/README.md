# eframe 0.35.0 — vendored with one patch

This is a copy of `eframe 0.35.0` from crates.io, with **one** modification.
The workspace's `[patch.crates-io]` points here so the viewer ships without the
white flash on startup.

## The patch (measured, 2026-08-16)

`src/native/glow_integration.rs`, in `run_ui_and_paint`:

- **Before (upstream):** `integration.post_rendering(&window)` (which reveals the
  window on the first frame) runs **before** `gl_surface.swap_buffers(...)`.
- **After (vendored):** the swap runs first, `post_rendering` second.

**Measurement:** on the hybrid-GPU dev laptop (NVIDIA GTX 1650 Ti + Intel UHD),
the first GL present takes ~665 ms (the discrete GPU waking up). With the
upstream order the window becomes visible before anything has been presented, so
the user sees a white window for ~665 ms at every launch. Swapping first means
the first (slow) present happens while the window is still hidden; the window
appears already painted. Confirmed fixed visually; the frame-1→frame-2 gap stays
(~600 ms of first-present warm-up) but is no longer visible.

The same order exists in eframe 0.36.1 (post_rendering at glow_integration.rs:838,
swap_buffers at :851) — not fixed upstream as of 2026-08-16.

## What keeps this honest

- `git diff` against crates.io `eframe-0.35.0` is exactly one hunk
  (the block above). If a future bump rewrites the file, re-check the order:
  `post_rendering` MUST come after `swap_buffers`.
- The pinned toolchain is 1.92.0; eframe 0.36.x needs 1.95. When the toolchain
  moves, try upstream again before carrying this forward.
