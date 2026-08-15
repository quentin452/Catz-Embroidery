//! Polyline resampling, ported from `PEmbroiderGraphics.resample`.
//!
//! The `randomize` parameter is NOT ported (pin 3: no consumer uses it — the
//! converter's path runs with RESAMPLE_NOISE = 0). A call with
//! `randomize != 0.0` fails loudly instead of pretending to be deterministic;
//! the Java's random values are multiplied by `randomize` so the deterministic
//! core here produces byte-identical output for `randomize == 0.0`.

use crate::Error;
use crate::geom::Point;

const MAX_TURN: f32 = 0.2;

/// `PEmbroiderGraphics.resample`: merge short segments when the corner turns
/// less than MAX_TURN, split long segments at max_len intervals.
pub fn resample(
    poly: &[Point],
    min_len: f32,
    max_len: f32,
    randomize: f32,
    randomize_offset: f32,
) -> Result<Vec<Point>, Error> {
    if randomize != 0.0 || randomize_offset != 0.0 {
        return Err(Error::ResampleRandomizeNotPorted);
    }
    let mut poly2: Vec<Point> = Vec::new();
    if poly.is_empty() {
        return Ok(poly.to_vec());
    }
    poly2.push(poly[0]);

    let mut clen = 0.0f32;
    for i in 0..poly.len() - 1 {
        let p0 = poly[i];
        let p1 = poly[i + 1];
        let l = p0.dist(p1);

        if l + clen < min_len && i != poly.len() - 2 {
            let a = poly[i];
            let b = poly[(i + 1) % poly.len()];
            let c = poly[(i + 2) % poly.len()];
            let u = Point::new(b.x - a.x, b.y - a.y);
            let v = Point::new(c.x - b.x, c.y - b.y);
            let ang = Point::angle_between(u, v).abs();
            if ang < MAX_TURN {
                clen += l;
                continue;
            }
        }

        clen = 0.0;
        if l < max_len {
            poly2.push(p1);
            continue;
        }

        let n = ((l / max_len) as f64).ceil() as i32;
        if n > 1 {
            let mut lin = Vec::with_capacity(n as usize - 1);
            for j in 1..n {
                // The Java rolls two random values here but multiplies them by
                // `randomize` (0.0 in every ported consumer) — the output is
                // identical without them.
                let rr = 0.0f32;
                lin.push(j as f32 / n as f32 + rr * randomize * (1.0 / n as f32) * 0.5);
            }
            for &t in &lin {
                poly2.push(Point::new(
                    p0.x * (1.0 - t) + p1.x * t,
                    p0.y * (1.0 - t) + p1.y * t,
                ));
            }
        }
        poly2.push(p1);
    }

    Ok(poly2)
}
