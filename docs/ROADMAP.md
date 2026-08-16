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
> `hatch_parallel` (PARALLEL mode only). LIN elements (stroke path),
> TXT elements (font rasteriser), CONCENTRIC hatch (hatchInset) and the
> layer stroke settings + cull toggle all enter with their consumers (M5,
> ruled in D003). The canvas preview shows the stitched design through
> `emb_draw::DrawList`, cached on a dirty flag (the Java's `needsUpdate`).

## How to resume

Verify before believing any of this file. A green gate run means the repository is as the last
session left it:

```powershell
cargo test --workspace   # expect: all green, incl. the gate tests
cargo clippy --workspace --all-targets
cargo fmt --check
```

### Next-session strategy (wrap 2026-08-16, late — converter slice 1 accepted)

State: **M0-M4 done, M5 slice 1 (converter) ACCEPTED 2026-08-16** (cat.png,
Java vs Rust PNG comparison — every measured difference maps to a ruled
deviation, see below). Branch `rs-greenfield`. Working tree clean, gates
green.

**Done this session (M5 slice 1, converter):**

- `emb_model::convert` (D005): the whole raster pipeline — binarize (the
  THRESHOLD max(r,g,b) >= 127 gate) → findContours → drop <3 →
  approxPolyDP(1) → fill (PARALLEL/CROSS/CONCENTRIC/SPIRAL) →
  PERPENDICULAR outline (D004) → colors (B&W black / MultiColor
  deterministic palette / Realistic histogram+resample) — 12 invariant +
  determinism tests; PERLIN refuses (`Error::PerlinNotPorted`, disabled in
  the UI, D005).
- `emb_converter` app: load button / drag-drop / Ctrl+V (arboard), the
  Java's control surface (hatch+color dropdowns, fill toggle, spacing,
  stroke weight, max colors, export mm), source image + stitched preview
  overlaid through the shared draw list, save → optimize() → centred
  PES/DST/SVG (never scaled, D005). E2E test: PNG file → pipeline → PES →
  read-back.
- D006 (found by the first real multicolor fill): the PES palette is the
  design's unique colours, clamped to the 256 the count byte can express;
  the reader wraps colour changes onto the palette.
- Shared save helpers extracted (derivation, not a copy): `emb_data::
  write_design` + `file_title`, `Model::centered_design(title, w, h)` — the
  editor now calls them too.
- `tools/java-fixtures/GenConverterPng.java`: the headless Java acceptance
  harness (also measured the Java's flat-stream multicolor artifact:
  `pushPolyline` adds N-1 colour entries per polyline, the writer reads one
  per polyline — `colors.size() == polylines.size()` despite 48.6 avg
  points per polyline, colours cycle `gc[j % k]`).

**Measured acceptance facts (cat.png):** outline polylines **197 = 197**
(Java = Rust); cross fill 1745 vs 1746 (one bar at a contour seam — the
D004 comparison level). The outline pixel diff = the two ruled deviations
(D004 stroke: the Java's thin fringe offset from the contour vs the port's
ribbon on the path — 2.08× painted pixels; D005 colours) + the D005-noted
resize pre-step (fill lines differ ~10% on real-image edges; the M2 fixture
fills are solid shapes, unaffected). Harness trap (in its header): a
headless `PGraphicsJava2D` needs `colorMode()` — `PApplet.color()` delegates
to `g.color()` when `g != null`, and uninitialized colorMode fields are 0.

**Next (the roadmap's M5 order):**

1. ~~emb-launcher~~ — **built 2026-08-16** (thin egui menu, matrix
   `allow = []`: version line, en/fr dropdown, editor/converter/viewer
   buttons that spawn the sibling exes; the Java's viewer was a TODO, ours
   exists). Not accepted yet — `cargo run -p emb-launcher`, click the
   buttons. Not ported (nothing consumes them today): the GitHub update
   check (no releases exist; lands with packaging) and Dropbox connect
   (save paths are local-only).
2. **Infinite canvas in emb-editor — built 2026-08-16** (the "éditeur sans
   contraintes de canvas", greenfield: the Java has no model — its
   infinite-draw is an empty skeleton). The `Infinite` toggle in the
   toolbar: no hoop (the view fits the drawn content,
   `Document::content_bounds`), the save centres on the content
   (`write_out_content_centered`). The build exposed **D007**: the PES
   writer measured deltas from 0 while its offset words declared the origin
   `-bounds[0]` — a latent misalignment for any nonzero-origin design
   (fixed: deltas from the origin; M1 fixtures unchanged). Not accepted
   yet: draw away from the origin in infinite mode, save, reload in the
   viewer.
3. **infinite-draw, redefined 2026-08-16**: the Java's infinite-draw is an
   empty skeleton — nothing to port. The user's split: the suite's
   stitching stays on egui/emb-draw (works on many PCs), while a
   **pure-drawing infinite canvas** ("dessin pur", no stitching) rides the
   CatzEngine renderer (`catz-render` is already decoupled: GPU-only,
   headless boot, offscreen capture; no shell coupling). Queued — it is a
   CatzEngine workstream: a ruling naming Catz-Embroidery as consumer
   (D110 portfolio amendment), the external consumption route (git path
   dep; publish=false + proprietary licence), and the 2D draw line (D109
   refused a 2D path until a consumer exists — this app would be it).
4. **Packaging**: exe builds.
5. Converter follow-ups (queued, not blocking): PES input (rasterize the
   stitches of a .pes back through the pipeline — D005 refused it), the
   progress bar / background thread, Dropbox save (not ported).

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
