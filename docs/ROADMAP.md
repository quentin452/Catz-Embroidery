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

### Next-session strategy (wrap 2026-08-16, evening — M3 slice 1)

State: **M0, M1, M2 done** (D003 deferrals named below). Branch `rs-greenfield`.
All gates green. M3 slice 1 landed this session: **emb-draw** (the vocabulary:
`Rgb`, `VisualIdentity`, `Command`, `Bounds`, `DrawList::from_model` — commands
carry precomputed bounds so culling is O(1) per command) and **emb-viewer**
(eframe 0.35 glow, open PES via `Model::from_pes`, pan/zoom, viewport culling,
overview texture cache with LOD switch to vectors when zoomed in). The M3 exit
criterion is proven headlessly by `crates/emb-draw/tests/real_file.rs` (the
exact Open… pipeline on the committed fixture); the exe smoke-runs.

Decisions taken this slice (each documented where it lives):

- **eframe 0.35, not 0.36.1**: 0.36.1 needs rust 1.95, the toolchain is pinned
  at 1.92.0 (D001). `default-features = false`, `features = ["glow",
  "default_fonts"]` — wgpu/accesskit/x11/wayland off.
- **Design → Model conversion lives in emb-model** (`Model::from_design`, split
  on colour change and jump — the Java SVG writer's rule); **`Model::from_pes`**
  is the one loader every app uses. The viewer's matrix row stays
  `["emb-model", "emb-draw"]`; emb-model gained a runtime emb-data dep (its row
  already allowed it; matrix comment + ARCHITECTURE.md updated in the same
  commit).

Remaining M3 work (the viewer's first slice is a viewer, not a finished one):

1. **Manual acceptance on real designs** — run the exe on several PES files
   (designs with jumps, multiple colours, big designs) and check pan/zoom,
   the texture↔vector LOD switch, and the fit-on-open. The texture switch
   threshold (`canvas smaller than the cache texels`) is a code-review item
   until a benchmark gate exists (D001).
2. **Visual identity tuning** — `VisualIdentity::default()` (white canvas, grey
   backdrop, 0.4 mm stitch) is a first guess taken from the Java editor's look;
   tune it from what the screen shows.
3. **Texture invalidation on edit** is a non-issue until M4 edits; the
   rasteriser in `apps/emb-viewer/src/overview.rs` is the second renderer of
   the same draw list — keep it fed from `DrawList`, never from the model.

Then M4 — emb-editor (layer/element model, undo/redo).

### Deferred by D003 (reopened at M5, converter in front)

PERLIN, strokePolyNormal/Tangent, hatchSpiral_v2-v4, offsetPolygon/inset,
hatchInset, satin — each renders through `app.createGraphics` (Java2D) and/or
`app.random`. Do NOT claim any of them ported until a ruling says how they are
compared.

### Named exceptions (pin 3)

- `resample(randomize != 0)` refuses with an error — no consumer uses it.
