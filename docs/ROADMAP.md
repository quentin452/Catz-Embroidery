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
| M2 | emb-model: stitch model + hatch/satin/trace/TSP | algorithms tolerance-compare (0.001 mm, D002) with Java model outputs on shared fixtures | **done** (D003 deferrals + D004 stroke below) |
| M3 | emb-draw + viewer | viewer renders a real design from emb-model via the draw list | **done** (user acceptance 2026-08-16: test.pes vs ThreadsES) |
| M4 | editor | stitch editing on real files; save via emb-data | **done** (user acceptance 2026-08-16; named exceptions below) |
| M5 | converter (UI) + infinite-draw + launcher hub + packaging | both apps work on real files; hub launches apps; exe builds | planned |

> Rows are re-ordered and their exit criteria tightened by the architecture ruling
> (D001) and by what the code finds. A row is `done` only when its exit criteria run green.

> **M2 is done with named deferrals (D003):** every vector-pure algorithm is
> ported and tolerance-compared against headless Java fixtures — model core,
> geometry, resample, hatch_parallel, hatchParallelComplex, TSP, trace
> (findContours + approxPolyDP), hatchParallelRaster + CROSS, isolines.
> Deferred to the converter phase (M5): PERLIN, spirals v2-v4,
> offset/inset, hatchInset, satin, the TANGENT stroke — each renders through
> `app.createGraphics` (Java2D rasterization) and/or `app.random` (measured in
> docs/LESSONS.md and ruled in docs/decisions/D003.md). The PERPENDICULAR
> stroke (`strokePolyNormal`) is ported 2026-08-16 as D004 (geometric distance
> oracle, invariant-tested — not compared against the Java, by that ruling).
>
> **Named exceptions (pin 3):** `resample(randomize != 0)` refuses with an error —
> no consumer uses it (RESAMPLE_NOISE = 0 everywhere); queued until a consumer asks.
>
> **M4 is done with named exceptions (the M4 ruling + D003):** the editor
> stitches only visible layers' PLY elements via the vector
> `hatch_parallel` (PARALLEL mode only). **2026-08-16: LIN elements entered
> with their M5 consumer** — the rasterised line's contour (the D004 oracle
> mask) strokes through the ported PERPENDICULAR stroke at the layer's
> stroke settings (colour + weight, now in the layer row; TANGENT stays
> deferred). TXT elements (font rasteriser) and the cull toggle (raster
> compositing) remain named exceptions. CONCENTRIC hatch (hatchInset) and
> the layer stroke mode toggle all enter with their consumers (M5, ruled in
> D003). The canvas preview shows the stitched design through
> `emb_draw::DrawList`, cached on a dirty flag (the Java's `needsUpdate`);
> TXT elements are drawn as raw drafts (they are not stitched).

## How to resume

Verify before believing any of this file. A green gate run means the repository is as the last
session left it:

```powershell
cargo test --workspace   # expect: all green, incl. the gate tests
cargo clippy --workspace --all-targets
cargo fmt --check
```

### Next-session strategy (wrap 2026-08-16, night — M5 nearly done)

State: **M0-M4 done; M5: converter ACCEPTED, launcher built, infinite
canvas + LIN tools + performance done; packaging is the last M5 piece.**
Branch `rs-greenfield`. Working tree clean, gates green
(`cargo test --workspace` incl. the gate tests, `cargo clippy
--workspace --all-targets`, `cargo fmt --check`).

**Done this session (11 commits, b174372..be8eb54):**

- **M5 slice 1 — converter** (D005): `emb_model::convert` (the whole raster
  pipeline, 12 invariant tests; PERLIN refuses), the `emb_converter` app
  (Java control surface, drag-drop/clipboard, stitched preview, PES/DST/SVG
  save), shared save helpers (`emb_data::write_design`/`file_title`,
  `Model::centered_design`). **ACCEPTED on cat.png** vs the Java harness
  (`tools/java-fixtures/GenConverterPng.java`; measured: outline 197=197,
  cross 1745 vs 1746; the pixel diff = the ruled D004/D005 deviations).
- **D006 + D007** (found by real files): the PES palette clamps to the 256
  the count byte can express; the writer measures deltas from the offset
  origin (the Java disagrees with its own offset words).
- **emb-launcher** (thin hub: version, en/fr, spawns the sibling exes).
- **Infinite canvas in emb-editor** (greenfield — the Java's infinite-draw
  is an empty skeleton): `Infinite` toggle (fit + save on the content).
- **Performance, measured**: the preview ran the TSP per refresh (D008: the
  TSP now stops at the first non-improving pass — a 4-circle save 265 s →
  0.34 s); edit-drag re-stitches at drag-stop; edit handles culled;
  **emb-egui** — the shared batched renderer (ONE mesh per frame, the three
  apps' single paint path).
- **Editor tools all coherent** (user verdict): LIN elements stitch via the
  D004 stroke (their M5 consumer — rasterised line contour, layer stroke
  settings in the layer row), raw TXT drafts drawn, and the element-local
  mask projection fixed (contours must return to the design space).
- **Save path audited end to end** (polygon + LIN, bounded + infinite,
  PES/DST/SVG): formats handle centred/negative designs; E2E regression
  test added.

**Next (prioritised, 1 = resume immediately):**

1. **Accept the launcher + the infinite canvas** (they are built, not
   accepted): `cargo run -p emb-launcher` and click the three buttons;
   in the editor, tick `Infinite`, draw far from the origin, save, reload
   the .pes in the viewer (it must be centred on the content).
2. **Packaging — exe builds** (the last M5 piece; the GitHub update check
   lands here). Then M5 is done.
3. **infinite-draw "dessin pur"** — a CatzEngine workstream (queued): the
   Java's infinite-draw is an empty skeleton, so the app is greenfield.
   `catz-render` is already decoupled (GPU-only, headless boot, offscreen
   capture). Needs: a ruling naming Catz-Embroidery as consumer (D110
   portfolio amendment), the external consumption route (git path dep;
   publish=false + proprietary licence), and the 2D draw line (D109
   refused a 2D path until a consumer exists — this app would be it).
4. **Editor follow-ups** (named exceptions): TXT stitching (font
   rasteriser), the cull toggle (raster compositing), CONCENTRIC
   (hatchInset), the stroke mode toggle (TANGENT).
5. **Converter follow-ups** (queued): PES input (rasterize a .pes back
   through the pipeline), the progress bar / background thread, Dropbox
   save (not ported).

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

PERLIN, strokePolyTangentRaster, hatchSpiral_v2-v4, offsetPolygon/inset,
hatchInset, satin — each renders through `app.createGraphics` (Java2D) and/or
`app.random`. D005 (2026-08-16) settled the converter's path: PERLIN refuses
as a named exception (disabled in the UI); the rest are not reached by the
converter's raster path (the converter's CONCENTRIC/SPIRAL dispatch to the
same `isolines` as the port). Do NOT claim any of them ported until a ruling
says how they are compared. (`strokePolyNormal` was ported 2026-08-16 — the
ruling is D004: a geometric distance oracle + invariant tests, not Java
fixtures.)

### Named exceptions (pin 3)

- `resample(randomize != 0)` refuses with an error — no consumer uses it.
