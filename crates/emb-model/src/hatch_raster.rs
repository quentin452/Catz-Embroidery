//! Raster hatches, ported from `PEmbroiderGraphics` (the converter's image
//! path): parallel lines sampled through a binary mask, then the cross-mode
//! resampling at the intersection grid.

use crate::Error;
use crate::geom::{BCircle, Point, cos, sin};
use crate::raster::Raster;

const HALF_PI: f32 = std::f32::consts::PI / 2.0;

/// `PEmbroiderGraphics.hatchParallelRaster`: parallel lines across a bounding
/// circle of the whole image, sampled pixel-by-pixel. Each run of in-mask
/// samples becomes a polyline (run edges at the midpoint between the last
/// out-mask and first in-mask sample, matching the Java's `(lx+xx)/2f`).
pub fn hatch_parallel_raster(mask: &Raster, ang: f32, d: f32, step: f32) -> Vec<Vec<Point>> {
    let w = mask.width as f32;
    let h = mask.height as f32;
    let r = ((w * w + h * h) as f64).sqrt() as f32 * 1.05;
    let bcirc = BCircle {
        x: w / 2.0,
        y: h / 2.0,
        r,
    };

    let (ca, sa) = (cos(ang), sin(ang));
    let x0 = bcirc.x - bcirc.r * ca;
    let y0 = bcirc.y - bcirc.r * sa;
    let x1 = bcirc.x + bcirc.r * ca;
    let y1 = bcirc.y + bcirc.r * sa;

    let l = Point::new(x0, y0).dist(Point::new(x1, y1));
    let n = ((l / d) as f64).ceil() as i32;
    // Java: `(int)PApplet.ceil(bcirc.r / step)`.
    let m = (bcirc.r / step).ceil() as i32;

    let mut polys: Vec<Vec<Point>> = Vec::new();
    for i in 0..n {
        let t = i as f32 / (n - 1) as f32;
        let x = x0 * (1.0 - t) + x1 * t;
        let y = y0 * (1.0 - t) + y1 * t;

        let px = x + bcirc.r * cos(ang - HALF_PI);
        let py = y + bcirc.r * sin(ang - HALF_PI);
        let qx = x + bcirc.r * cos(ang + HALF_PI);
        let qy = y + bcirc.r * sin(ang + HALF_PI);

        let mut prev = false;
        let (mut lx, mut ly) = (None::<f32>, None::<f32>);
        let mut last_open = 0usize;
        for j in 0..=m {
            let s = j as f32 / m as f32;
            let xx = px * (1.0 - s) + qx * s;
            let yy = py * (1.0 - s) + qy * s;
            // The Java's run state machine: out->in opens a polyline, in->out
            // closes one; `add` is true on both transitions.
            let in_mask = mask.get(xx as i32, yy as i32);
            let (add, is_open) = if in_mask {
                if !prev { (true, true) } else { (false, false) }
            } else {
                (prev, false)
            };
            prev = in_mask;
            if add {
                let idx = if is_open {
                    polys.push(Vec::new());
                    let idx = polys.len() - 1;
                    last_open = idx;
                    idx
                } else {
                    last_open
                };
                // The Java: `lx == null` only on the very first sample of a
                // line (lx is updated after every sample); later openings add
                // the midpoint to the new polyline.
                match (lx, ly) {
                    (Some(lxx), Some(lyy)) => {
                        polys[idx].push(Point::new((lxx + xx) / 2.0, (lyy + yy) / 2.0));
                    }
                    _ => polys[idx].push(Point::new(xx, yy)),
                }
            }
            lx = Some(xx);
            ly = Some(yy);
        }
    }
    polys
}

