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

- Linux/Arch native build: `tools/package-release.sh` (the counterpart of the
  Windows `tools/package-release.ps1`), with the eframe `wayland` and `x11`
  features enabled in all four apps so the egui/glow stack compiles on Linux.

### Changed

- The launcher's update check switched from `native-tls` (openssl on Linux)
  to ureq's `tls` (rustls, pure-Rust) so the Linux build cross-compiles from
  Windows with no system openssl — same behaviour, no system dependency.
- The Linux release zip now carries the Unix executable bit:
  `tools/zip-linux.py` (Windows Compress-Archive dropped `+x`, so every
  binary failed with "permission denied" on Linux).

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
