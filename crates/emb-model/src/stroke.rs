//! The PERPENDICULAR stroke path, ported from
//! `PEmbroiderGraphics.strokePolyNormal` (the `_stroke` PERPENDICULAR branch
//! the converter uses at M5).
//!
//! D003 deferred the stroke because the Java renders its on/off oracle through
//! Java2D (createGraphics + strokeWeight + filter(DILATE) + pg.get sampling).
//! This port replaces the raster oracle with an exact geometric distance
//! oracle (docs/decisions/D004.md): a sample is ON iff its distance to the
//! polyline is within `spacing/2` (the half-width of the Java's stroked
//! ribbon). The output is verified by geometric invariants, never compared
//! against the Java.
//!
//! The raster's Java2D side effects are represented geometrically: the DILATE
//! becomes +1 mm on the vertex-pass oracle (1 px = 1 mm), the endpoint ellipse
//! of an open polyline becomes a disc oracle of radius `half_weight + 1`, and
//! a run closes at its last ON sample (the Java appends the terminating OFF
//! sample — the same mmm outcome, a one-step tip difference below the
//! invariant comparison level).
//!
//! The oracle is answered by a uniform [`SegmentGrid`] over the polyline
//! instead of a per-sample scan of every segment: the sample count is
//! O(polyline length × stroke²) (the ray fans of the vertex pass), so a
//! brute-force scan is quadratic in the vertex count — the converter froze
//! for seconds on photo contours at stroke_weight 100+. The grid is an exact
//! acceleration, not an approximation: a segment within `threshold` of the
//! sample has its closest point inside the query square, and the piece
//! containing it sits in a cell the square intersects — the answer is the
//! same boolean the brute-force scan would give. Measured 2026-08-16
//! (release, 1000×1000, spacing 4): an 800-vertex contour at weight 200
//! stroked in 760 ms against ~20 s for the brute-force scan; the remaining
//! cost is the sample count itself, quadratic in the weight — bounded in
//! the converter by the stroke cap (see ROADMAP).

use crate::geom::{self, Point};
use crate::trace;

const HALF_PI: f32 = std::f32::consts::PI / 2.0;

/// The Java's `PERPENDICULAR_STROKE_CAP_DENSITY_MULTIPLIER` default.
const CAP_DENSITY_MULTIPLIER: f32 = 1.0;

/// Exact spatial index over the polyline's segments for the oracle: each
/// segment is cut into pieces no longer than `cell/2`, and every piece is
/// inserted into each grid cell its bounding box touches (CSR lists).
///
/// A query at `p` with threshold `t` scans only the cells the square
/// `[p-t, p+t]²` intersects and returns true on the first piece within `t`.
/// Exact: a segment within `t` of `p` has its closest point inside the
/// square, that point lies in the bounding box of the piece containing it,
/// and the cell holding that point is both a piece cell and a query cell —
/// so the grid can never miss a segment the brute-force scan would find.
/// Points and pieces outside the grid are clamped into it: the distance
/// check stays exact, so clamping only costs extra cells, never a wrong
/// answer.
struct SegmentGrid {
    min_x: f32,
    min_y: f32,
    cell: f32,
    cols: usize,
    rows: usize,
    /// Per-cell head of the node list.
    head: Vec<u32>,
    /// Per-node next pointer.
    next: Vec<u32>,
    /// Per-node piece `[ax, ay, bx, by]`.
    nodes: Vec<[f32; 4]>,
}

const NO_NODE: u32 = u32::MAX;

impl SegmentGrid {
    /// Build the index over `poly`'s segments (the same `segs` count the
    /// oracle used). `cell` is the caller's `max(spacing/2, 1)` — the
    /// smaller threshold of the two passes, keeping every query ring to a
    /// handful of cells (the grid would explode in memory at the raw
    /// `spacing/2` when the user sets a tiny spacing).
    fn build(poly: &[Point], close: bool, cell: f32) -> SegmentGrid {
        let nseg = poly.len() - if close { 0 } else { 1 };
        let (mut min_x, mut min_y, mut max_x, mut max_y) = (
            f32::INFINITY,
            f32::INFINITY,
            f32::NEG_INFINITY,
            f32::NEG_INFINITY,
        );
        for p in poly {
            min_x = min_x.min(p.x);
            max_x = max_x.max(p.x);
            min_y = min_y.min(p.y);
            max_y = max_y.max(p.y);
        }
        let cols = ((max_x - min_x) / cell).ceil() as usize + 1;
        let rows = ((max_y - min_y) / cell).ceil() as usize + 1;
        let mut grid = SegmentGrid {
            min_x,
            min_y,
            cell,
            cols,
            rows,
            head: vec![NO_NODE; cols * rows],
            next: Vec::new(),
            nodes: Vec::new(),
        };
        let col_of =
            |x: f32| (((x - min_x) / cell).floor() as isize).clamp(0, cols as isize - 1) as usize;
        let row_of =
            |y: f32| (((y - min_y) / cell).floor() as isize).clamp(0, rows as isize - 1) as usize;
        for i in 0..nseg {
            let (p0, p1) = (poly[i], poly[(i + 1) % poly.len()]);
            let len = p0.dist(p1);
            let pieces = 1.max((len / (cell / 2.0)).ceil() as usize);
            for k in 0..pieces {
                let a = Point::new(
                    p0.x + (p1.x - p0.x) * (k as f32 / pieces as f32),
                    p0.y + (p1.y - p0.y) * (k as f32 / pieces as f32),
                );
                let b = Point::new(
                    p0.x + (p1.x - p0.x) * ((k + 1) as f32 / pieces as f32),
                    p0.y + (p1.y - p0.y) * ((k + 1) as f32 / pieces as f32),
                );
                let c0 = col_of(a.x.min(b.x));
                let c1 = col_of(a.x.max(b.x));
                let r0 = row_of(a.y.min(b.y));
                let r1 = row_of(a.y.max(b.y));
                for cy in r0..=r1 {
                    for cx in c0..=c1 {
                        let node = grid.nodes.len() as u32;
                        grid.nodes.push([a.x, a.y, b.x, b.y]);
                        grid.next.push(grid.head[cy * grid.cols + cx]);
                        grid.head[cy * grid.cols + cx] = node;
                    }
                }
            }
        }
        grid
    }

