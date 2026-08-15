//! Contour tracing, ported from `PEmbroiderTrace` (itself ported there from
//! PContour by LingDong â€” a Suzuki-Abe style border follower).
//!
//! The 3-argument variant's hole/parent bookkeeping is not ported: no consumer
//! uses it (the converter's `image()` path calls the 2-argument version).

use crate::Error;
use crate::geom::Point;
use crate::raster::Raster;

const N_PIXEL_NEIGHBOR: i32 = 8;

/// The Java `neighborIDToIndex`: counter-clockwise neighbour IDs starting at
/// (0, +1).
fn neighbor_id_to_index(i: i32, j: i32, id: i32) -> Option<(i32, i32)> {
    match id {
        0 => Some((i, j + 1)),
        1 => Some((i - 1, j + 1)),
        2 => Some((i - 1, j)),
        3 => Some((i - 1, j - 1)),
        4 => Some((i, j - 1)),
        5 => Some((i + 1, j - 1)),
        6 => Some((i + 1, j)),
        7 => Some((i + 1, j + 1)),
        _ => None,
    }
}

fn neighbor_index_to_id(i0: i32, j0: i32, i: i32, j: i32) -> i32 {
    match (i - i0, j - j0) {
        (0, 1) => 0,
        (-1, 1) => 1,
        (-1, 0) => 2,
        (-1, -1) => 3,
        (0, -1) => 4,
        (1, -1) => 5,
        (1, 0) => 6,
        (1, 1) => 7,
        _ => -1,
    }
}

/// The labelled working grid the border follower mutates (the Java's `F`, `w`,
/// `h` trio, bundled so the neighbour scans stay small).
struct Grid<'a> {
    f: &'a mut [i32],
    w: usize,
    h: usize,
}

impl Grid<'_> {
    fn get(&self, i: usize, j: usize) -> i32 {
        self.f[i * self.w + j]
    }

    fn set(&mut self, i: usize, j: usize, v: i32) {
        self.f[i * self.w + j] = v;
    }

    /// The Java `ccwNon0`: first counter-clockwise non-zero neighbour.
    fn ccw_non0(&self, i0: i32, j0: i32, i: i32, j: i32, offset: i32) -> Option<(i32, i32)> {
        let id = neighbor_index_to_id(i0, j0, i, j);
        for k in 0..N_PIXEL_NEIGHBOR {
            let kk = (k + id + offset + N_PIXEL_NEIGHBOR * 2) % N_PIXEL_NEIGHBOR;
            let (ii, jj) = neighbor_id_to_index(i0, j0, kk)?;
            if ii >= 0
                && ii < self.h as i32
                && jj >= 0
                && jj < self.w as i32
                && self.f[ii as usize * self.w + jj as usize] != 0
            {
                return Some((ii, jj));
            }
        }
        None
    }

    /// The Java `cwNon0`: first clockwise non-zero neighbour.
    fn cw_non0(&self, i0: i32, j0: i32, i: i32, j: i32, offset: i32) -> Option<(i32, i32)> {
        let id = neighbor_index_to_id(i0, j0, i, j);
        for k in 0..N_PIXEL_NEIGHBOR {
            let kk = (-k + id - offset + N_PIXEL_NEIGHBOR * 2) % N_PIXEL_NEIGHBOR;
            let (ii, jj) = neighbor_id_to_index(i0, j0, kk)?;
            if ii >= 0
                && ii < self.h as i32
                && jj >= 0
                && jj < self.w as i32
                && self.f[ii as usize * self.w + jj as usize] != 0
            {
                return Some((ii, jj));
            }
        }
        None
    }
}

/// `PEmbroiderTrace.DistanceTransform.EDT_f`: squared distance from x to the
/// site i with the 1D distance g_i (Meijster's Euclidean distance transform,
/// ported there from JS).
fn edt_f(x: f32, i: f32, g_i: f32) -> f32 {
    (x - i) * (x - i) + g_i * g_i
}

/// `PEmbroiderTrace.DistanceTransform.EDT_Sep`: the separation point between
/// two 1D sites, floored (the Java's `PApplet.floor`).
fn edt_sep(i: f32, u: f32, g_i: f32, g_u: f32) -> f32 {
    ((u * u - i * i + g_u * g_u - g_i * g_i) / (2.0 * (u - i))).floor()
}

