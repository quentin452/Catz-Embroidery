//! Contour tracing, ported from `PEmbroiderTrace` (itself ported there from
//! PContour by LingDong — a Suzuki-Abe style border follower).
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

/// `PEmbroiderTrace.findContours(im)`: all contours of a binary mask, as
/// pixel-grid points. The working array is labelled in place by the algorithm
/// (the Java mutates its copy of the pixels too).
pub fn find_contours(raster: &Raster) -> Result<Vec<Vec<Point>>, Error> {
    let w = raster.width;
    let h = raster.height;
    let mut f: Vec<i32> = raster
        .pixels()
        .iter()
        .map(|&on| if on { 1 } else { 0 })
        .collect();

    let mut nbd = 1i32;
    // `lnbd` exists in the Java only to feed the hole/parent bookkeeping of the
    // 3-argument variant, which no consumer uses (see the module docs) — not
    // ported.
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