    /// The oracle: is `p` within `threshold` of any polyline segment? The
    /// exact same answer as the brute-force scan, found by visiting only the
    /// cells the query square intersects.
    fn on_path(&self, p: Point, threshold: f32) -> bool {
        let t = threshold.max(0.0);
        let cx0 = self.col_of(p.x - t);
        let cx1 = self.col_of(p.x + t);
        let cy0 = self.row_of(p.y - t);
        let cy1 = self.row_of(p.y + t);
        for cy in cy0..=cy1 {
            for cx in cx0..=cx1 {
                let mut node = self.head[cy * self.cols + cx];
                while node != NO_NODE {
                    let [ax, ay, bx, by] = self.nodes[node as usize];
                    if trace::point_distance_to_segment(p, Point::new(ax, ay), Point::new(bx, by))
                        <= threshold
                    {
                        return true;
                    }
                    node = self.next[node as usize];
                }
            }
        }
        false
    }

    fn col_of(&self, x: f32) -> usize {
        (((x - self.min_x) / self.cell).floor() as isize).clamp(0, self.cols as isize - 1) as usize
    }

    fn row_of(&self, y: f32) -> usize {
        (((y - self.min_y) / self.cell).floor() as isize).clamp(0, self.rows as isize - 1) as usize
    }
}

/// The vertex oracle for the endpoint of an OPEN polyline: the Java fills a
/// disc of diameter `2*d+2` around the endpoint (`pg.ellipse`, white) before
/// the vertex pass. Ported as a disc oracle of radius `d+1` — every ray
/// sample lies within `d` of the vertex, so the disc decides all of them.
fn on_endpoint_disc(p: Point, vertex: Point, half_weight: f32) -> bool {
    p.dist(vertex) <= half_weight + 1.0
}

/// The run state machine shared by both passes: consecutive ON samples form
/// runs; an OFF sample closes a run (discarding it when shorter than `mmm`);
/// a run still open at the last sample is discarded or extended by it (the
/// Java's `k == m-1 && lastOn` seam). The Java's `pg.vertex` calls (drawing
/// the samples back onto the raster) are not modelled — the oracle is
/// stateless (D004).
fn walk_line(
    runs: &mut Vec<Vec<Point>>,
    line0: Point,
    line1: Point,
    m: usize,
    mmm: usize,
    oracle: impl Fn(Point) -> bool,
) {
    let mut last_on = false;
    for k in 0..m {
        // Java: u = k/max(1, m-1) (int max, then float division).
        let u = k as f32 / 1.max(m - 1) as f32;
        let p = Point::new(
            line0.x * (1.0 - u) + line1.x * u,
            line0.y * (1.0 - u) + line1.y * u,
        );
        if k == m - 1 && last_on {
            if let Some(last) = runs.last_mut() {
                if last.len() < mmm {
                    runs.pop();
                } else {
                    last.push(p);
                }
            }
            continue;
        }
        if oracle(p) {
            if last_on {
                if let Some(last) = runs.last_mut() {
                    last.push(p);
                }
            } else {
                runs.push(vec![p]);
            }
            last_on = true;
        } else {
            if last_on {
                // Close the run; discard it when too short (the Java removes
                // it when `size() < mmm`; the port does not append the OFF
                // sample the Java adds as the run's final point).
                if let Some(last) = runs.last()
                    && last.len() < mmm
                {
                    runs.pop();
                }
            }
            last_on = false;
        }
    }
}

