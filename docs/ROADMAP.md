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
| M5 | converter (UI) + launcher hub + packaging | both apps work on real files; hub launches apps; exe builds | **done** (converter/infinite-canvas/launcher accepted 2026-08-16; packaging accepted 2026-08-17 — `dist/` launched and tested; **the infinite-draw app was removed 2026-08-17** — the work moved to CatzEngine, D001 amendment) |

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
> contributes its Hershey glyph strokes' masks since 2026-08-17 (D011 — see
> the TXT entry below). When no later element actually covers an element, the vector
> hatch runs untouched. The Java subtracts later layers regardless of
> visibility; kept (recorded below). **2026-08-17: CONCENTRIC entered** (D010) —
> the editor's CONCENTRIC is the isolines RASTER path (the Java calls `E.image()`, which
> routes CONCENTRIC to `hatchRaster` → `isolines`; hatchInset, the vector path
> the D003 deferral named, is never called). Both the isolines and the contour
> tracer were already ported and fixture-compared in M2, so the editor's
> CONCENTRIC is wiring: each PLY element's fill mask (culled first when cull
> is on) feeds `emb_model::hatch::isolines` at the layer's spacing, rings
> inside the shape. The boundary contour (the Java's `!isStroke` push) and
> the layer stroke mode toggle enter together (D003). **2026-08-17: TXT
> entered** (D011) — the Hershey SIMPLEX vector font (`emb_model::font`, the
> Java suite's OWN `PEmbroiderFont.putText`, fixture-compared within D002), a
> documented deviation from the Java editor's Java2D text raster (D003-class);
> TXT strokes through the layer's PERPENDICULAR stroke like a LIN and its
> glyph strokes contribute cull masks. **2026-08-17: the stroke-mode toggle
> entered** (D012) — the layer's `strokeMode` (the Java dialog) selects
> PERPENDICULAR (the D004 oracle) or TANGENT (`emb_model::stroke::
> stroke_poly_tangent`, a D004-style geometric offset oracle: the path's
> parallel curves at each band's distance, miter formula + winding-aware
> side guard, invariant-tested not fixture-compared). The canvas preview shows
> the stitched design through
> `emb_draw::DrawList`, cached on a dirty flag (the Java's `needsUpdate`);
> TXT elements are drawn as raw drafts (a visual placeholder, not the stitch;
> the stitched anchor is the baseline-left the putText port uses).

## How to resume

Verify before believing any of this file. A green gate run means the repository is as the last
session left it:

```powershell
cargo test --workspace   # expect: all green, incl. the gate tests
cargo clippy --workspace --all-targets
cargo fmt --check
```

### Next-session strategy (wrap 2026-08-17 — cross-compile promoted, converter bugs found)

State: **M0-M5 done; all editor follow-ups DONE** (cull D009, CONCENTRIC
D010, TXT D011, TANGENT D012); **first Rust GitHub release v0.1.0 published**
(2026-08-17); **Linux cross-compile VALIDATED on the Arch box and promoted
to the standard Linux build** (2026-08-17). Commits 2026-08-17 (main):
f50f19a (TANGENT stroke, D012), 7c86a13 (Linux build + release skill +
CHANGELOG + opencode.json), 21bd9bd (cross-compile via cargo-zigbuild +
launcher rustls), 3e9de3e + 3cee9d3 (Linux zip exec bit + Unix host). Working
tree clean, gates green (`cargo test --workspace` incl. the gate tests,
`cargo clippy --workspace --all-targets`, `cargo fmt --check`). Branch `main`.

**2026-08-17 (the cross-compile session):** the Linux build is now
cross-compiled from Windows with `cargo zigbuild` (`x86_64-unknown-linux-gnu`,
all 4 apps ELF, glibc ≤ 2.27) — validated on the Arch box after two zip bugs
were fixed (`tools/zip-linux.py`: the exec bit AND the Unix host marker, both
required or `unzip` extracts 0644). The draft `v0.1.0-linux-test` was deleted
after validation. The native `tools/package-release.sh` remains as an
optional per-box alternative; the release skill documents cross-compile as
the standard path. The launcher's update check switched from `native-tls`
(openssl on Linux) to ureq `tls` (rustls) to cross-compile cleanly.

**Next (prioritised, 1 = resume immediately):**

1. ~~**Converter bug A — converting on every param change spams the pipeline.**~~
   **DONE 2026-08-17**: the converter now converts on release — a `last_change`
   debounce (200 ms) between a knob change and the conversion, so a drag mutates
   the params but the pipeline runs once when the user lets go (the Java
   converts on button press). The source load converts immediately (no debounce).
2. ~~**Converter bug B — the saved design does not match the preview.**~~
   **DONE 2026-08-17, same root as editor bug C**: the save used
   `centered_design`, which shifted the content into coordinates the PES
   reader does not restore (it returns bounds `[0,0,w,h]` while the stitches
   keep their absolute shifted positions). A converter design (1000 units) was
   declared on a 95-unit hoop — 10× too big on reload; an editor design was
   shifted negative, appearing off-centre in the viewer. Fix (D005 amended):
   the save writes the design's NATIVE space (`Model::to_design`); the preview
   and viewer both fit the motif, so save == preview == reload. The converter's
   export width/height knobs were removed (dead controls — pin 3); the editor
   and converter bounded saves no longer centre; `Model::centered_design` was
   deleted (no caller, pin 3). The editor's infinite-canvas mode keeps its
    content-centring (its own tested intent). Gates green.
    **2026-08-17 (same session): the editor preview is now aligned on the
    save** — `refresh_stitched` applies the TSP `optimize()` too, so the
    on-screen stitch order matches the saved file (the old "preview skips the
    TSP" compromise made the viewer's saved design look differently ordered).
    Measured: ~9 ms on a 150-polyline hatch, ~40 ms on 300, on a document
    change not per frame. `optimize()` itself was measured worthwhile: it cuts
    ~50% of the thread travel at save (140857 → 71345 mm on a test hatch).
3. **Cut the next release** (when ready): use `.claude/skills/release/`
   (works in opencode via `opencode.json` and in Claude Code). Confirm scope
   in `docs/CHANGELOG.md`'s `[Unreleased]` first; ship Linux + Windows zips.
4. **Remaining named exceptions / dead-in-Java APIs** (not queued work, for
   the record): FPS/V-sync, i18n, Dropbox (OUT OF SCOPE); PERLIN, spirals
   v2-v4, offset/inset, hatchInset, satin (deferred by D003, no consumer);
   `resample(randomize != 0)` refuses.

> **2026-08-17 earlier wrap (kept as history):** the previous session's
> strategy follows below — converter complete, packaging accepted, Dropbox
> closed, branch rs-greenfield.

### Next-session strategy (wrap 2026-08-17 — converter complete, Dropbox closed)

State: **M0-M5 done, all exit criteria green AND user-accepted** (packaging
accepted 2026-08-17). Commits 2026-08-17: e4c90a1 (invert + release
profile), 752e49b (.pes input), 4ccc2d4 (background thread + progress bar),
1dd4071 (JPEG round-trip test) + this wrap. Working tree clean, gates green
(`cargo test --workspace` incl. the gate tests, `cargo clippy --workspace
--all-targets`, `cargo fmt --check`). Branch `rs-greenfield`.

**2026-08-17 session:** packaging ACCEPTED (M5 fully done); converter
Invert toggle (Rust-only knob, default OFF — parity) + release profile
(thin LTO + strip: the 5 exes ~30 MB total, from ~65 MB); converter .pes
input + progress bar/background thread + JPEG round-trip test — the whole
converter follow-up list is now DONE; Dropbox save declared OUT OF SCOPE
and the Java's Dropbox app DELETED by the user (`disabled_app` verified on
the OAuth endpoint — the hardcoded key/secret in the Java are inert).

**Done 2026-08-16 (the previous wrap's session, kept as history):**

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
  DRUNK — plus Dropbox (launcher + save), declared OUT OF SCOPE 2026-08-17.

**Next (prioritised, 1 = resume immediately):**

1. ~~**Accept the packaging**~~ — DONE 2026-08-17 (`dist/` launched and tested).
2. ~~**Converter follow-ups**~~ — DONE 2026-08-17: PES input (load dialog /
   drag-drop accept `.pes`, rasterized back into the pipeline), progress bar
   + background thread (`convert_image_with_progress`, the Java's
   `processImageWithProgress` — the knobs lock and the status bar shows the
   stage; the UI no longer freezes on a heavy stroke), JPEG round-trip
   test. **Dropbox save: declared OUT OF SCOPE 2026-08-17** (named
   exception, like FPS/V-sync and i18n) — a full OAuth/cloud feature with
   external credentials; the Java's app key/secret were hardcoded in a
   public repo, and the app has now been DELETED by the user
   (`disabled_app` verified on the OAuth endpoint 2026-08-17) — the leak
   is inert.
3. ~~**infinite-draw "dessin pur"**~~ — **MOVED 2026-08-17 to the CatzEngine
   repo**, where it is now a planned app in that portfolio (D110 amendment).
   The app `emb-infinitedraw` was removed from this suite in the same move
   (D001 amendment): the Java's infinite-draw is an 18-line skeleton, so the
   CatzEngine app is greenfield. The CatzEngine needs named there: a ruling
   naming it as a consumer (now done, D110), the external consumption route
   (git path dep; publish=false + proprietary licence — moot while the app
   lives IN CatzEngine), and the 2D draw line (D109 refused a 2D path until a
   consumer exists — this app would be it).
4. ~~**Editor follow-ups**~~ — all three DONE 2026-08-17:
   **cull** (D009, 2026-08-16) — element-local mask subtraction, default ON,
   invisible later layers still cut; **CONCENTRIC** (D010) — the isolines
   raster path (the Java's `E.image()` CONCENTRIC, not hatchInset),
   element-local like the cull, with the Parallel/Concentric toggle;
   **TXT stitching** (D011) — the Hershey SIMPLEX vector font
   (`emb_model::font`, fixture-compared), stroked at the layer's stroke
   settings, contributing cull masks (the Java2D text raster stays a
   documented deviation); **the stroke-mode toggle** (D012) —
   PERPENDICULAR/TANGENT, TANGENT being the geometric offset oracle
   (`stroke_poly_tangent`). The editor's M4 named-exception list is now
   empty.
5. ~~**The first Rust GitHub release**~~ — DONE 2026-08-17 (`v0.1.0` tag,
   `gh release create` with the win64 zip; the launcher's update check now
   sees v0.1.0, not the Java's V0.2.0; the release skill + Linux build landed
   in the same session, 7c86a13).

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
> the cost (2026-08-16); the background-thread conversion (2026-08-17)
> makes even the cap-free case non-blocking.

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
fixtures. `strokePolyTangentRaster`'s OUTPUT was ported 2026-08-17 as the
editor's TANGENT mode — the ruling is D012: a geometric offset oracle +
invariant tests, not Java fixtures; the Java2D raster itself is not
reproduced.)

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
  on the dirty flag; TXT is a box at its anchor (**2026-08-17: the stitch
  now uses the Hershey glyphs, D011 — the thumbnail's box remains a
  preview-only placeholder**).
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
- **Converter UI caps**: spacing ≤ 1000, max_colors ≤ 256 are Rust-only
  guards (the Java text fields are uncapped); the stroke cap 64 was already
  named above. **The export width/height knobs were REMOVED 2026-08-17**: they
  were a centring knob (D005), and the save is now native-space (the fix for
  save ≠ preview) — a knob that stops at the UI with no effect is a dead
  control (pin 3).
- **Invert toggle** (2026-08-17): Rust-only knob on the converter (the Java
  has none), default OFF so parity holds: flips the pipeline's binarization
  to the dark pixels (`max(r,g,b) < 127`) for dark-subject-on-bright-
  background photos, whose parity mask converts the background blob instead
  of the subject.
- **PES input** (2026-08-17): the converter's load dialog / drag-drop accept
  a `.pes` design — read through `emb_data::pes::read` (M1,
  pyembroidery-tested; the Java's own `PES.read` adds raw deltas WITHOUT
  accumulation — a model bug, not ported), grouped per colour run,
  `normalizePolylines` fit, rasterized with the Java's Bresenham onto a
  canvas, then re-converted with the current knobs. Deviation: the raster's
  background is BLACK, not the Java's `p.color(255)` — the pipeline's bright
  threshold can never separate thread-colour pixels from a white canvas
  (the whole mask turns on as one blob; the Java only "worked" because it
  drew near-black thread-INDEX values, extracting the lines as negative
  space). Dark-thread designs take the Invert toggle, like any dark image.
- **DRUNK hatch** (PEmbroiderGraphics.java:3090/3117): dead in Java (no app
  selects it) and in NO doc — the only deferral absent from both ROADMAP's
  list and D003's eleven. Same class as VECFIELD/ANGLED (those ARE in
  D003's eleven); noted so a future consumer argues it fresh.
- **Launcher Dropbox connect**: the Java launcher's red/green Dropbox button
  (PEmbroiderLauncher.java:67, 84-88, 100-101) has no Rust counterpart.
  **Declared OUT OF SCOPE 2026-08-17** with the converter's Dropbox save:
  the whole family (OAuth + upload) is a cloud feature the Rust suite
  refuses — the Java's credentials are hardcoded in a public repo
  (DropboxUtil.java:33-34). **2026-08-17: the Dropbox app was deleted by
  the user; the OAuth endpoint now answers `error_name=disabled_app`
  (verified) — the leaked key/secret are inert.** Local save stays.

Also fixed by this audit (stale docs): D001's viewer/infinite-draw line
counts (79/15 → 90/18, measured); convert.rs's clamp citation
(Main.java:305-307 → PEmbroiderGraphics.java:306/489/499); the launcher's
Dropbox pointer now names the ROADMAP row.

No Java feature CALLED BY AN APP is both unported and undocumented: the only
unlisted absences are dead-in-Java APIs (DRUNK, ANGLED, VECFIELD, spine,
boolean shapes) and the app UX gaps named above (FPS/V-sync and i18n).
