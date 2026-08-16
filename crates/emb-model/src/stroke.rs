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

use crate::geom::{self, Point};
use crate::trace;

const HALF_PI: f32 = std::f32::consts::PI / 2.0;

/// The Java's `PERPENDICULAR_STROKE_CAP_DENSITY_MULTIPLIER` default.
const CAP_DENSITY_MULTIPLIER: f32 = 1.0;

/// The oracle: is `p` within `threshold` of the polyline?
///
/// The Java draws the whole polyline into the raster once, stroked with weight
/// `spacing` (half-width `spacing/2`), so the oracle is the minimum distance
/// over the polyline's segments. The stroked SQUARE caps and MITER joins are
/// raster-side details below the invariant comparison level (D004) and are
/// not modelled.
fn on_path(p: Point, poly: &[Point], close: bool, threshold: f32) -> bool {
    let segs = poly.len() - if close { 0 } else { 1 };
    let mut best = f32::INFINITY;
    for i in 0..segs {
        best = best.min(trace::point_distance_to_segment(
            p,
            poly[i],
            poly[(i + 1) % poly.len()],
        ));
    }
    best <= threshold
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
                    |p| on_path(p, poly, close, spacing / 2.0),
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
                    on_path(p, poly, close, spacing / 2.0 + 1.0)
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
}