/// Port of `PEmbroiderGraphics.strokePolyNormal`: the PERPENDICULAR stroke
/// path. `half_weight` = d (STROKE_WEIGHT/2), `spacing` = s (STROKE_SPACING),
/// `close` = close, `connect` = !NO_CONNECT.
///
/// The caller applies the Java's clamps (STROKE_WEIGHT >= 1, STROKE_SPACING >= 0.1);
/// the function itself refuses degenerate inputs — fewer than 2 points,
/// `half_weight <= 0`, `spacing <= 0` — with an empty result (the Java's BBox
/// would NPE on an empty poly).
pub fn stroke_poly_normal(
    poly: &[Point],
    half_weight: f32,
    spacing: f32,
    close: bool,
    connect: bool,
) -> Vec<Vec<Point>> {
    if poly.len() < 2 || half_weight <= 0.0 || spacing <= 0.0 {
        return Vec::new();
    }

    // The exact oracle index, built once: the segment pass queries it at
    // spacing/2, the vertex pass at spacing/2 + 1 (the DILATE).
    let grid = SegmentGrid::build(poly, close, (spacing / 2.0).max(1.0));

    // Segment pass (PEmbroiderGraphics.java:1891-1958): one bucket of runs
    // per segment, sampled on perpendicular lines with the oracle at
    // spacing/2.
    let segs = poly.len() - if close { 0 } else { 1 };
    let mut buckets: Vec<Vec<Vec<Point>>> = Vec::with_capacity(segs + 1);
    for i in 0..segs {
        let p0 = poly[i];
        let p1 = poly[(i + 1) % poly.len()];
        let a1 = geom::atan2(p1.y - p0.y, p1.x - p0.x) + HALF_PI;
        let l = p0.dist(p1);
        let n = ((l / spacing) as f64).ceil() as usize;
        let mut runs: Vec<Vec<Point>> = Vec::new();
        if n > 0 {
            let (ca, sa) = (geom::cos(a1), geom::sin(a1));
            let m = half_weight.ceil() as usize + 1;
            let mmm = 20.min(m / 3);
            for j in 0..=n {
                let t = j as f32 / n as f32;
                // PVector.lerp: start + (stop - start) * amt.
                let px = p0.x + (p1.x - p0.x) * t;
                let py = p0.y + (p1.y - p0.y) * t;
                walk_line(
                    &mut runs,
                    Point::new(px - half_weight * ca, py - half_weight * sa),
                    Point::new(px + half_weight * ca, py + half_weight * sa),
                    m,
                    mmm,
                    |p| grid.on_path(p, spacing / 2.0),
                );
            }
        }
        buckets.push(runs);
    }
    // The Java's trailing empty bucket (PEmbroiderGraphics.java:1961), which
    // the open-polyline vertex-0 rays land in.
    buckets.push(Vec::new());

    // mm rays per vertex (the Java's PERPENDICULAR_STROKE_CAP_DENSITY_
    // MULTIPLIER times the circumference at half_weight, spaced `spacing`).
    let mm = ((std::f32::consts::PI * (half_weight * 2.0) / spacing * CAP_DENSITY_MULTIPLIER)
        as f64)
        .ceil() as usize;
    let n = poly.len();

    // The open-polyline caps (the Java's `if (!close)` block): mm/2 arc bars
    // fanning out of each endpoint along its end-segment direction.
    if !close {
        for i in 0..n {
            if i != 0 && i != n - 1 {
                continue;
            }
            let p0 = poly[i];
            let a = if i == 0 {
                geom::atan2(poly[0].y - poly[1].y, poly[0].x - poly[1].x)
            } else {
                geom::atan2(poly[n - 1].y - poly[n - 2].y, poly[n - 1].x - poly[n - 2].x)
            };
            let (ca, sa) = (geom::cos(a), geom::sin(a));
            let bucket = &mut buckets[(i + n - 1) % n];
            for j in 0..mm / 2 {
                let t = j as f32 / (mm / 2) as f32;
                let x1 = p0.x + half_weight * t * ca;
                let y1 = p0.y + half_weight * t * sa;
                let cw = half_weight * (((1.0 - t * t) as f64).sqrt() as f32);
                bucket.push(vec![
                    Point::new(
                        x1 + cw * geom::cos(a - HALF_PI),
                        y1 + cw * geom::sin(a - HALF_PI),
                    ),
                    Point::new(
                        x1 + cw * geom::cos(a + HALF_PI),
                        y1 + cw * geom::sin(a + HALF_PI),
                    ),
                ]);
            }
        }
    }

    // Vertex pass (PEmbroiderGraphics.java:2009-2069): mm rays per vertex,
    // sampled on the DILATED oracle. The DILATE (1 px = 1 mm) thickens the
    // oracle by 1 mm.
    for i in 0..n {
        let p0 = poly[i];
        let disc = !close && (i == 0 || i == n - 1);
        let bucket = &mut buckets[(i + n - 1) % n];
        let m = half_weight.ceil() as usize;
        // The Java computes mmm = min(10, m/3) then overwrites it with 0:
        // no run is ever discarded in the vertex pass.
        let mmm = 0;
        for j in 0..mm {
            let a = (j as f32 / mm as f32) * std::f32::consts::TAU;
            let far = Point::new(
                p0.x - half_weight * geom::cos(a),
                p0.y - half_weight * geom::sin(a),
            );
            walk_line(bucket, p0, far, m, mmm, |p| {
                if disc {
                    on_endpoint_disc(p, p0, half_weight)
                } else {
                    grid.on_path(p, spacing / 2.0 + 1.0)
                }
            });
        }
    }

    // Assembly (PEmbroiderGraphics.java:2071-2080): the off-by-one rotation
    // starts at the trailing bucket, and every even-indexed run is reversed.
    let mut runs: Vec<Vec<Point>> = Vec::new();
    let nb = buckets.len();
    for bi in 0..nb {
        let i = (bi + nb - 1) % nb;
        let mut bucket = std::mem::take(&mut buckets[i]);
        for (j, run) in bucket.iter_mut().enumerate() {
            if j % 2 == 0 {
                run.reverse();
            }
            runs.push(std::mem::take(run));
        }
    }

    // Cleanup (PEmbroiderGraphics.java:2082-2095): drop runs with < 2 points
    // and runs whose first-last distance is below ml = min(2, d-1), then
    // reduce every run to its first and last point.
    let ml = 2.0f32.min(half_weight - 1.0);
    let mut kept: Vec<Vec<Point>> = Vec::new();
    for run in runs {
        if run.len() < 2 {
            continue;
        }
        let (first, last) = (run[0], run[run.len() - 1]);
        if first.dist(last) < ml {
            continue;
        }
        kept.push(vec![first, last]);
    }

    // NO_CONNECT == false in the converter: concatenate every run into one
    // polyline.
    if connect && kept.len() > 1 {
        let mut one = kept.remove(0);
        for run in kept {
            one.extend(run);
        }
        return vec![one];
    }
    kept
}

