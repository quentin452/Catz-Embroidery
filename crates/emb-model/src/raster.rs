//! A binary raster mask: what the trace and the raster hatches read.
//!
//! Pixels are `true` inside the mask. The Java suite's equivalent is a
//! thresholded PImage (pixels > 0x7F red channel); `get` out of bounds returns
//! false — the vendored `PImage.get` returns 0 for out-of-range coordinates
//! (measured, docs/LESSONS.md).

use crate::Error;
use crate::geom::Point;

#[derive(Debug, Clone, PartialEq)]
pub struct Raster {
    pub width: usize,
    pub height: usize,
    pixels: Vec<bool>,
}

impl Raster {
    pub fn new(width: usize, height: usize, pixels: Vec<bool>) -> Result<Self, Error> {
        if pixels.len() != width * height {
            return Err(Error::RasterSizeMismatch {
                width,
                height,
                found: pixels.len(),
            });
        }
        Ok(Self {
            width,
            height,
            pixels,
        })
    }

    /// The Java `PImage.get(x, y)` on a thresholded mask: true inside the
    /// mask, false outside the image bounds.
    pub fn get(&self, x: i32, y: i32) -> bool {
        if x < 0 || y < 0 || x >= self.width as i32 || y >= self.height as i32 {
            return false;
        }
        self.pixels[y as usize * self.width + x as usize]
    }

    pub fn pixels(&self) -> &[bool] {
        &self.pixels
    }

    /// The Java's `blendMode(SUBTRACT)` on a white-on-black mask, binary:
    /// keep `self` where `other` is off, clear it where `other` is on
    /// (255 - 255 = 0, 255 - 0 = 255). Same dimensions required.
    pub fn and_not(&mut self, other: &Raster) -> Result<(), Error> {
        if self.width != other.width || self.height != other.height {
            return Err(Error::RasterBlendSizeMismatch {
                self_w: self.width,
                self_h: self.height,
                other_w: other.width,
                other_h: other.height,
            });
        }
        for (a, b) in self.pixels.iter_mut().zip(&other.pixels) {
            *a &= !*b;
        }
        Ok(())
    }

    /// OR `other` into `self` at the pixel offset `(dx, dy)`, clipped: the
    /// mask-union builder (later layers' element masks overlaid onto the
    /// element's box). Pixels of `other` outside `self` are dropped, like
    /// `get`'s out-of-bounds rule.
    pub fn overlay_or(&mut self, other: &Raster, dx: i32, dy: i32) {
        for y in 0..other.height as i32 {
            let sy = y + dy;
            if sy < 0 || sy >= self.height as i32 {
                continue;
            }
            for x in 0..other.width as i32 {
                let sx = x + dx;
                if sx < 0 || sx >= self.width as i32 {
                    continue;
                }
                if other.get(x, y) {
                    self.pixels[sy as usize * self.width + sx as usize] = true;
                }
            }
        }
    }
}

/// The Java2D polygon fill's rasterised mask (the editor's cull): a pixel is
/// ON when its center `(x + 0.5, y + 0.5)` is inside the polygon by the
/// NONZERO winding rule (the Java2D fill rule; D004's class — the AA edge
/// coverage is not reproduced, so this is an oracle, invariant-tested, never
/// fixture-compared against the Java). `(x0, y0)` is the design-space origin
/// of the mask, at 1 px per mm like the Java's layer render.
pub fn fill_polygon(poly: &[Point], x0: f32, y0: f32, width: usize, height: usize) -> Raster {
    let mut pixels = Vec::with_capacity(width * height);
    for py in 0..height as i32 {
        for px in 0..width as i32 {
            let p = Point::new(x0 + px as f32 + 0.5, y0 + py as f32 + 0.5);
            pixels.push(winding_number(poly, p) != 0);
        }
    }
    Raster {
        width,
        height,
        pixels,
    }
}

/// The nonzero winding number of `p` against `poly`: how many times the
/// polygon's boundary winds around the point (0 = outside, the Java2D fill
/// rule; self-intersecting polygons keep their signed crossings).
fn winding_number(poly: &[Point], p: Point) -> i32 {
    let mut w = 0i32;
    let mut j = poly.len() - 1;
    for i in 0..poly.len() {
        let (a, b) = (poly[j], poly[i]);
        j = i;
        if a.y <= p.y {
            if b.y > p.y && is_left(a, b, p) > 0.0 {
                w += 1;
            }
        } else if b.y <= p.y && is_left(a, b, p) < 0.0 {
            w -= 1;
        }
    }
    w
}