/// `PEmbroiderTrace.DistanceTransform.getDistTransform`: the Meijster
/// Euclidean distance transform. x-major indexing (index = x + y * m).
pub fn distance_transform(mask: &[bool], m: usize, n: usize) -> Vec<f32> {
    let infinity = (m + n) as f32;
    let mut g = vec![0.0f32; m * n];
    // First phase: 1D distance along columns, then a backward pass.
    for x in 0..m {
        g[x] = if mask[x] { 0.0 } else { infinity };
        for y in 1..n {
            let idx = x + y * m;
            g[idx] = if mask[idx] { 0.0 } else { 1.0 + g[idx - m] };
        }
        for y in (0..n).rev() {
            let idx = x + y * m;
            // The Java's `if (x+(y+1)*m < g.length)` "hack" bounds the
            // backward pass at the bottom row.
            if idx + m < g.len() && g[idx + m] < g[idx] {
                g[idx] = 1.0 + g[idx + m];
            }
        }
    }

    // Second phase: the parabolic-envelope scan along rows.
    let mut dt = vec![0.0f32; m * n];
    let mut s = vec![0usize; m];
    let mut t = vec![0.0f32; m];
    for y in 0..n {
        let mut q3 = 0usize;
        s[0] = 0;
        t[0] = 0.0;
        for u in 1..m {
            // The Java: `while (q >= 0 && f(...) > f(...)) q--;` — the q == 0
            // check below replicates the decrement-into -1 and reset.
            while q3 > 0
                && edt_f(t[q3], s[q3] as f32, g[s[q3] + y * m])
                    > edt_f(t[q3], u as f32, g[u + y * m])
            {
                q3 -= 1;
            }
            if q3 == 0
                && edt_f(t[0], s[0] as f32, g[s[0] + y * m]) > edt_f(t[0], u as f32, g[u + y * m])
            {
                // Java: q fell to -1, reset to 0 with the new site.
                q3 = 0;
                s[0] = u;
            } else {
                let qi = if q3 == 0 { 0 } else { q3 };
                let w = 1.0 + edt_sep(s[qi] as f32, u as f32, g[s[qi] + y * m], g[u + y * m]);
                if w < m as f32 {
                    q3 += 1;
                    s[q3] = u;
                    t[q3] = w;
                }
            }
        }
        // Scan 4: read the envelope back. The Java decrements q unconditionally
        // (`if (u == t[q]) q--`) and only ever hits -1 on the last iteration
        // (t[0] stays 0.0); the `q > 0` guard is that safety made explicit.
        let mut q = q3;
        for u in (0..m).rev() {
            let d = edt_f(u as f32, s[q] as f32, g[s[q] + y * m]);
            let d = ((d as f64).sqrt() as f32).floor();
            dt[u + y * m] = d;
            if u as f32 == t[q] && q > 0 {
                q -= 1;
            }
        }
    }
    dt
}

/// `PEmbroiderTrace.findIsolines(im, n, d)`: the distance-transform isolines of
/// a mask, each level traced and simplified. `n == -1` picks the level count
/// from the maximum distance.
pub fn find_isolines(raster: &Raster, n: i32, d: f32) -> Result<Vec<Vec<Vec<Point>>>, Error> {
    let w = raster.width;
    let h = raster.height;
    // The Java: `bim[i] = red < 128` â€” the mask INVERTED (distance measured
    // from the outside).
    let bim: Vec<bool> = raster.pixels().iter().map(|&on| !on).collect();
    let dt = distance_transform(&bim, w, h);
    let mut max_dt = f32::NEG_INFINITY;
    for &v in &dt {
        max_dt = max_dt.max(v);
    }
    let n = if n == -1 { (max_dt / d) as i32 } else { n };
    let n = n as usize;
    let thresh: Vec<f32> = (0..n)
        .map(|i| (i as f32 + 1.0) / (n as f32 + 1.0) * max_dt)
        .collect();
    let mut isolines = Vec::with_capacity(n);
    for &th in &thresh {
        let mut iso = vec![0i32; w * h];
        for (i, &v) in dt.iter().enumerate() {
            iso[i] = if v > th { 1 } else { 0 };
        }
        let mut contours = find_contours_grid(&iso, w, h)?;
        for c in contours.iter_mut() {
            *c = approx_poly_dp(c, 1.0);
        }
        isolines.push(contours);
    }
    Ok(isolines)
}

