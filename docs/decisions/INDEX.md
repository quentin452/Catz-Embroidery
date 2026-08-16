# Catz-Embroidery — decisions

**A ruling — we chose X over Y, and refused Z.** One file per ruling, numbered, never
re-edited except to add history. The table below is the index.

| # | Ruling | Date |
|---|---|---|
| D001 | **Architecture ruling** — workspace shape, crates, dependency directions, rendering choice, format scope | 2026-08-16 |
| D002 | **Float comparison tolerance** — geometry algorithms compare with |diff| < 0.001 mm; byte-compare stays for the binary writers | 2026-08-16 |
| D003 | **Stroke path deferred** — PERPENDICULAR/TANGENT depends on the Java2D rasterizer (measured); enters with the converter (M5) | 2026-08-16 |
| D004 | **PERPENDICULAR stroke ported** — the Java2D raster oracle becomes an exact geometric distance oracle; output verified by invariants, not Java fixtures (D003's re-evaluation) | 2026-08-16 |

## Writing a ruling

Copy the D001 file's shape. Every ruling names: what was decided, what was refused and
why, what the alternatives cost, and what catches a violation (the gate, not the review).

## Reading a ruling

Rulings are history: they record what was refused and why. A later ruling may
overturn an earlier one — that later file is then the truth, and this index shows the
chain.