/// Is `p` inside `poly` by the NONZERO winding rule (the Java's
/// `pointInPolygon`, the Java2D fill rule the offset oracle needs to tell an
/// inset vertex's side). A point on the boundary's winding is ambiguous but
/// the offsets test against strictly-inside/outside, never on the edge.
pub fn point_in_polygon(poly: &[Point], p: Point) -> bool {
    winding_number(poly, p) != 0
}

/// The signed cross product `(b - a) x (p - a)`: > 0 = `p` left of the ray
/// from `a` to `b`.
fn is_left(a: Point, b: Point, p: Point) -> f32 {
    (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn filled(w: usize, h: usize) -> Raster {
        Raster::new(w, h, vec![true; w * h]).unwrap()
    }

    #[test]
    fn and_not_keeps_self_outside_the_other() {
        let mut a = filled(3, 3);
        let mut b = filled(3, 3);
        b.pixels[4] = false;
        a.and_not(&b).unwrap();
        // Only the pixel that is off in b survives.
        assert!(a.pixels[4]);
        assert_eq!(a.pixels.iter().filter(|&&p| p).count(), 1);
    }

    #[test]
    fn and_not_refuses_size_mismatch() {
        let mut a = filled(2, 2);
        let b = filled(3, 3);
        assert!(matches!(
            a.and_not(&b),
            Err(Error::RasterBlendSizeMismatch { .. })
        ));
    }

    #[test]
    fn overlay_or_unions_at_an_offset_clipped() {
        let mut a = filled(4, 4);
        // Clear everything, then union a 2x2 block at the center.
        a.pixels.fill(false);
        let b = filled(2, 2);
        a.overlay_or(&b, 1, 1);
        assert_eq!(
            a.pixels.iter().filter(|&&p| p).count(),
            4,
            "the 2x2 block lands fully inside"
        );
        assert!(a.get(1, 1) && a.get(2, 2));
        // The same block pushed off the top-left corner is clipped away.
        a.pixels.fill(false);
        a.overlay_or(&b, -1, -1);
        assert_eq!(a.pixels.iter().filter(|&&p| p).count(), 1);
        assert!(a.get(0, 0));
    }

    #[test]
    fn fill_polygon_covers_the_interior_only() {
        // A 10x10 square in a 14x14 mask at the origin: exactly the inner
        // 10x10 pixels are ON (the Java2D center-in-polygon rule).
        let poly = [
            Point::new(0.0, 0.0),
            Point::new(10.0, 0.0),
            Point::new(10.0, 10.0),
            Point::new(0.0, 10.0),
        ];
        let mask = fill_polygon(&poly, 0.0, 0.0, 14, 14);
        assert_eq!(mask.pixels.iter().filter(|&&p| p).count(), 100);
        assert!(mask.get(0, 0) && mask.get(9, 9));
        assert!(!mask.get(10, 10) && !mask.get(13, 0));
    }

    #[test]
    fn fill_polygon_respects_the_origin_shift() {
        // The same square moved to (100, 200): the mask pixels are the same,
        // only the design-space origin changed (element-local masks).
        let poly = [
            Point::new(100.0, 200.0),
            Point::new(110.0, 200.0),
            Point::new(110.0, 210.0),
            Point::new(100.0, 210.0),
        ];
        let mask = fill_polygon(&poly, 100.0, 200.0, 14, 14);
        assert_eq!(mask.pixels.iter().filter(|&&p| p).count(), 100);
    }

    #[test]
    fn fill_polygon_nonzero_winding_handles_holes() {
        // A square with a square hole: the hole's winding cancels, so the
        // hole pixels are OFF (the editor's cull cuts real holes, this is the
        // rule the traced contours of a culled mask rely on).
        let poly = [
            Point::new(0.0, 0.0),
            Point::new(20.0, 0.0),
            Point::new(20.0, 20.0),
            Point::new(0.0, 20.0),
            Point::new(0.0, 0.0), // back to the start to begin the hole
            Point::new(5.0, 5.0),
            Point::new(5.0, 15.0),
            Point::new(15.0, 15.0),
            Point::new(15.0, 5.0),
            Point::new(5.0, 5.0),
        ];
        let mask = fill_polygon(&poly, 0.0, 0.0, 22, 22);
        assert!(mask.get(1, 1), "the outer region is filled");
        assert!(!mask.get(10, 10), "the hole is cut");
        assert_eq!(
            mask.pixels.iter().filter(|&&p| p).count(),
            400 - 100,
            "20x20 minus the 10x10 hole"
        );
    }
}
