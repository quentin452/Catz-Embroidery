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

/// How often stitches are created when resampling a polyline into a stitch
/// path. Mirrors the Java apps' knobs (`PEmbroiderGraphics.STITCH_LENGTH` and
/// `MIN_STITCH_LENGTH`; the Java clamps the setters to `>= 0.1` / `>= 0`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct StitchSettings {
    /// Target stitch length in mm: segments longer than this are split at this
    /// interval (Java `STITCH_LENGTH`).
    pub stitch_length: f32,
    /// Minimum stitch length in mm: shorter segments are merged when the
    /// corner turns less than MAX_TURN (Java `MIN_STITCH_LENGTH`).
    pub min_stitch_length: f32,
}

impl Default for StitchSettings {
    /// The Java apps' defaults (`PEmbroiderGraphics.java`).
    fn default() -> Self {
        Self {
            stitch_length: 10.0,
            min_stitch_length: 4.0,
        }
    }
}

impl StitchSettings {
    /// Resample a polyline at this frequency: `resample(poly, min, max, 0, 0)`.
    pub fn resample(&self, poly: &[Point]) -> Result<Vec<Point>, Error> {
        resample(poly, self.min_stitch_length, self.stitch_length, 0.0, 0.0)
    }
}

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_match_the_java_apps() {
        let s = StitchSettings::default();
        assert_eq!(s.stitch_length, 10.0);
        assert_eq!(s.min_stitch_length, 4.0);
    }

    #[test]
    fn resample_uses_the_settings_as_min_max() {
        let s = StitchSettings::default();
        let poly = vec![Point::new(0.0, 0.0), Point::new(0.0, 50.0)];
        let got = s.resample(&poly).unwrap();
        let raw = resample(&poly, s.min_stitch_length, s.stitch_length, 0.0, 0.0).unwrap();
        assert_eq!(got, raw);
        // The 50 mm segment splits at the 10 mm target: 6 points.
        assert_eq!(got.len(), 6);
        assert_eq!(got[0], Point::new(0.0, 0.0));
        assert_eq!(got[1], Point::new(0.0, 10.0));
        assert_eq!(got[5], Point::new(0.0, 50.0));
    }
}
