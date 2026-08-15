//! Geometry primitives ported from the Java model: `PEmbroiderGraphics` helpers
//! plus the vendored `PVector` semantics.
//!
//! Arithmetic order matters: Java computes products in f32 and casts to f64 only
//! for sqrt/cos/sin. We replicate that order so differences stay in the last
//! ulp (docs/decisions/D002.md allows 0.001 mm in tests).

/// A 2D point in design space, in millimetres.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Point {
    pub x: f32,
    pub y: f32,
}

impl Point {
    pub const fn new(x: f32, y: f32) -> Self {
        Self { x, y }
    }

    /// `PVector.dist`: f32 products, f64 sqrt, cast back to f32.
    pub fn dist(self, other: Point) -> f32 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        ((dx * dx + dy * dy) as f64).sqrt() as f32
    }

    /// `PVector.angleBetween` (static): f32 products, f64 math, clamped to
    /// [-1, 1] before acos. Zero vectors return 0 (the Java's guard).
    pub fn angle_between(u: Point, v: Point) -> f32 {
        if (u.x == 0.0 && u.y == 0.0) || (v.x == 0.0 && v.y == 0.0) {
            return 0.0;
        }
        let dot = (u.x * v.x + u.y * v.y) as f64;
        let u_mag = ((u.x * u.x + u.y * u.y) as f64).sqrt();
        let v_mag = ((v.x * v.x + v.y * v.y) as f64).sqrt();
        let amt = dot / (u_mag * v_mag);
        if amt <= -1.0 {
            return std::f32::consts::PI;
        }
        if amt >= 1.0 {
            return 0.0;
        }
        amt.acos() as f32
    }
}

/// `PApplet.cos(float)`: f64 math cast back to f32.
pub fn cos(ang: f32) -> f32 {
    (ang as f64).cos() as f32
}

/// `PApplet.sin(float)`: f64 math cast back to f32.
pub fn sin(ang: f32) -> f32 {
    (ang as f64).sin() as f32
}

/// `PEmbroiderGraphics.det` on two 2D points plus a 3D vector (the cross
/// product carries the 2D area in its z). Computed with the Java's term order.
fn det(a: [f32; 3], b: [f32; 3], c: [f32; 3]) -> f32 {
    a[0] * b[1] * c[2] + a[1] * b[2] * c[0] + a[2] * b[0] * c[1]
        - a[2] * b[1] * c[0]
        - a[1] * b[0] * c[2]
        - a[0] * b[2] * c[1]
}

fn cross(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [
        a[1] * b[2] - b[1] * a[2],
        a[2] * b[0] - b[2] * a[0],
        a[0] * b[1] - b[0] * a[1],
    ]
}

/// `PEmbroiderGraphics.segmentIntersect3D` (2D usage): returns the lerp
/// parameter on the first segment (and the second), or None when parallel or
/// out of range.
pub fn segment_intersect(p0: Point, p1: Point, q0: Point, q1: Point) -> Option<(f32, f32)> {
    let p0 = [p0.x, p0.y, 0.0];
    let p1 = [p1.x, p1.y, 0.0];
    let q0 = [q0.x, q0.y, 0.0];
    let q1 = [q1.x, q1.y, 0.0];
    let d0 = [p1[0] - p0[0], p1[1] - p0[1], 0.0];
    let d1 = [q1[0] - q0[0], q1[1] - q0[1], 0.0];
    let vc = cross(d0, d1);
    let vcn = vc[0] * vc[0] + vc[1] * vc[1] + vc[2] * vc[2];
    if vcn == 0.0 {
        return None;
    }
    let qp0 = [q0[0] - p0[0], q0[1] - p0[1], 0.0];
    let t = det(qp0, d1, vc) / vcn;
    let s = det(qp0, d0, vc) / vcn;
    // Java fidelity: `t < 0 || t > 1 || ...` LETS NaN through (NaN compares
    // false to everything). The condition is written in that exact form.
    #[allow(clippy::manual_range_contains)]
    if t < 0.0 || t > 1.0 || s < 0.0 || s > 1.0 {
        return None;
    }
    Some((t, s))
}

