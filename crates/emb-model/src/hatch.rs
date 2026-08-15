//! Hatch fills, ported from `PEmbroiderGraphics`.

use crate::geom::{self, BCircle, Point};

const HALF_PI: f32 = std::f32::consts::PI / 2.0;

/// `PEmbroiderGraphics.hatchParallel`: parallel lines across the polygon's
/// bounding circle, crossing the polygon pairwise. Returns one 2-point
/// polyline per crossing pair.
pub fn hatch_parallel(poly: &[Point], ang: f32, d: f32) -> Vec<Vec<Point>> {
    let mut bcirc = BCircle::from_poly(poly);
    bcirc.r *= 1.05;

    let (ca, sa) = (geom::cos(ang), geom::sin(ang));
    let x0 = bcirc.x - bcirc.r * ca;
    let y0 = bcirc.y - bcirc.r * sa;
    let x1 = bcirc.x + bcirc.r * ca;
    let y1 = bcirc.y + bcirc.r * sa;

    let l = Point::new(x0, y0).dist(Point::new(x1, y1));
    // Java: `(int)Math.ceil(l/d)` with l/d a FLOAT division.
    let n = ((l / d) as f64).ceil() as i32;

    let mut hatch: Vec<Vec<Point>> = Vec::new();
    for i in 0..n {
        let t = i as f32 / (n - 1) as f32;
        let x = x0 * (1.0 - t) + x1 * t;
        let y = y0 * (1.0 - t) + y1 * t;

        let px = x + bcirc.r * geom::cos(ang - HALF_PI);
        let py = y + bcirc.r * geom::sin(ang - HALF_PI);
        let qx = x + bcirc.r * geom::cos(ang + HALF_PI);
        let qy = y + bcirc.r * geom::sin(ang + HALF_PI);

        let ps = geom::segment_intersect_polygon(Point::new(px, py), Point::new(qx, qy), poly);
        let mut j = 0;
        while j + 1 < ps.len() {
            hatch.push(vec![ps[j], ps[j + 1]]);
            j += 2;
        }
    }
    hatch
}
