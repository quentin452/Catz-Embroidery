# Catz-Embroidery — lessons

**What crossed from a model repository, with its source and the measurement behind it.** A lesson
without its source is an opinion that has been repeated; a lesson without the measurement that made
it worth taking cannot be checked.

Sources: the Java suite in `src/` of this repo, `CatzEngine/`, `er-cat-kit-rs/`. **Lessons cross;
code does not.**

## Captured

### One world model, one API, every frontend consumes it
**Source:** er-cat-kit-rs (`er-world`). The historical three-map divergence in the ER
modding lineage was killed structurally: one crate owns the world, every frontend
derives from it, an undeclared second model cannot compile (`matrix.toml` + `tests/arch.rs`).
**Measurement:** the Java suite here shows the cost of the opposite — the four apps
each reach into PEmbroiderGraphics with their own assumptions, and they have diverged
(verify: the Java apps' differing hoops, palettes and offset handling).

### One draw-list vocabulary, renderers cannot drift
**Source:** er-cat-kit-rs (`er-draw`). Commands + visual identity in ONE crate;
er-ui emits, every renderer (GDI host, D3D12, in-game) rasterises the same source.
**Measurement:** an editor preview and a viewer preview that each draw their own
version of a stitch will disagree on line width, colour and spacing — a comparison
that would have to be done by eye, i.e. never.

### Typed, bounds-checked parsers, tested on real files
**Source:** er-cat-kit-rs (`er-data`). Structured errors, no magic offsets, and the
tests run against REAL game files committed as fixtures.
**Measurement:** binary embroidery formats are untrusted input; a reader that returns
`String` instead of a typed error hides the corruption until the stitch lands on the
machine.

### Nothing enters without a consumer that demands it TODAY
**Source:** CatzEngine (pin 3), learned from 568 000 lines of engine whose lesson is
that "we will need it" is how codebases double in size without shipping anything.
**Measurement:** the Java suite here carries ~100k lines of vendored framework the
apps barely touch (the four apps total ~18k lines including their own code).

### Gate culture: docs hold manifests to reality
**Source:** CatzEngine (`tests/boundaries.rs`, `tests/memory.rs`, `catzc check`). A
claim a gate can check is only true when the gate ran green this session.
**Measurement:** hand-checked dependency rules diverged from the manifests within
weeks; the matrix gate cannot.

## Pending capture

- Nothing in the queue.
