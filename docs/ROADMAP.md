# Catz-Embroidery — roadmap

**A phase is done when its exit criteria pass, not when its code is written.** Each phase below
names what stops it from being called done, and those are gates that run — not a checklist somebody
walks.

**Status: the phase table below is the single source of truth — every row is `done` or `planned`,
and a row is `done` only when its exit criteria run green.**

## Phase table

| Phase | Delivers | Exit criteria | Status |
|---|---|---|---|
| M0 | Scaffold: workspace, crates (emb-data/model/draw + 4 apps as empty skeletons), gates (arch.rs, memory.rs), docs | `cargo test --workspace` green incl. the gate tests; every crate carries `forbid(unsafe_code)`; matrix.toml rows match the manifests | **done** |
| M1 | emb-data: PES read+write, DST write, SVG write | round-trip and byte-compare against Java-generated fixtures; structured errors; no model dependency; each format has a named consumer (converter: PES/SVG, editor: DST) | **done** |
| M2 | emb-model: stitch model + hatch/satin/trace/TSP | algorithms tolerance-compare (0.001 mm, D002) with Java model outputs on shared fixtures | **done** (see D003 deferrals below) |
| M3 | emb-draw + viewer | viewer renders a real design from emb-model via the draw list | planned |
| M4 | editor | stitch editing on real files; save via emb-data | planned |
| M5 | converter (UI) + infinite-draw + launcher hub + packaging | both apps work on real files; hub launches apps; exe builds | planned |

> Rows are re-ordered and their exit criteria tightened by the architecture ruling
> (D001) and by what the code finds. A row is `done` only when its exit criteria run green.

> **M2 is done with named deferrals (D003):** every vector-pure algorithm is
> ported and tolerance-compared against headless Java fixtures — model core,
> geometry, resample, hatch_parallel, hatchParallelComplex, TSP, trace
> (findContours + approxPolyDP), hatchParallelRaster + CROSS, isolines.
> Deferred to the converter phase (M5): PERLIN, strokes, spirals v2-v4,
> offset/inset, hatchInset, satin — each renders through `app.createGraphics`
> (Java2D rasterization) and/or `app.random` (measured in docs/LESSONS.md and
> ruled in docs/decisions/D003.md).
>
> **Named exceptions (pin 3):** `resample(randomize != 0)` refuses with an error —
> no consumer uses it (RESAMPLE_NOISE = 0 everywhere); queued until a consumer asks.

## How to resume

Verify before believing any of this file. A green gate run means the repository is as the last
session left it:

```powershell
cargo test --workspace   # expect: all green, incl. the gate tests
cargo clippy --workspace --all-targets
cargo fmt --check
```

### Next-session strategy (wrap 2026-08-16)

State: **M0, M1, M2 done** (D003 deferrals named below). Branch `rs-greenfield`.
All gates green; the Java suite in `src/` is untouched and is the MODEL, never a
source (pin 2).

1. **M3 — emb-draw + emb-viewer** (the immediate resume):
   - `emb-draw`: the shared draw-list vocabulary (commands + visual identity) —
     the contract between the apps and the renderer; emb-model types only.
   - `emb-viewer`: the first egui/eframe app — **glow backend** (OpenGL 2.1+,
     D001), viewport culling, overview texture cache. Renders a design loaded
     via emb-data (PES reader) from the model.
   - Dependency decisions to make first: egui/eframe version + features
     (default-features off for wgpu, glow on), and the matrix.toml row for
     emb-viewer (already `allow = ["emb-model", "emb-draw"]`).
2. **M4 — emb-editor** (the layer/element model of the Java editor, undo/redo).
3. **M5 — emb-converter + emb-infinitedraw + emb-launcher + packaging** — the
   converter reopens the **D003 deferrals**: PERLIN, strokes, spirals v2-v4,
   offset/inset, hatchInset, satin (all Java2D-rasterized and/or random; see
   docs/decisions/D003.md and the measured notes in docs/LESSONS.md). The
   Java2D-rasterization question is decided there (raster-level comparison, a
   rasterizer port, or a deviation ruling).

### Deferred by D003 (reopened at M5, converter in front)

PERLIN, strokePolyNormal/Tangent, hatchSpiral_v2-v4, offsetPolygon/inset,
hatchInset, satin — each renders through `app.createGraphics` (Java2D) and/or
`app.random`. Do NOT claim any of them ported until a ruling says how they are
compared.

### Named exceptions (pin 3)

- `resample(randomize != 0)` refuses with an error — no consumer uses it.
