# Catz-Embroidery

A native Rust rewrite of the Java embroidery suite (Processing + PEmbroider):
an **editor**, a **viewer**, a **converter** (image → embroidery) and a
**launcher** hub — one stitch model, one shared
renderer, every frontend a thin consumer.

The suite reads and writes real machine formats (PES read+write, DST and SVG
write), and its algorithms (hatch, trace, TSP, the PERPENDICULAR stroke) are
tolerance-compared against the Java model on shared fixtures, with every
deviation ruled and documented.

## Status

M0–M5 done (workspace, formats, model, shared draw list, viewer, editor,
converter, launcher, packaging) — the phase table in `docs/ROADMAP.md` is the
single source of truth for what is done and what is not, and the same file
holds the next-session strategy and every named exception.

## Build and run

Requires a Rust toolchain (see `rust-toolchain.toml`).

```powershell
cargo run -p emb-launcher      # the hub: launches editor / converter / viewer
cargo run -p emb-editor        # or run any app directly
cargo run -p emb-converter
cargo run -p emb-viewer
```

Release builds and the distributable folder (all four exes + a README, the
layout the launcher expects):

```powershell
powershell -ExecutionPolicy Bypass -File tools/package-release.ps1
# output: dist/  (git-ignored)
```

The launcher checks GitHub for a newer release once at startup and offers
the releases page when one exists.

## Layout

```
crates/emb-data     parsers/writers for machine formats (PES, DST, SVG)
crates/emb-model    the single stitch model + algorithms (hatch, trace, TSP, stroke)
crates/emb-draw     the shared preview draw-list vocabulary
crates/emb-egui     the shared egui renderer + app UI helpers (exit dialog)
apps/               the four apps (egui/eframe, native exes)
tools/              packaging script + the Java fixture harness
tests/              cross-crate gates (dependency matrix, memory index)
```

## Docs

| What you need | Where |
|---|---|
| The agent-facing entry point (pins, gates, conventions) | `AGENTS.md` |
| What is done / what is next / named exceptions | `docs/ROADMAP.md` |
| Architecture + the dependency matrix ruling | `docs/ARCHITECTURE.md`, `docs/decisions/D001.md` |
| Design rulings (deferrals, the stroke oracle, the converter path) | `docs/decisions/` |
| Code conventions, testing culture, format details | `docs/CODE.md`, `docs/TESTING.md`, `docs/FORMAT.md` |
| Crossed lessons from the Java repo and the model repos | `docs/LESSONS.md` |
| A word you do not know | `docs/GLOSSARY.md` |

The Java suite, `CatzEngine` and `er-cat-kit-rs` are **models, never
sources**: lessons cross, code does not (AGENTS.md pin 2).

## License

MIT (see `Cargo.toml`).
