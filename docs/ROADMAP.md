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

### Next-session strategy (wrap 2026-08-16, M3 slice 2)

State: **M0, M1, M2 done** (D003 deferrals named below). Branch `rs-greenfield`.
All gates green. M3 slice 1 landed earlier today: **emb-draw** (vocabulary,
`DrawList::from_model`) and **emb-viewer** (eframe 0.35 glow, open PES, pan/zoom,
culling, overview texture cache). Slice 2 this evening:

- **White flash fixed** by vendoring eframe 0.35 with a one-hunk patch
  (present before reveal — `tools/vendor/eframe/README.md`), because the first
  GL present on this hybrid-GPU laptop takes ~665 ms and upstream reveals the
  window before the swap. Measured with samply; committed `e5e53fb`.
- **PES reader: record grammar + origin fixed** — the projection bug on
  test.pes (see the acceptance-bugs section below) turned out to be a 4-byte
  long-form assumption and ignored offsets; both fixed and regression-tested
  against pyembroidery (reference reader, `pip install pyembroidery`).
- **Viewer fits and textures the MOTIF, not the canvas** (committed `1b332b3`):
  the canvas is now a backdrop rect; `DrawList::content_bounds()` drives fit-on-open
  and the overview texture covers the motif (translated to its own origin), so
  a design sitting far from its declared hoop still renders centred and full.

Decisions taken (each documented where it lives):

- **eframe 0.35, not 0.36.1**: 0.36.1 needs rust 1.95, the toolchain is pinned
  at 1.92.0 (D001). Vendored with the reveal-order patch (above).
- **Design → Model conversion lives in emb-model** (`Model::from_design`, split
  on colour change and jump — the Java SVG writer's rule); **`Model::from_pes`**
  is the one loader every app uses. The viewer's matrix row stays
  `["emb-model", "emb-draw"]`; emb-model gained a runtime emb-data dep.
- **pyembroidery is the PES reference reader** (Ink/Stitch's engine): install
  with `pip install pyembroidery`; diff its stitch list against ours when a
  third-party PES misbehaves. libembroidery exists but its PEC long-form
  parsing is buggy — not a reference.

Remaining M3 work (in priority order):

1. **Manual acceptance on test.pes + simple.pes (the resume point)** — with the
   reader fixed, test.pes renders centred in its frame (characters + paws,
   like ThreadsES); check pan/zoom, texture↔vector LOD, fit-on-open, and the
   visual identity (`VisualIdentity::default()` is a first guess from the Java
   editor's look).
2. **Texture invalidation on edit** is a non-issue until M4 edits; keep the
   rasteriser in `apps/emb-viewer/src/overview.rs` fed from `DrawList`, never
   from the model.

Then M4 — emb-editor (layer/element model, undo/redo). First brick already in:
`emb_model::resample::StitchSettings` (Java's STITCH_LENGTH=10 /
MIN_STITCH_LENGTH=4 defaults, consumed by resample) — the editor's
stitch-frequency knob.

### M3 acceptance bugs found on test.pes (2026-08-16, unknown-provenance file)

Reported by the user after comparing against **ThreadsES** (Ink/Stitch could not
be installed). All three turned out to have ONE cause, fixed and regression-
tested (see below) — verify visually before claiming acceptance.

1. ~~XY projection is wrong: the PES renders in the wrong orientation, and the
   motif is not inside the drawn hoop.~~ **FIXED 2026-08-16.** The reader had
   two bugs, both in `emb-data/src/pes.rs` and both measured against
   pyembroidery (the Ink/Stitch PES engine, installed as a reference):
   - **Long-form records are not always 4 bytes.** The Brother grammar flags
     each axis word independently: a byte with bit 7 opens a 12-bit value over
     2 bytes, otherwise it is a 7-bit delta on its own. A record is 2, 3 or
     4 bytes. test.pes's writer (Ticetac) emits 3-byte records (a dy word
     without the 0x80 flag); a reader that forces 4 bytes shifts the stream
     and invents phantom points — the characters' paws appeared at x ≈ -2500,
     140 mm left of the motif, and the x span came out 2.1× too wide.
   - **The offset words are the design origin.** `0x9000 | -left`: test.pes's
     `0x9480 0x90A4` decode to origin (1152, 164) — with the deltas accumulated
     from there, the design fills the declared bounds exactly (0..1234 ×
     0..900), instead of sitting ~115 mm left of the canvas (the drawn hoop
     floated to the right of the characters).
   Both fixed + regression-tested: `pes_third_party_offsets_and_three_byte_
   records` on the committed `fixtures/test.pes` (31077 points, bounds filled
   exactly, no point outside the canvas). The old reading spanned x -2554..74.
   ThreadsES's 31083 count = our 31077 + the 6 colour-change STOP stitches the
   reference emits.
2. ~~Bug 2~~ — user-reported as caused by 1 or 3; not an independent bug.
3. ~~Two "ears" of the motif land in the wrong place~~ — **FIXED by 1**: the
   "ears" were the phantom paws, an artifact of the 4-byte-long misparse. The
   CSewSeg section of test.pes (the design as authored, centred on the hoop)
   confirmed the paws belong with the characters, sticking slightly out of the
   frame.

### Deferred by D003 (reopened at M5, converter in front)

PERLIN, strokePolyNormal/Tangent, hatchSpiral_v2-v4, offsetPolygon/inset,
hatchInset, satin — each renders through `app.createGraphics` (Java2D) and/or
`app.random`. Do NOT claim any of them ported until a ruling says how they are
compared.

### Named exceptions (pin 3)

- `resample(randomize != 0)` refuses with an error — no consumer uses it.