/// Port of `PEmbroiderGraphics.strokePolyTangentRaster`'s OUTPUT through a
/// D004-style geometric oracle: the TANGENT stroke's band edges are the
/// parallel (offset) curves of the path at the band's half-width, computed
/// with the Java's OWN miter formula (`offsetPolygon`'s vertex math,
/// PEmbroiderGraphics.java:1452-1458) instead of the Java2D raster the
/// original traces.
///
/// The `_stroke` TANGENT dispatch (PEmbroiderGraphics.java:2375-2391):
/// `STROKE_TANGENT_MODE` defaults to WEIGHT — `cnt = ceil(weight/spacing)+1`,
/// `spa = weight/cnt`. Each band `k` in `0..cnt/2` has two edges: for even
/// `cnt` at `±spa*(k+0.5)`, for odd `cnt` at `±spa*(k+1)` plus the path itself
/// as the centerline at `k == 0`. Even bands get the CONCENTRIC_ANTIALIGN pass
/// (the Java's smoothing on even `k`). The output is verified by geometric
/// invariants (each band's points lie at the band's distance from the path),
/// never compared against the Java — the raster's band edges would differ in
/// the last pixels (D004's precedent; D012).
///
/// The caller applies the Java's clamps (STROKE_WEIGHT >= 1, STROKE_SPACING >= 0.1);
/// the function refuses degenerate inputs — fewer than 2 points, `weight <= 0`,
/// `spacing <= 0` — with an empty result.
pub fn stroke_poly_tangent(
    poly: &[Point],
    weight: f32,
    spacing: f32,
    close: bool,
) -> Vec<Vec<Point>> {
    if poly.len() < 2 || weight <= 0.0 || spacing <= 0.0 {
        return Vec::new();
    }
    // The Java's STROKE_WEIGHT <= 1 branch: just push the polyline.
    if weight <= 1.0 {
        let mut c = poly.to_vec();
        if close && c.len() > 1 {
            c.push(c[0]);
        }
        return vec![c];
    }
    let cnt = (weight / spacing).ceil() as i32 + 1;
    let spa = weight / cnt as f32;
    let hn = cnt / 2;
    let mut out: Vec<Vec<Point>> = Vec::new();
    for k in 0..hn {
        if k == 0 && cnt % 2 == 1 {
            let mut c = poly.to_vec();
            if close && c.len() > 1 {
                c.push(c[0]);
            }
            out.push(c);
        }
        let r = if cnt % 2 == 0 {
            spa * (k as f32 + 0.5)
        } else {
            spa * (k as f32 + 1.0)
        };
        let curves = if close {
            let mut v = Vec::new();
            if let Some(c) = parallel_curve(poly, r) {
                v.push(c);
            }
            if let Some(c) = parallel_curve(poly, -r) {
                v.push(c);
            }
            v
        } else {
            ribbon_outline(poly, r)
        };
        for curve in curves {
            let curve = if k % 2 == 0 {
                geom::concentric_antialign(&curve)
            } else {
                curve
            };
            out.push(curve);
        }
    }
    out
}

/// One side of a CLOSED poly's parallel curve at signed distance `r` (positive
/// = outward, negative = inward): each vertex is offset along its bisector by
/// `r/sin(half-turn)` — the Java `offsetPolygon` miter formula — and dropped
/// when the miter lands on the wrong side of the polygon (the Java's
/// sharp-angle guard, geometric instead of raster). `None` when fewer than 3
/// vertices survive (a deep inward offset collapses the shape).
fn parallel_curve(poly: &[Point], r: f32) -> Option<Vec<Point>> {
    let n = poly.len();
    if n < 3 {
        return None;
    }
    // A contour tracer (findContours) may close the loop by repeating the
    // first point as the last: skip that duplicate so the last real vertex
    // computes its miter against (n-2, 0) instead of (n-2, n-1 == 0).
    let last_real = if poly[n - 1].dist(poly[0]) < 1e-3 {
        n - 1
    } else {
        n
    };
    // Which side is "outward" depends on the winding (the traced contours can
    // be either): the miter lands OUTSIDE when the signed turn of the polygon
    // agrees with the offset sign. `cw` is true for clockwise (y-down) input;
    // for a CW polygon, +r is the INWARD offset, so the "must be outside for
    // r > 0" test flips.
    let mut area2 = 0.0f32;
    for i in 0..last_real {
        let p = poly[i];
        let q = poly[(i + 1) % last_real];
        area2 += p.x * q.y - q.x * p.y;
    }
    let cw = area2 < 0.0;
    let mut out: Vec<Point> = Vec::new();
    for i in 0..last_real {
        let p = poly[i];
        let p0 = poly[(i + last_real - 1) % last_real];
        let p1 = poly[(i + 1) % last_real];
        let a0 = geom::atan2(p0.y - p.y, p0.x - p.x);
        let a1 = geom::atan2(p1.y - p.y, p1.x - p.x);
        let sin_half = geom::sin((a1 - a0) / 2.0);
        if sin_half.abs() < 1e-3 {
            continue; // collinear run: the parallel curve needs no miter vertex
        }
        let a = (a1 + a0) / 2.0;
        let d2 = r / sin_half;
        let q = Point::new(p.x + d2 * geom::cos(a), p.y + d2 * geom::sin(a));
        let inside = crate::raster::point_in_polygon(poly, q);
        // For r > 0 the miter must be OUTSIDE; for r < 0 INSIDE. The winding
        // decides which geometric side the miter formula produced, so flip
        // the check when the input is clockwise.
        let wrong_side = if cw {
            (r > 0.0 && !inside) || (r < 0.0 && inside)
        } else {
            (r > 0.0 && inside) || (r < 0.0 && !inside)
        };
        if wrong_side {
            continue; // the miter crossed to the wrong side — drop the vertex
        }
        if let Some(last) = out.last()
            && last.dist(q) < 1e-3
        {
            continue; // near-duplicate
        }
        out.push(q);
    }
    if out.len() < 3 {
        return None;
    }
    // Close the loop (the parallel curve of a closed poly is a closed curve).
    let first = out[0];
    let last = *out.last().unwrap_or(&first);
    if first.dist(last) > 1e-3 {
        out.push(first);
    }
    Some(out)
}

