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

### PES record grammar: each axis word is independently flagged
**Measurement:** a third-party PES (`test.pes`, unknown provenance, title
"Ticetac") contains records like `80 40 01 56` — a 12-bit dx word followed by a
dy word WITHOUT the 0x80 flag. The Brother grammar flags each axis
independently: a byte with bit 7 opens a 12-bit signed value (2 bytes),
otherwise the byte is a 7-bit signed delta on its own — a record is 2, 3 or
4 bytes. Reading with the Java-derived "long form is always 4 bytes" rule
shifts the stream at the first unflagged dy word and invents phantom points:
the design's paws appeared at x ≈ -2500, far left of the motif, and the x span
came out 2.1× too wide (neither pyembroidery nor ThreadsES shows them).
pyembroidery (Ink/Stitch's PES engine) decodes the same stream to exactly the
declared bounds.
**Ruling:** the Rust reader parses each axis word independently. Regression-
tested by `pes_third_party_offsets_and_three_byte_records` against the
committed `test.pes` fixture (31077 points filling 0..1234 × 0..900 exactly).

### PES block offsets are the design origin
**Measurement:** the stitch block's two u16BE offset words encode the design's
top-left corner: `0x9000 | -left`. `test.pes` has `0x9480 0x90A4` → origin
(1152, 164) in 0.1 mm units. pyembroidery reads them as a leading long-form
record (its first stitch is exactly (1152, 164)); added to the deltas, the
design lands inside the declared width/height, while raw deltas alone put the
motif ~115 mm left of the canvas. Our own writer emits `0x9000` (origin
(0, 0)), so the round-trip is unchanged.
**Ruling:** the Rust reader accumulates from the decoded origin; the bounds
stay `[0, 0, w, h]` from the block header.

### PES reader accepts a missing end marker when the declared length is respected
**Measurement:** under the correct record grammar the 0xFF marker IS found at
the end of `test.pes`'s stitch block — the earlier "missing marker" reading was
itself a misparse caused by the 4-byte-long assumption. A file that genuinely
omits the marker is still handled: the declared u24 block length bounds the
stream, and a block whose records end exactly at (or are cut by) `block_end`
is accepted.
**Ruling:** the Rust reader accepts when the declared length bounds the stream.
Same family as the DST END record ruling. Regression-tested by
`pes_accepts_a_missing_end_marker_when_length_is_respected`.

### The PES block unit is not established
**Measurement:** the Java writer's `write_pec_block` writes the PEC width/height
and the stitch deltas in millimetres (`Math.rint(bounds[2]-bounds[0])`), and the
round-trip reader/writer is consistent in mm. `test.pes` (unknown provenance)
declares 1234×900 units and its deltas are mostly 1-9 units per stitch — only
plausible at 0.1 mm/unit (a 123.4×90 mm hoop, 0.1-0.9 mm stitches); at mm it
would be a 1.2 m canvas with 1-9 mm stitches. If the real PES unit is 0.1 mm,
files written by this suite read ten times too large in other software — but a
file of unknown provenance cannot settle it.
**Ruling:** left open. Needs a PES produced by known machine software
(Brother/Wilcom/Embird) with a measured stitch spacing to compare.


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

### The multi-polygon centerpoint divides by the POLYLINE count, not the point count
**Measurement:** `PEmbroiderGraphics.centerpoint(polys, 0)` (line 682) sums ALL
points but divides by `poly.size()` — the number of polylines. For the CROSS
hatch fixture the resulting bounding circle center is (40, 40) instead of the
true mean (20, 20); the cross-resampling grid is shifted by that amount.
Measured by comparing the Rust port's naive mean against the Java's output
(CrossProbe.java).
**Ruling:** replicated — the shifted grid produces a valid hatch (same spacing,
different phase), so fixture fidelity wins over fixing a quirk whose effect is
invisible in the output.

### The trace's lnbd state is dead for our consumers
**Measurement:** `PEmbroiderTrace.findContours` maintains `lnbd` only to feed
the hole/parent bookkeeping of the 3-argument variant, which no consumer uses
(the converter calls the 2-argument version).
**Ruling:** not ported; noted here so nobody re-adds it "for completeness".

### offsetPolygon is raster- AND random-dependent
**Measurement:** beyond the createGraphics raster (the fill + miter stroke
"ring" filter, D003), `offsetPolygon` culls its self-intersection fragments
with up to 5 × 99 `randomPointInPolygon` samples (`app.random`) — the fragment
survival is non-deterministic per run. `hatchInset` (the editor's CONCENTRIC)
inherits both via `insetPolygon → offsetPolygon`.
**Ruling:** joins the D003 deferred class; the random sampling alone would make
a fixture comparison meaningless even with a rasterizer.
