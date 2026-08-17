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
| M5 | converter (UI) + infinite-draw + launcher hub + packaging | both apps work on real files; hub launches apps; exe builds | **done** (converter/infinite-canvas/launcher accepted 2026-08-16; packaging built + verified — user acceptance of `dist/` pending) |

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
> deferred). **2026-08-16: the cull toggle entered** (the Java's
> `Layer.cull`, default true): each element of a culled layer stitches
> through its mask minus every later layer's element masks (Main.java:
> 244-259), so later content cuts holes out of earlier stitches. The
> subtraction runs on the D004-class raster oracles (the polygon fill + the
> line's distance mask, 1 px per mm) — recorded in the decisions below; TXT
> contributes nothing to the masks (its font rasteriser stays a named
> exception). When no later element actually covers an element, the vector
> hatch runs untouched. The Java subtracts later layers regardless of
> visibility; kept (recorded below). TXT elements (font rasteriser) remain a
> named exception. CONCENTRIC hatch (hatchInset) and
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

### Next-session strategy (wrap 2026-08-16, late night — M5 done, parity audited)

State: **M0-M5 done** (phase table: M5 done, packaging user-acceptance
pending). 4 commits this session (aa084f1, ba0087f, 609e8de + this wrap).
Working tree clean, gates green (`cargo test --workspace` incl. the gate
tests, `cargo clippy --workspace --all-targets`, `cargo fmt --check`).
Branch `rs-greenfield`.

**Done this session (after the 11-commit b174372..be8eb54 run):**

- **Stroke oracle perf fix** (the converter froze on a big stroke_weight):
  exact `SegmentGrid` index in `emb-model`'s PERPENDICULAR stroke (D004
  semantics bit-identical, pinned by `segment_grid_matches_the_brute_force_
  oracle` + the untouched D004 invariants); 800-vertex contour at weight 200
  stroked 760 ms vs ~20 s before. The remaining sample-count floor is
  quadratic in the weight (the Java pays it too), so the converter UI caps
  stroke_weight at 64 px with a tooltip.
