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
| M3 | emb-draw + viewer | viewer renders a real design from emb-model via the draw list | **in progress** (emb-draw done + viewer first slice, see wrap) |
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

### Next-session strategy (wrap 2026-08-16, late — M3 slice 2)

State: **M0, M1, M2 done** (D003 deferrals named below). Branch `rs-greenfield`.
All gates green. M3 slice 1 landed earlier today: **emb-draw** (vocabulary,
`DrawList::from_model`) and **emb-viewer** (eframe 0.35 glow, open PES, pan/zoom,
culling, overview texture cache). Slice 2 this evening:

- **White flash fixed** by vendoring eframe 0.35 with a one-hunk patch
  (present before reveal — `tools/vendor/eframe/README.md`), because the first
  GL present on this hybrid-GPU laptop takes ~665 ms and upstream reveals the
  window before the swap. Measured with samply; committed `e5e53fb`.
- **PES reader tolerates a missing 0xFF end marker** when the declared block
  length is respected (`pes_accepts_a_missing_end_marker...` test) — the same
  family as the DST END-record ruling. Committed `8e84837`.
- **Viewer fits and textures the MOTIF, not the canvas** (uncommitted): the
  canvas is now a backdrop rect; `DrawList::content_bounds()` drives fit-on-open
  and the overview texture covers the motif (translated to its own origin), so
  a design sitting far from its declared hoop still renders centred and full.

Decisions taken (each documented where it lives):

- **eframe 0.35, not 0.36.1**: 0.36.1 needs rust 1.95, the toolchain is pinned
  at 1.92.0 (D001). Vendored with the reveal-order patch (above).
- **Design → Model conversion lives in emb-model** (`Model::from_design`, split
  on colour change and jump — the Java SVG writer's rule); **`Model::from_pes`**
  is the one loader every app uses. The viewer's matrix row stays
  `["emb-model", "emb-draw"]`; emb-model gained a runtime emb-data dep.

Remaining M3 work (in priority order):

1. **PES projection bug on test.pes (the resume point)** — the reader produces
   phantom points: our x span is 2628 vs ThreadsES's 1234 (y matches within
   1 %), and we read 6 stitches fewer than the reference. Investigate record
   by record where the read diverges; then settle unit (mm vs 0.1 mm), then
   y-flip, then PEC offsets. All three bugs (projection, ears, derived) are
   consigned in the section below with the measurements.
2. **Manual acceptance** on the fixed test.pes + the committed fixture —
   pan/zoom, texture↔vector LOD, fit-on-open; visual identity tuning
   (`VisualIdentity::default()` is a first guess from the Java editor's look).
3. **Texture invalidation on edit** is a non-issue until M4 edits; keep the
   rasteriser in `apps/emb-viewer/src/overview.rs` fed from `DrawList`, never
   from the model.

Then M4 — emb-editor (layer/element model, undo/redo).

### M3 acceptance bugs found on test.pes (2026-08-16, unknown-provenance file)

Reported by the user after comparing against **ThreadsES** (Ink/Stitch could not
be installed). Each needs investigation and a fix decision; none is claimed
fixed until verified against the reference viewer on the same file.

1. **XY projection is wrong: the PES renders in the wrong orientation, and the
   motif is not inside the drawn hoop.** The reader ignores the PEC offsets
   (x-offset/y-offset in the block header) and returns `bounds = [0, 0, w, h]`
   while the stitches sit far outside that rect. Whether the y axis needs
   flipping (machine coords vs screen coords) must be measured against the
   reference viewer before fixing. The DST writer flips y — the PEC likely
   does too.

   **Measured on test.pes (ThreadsES vs ours):**
   - ThreadsES: design size **1234×1378**, **31083** stitches.
   - Ours: stitched size **2628×1396**, **31077** points (after from_design).
   - The y span matches within ~1% (1396 vs 1378); the x span does NOT
     (2628 vs 1234). Our reader produces points far left of the motif that
     the reference viewer does not have — a record mis-read (phantom
     coordinates), not just a unit or offset problem.
   - 31083 − 31077 = 6 stitches: we read FEWER points than the reference.
   - If ThreadsES sizes are in 0.1 mm, its motif is 123.4×137.8 mm; our raw
     span is 2628×1396 units (262.8×139.6 mm at 0.1 mm) — width off by ~2.1×.

   Next step before fixing: locate where our x span diverges (which records
   read wrong → phantom points), then settle unit, then y-flip, then offsets.
2. ~~Bug 2~~ — user-reported as caused by 1 or 3; not an independent bug.
3. **Two "ears" of the motif land in the wrong place** — likely a consequence
   of the projection bug (wrong origin/y-flip shifting whole polylines), but
   must be confirmed once 1 is fixed; could also be a jump/colour-run
   mis-grouping in `Model::from_design`.

Then M4 — emb-editor (layer/element model, undo/redo).

### Deferred by D003 (reopened at M5, converter in front)

PERLIN, strokePolyNormal/Tangent, hatchSpiral_v2-v4, offsetPolygon/inset,
hatchInset, satin — each renders through `app.createGraphics` (Java2D) and/or
`app.random`. Do NOT claim any of them ported until a ruling says how they are
compared.

### Named exceptions (pin 3)

- `resample(randomize != 0)` refuses with an error — no consumer uses it.
