# Catz-Embroidery — agent entry point

**Single source of truth for working in this repository.** Any other agent-config file in this repo (`CLAUDE.md`, future `.cursorrules`, `.github/copilot-instructions.md`, ...) is a one-line pointer here and carries no content of its own. If one of them ever states a rule, that file is the defect.

> **State:** see `docs/ROADMAP.md`'s phase table — the single source of truth for what is done and what is not. A row is `done` only when its exit criteria pass; a row is never claimed done here.

> Do not report a gate as passing until you have run it.

> **Task Completion Rule:** before declaring a task done or a gate passing, check whether your changes render any document under `docs/` inaccurate or incomplete, and update it in the SAME commit — never "later".

---

## 1. What this is

A greenfield Rust rewrite of the Java embroidery suite in this repo (Processing + PEmbroider + ControlP5 + JavaFX). The Java code — and the two Rust model repos `CatzEngine` and `er-cat-kit-rs` — are **models, never sources**: lessons cross, code does not. Every crossing lesson is written into `docs/LESSONS.md` with its source and the measurement behind it.

**One sentence:** one stitch model owns every fact (paths, stitches, colours, transforms); every frontend — editor, viewer, converter, infinite-draw — consumes that single API; the preview draw-list is shared so renderers cannot drift.

## 2. The pins — non-negotiable, and each one is enforced by something

Rules a change can violate silently, so each names what catches it.

1. **One stitch model, one API — every frontend consumes it.**
   Editor, viewer, converter and infinite-draw all derive from the same model crate. A second, parallel model is a divergence whose date is already set. The dependency matrix (`matrix.toml`) + `tests/arch.rs` refuse an edge no one declared.

2. **This is a REWRITE. The Java repo and the two Rust model repos are models, never sources.**
   No file, no function and no test is copied from `src/` (Java), `CatzEngine/` or `er-cat-kit-rs/`. Lessons cross; code does not. A crossing lesson is written into `docs/LESSONS.md` with its source, so the next reader can check whether it still holds.

3. **Nothing enters without a consumer that demands it TODAY.**
   Not "we will need it". An API with no caller is deleted, not deprecated. A struct field that stops at the boundary where it is parsed, without reaching the logic that acts on it, is a pipeline disconnect — checked by hand, every time. "Left format-only" is only acceptable as a *named* exception queued in `docs/ROADMAP.md`.

4. **Closed sets are types, not strings.**
   An enum of stitch kinds, formats or flags cannot grow a case silently: a consumer that ignores the unknown is refused by name (`_` arms, `unwrap_or` fallbacks, `get(name)` that swallows). Adding a case breaks every consumer at compile time.

5. **No hardcoded machine paths.**
   `E:\...`, `C:\Users\...`, a homedir — never in code, never in committed docs. Per-machine values live in `.env.local` (git-ignored), documented in `.env.example`. A copy of a function that resolves a machine path is a defect: one shared reader.

6. **No unsafe in this workspace.**
   `unsafe_code = "forbid"` workspace-wide. The renderer, the font rasteriser, the window live in dependencies; our crates never touch raw memory. A new crate that opts out must be argued for in a decision.

7. **Docs are updated in the SAME commit that makes them false.**
   A comment or doc that describes a mechanism that no longer exists is a defect, as real as a failing test — and a confidently false comment is worse than an outdated one.

8. **Tests read the verdict, gates run.**
   A phase is done when its exit criteria run green, not when its code is written. Gate output is grepped for the verdict line, never tailed wholesale.

---

## 3. Structure

```
crates/emb-data    parsers/writers for machine formats (DST, PES, CSV) — typed, bounds-checked,
                   structured errors. No model dependency.
crates/emb-model   the single stitch model + algorithms (hatch, satin, trace, TSP, boolean
                   shapes). Consumes emb-data's types in tests.
crates/emb-draw    the shared preview draw-list vocabulary (commands + visual identity):
                   the contract between the apps and the renderer.
apps/              thin binaries (editor, viewer, converter, infinite-draw, launcher hub) —
                   egui/eframe glow backend; each app is a native exe.
tools/             CLI helpers (fixture conversion, benching).
tests/             cross-crate gates (arch.rs holds matrix.toml to the manifests;
                   memory.rs holds memory/MEMORY.md to the directory).
```

The shape above is the architecture ruling `docs/decisions/D001.md`. The crates exist
when M0's exit criteria pass — until then the workspace skeleton is empty by design.

## 4. Docs map

| The finding is... | It goes in |
|---|---|
| A ruling — we chose X over Y, and refused Z | `docs/decisions/INDEX.md`, appended |
| A convention for writing code | `docs/CODE.md` |
| A thing a test cannot see | `docs/TESTING.md` |
| Something the DST/PES/CSV formats do | `docs/FORMAT.md` |
| What crossed from a model repo | `docs/LESSONS.md` |
| What a word means | `docs/GLOSSARY.md` |
| What to do next | `docs/ROADMAP.md` — the queue, and the only one |
| Why one function is the way it is | a comment beside it, at length |
| Only true on one machine | not here. `~/.claude/projects/*/memory/` |
| None of the above, and it will bite again | `memory/`, with an index line |

## 5. How to run the gates

```powershell
cargo test --workspace   # unit + integration tests; includes the gate tests:
                         # tests/arch.rs (matrix.toml <-> manifests) and
                         # tests/memory.rs (MEMORY.md <-> memory/)
cargo clippy --workspace --all-targets  # deny unwrap/expect outside tests
cargo fmt --check
```

A green gate run means the repository is as the last session left it. If it is not, **read the
failure before changing anything** — every gate names what it guards.