/// `PEmbroiderTrace.findContours(F, w, h)`: contours of a labelled grid (the
/// shared core of `find_contours` and `find_isolines`).
fn find_contours_grid(f: &[i32], w: usize, h: usize) -> Result<Vec<Vec<Point>>, Error> {
    let mut f = f.to_vec();
    let mut nbd = 1i32;
    let mut contours: Vec<Vec<Point>> = Vec::new();
    let mut grid = Grid { f: &mut f, w, h };
    for i in 1..h - 1 {
        for j in 1..w - 1 {
            if grid.get(i, j) == 0 {
                continue;
            }
            let (mut i2, mut j2) = if grid.get(i, j) == 1 && grid.get(i, j - 1) == 0 {
                nbd += 1;
                (i as i32, j as i32 - 1)
            } else if grid.get(i, j) >= 1 && grid.get(i, j + 1) == 0 {
                nbd += 1;
                (i as i32, j as i32 + 1)
            } else {
                continue;
            };
            let i1j1 = grid.cw_non0(i as i32, j as i32, i2, j2, 0);
            let (i1, j1) = match i1j1 {
                Some(v) => v,
                None => {
                    grid.set(i, j, -nbd);
                    continue;
                }
            };
            i2 = i1;
            j2 = j1;
            let (mut i3, mut j3) = (i as i32, j as i32);
            let mut contour = vec![Point::new(j as f32, i as f32)];
            loop {
                let i4j4 = grid.ccw_non0(i3, j3, i2, j2, 1).ok_or_else(|| {
                    Error::Trace(format!("border follower lost at pixel ({j3}, {i3})"))
                })?;
                let (i4, j4) = i4j4;
                contour.push(Point::new(j4 as f32, i4 as f32));
                if j3 + 1 < w as i32 && grid.get(i3 as usize, j3 as usize + 1) == 0 {
                    grid.set(i3 as usize, j3 as usize, -nbd);
                } else if grid.get(i3 as usize, j3 as usize) == 1 {
                    grid.set(i3 as usize, j3 as usize, nbd);
                }
                if i4 == i as i32 && j4 == j as i32 && i3 == i1 && j3 == j1 {
                    break;
                }
                i2 = i3;
                j2 = j3;
                i3 = i4;
                j3 = j4;
            }
            contours.push(contour);
        }
    }
    Ok(contours)
}

/// `PEmbroiderTrace.findContours(im)`: all contours of a binary mask, as
/// pixel-grid points.
pub fn find_contours(raster: &Raster) -> Result<Vec<Vec<Point>>, Error> {
    let f: Vec<i32> = raster
        .pixels()
        .iter()
        .map(|&on| if on { 1 } else { 0 })
        .collect();
    find_contours_grid(&f, raster.width, raster.height)
}

/// `PEmbroiderGraphics.pointDistanceToSegment` (the SO answer formula).
pub fn point_distance_to_segment(p: Point, p0: Point, p1: Point) -> f32 {
    let (x, y) = (p.x, p.y);
    let (x1, y1) = (p0.x, p0.y);
    let (x2, y2) = (p1.x, p1.y);
    let a = x - x1;
    let b = y - y1;
    let c = x2 - x1;
    let d = y2 - y1;
    let dot = a * c + b * d;
    let len_sq = c * c + d * d;
    let param = if len_sq != 0.0 { dot / len_sq } else { -1.0 };
    let (xx, yy) = if param < 0.0 {
        (x1, y1)
    } else if param > 1.0 {
        (x2, y2)
    } else {
        (x1 + param * c, y1 + param * d)
    };
    let dx = x - xx;
    let dy = y - yy;
    ((dx * dx + dy * dy) as f64).sqrt() as f32
}

/// `PEmbroiderTrace.approxPolyDP`: recursive polyline simplification.
pub fn approx_poly_dp(polyline: &[Point], epsilon: f32) -> Vec<Point> {
    if polyline.len() <= 2 {
        return polyline.to_vec();
    }
    let mut dmax = 0.0f32;
    let mut argmax = -1i32;
    for i in 1..polyline.len() - 1 {
        let d = point_distance_to_segment(polyline[i], polyline[0], polyline[polyline.len() - 1]);
        if d > dmax {
            dmax = d;
            argmax = i as i32;
        }
    }
    if dmax > epsilon {
        let l = approx_poly_dp(&polyline[0..=argmax as usize], epsilon);
        let r = approx_poly_dp(&polyline[argmax as usize..], epsilon);
        let mut ret = l[..l.len() - 1].to_vec();
        ret.extend(r);
        ret
    } else {
        vec![polyline[0], polyline[polyline.len() - 1]]
    }
}
