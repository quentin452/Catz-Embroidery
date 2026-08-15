//! Hatch fills, ported from `PEmbroiderGraphics`.

use crate::Error;
use crate::geom::{self, BCircle, Point};
use crate::raster::Raster;
use crate::trace;

const HALF_PI: f32 = std::f32::consts::PI / 2.0;

/// The Java's `CONCENTRIC_ANTIALIGN` default: corners sharper than 0.6 rad
/// survive the even-level antialias pass.
const CONCENTRIC_ANTIALIGN: f32 = 0.6;

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

/// `PEmbroiderGraphics.isolines(im, d)`: the CONCENTRIC/SPIRAL raster hatch.
/// Each distance-transform level is traced and simplified; even levels get the
/// antialign pass (sharp corners kept, midpoints inserted).
pub fn isolines(raster: &Raster, d: f32) -> Result<Vec<Vec<Point>>, Error> {
    let isos = trace::find_isolines(raster, -1, d)?;
    let mut polys: Vec<Vec<Point>> = Vec::new();
    for (i, level) in isos.iter().enumerate() {
        for poly in level {
            if CONCENTRIC_ANTIALIGN > 0.0 && i % 2 == 0 {
                let mut qq: Vec<Point> = Vec::new();
                for k in 0..poly.len() + 1 {
                    if k != poly.len() {
                        let a = poly[(k + poly.len() - 1) % poly.len()];
                        let b = poly[k];
                        let c = poly[(k + 1) % poly.len()];
                        let u = Point::new(b.x - a.x, b.y - a.y);
                        let v = Point::new(c.x - b.x, c.y - b.y);
                        let mut ang = Point::angle_between(u, v).abs();
                        if ang > std::f32::consts::PI {
                            ang = std::f32::consts::TAU - ang;
                        }
                        if ang > CONCENTRIC_ANTIALIGN {
                            qq.push(b);
                        }
                    }
                    let pa = poly[k % poly.len()];
                    let pb = poly[(k + 1) % poly.len()];
                    qq.push(Point::new(pa.x * 0.5 + pb.x * 0.5, pa.y * 0.5 + pb.y * 0.5));
                }
                polys.push(qq);
            } else {
                polys.push(poly.clone());
            }
        }
    }
    Ok(polys)
}
