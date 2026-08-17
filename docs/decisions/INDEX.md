# Catz-Embroidery — decisions

**A ruling — we chose X over Y, and refused Z.** One file per ruling, numbered, never
re-edited except to add history. The table below is the index.

| # | Ruling | Date |
|---|---|---|
| D001 | **Architecture ruling** — workspace shape, crates, dependency directions, rendering choice, format scope | 2026-08-16 |
| D002 | **Float comparison tolerance** — geometry algorithms compare with |diff| < 0.001 mm; byte-compare stays for the binary writers | 2026-08-16 |
| D003 | **Stroke path deferred** — PERPENDICULAR/TANGENT depends on the Java2D rasterizer (measured); enters with the converter (M5) | 2026-08-16 |
| D004 | **PERPENDICULAR stroke ported** — the Java2D raster oracle becomes an exact geometric distance oracle; output verified by invariants, not Java fixtures (D003's re-evaluation) | 2026-08-16 |
| D005 | **Converter pipeline ported** — deterministic palette/colors/resample replace the Java's app.random; PERLIN refused; export centres, never scales (D003's re-evaluation for the converter's path) | 2026-08-16 |
| D006 | **PES palette clamped** — unique colours, capped at the 256 the count byte can express; the reader wraps colour changes onto the palette (the Java corrupts the header past 463 runs) | 2026-08-16 |
| D007 | **PES deltas measured from the offset origin** — the writer accumulates from `-bounds[0]`, not 0 (the Java disagrees with its own offset words; found by the infinite canvas's content-centred save) | 2026-08-16 |
| D008 | **TSP stops at the first non-improving 2-opt pass** — the Java runs 999 passes even when the tour length is invariant (measured: ~265 s of pure waste on hatch-parallel designs; the stall rule: ~0.34 s) | 2026-08-16 |
| D009 | **Editor cull ported** — element-local mask subtraction on the D004-class oracles (polygon fill + line distance mask, 1 px/mm), invariant-tested not fixture-compared; default ON, invisible later layers still cut (Java parity) | 2026-08-16 |
| D010 | **Editor CONCENTRIC ported** — the isolines raster path (NOT hatchInset, which the editor never calls: `stitchLayer` → `E.image()` → `hatchRaster` → `isolines`), element-local like the cull; the boundary contour stays out until the stroke-mode toggle | 2026-08-17 |
| D011 | **Editor TXT stitched** — the Hershey SIMPLEX vector font (`emb_model::font`, fixture-compared against the Java's own `putText` within D002), NOT the Java2D text raster the editor actually uses (D003-class, documented deviation); TXT strokes like LIN and contributes cull masks | 2026-08-17 |
| D012 | **Editor TANGENT stroke ported** — the layer's stroke-mode toggle (PERPENDICULAR/TANGENT, the Java dialog); TANGENT is a D004-style geometric offset oracle (`stroke_poly_tangent`: the path's parallel curves at each band's distance, miter formula + winding-aware side guard, CONCENTRIC_ANTIALIGN shared with isolines), invariant-tested not fixture-compared | 2026-08-17 |

## Writing a ruling

Copy the D001 file's shape. Every ruling names: what was decided, what was refused and
why, what the alternatives cost, and what catches a violation (the gate, not the review).

## Reading a ruling

Rulings are history: they record what was refused and why. A later ruling may
overturn an earlier one — that later file is then the truth, and this index shows the
chain.
