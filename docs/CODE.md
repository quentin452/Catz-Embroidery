# Catz-Embroidery — code conventions

What belongs here: conventions for writing code in this workspace. A ruling is a
decision, not a convention — rulings go in `docs/decisions/INDEX.md`. The
non-negotiables live in `AGENTS.md` (pins) and are enforced by `rust-toolchain.toml`,
`deny.toml`, `clippy.toml` and the gate tests — read those first.

## `unwrap` / `expect`

Denied in code (`clippy.toml`), allowed in tests: a test's failure IS its report.
Production errors are structured (`thiserror` derives — D001), and a fallible call in
code either propagates or maps to a structured error.

## File size as a signal

A file that holds more than one reason to change is two files. `clippy.toml` nudges at
400 lines; the rule is a signal a human reports, because a number that can be raised
will be raised.

## Closed sets are types

An enum of stitch kinds, formats or commands cannot grow a case silently: no `_` arms,
no `unwrap_or` fallbacks, no `get(name)` that swallows the unknown. Adding a case
breaks every consumer at compile time — that is the feature.

## One fact per type

A struct field that stops at the boundary where it is parsed, without reaching the
logic that acts on it, is a pipeline disconnect (pin 3). Name the field for the fact
it carries, not for the column it came from.

## No machine paths

Never in code, never in committed docs. Per-machine values live in `.env.local`
(git-ignored), documented in `.env.example`, read through one shared reader in the
workspace — a copy of that reader is a defect.

## Cross-repo convention

The Java suite in `src/` and the two model repos are MODELS, never sources: lessons
cross (`docs/LESSONS.md`), code does not (pin 2). Code style follows this repo's docs,
never theirs.