/// The outline of the ribbon around an OPEN polyline: the two side curves at
/// `±r`, joined at each end by a semicircular cap (the Java's ROUND caps in
/// the raster — here a geometric arc of radius `r` around each endpoint).
fn ribbon_outline(poly: &[Point], r: f32) -> Vec<Vec<Point>> {
    if poly.len() < 2 {
        return Vec::new();
    }
    let n = poly.len();
    // Per-vertex miter offsets for the two sides; endpoints use the
    // perpendicular of the end segment.
    let mut plus: Vec<Point> = Vec::new();
    let mut minus: Vec<Point> = Vec::new();
    for i in 0..n {
        let p = poly[i];
        let p0 = if i == 0 { None } else { Some(poly[i - 1]) };
        let p1 = if i == n - 1 { None } else { Some(poly[i + 1]) };
        let sides = [(&mut plus, r), (&mut minus, -r)];
        for (out, off) in sides {
            let q = match (p0, p1) {
                (Some(p0), Some(p1)) => {
                    let a0 = geom::atan2(p0.y - p.y, p0.x - p.x);
                    let a1 = geom::atan2(p1.y - p.y, p1.x - p.x);
                    let sin_half = geom::sin((a1 - a0) / 2.0);
                    if sin_half.abs() < 1e-3 {
                        // Collinear: offset along the shared direction's normal.
                        let seg = Point::new(p1.x - p0.x, p1.y - p0.y);
                        let len = (seg.x * seg.x + seg.y * seg.y).sqrt();
                        if len < 1e-6 {
                            None
                        } else {
                            let nx = -seg.y / len;
                            let ny = seg.x / len;
                            Some(Point::new(p.x + off * nx, p.y + off * ny))
                        }
                    } else {
                        let a = (a1 + a0) / 2.0;
                        let d2 = off / sin_half;
                        Some(Point::new(p.x + d2 * geom::cos(a), p.y + d2 * geom::sin(a)))
                    }
                }
                (None, Some(p1)) => {
                    // Endpoint 0: perpendicular to the first segment.
                    let seg = Point::new(p1.x - p.x, p1.y - p.y);
                    let len = (seg.x * seg.x + seg.y * seg.y).sqrt();
                    if len < 1e-6 {
                        None
                    } else {
                        let nx = -seg.y / len;
                        let ny = seg.x / len;
                        Some(Point::new(p.x + off * nx, p.y + off * ny))
                    }
                }
                (Some(p0), None) => {
                    // Endpoint n-1: perpendicular to the last segment.
                    let seg = Point::new(p.x - p0.x, p.y - p0.y);
                    let len = (seg.x * seg.x + seg.y * seg.y).sqrt();
                    if len < 1e-6 {
                        None
                    } else {
                        let nx = -seg.y / len;
                        let ny = seg.x / len;
                        Some(Point::new(p.x + off * nx, p.y + off * ny))
                    }
                }
                (None, None) => None,
            };
            if let Some(q) = q {
                if let Some(last) = out.last() {
                    if last.dist(q) >= 1e-3 {
                        out.push(q);
                    }
                } else {
                    out.push(q);
                }
            }
        }
    }
    if plus.len() < 2 || minus.len() < 2 {
        return Vec::new();
    }
    // The outline: plus side forward, cap at the far end, minus side reversed,
    // cap at the near end — one closed loop per band (the Java's findContours
    // of the stroked ribbon).
    //
    // A cap is a semicircle of radius r around the polyline ENDPOINT, sweeping
    // from the plus side's offset point to the minus side's, through the
    // direction the ribbon bulges (forward at the far end, backward at the
    // near end). The start angle is the plus point's direction (far) or the
    // minus point's direction (near); sweeping -PI reaches the other side.
    let cap = |center: Point, start_angle: f32, r: f32, steps: usize| -> Vec<Point> {
        (0..=steps)
            .map(|j| {
                let t = std::f32::consts::PI * j as f32 / steps as f32;
                let ang = start_angle - t;
                Point::new(center.x + r * geom::cos(ang), center.y + r * geom::sin(ang))
            })
            .collect()
    };
    // The left normal of a segment direction (the plus side's offset dir).
    let normal = |d: &Point| Point::new(-d.y, d.x);
    let mut outline: Vec<Point> = Vec::new();
    outline.extend(plus.iter().copied());
    // Far-end cap: centered at poly[n-1], from the plus point (angle +n_last)
    // sweeping through +d_last to the minus point.
    let last_seg = Point::new(poly[n - 1].x - poly[n - 2].x, poly[n - 1].y - poly[n - 2].y);
    let len = (last_seg.x * last_seg.x + last_seg.y * last_seg.y).sqrt();
    if len >= 1e-6 {
        let d = Point::new(last_seg.x / len, last_seg.y / len);
        let nrm = normal(&d);
        let start_angle = geom::atan2(nrm.y, nrm.x);
        outline.extend(cap(poly[n - 1], start_angle, r, 8));
    }
    let mut back = minus.clone();
    back.reverse();
    outline.extend(back);
    // Near-end cap: centered at poly[0], from the minus point (angle -n_first)
    // sweeping through -d_first to the plus point.
    let first_seg = Point::new(poly[1].x - poly[0].x, poly[1].y - poly[0].y);
    let len = (first_seg.x * first_seg.x + first_seg.y * first_seg.y).sqrt();
    if len >= 1e-6 {
        let d = Point::new(first_seg.x / len, first_seg.y / len);
        let nrm = normal(&d);
        let start_angle = geom::atan2(-nrm.y, -nrm.x);
        outline.extend(cap(poly[0], start_angle, r, 8));
    }
    vec![outline]
}

