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

---

## Measured defects in the Java model (M1, 2026-08-16)

Each defect is measured (fixtures + probe in `tools/java-fixtures/`) and the Rust
port deviates deliberately, with the deviation encoded in the fixture tests rather
than replicated silently.

### DST writer drops the END record
**Measurement:** `PEmbroiderWriter.java:206-207` encodes the END record and closes
the stream without writing it; the fixture `simple.dst` has no trailing
`00 00 F3`. A DST without an END marker is non-conformant (machines stop on file
end).
**Ruling:** the Rust writer writes the END record; the fixture test asserts
`rust == fixture + [0x00, 0x00, 0xF3]`.

### SVG writer is locale-dependent
**Measurement:** `svgString` uses `String.format("%.3f", ...)` without a locale —
on a French-locale machine the coordinates are comma-separated (`10,000`) and the
file is unparsable. Fixtures are therefore generated with
`-Duser.language=en -Duser.country=US`.
**Ruling:** the Rust writer always emits dots (`{:.3}`).

### PES reader is fundamentally misaligned
**Measurement:** `PEmbroiderReader.PES.read` reads the 4-byte PEC offset from the
v1 header instead of the offset field (`dataOffset = 0xFFFF0001` on our own file),
the `skipBytes(dataOffset - 16)` is negative and skips nothing, and the reader
decodes the CEmbOne/CSewSeg text and the PEC icon graphics as a stitch stream —
it cannot read the files its own writer produces. Reproduced with
`tools/java-fixtures/PesProbe.java` (committed as the measurement).
**Ruling:** the Rust reader parses the real PES v1 structure (PEC offset, delta
records, palette); round-trip `reader(writer(design))` is a test.

### PES writer's first stitch emits two redundant records
**Measurement:** `pec_encode` writes, for the first stitch: the long-form pair,
two spare 0x00 bytes, and a short (0,0) record — the first stitch reads back as
THREE identical points. The round-trip test pins the read-back exactly.
**Ruling:** kept as-is (byte-fidelity with the Java writer); the test documents it.

### PES long-form records: trim flag only on the first stitch
**Measurement:** the Java's `pec_encode` applies `flagTrim` only in the `i == 0`
branch; later long forms are `0x80xx`, not `0xA0xx` (byte 728 of `simple.pes`).
**Ruling:** replicated exactly — this is what the fixture says.

### TSP trials >= 3 are non-deterministic in the Java (Math.random)
**Measurement:** `PEmbroiderTSP.solve` starts trials 0, 1, 2 at fixed points
(0, y-min, x-min) and trials >= 3 at `Math.random()` — a design's stitch order
changes from run to run, and `optimize()` (5 trials) is never reproducible.
**Ruling:** the Rust port uses a fixed-seed PRNG for trials >= 3 (deterministic
runs). Fixture tests use trials=3, which is deterministic in both. Noted as a
design change the Java should have had.

### TSP mutates its input polylines
**Measurement:** `PEmbroiderTSP.solve` reverses the input polylines in place
(`Collections.reverse` on the caller's ArrayList instances) — the output shares
the input's storage.
**Ruling:** the Rust port takes `&[Vec<Point>]` and returns new reversed copies.

### resample's randomize path is dead weight
**Measurement:** `PEmbroiderGraphics.resample` rolls random values on EVERY long
segment (`app.random` + `randomGaussian`) but multiplies them by `randomize`,
which is 0.0 in every consumer (RESAMPLE_NOISE defaults to 0). The output is
deterministic; the random calls are noise.
**Ruling:** the Rust port errors on `randomize != 0` (named exception, queued in
ROADMAP) and skips the dead random calls — byte-identical output for the ported
path.