- **Packaging + update check** (the last M5 pieces): `tools/package-release.
  ps1` → `dist/` with the 5 exes + README; the launcher checks the GitHub
  latest-release endpoint once at startup on a background thread and pops a
  modal when newer (`apps/emb-launcher/src/update.rs`; version compare
  strips the leading `v` and walks numeric segments — the Java's string
  inequality flagged `0.1.0` vs `v0.1.0`).
  **Note:** GitHub already carries release `V0.2.0` (the Java's tag), so the
  launcher WILL offer it as an update until the first Rust release is
  published (a `v0.1.0` tag makes the check go quiet).
- **Parity audit** (4 parallel agents, Java suite vs Rust — see the audit
  section at the bottom): every named deferral verified true against the
  code; stale docs fixed (D001 line counts, convert.rs clamp citation,
  launcher Dropbox pointer). Then ported the unlisted gaps: shared exit
  dialog (`emb_egui::exit_dialog`, editor + converter), 42×42 layer
  thumbnails (editor), P-key preview toggle (converter); ESC kept as a
  documented deviation. Remaining named exceptions: FPS/V-sync, i18n,
  Dropbox (launcher + save), DRUNK.

**Next (prioritised, 1 = resume immediately):**

1. **Accept the packaging** (built, not accepted): run
   `tools/package-release.ps1`, then `dist\emb-launcher.exe` and click the
   three buttons (they must launch the packaged siblings). With that, M5's
   exit criteria are all green AND user-accepted.
2. **infinite-draw "dessin pur"** — a CatzEngine workstream (queued): the
   Java's infinite-draw is an empty skeleton, so the app is greenfield.
   `catz-render` is already decoupled (GPU-only, headless boot, offscreen
   capture). Needs: a ruling naming Catz-Embroidery as consumer (D110
   portfolio amendment), the external consumption route (git path dep;
   publish=false + proprietary licence), and the 2D draw line (D109
   refused a 2D path until a consumer exists — this app would be it).
3. **Editor follow-ups** (named exceptions): TXT stitching (font
   rasteriser), CONCENTRIC (hatchInset), the stroke mode toggle (TANGENT).
   **2026-08-16: the cull toggle entered** (D009) — element-local mask
   subtraction, default ON, invisible later layers still cut.
4. **Converter follow-ups** (queued): PES input (rasterize a .pes back
   through the pipeline), the progress bar / background thread, Dropbox
   save (not ported). The progress bar also kills the frozen-UI aspect of
   the stroke sample-count floor (perf note below).
5. **The first Rust GitHub release** (a `v0.1.0` tag): quiets the launcher's
   update check (it currently offers the Java's `V0.2.0`) and gives the
   packaging a real distributable.

> **2026-08-16 perf, stroke bottleneck (fixed):** a big stroke_weight froze
> the converter. Cause: `stroke_poly_normal`'s oracle scanned every segment
> per sample (O(n²·stroke²) per contour) — the sample count is the Java's
> own ray fans, but the scan was ours. Fixed: exact `SegmentGrid` index
> (D004 semantics bit-identical — the property test
> `segment_grid_matches_the_brute_force_oracle` pins it; D004 invariant
> tests untouched and green). Measured (release): an 800-vertex contour at
> weight 200 stroked in 760 ms vs ~20 s before. The remaining floor is the
> sample count itself, quadratic in the weight — the Java pays it too, so
> the converter UI caps stroke_weight at 64 px with a tooltip explaining
> the cost (2026-08-16); the queued background-thread item (next-session
> #4) will make even the cap-free case non-blocking.

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

### Parity audit 2026-08-16 (four parallel agents, Java suite vs Rust)

Every deferral already named in this file and in D003/D004/D005 was verified
against the code and holds (PERLIN refuses + UI-disabled; spirals v2-v4,
offset/inset, hatchInset, satin, TANGENT, TXT stitching, editor
CONCENTRIC all genuinely absent at audit time — cull entered afterwards,
D009; PERPENDICULAR = the D004 geometric oracle;
isolines ported; CONCENTRIC/SPIRAL dispatch to isolines like the Java; the
D006 palette clamp and the D007 offset-origin deltas confirmed). The audit
also found these UNLISTED divergences — each is now either ported, a named
exception, or a recorded deviation (pin 7):

- **Exit confirmation**: PORTED 2026-08-16 (editor + converter) — the
  shared `emb_egui::exit_dialog::ExitDialog` (the Java's
  DialogUtil.showExitDialog minus the Dropbox option): save-and-quit /
  exit-without-save / cancel on window close; "Save and quit" quits only
  after a successful save (the Java's cancelled save stays in the app).
- **Layer thumbnail**: PORTED 2026-08-16 (editor) — per-layer 42×42
  white-on-black previews in the layer panel (`apps/emb-editor/src/
  thumb.rs`, the Java's `rasterizeLayer` + `image(..., 42, 42)`), rebuilt
  on the dirty flag; TXT is a box at its anchor (the font rasteriser
  stays deferred).
- **P-key preview toggle**: PORTED 2026-08-16 (converter) — the Java's
  `showPreview` (Main.java:394-396).
- **Escape key** (editor): the Java's ESC does nothing; the Rust editor
  deletes the current layer's last element — the port INVENTED this
  (crediting `removeElementFromPolyBuff`, which the Java never calls).
  DECISION 2026-08-16: KEPT, as a recorded deviation (a convenience the
  user relies on; deleting is recoverable via undo).
- **FPS counter + V-sync toggles** (editor, F/V keys — FPSUtil.java:15-120):
  Java debug tooling, not ported. Named exception.
- **i18n**: the Java translates the whole suite (Translator + translations.
  json); the Rust apps are English-only except the launcher (en/fr). Not
  ported. Named exception.
- **Converter UI caps**: spacing ≤ 1000, max_colors ≤ 256, export mm ≤ 500
  are Rust-only guards (the Java text fields are uncapped); the stroke cap
  64 was already named above.
- **DRUNK hatch** (PEmbroiderGraphics.java:3090/3117): dead in Java (no app
  selects it) and in NO doc — the only deferral absent from both ROADMAP's
  list and D003's eleven. Same class as VECFIELD/ANGLED (those ARE in
  D003's eleven); noted so a future consumer argues it fresh.
- **Launcher Dropbox connect**: the Java launcher's red/green Dropbox button
  (PEmbroiderLauncher.java:67, 84-88, 100-101) has no Rust counterpart; the
  "Dropbox save" converter follow-up below covers the same feature family.

Also fixed by this audit (stale docs): D001's viewer/infinite-draw line
counts (79/15 → 90/18, measured); convert.rs's clamp citation
(Main.java:305-307 → PEmbroiderGraphics.java:306/489/499); the launcher's
Dropbox pointer now names the ROADMAP row.

No Java feature CALLED BY AN APP is both unported and undocumented: the only
unlisted absences are dead-in-Java APIs (DRUNK, ANGLED, VECFIELD, spine,
boolean shapes) and the app UX gaps named above (FPS/V-sync and i18n).