#[cfg(test)]
mod tests {
    use super::*;

    /// D002's 0.001 mm tolerance for coordinate assertions.
    const EPS: f32 = 1e-3;

    /// Min over the polyline's segments of the point-to-segment distance.
    fn dist_to_poly(p: Point, poly: &[Point], close: bool) -> f32 {
        let segs = poly.len() - if close { 0 } else { 1 };
        let mut best = f32::INFINITY;
        for i in 0..segs {
            best = best.min(trace::point_distance_to_segment(
                p,
                poly[i],
                poly[(i + 1) % poly.len()],
            ));
        }
        best
    }

    /// A straight 2-point horizontal segment: one connected polyline whose
    /// points stay within [y-5, y+5], spanning x from 0 to 100, with every
    /// interior point at the bar tips — near the path (|y| <= 2, the
    /// spacing/2 oracle half-width) or near the band edge (||y| - 5| <= 2).
    #[test]
    fn straight_segment_strokes_within_band() {
        let poly = vec![Point::new(0.0, 0.0), Point::new(100.0, 0.0)];
        let out = stroke_poly_normal(&poly, 5.0, 4.0, false, true);
        assert_eq!(out.len(), 1, "connect=true produces one polyline");
        let pts = &out[0];
        // 26 segment bars + 8 endpoint rays + 4 arc bars, at each end.
        assert!(pts.len() > 50, "expected many bars, got {}", pts.len());
        for p in pts {
            assert!(
                p.x >= -5.0 - EPS && p.x <= 105.0 + EPS,
                "x out of range: {p:?}"
            );
            assert!(p.y.abs() <= 5.0 + EPS, "y out of range: {p:?}");
            let near_path = p.y.abs() <= 2.0;
            let near_edge = (p.y.abs() - 5.0).abs() <= 2.0;
            assert!(
                near_path || near_edge,
                "interior point off the bar tips: {p:?}"
            );
        }
        assert!(pts.iter().any(|p| p.x.abs() <= 0.01), "no point at x ~ 0");
        assert!(
            pts.iter().any(|p| (p.x - 100.0).abs() <= 0.01),
            "no point at x ~ 100"
        );
    }

    /// A closed square: the stroke covers all four sides and its extent
    /// matches the stroke band (the square plus half_weight on every side).
    #[test]
    fn closed_square_all_four_sides() {
        let poly = vec![
            Point::new(0.0, 0.0),
            Point::new(100.0, 0.0),
            Point::new(100.0, 100.0),
            Point::new(0.0, 100.0),
        ];
        let out = stroke_poly_normal(&poly, 5.0, 4.0, true, true);
        assert_eq!(out.len(), 1);
        let pts = &out[0];
        assert!(pts.len() > 100, "expected many bars, got {}", pts.len());
        for p in pts {
            assert!(
                p.x >= -5.0 - EPS && p.x <= 105.0 + EPS,
                "x out of range: {p:?}"
            );
            assert!(
                p.y >= -5.0 - EPS && p.y <= 105.0 + EPS,
                "y out of range: {p:?}"
            );
        }
        // Points on the stroke band of each side (interior of the side, not
        // the corners).
        let mid_x = |p: &Point| p.x > 1.0 && p.x < 99.0;
        let mid_y = |p: &Point| p.y > 1.0 && p.y < 99.0;
        assert!(
            pts.iter().any(|p| p.y.abs() <= 2.0 && mid_x(p)),
            "bottom side"
        );
        assert!(
            pts.iter().any(|p| (p.y - 100.0).abs() <= 2.0 && mid_x(p)),
            "top side"
        );
        assert!(
            pts.iter().any(|p| p.x.abs() <= 2.0 && mid_y(p)),
            "left side"
        );
        assert!(
            pts.iter().any(|p| (p.x - 100.0).abs() <= 2.0 && mid_y(p)),
            "right side"
        );
    }

    /// connect=false: multiple runs, each reduced to exactly 2 points (the
    /// first-last cleanup), with plausible bar lengths.
    #[test]
    fn unconnected_runs_are_two_point_bars() {
        let poly = vec![Point::new(0.0, 0.0), Point::new(100.0, 0.0)];
        let out = stroke_poly_normal(&poly, 5.0, 4.0, false, false);
        assert!(out.len() > 10, "expected many runs, got {}", out.len());
        for run in &out {
            assert_eq!(run.len(), 2, "run not reduced to its ends");
            let d = run[0].dist(run[1]);
            assert!(
                (1.0..=10.0 + EPS).contains(&d),
                "bar length {d} out of the stroke band range"
            );
        }
    }

    /// Degenerate inputs: empty result, no panic, no NaN.
    #[test]
    fn degenerate_inputs_are_empty() {
        let empty: Vec<Point> = Vec::new();
        assert!(stroke_poly_normal(&empty, 5.0, 4.0, false, true).is_empty());
        let one = vec![Point::new(0.0, 0.0)];
        assert!(stroke_poly_normal(&one, 5.0, 4.0, false, true).is_empty());
        let two = vec![Point::new(0.0, 0.0), Point::new(10.0, 0.0)];
        assert!(stroke_poly_normal(&two, 0.0, 4.0, false, true).is_empty());
        assert!(stroke_poly_normal(&two, 5.0, 0.0, false, true).is_empty());
        assert!(stroke_poly_normal(&two, -1.0, 4.0, false, true).is_empty());
        assert!(stroke_poly_normal(&two, 5.0, -1.0, false, true).is_empty());
    }