/// `PEmbroiderGraphics.segmentIntersectPolygon`: all intersections of a segment
/// with a polygon's edges, sorted by the segment's lerp parameter.
pub fn segment_intersect_polygon(p0: Point, p1: Point, poly: &[Point]) -> Vec<Point> {
    let mut iparams: Vec<f32> = Vec::new();
    for i in 0..poly.len() {
        let v0 = poly[i];
        let v1 = poly[(i + 1) % poly.len()];
        if let Some((t, _)) = segment_intersect(p0, p1, v0, v1) {
            iparams.push(t);
        }
    }
    iparams.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    iparams
        .iter()
        .map(|&t| Point::new(p0.x * (1.0 - t) + p1.x * t, p0.y * (1.0 - t) + p1.y * t))
        .collect()
}

/// `PEmbroiderGraphics.segmentIntersectPolygons`: all intersections of a segment
/// with several polygons' edges, sorted by the segment's lerp parameter.
pub fn segment_intersect_polygons(p0: Point, p1: Point, polys: &[Vec<Point>]) -> Vec<Point> {
    let mut iparams: Vec<f32> = Vec::new();
    for poly in polys {
        for i in 0..poly.len() {
            let v0 = poly[i];
            let v1 = poly[(i + 1) % poly.len()];
            if let Some((t, _)) = segment_intersect(p0, p1, v0, v1) {
                iparams.push(t);
            }
        }
    }
    iparams.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    iparams
        .iter()
        .map(|&t| Point::new(p0.x * (1.0 - t) + p1.x * t, p0.y * (1.0 - t) + p1.y * t))
        .collect()
}

/// `PEmbroiderGraphics.centerpoint`: the arithmetic mean of the points.
pub fn centerpoint(poly: &[Point]) -> Point {
    let (mut x, mut y) = (0.0f32, 0.0f32);
    for p in poly {
        x += p.x;
        y += p.y;
    }
    Point::new(x / poly.len() as f32, y / poly.len() as f32)
}

/// `PEmbroiderGraphics.centerpoint(polys, 0)`: sums ALL points but divides by
/// the POLYLINE count — a measured Java quirk (PEmbroiderGraphics.java:682,
/// docs/LESSONS.md). Replicated: the shifted cross-grid it produces is a valid
/// hatch (same spacing, different phase), so fixture fidelity wins.
pub fn centerpoint_polys(polys: &[Vec<Point>]) -> Point {
    let (mut x, mut y) = (0.0f32, 0.0f32);
    for poly in polys {
        for p in poly {
            x += p.x;
            y += p.y;
        }
    }
    Point::new(x / polys.len() as f32, y / polys.len() as f32)
}

/// `PEmbroiderGraphics.BCircle`: the bounding circle around a polygon.
#[derive(Debug, Clone, Copy)]
pub struct BCircle {
    pub x: f32,
    pub y: f32,
    pub r: f32,
}

impl BCircle {
    pub fn from_poly(poly: &[Point]) -> Self {
        let c = centerpoint(poly);
        let mut rmax = 0.0f32;
        for p in poly {
            rmax = rmax.max(c.dist(*p));
        }
        Self {
            x: c.x,
            y: c.y,
            r: rmax,
        }
    }

    /// `BCircle(polys, whatever)`: the bounding circle around several
    /// polylines (used by the raster hatches). The center comes from the
    /// Java's quirky `centerpoint(polys, 0)` — see that function's doc.
    pub fn from_polys(polys: &[Vec<Point>]) -> Self {
        let c = centerpoint_polys(polys);
        let mut rmax = 0.0f32;
        for poly in polys {
            for p in poly {
                rmax = rmax.max(c.dist(*p));
            }
        }
        Self {
            x: c.x,
            y: c.y,
            r: rmax,
        }
    }
}