/// `PEmbroiderGraphics.resampleCrossIntersection`: resample the hatch polylines
/// at their intersections with a perpendicular cross grid (the CROSS mode's
/// stitch length control). The Java rolls `app.random(t0, t1)` per
/// intersection and lerps with `randomize`; with `randomize == 0` (RESAMPLE_NOISE
/// default) the result is deterministic — the Rust port refuses anything else
/// (pin 3, same ruling as `resample`).
pub fn resample_cross_intersection(
    polys: &[Vec<Point>],
    angle: f32,
    spacing: f32,
    len: f32,
    offset_factor: f32,
    randomize: f32,
) -> Result<Vec<Vec<Point>>, Error> {
    if randomize != 0.0 {
        return Err(Error::ResampleRandomizeNotPorted);
    }
    let base = len * offset_factor;
    let relang = ((spacing as f64).atan2(base as f64)) as f32;
    let d = len * cos(HALF_PI - relang);
    let ang = angle - relang;

    let mut bcirc = BCircle::from_polys(polys);
    bcirc.r *= 1.05;

    let (ca, sa) = (cos(ang), sin(ang));
    let x0 = bcirc.x - bcirc.r * ca;
    let y0 = bcirc.y - bcirc.r * sa;
    let x1 = bcirc.x + bcirc.r * ca;
    let y1 = bcirc.y + bcirc.r * sa;

    let l = Point::new(x0, y0).dist(Point::new(x1, y1));
    let n = ((l / d) as f64).ceil() as i32;

    let mut crosslines: Vec<(Point, Point)> = Vec::new();
    for i in 0..n {
        let t = i as f32 / (n - 1) as f32;
        let x = x0 * (1.0 - t) + x1 * t;
        let y = y0 * (1.0 - t) + y1 * t;
        let px = x + bcirc.r * cos(ang - HALF_PI);
        let py = y + bcirc.r * sin(ang - HALF_PI);
        let qx = x + bcirc.r * cos(ang + HALF_PI);
        let qy = y + bcirc.r * sin(ang + HALF_PI);
        crosslines.push((Point::new(px, py), Point::new(qx, qy)));
    }

    let mut result: Vec<Vec<Point>> = Vec::new();
    for poly in polys {
        if poly.len() < 2 {
            continue;
        }
        let mut resampled: Vec<Point> = Vec::new();
        for j in 0..poly.len() - 1 {
            let a = poly[j];
            let b = poly[j + 1];
            resampled.push(a);

            let mut iparams: Vec<f32> = Vec::new();
            for (p, q) in &crosslines {
                if let Some((t, _)) = crate::geom::segment_intersect(a, b, *p, *q) {
                    iparams.push(t);
                }
            }
            iparams.sort_by(|x, y| x.partial_cmp(y).unwrap_or(std::cmp::Ordering::Equal));

            for &t0 in &iparams {
                // Java: tr = app.random(t0, t1); t = lerp(t0, tr, randomize) —
                // with randomize == 0 the roll and the t1 clamp are dead
                // weight (measured, docs/LESSONS.md).
                resampled.push(Point::new(
                    a.x * (1.0 - t0) + b.x * t0,
                    a.y * (1.0 - t0) + b.y * t0,
                ));
            }
            resampled.push(b);
        }
        result.push(resampled);
    }
    Ok(result)
}

/// The CROSS raster mode (`hatchRaster` with HATCH_MODE == CROSS): two
/// perpendicular passes, then the intersection resampling. `angle` is the
/// effective angle AFTER `calcAxisAngleForParallel` (HALF_PI - ang) — the
/// caller applies it, mirroring `hatchRaster`.
pub fn hatch_cross_raster(
    mask: &Raster,
    angle: f32,
    spacing: f32,
    len: f32,
    offset_factor: f32,
) -> Result<Vec<Vec<Point>>, Error> {
    let mut polys = hatch_parallel_raster(mask, angle, spacing, 1.0);
    polys.extend(hatch_parallel_raster(mask, angle + HALF_PI, spacing, 1.0));
    resample_cross_intersection(&polys, angle, spacing, len, offset_factor, 0.0)
}