    // ---- TANGENT stroke (D012, the geometric offset oracle) ----

    /// Distance to an OPEN polyline INCLUDING its endpoints: a point off the
    /// last segment's end is measured to the vertex, not the extended segment
    /// line (the ribbon's round caps are arcs around the endpoints, so their
    /// points sit at distance `r` from the vertex).
    fn dist_to_open_path(p: Point, poly: &[Point]) -> f32 {
        let mut best = dist_to_poly(p, poly, false);
        for v in poly {
            best = best.min(p.dist(*v));
        }
        best
    }

    /// A straight horizontal segment, close=false, weight 10 spacing 4:
    /// cnt = ceil(10/4)+1 = 4 (even), spa = 2.5, two bands at 1.25 and 3.75,
    /// each an outline around the segment.
    #[test]
    fn tangent_straight_segment_bands_at_correct_distances() {
        let poly = vec![Point::new(0.0, 0.0), Point::new(100.0, 0.0)];
        let out = stroke_poly_tangent(&poly, 10.0, 4.0, false);
        assert_eq!(out.len(), 2, "two bands");
        let expected = [1.25f32, 3.75];
        for (curve, &r) in out.iter().zip(&expected) {
            assert!(curve.len() >= 2, "curve has points");
            for p in curve {
                let d = dist_to_open_path(*p, &poly);
                assert!(
                    (d - r).abs() < 0.5,
                    "point {p:?} is {d} away, expected ~{r}"
                );
            }
        }
    }

    /// Band count arithmetic: weight 21 spacing 4 â†’ cnt = ceil(21/4)+1 = 7
    /// (odd), spa = 3: a centerline at k=0 plus three bands at 3, 6, 9.
    #[test]
    fn tangent_band_count_and_distances_scale() {
        let poly = vec![Point::new(0.0, 0.0), Point::new(100.0, 0.0)];
        let out = stroke_poly_tangent(&poly, 21.0, 4.0, false);
        assert_eq!(out.len(), 4, "centerline + 3 bands");
        let d0 = dist_to_open_path(out[0][0], &poly);
        assert!(d0.abs() < 1e-3, "the centerline lies on the path: {d0}");
        let expected = [3.0f32, 6.0, 9.0];
        for (curve, &r) in out[1..].iter().zip(&expected) {
            for p in curve {
                let d = dist_to_open_path(*p, &poly);
                assert!(
                    (d - r).abs() < 0.5,
                    "point {p:?} is {d} away, expected ~{r}"
                );
            }
        }
    }

    /// An odd cnt emits the centerline at k=0 plus that band's edges: weight 9
    /// spacing 5 â†’ cnt = ceil(9/5)+1 = 3 (odd), spa = 3, hn = 1 â†’ centerline
    /// + the single band's outline at ±3.
    #[test]
    fn tangent_odd_cnt_emits_the_centerline() {
        let poly = vec![Point::new(0.0, 0.0), Point::new(100.0, 0.0)];
        let even = stroke_poly_tangent(&poly, 9.0, 4.0, false);
        assert_eq!(even.len(), 2, "even cnt: no centerline, two bands");
        let odd = stroke_poly_tangent(&poly, 9.0, 5.0, false);
        assert_eq!(odd.len(), 2, "odd cnt: centerline + one band");
        let d0 = dist_to_open_path(odd[0][0], &poly);
        assert!(d0.abs() < 1e-3, "the centerline lies on the path: {d0}");
        for p in &odd[1] {
            let d = dist_to_open_path(*p, &poly);
            assert!(
                (d - 3.0).abs() < 0.5,
                "point {p:?} is {d} away, expected ~3"
            );
        }
    }

    /// A closed square at weight 10 spacing 4: two closed curves per band
    /// (inner + outer offset). Every point lies at the band's distance from
    /// the path: never INSIDE the band (>= r - eps), and the miter corners
    /// overshoot outward (the Java's own miter joins do the same), so the
    /// upper bound allows the corner reach.
    #[test]
    fn tangent_closed_square_two_edges_per_band() {
        let poly = vec![
            Point::new(0.0, 0.0),
            Point::new(100.0, 0.0),
            Point::new(100.0, 100.0),
            Point::new(0.0, 100.0),
        ];
        let out = stroke_poly_tangent(&poly, 10.0, 4.0, true);
        assert_eq!(out.len(), 4, "two bands x two edges");
        let expected = [1.25f32, 1.25, 3.75, 3.75];
        for (curve, &r) in out.iter().zip(&expected) {
            assert!(curve.len() >= 4, "closed curve has points");
            for p in curve {
                let d = dist_to_poly(*p, &poly, true);
                assert!(
                    d >= r - 0.5 && d <= r * 1.5 + 0.5,
                    "point {p:?} is {d} away, expected ~{r}"
                );
            }
        }
    }

    /// A closed poly whose first point is repeated as the last (what
    /// findContours produces): the duplicate must not corrupt the offset
    /// (the last real vertex's miter computes against (n-2, 0)).
    #[test]
    fn tangent_handles_the_closing_duplicate() {
        let poly = vec![
            Point::new(0.0, 0.0),
            Point::new(100.0, 0.0),
            Point::new(100.0, 100.0),
            Point::new(0.0, 100.0),
            Point::new(0.0, 0.0), // the tracer's closing repeat
        ];
        let out = stroke_poly_tangent(&poly, 10.0, 4.0, true);
        assert!(
            !out.is_empty(),
            "the closing duplicate must not kill the bands"
        );
    }

