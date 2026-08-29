# Changelog

All user-facing changes to Catz-Embroidery, newest first. The release skill
(`.claude/skills/release/SKILL.md`) reads this file to write release notes —
derive notes from the version blocks here, never from raw `git log`.

The format follows Keep a Changelog: `Added` / `Changed` / `Fixed` /
`Performance`. A `Fixed`/`Performance` entry is only logged for a defect that
was **present in the last shipped tag** (a migrating user would perceive it);
a bug introduced and fixed within one cycle is intra-cycle and is not logged
here.

## [Unreleased]

### Added

- **ANGLED stroke mode** (D013): the editor's stroke-mode toggle cycles
  PERPENDICULAR → TANGENT → ANGLED → PERPENDICULAR. ANGLED is a rotated-bar
  variant of the D004 PERPENDICULAR oracle — the bars sample at a fixed angle
  offset from perpendicular, stretched by `1/|cos(ang)|` (the secant formula),
  with a degree-suffixed angle knob (±89°) visible only when ANGLED is active.
  The Java's `strokePolyNormalAng` is a Java2D raster; the Rust port is a
  geometric invariant, never fixture-compared (D004's precedent).

## [v0.2.0] - 2026-08-17

### Added

- Linux/Arch native build: `tools/package-release.sh` (the counterpart of the
  Windows `tools/package-release.ps1`), with the eframe `wayland` and `x11`
  features enabled in all four apps so the egui/glow stack compiles on Linux.
- **The viewer now opens SVG designs** (gap #15): a new `emb_data::svg::read`
  (the reader counterpart of the existing writer, round-trip-tested against
  the committed `simple.svg`), exposed as `Model::from_svg`; the viewer's
  Open/drag-drop accept `.pes` and `.svg`.

### Changed

- The launcher's update check switched from `native-tls` (openssl on Linux)
  to ureq's `tls` (rustls, pure-Rust) so the Linux build cross-compiles from
  Windows with no system openssl — same behaviour, no system dependency.
- The Linux release zip now carries the Unix executable bit and a Unix
  (host 3) marker: `tools/zip-linux.py` (Windows Compress-Archive dropped
  `+x` AND a DOS-host entry makes unzip ignore the mode — both hit as
  "permission denied" on Linux).
- Linux builds are cross-compiled from a Windows box via `cargo zigbuild`
  (`x86_64-unknown-linux-gnu`) — the standard path, validated on Arch
  2026-08-17; the native `tools/package-release.sh` stays as an optional
  alternative.
- The converter converts **on release**, not on every param tick: dragging a
  slider mutates the params but the pipeline runs once when you let go (the
  "Converting…" spam is gone).
- **Saved designs now match the preview.** The save writes the design's native
  space instead of centring it (`centered_design` shifted the content into
  coordinates the PES reader does not restore, so a reloaded design appeared
  off-centre in the editor / 10× oversized in the converter). The converter's
  export width/height knobs were removed as a consequence.
- The editor preview now stitches in the same TSP order as the save, so what
  you see on screen is exactly the saved thread path (the preview no longer
  skips the optimisation).

## [0.1.0] - 2026-08-17

The first Rust release — the native rewrite of the Java embroidery suite.

### Added

- The full suite as native executables: launcher (hub, en/fr, update check),
  editor, viewer, converter, plus packaging (`tools/package-release.ps1` →
  `dist/`).
- PES read+write, DST write, SVG write, verified against the Java suite's
  fixtures.
- Editor: layers, cull (element-local mask subtraction), PARALLEL and
  CONCENTRIC hatch, TXT (via the Hershey vector font), PERPENDICULAR and
  TANGENT stroke modes, save to PES/SVG/DST.
- Converter: image → embroidery, including `.pes` input, an Invert toggle and
  a progress bar.
- The update check: the launcher offers newer GitHub releases at startup.