    /// Degenerate TANGENT inputs: empty, no panic, no NaN.
    #[test]
    fn tangent_degenerate_inputs_are_empty() {
        let empty: Vec<Point> = Vec::new();
        assert!(stroke_poly_tangent(&empty, 10.0, 4.0, false).is_empty());
        let one = vec![Point::new(0.0, 0.0)];
        assert!(stroke_poly_tangent(&one, 10.0, 4.0, false).is_empty());
        let two = vec![Point::new(0.0, 0.0), Point::new(10.0, 0.0)];
        assert!(stroke_poly_tangent(&two, 0.0, 4.0, false).is_empty());
        assert!(stroke_poly_tangent(&two, 10.0, 0.0, false).is_empty());
        assert!(stroke_poly_tangent(&two, -1.0, 4.0, false).is_empty());
        assert!(stroke_poly_tangent(&two, 10.0, -1.0, false).is_empty());
    }

    /// Every bar's midpoint stays within half_weight + epsilon of the
    /// polyline (the bars are perpendicular to it by construction, and the
    /// oracle never extends them past the band).
    #[test]
    fn every_run_midpoint_within_half_weight_of_polyline() {
        let line = vec![Point::new(0.0, 0.0), Point::new(100.0, 0.0)];
        let out = stroke_poly_normal(&line, 5.0, 4.0, false, true);
        assert_eq!(out.len(), 1);
        for run in &out {
            let mid = Point::new((run[0].x + run[1].x) * 0.5, (run[0].y + run[1].y) * 0.5);
            let d = trace::point_distance_to_segment(mid, line[0], line[1]);
            assert!(d <= 5.0 + EPS, "midpoint {mid:?} is {d} away");
        }

        let square = vec![
            Point::new(0.0, 0.0),
            Point::new(100.0, 0.0),
            Point::new(100.0, 100.0),
            Point::new(0.0, 100.0),
        ];
        let out = stroke_poly_normal(&square, 5.0, 4.0, true, true);
        assert_eq!(out.len(), 1);
        for run in &out {
            let mid = Point::new((run[0].x + run[1].x) * 0.5, (run[0].y + run[1].y) * 0.5);
            let d = dist_to_poly(mid, &square, true);
            assert!(d <= 5.0 + EPS, "midpoint {mid:?} is {d} away");
        }
    }

    /// The grid answers EXACTLY like the brute-force scan, on adversarial
    /// polys: self-approaching (a polyline that comes back near itself — the
    /// case an index-window oracle would get wrong), collinear runs,
    /// zero-length segments, and sample points far outside the bbox.
    #[test]
    fn segment_grid_matches_the_brute_force_oracle() {
        let mut seed = 0x2F6E2B1u64;
        let mut rng = || {
            seed ^= seed << 13;
            seed ^= seed >> 7;
            seed ^= seed << 17;
            (seed >> 32) as f32 / u32::MAX as f32
        };
        let polys: Vec<(Vec<Point>, bool)> = vec![
            // A U that doubles back on itself (far-side segments within the
            // stroke band of the near side).
            (
                vec![
                    Point::new(0.0, 0.0),
                    Point::new(100.0, 0.0),
                    Point::new(100.0, 100.0),
                    Point::new(0.0, 100.0),
                ],
                true,
            ),
            // A tight zigzag: dense vertices, self-approach everywhere.
            (
                (0..400)
                    .map(|i| {
                        Point::new(
                            i as f32 * 2.0,
                            ((i as f32 / 7.0).sin() * 15.0) + ((i / 23) as f32 % 2.0) * 20.0,
                        )
                    })
                    .collect(),
                false,
            ),
            // Collinear + a zero-length segment + a repeated vertex.
            (
                vec![
                    Point::new(10.0, 10.0),
                    Point::new(20.0, 10.0),
                    Point::new(30.0, 10.0),
                    Point::new(30.0, 10.0),
                    Point::new(40.0, 10.0),
                    Point::new(50.0, 10.0),
                    Point::new(50.0, 20.0),
                    Point::new(10.0, 20.0),
                ],
                true,
            ),
        ];
        for (poly, close) in &polys {
            for spacing in [0.5f32, 2.0, 4.0, 15.0] {
                let grid = SegmentGrid::build(poly, *close, (spacing / 2.0).max(1.0));
                let mut max_x = f32::NEG_INFINITY;
                let mut min_x = f32::INFINITY;
                let mut max_y = f32::NEG_INFINITY;
                let mut min_y = f32::INFINITY;
                for p in poly.iter() {
                    max_x = max_x.max(p.x);
                    min_x = min_x.min(p.x);
                    max_y = max_y.max(p.y);
                    min_y = min_y.min(p.y);
                }
                let (w, h) = (max_x - min_x, max_y - min_y);
                for _ in 0..4000 {
                    let p = Point::new(
                        min_x - w * 0.5 + rng() * (w * 2.0),
                        min_y - h * 0.5 + rng() * (h * 2.0),
                    );
                    // Thresholds both below and above the grid cell size.
                    let t = rng() * 3.5 + 0.25;
                    let brute = dist_to_poly(p, poly, *close) <= t;
                    assert_eq!(
                        grid.on_path(p, t),
                        brute,
                        "grid disagrees with brute force at {p:?}, t={t}, spacing={spacing}, close={close}"
                    );
                }
            }
        }
    }
}
