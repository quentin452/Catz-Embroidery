//! The layer row's thumbnail, ported from the Java's `rasterizeLayer` +
//! `image(lay.render, 4, oy+4, 42, 42)` (Main.java:200-231, 475-476): each
//! layer's elements drawn white on black, stretched into a 42×42 preview.
//!
//! Deviations, each below the thumbnail's level: LIN lines are drawn 1 px
//! (the Java strokes them at the element's weight, which is sub-pixel at
//! 42×42); TXT is a box at its anchor (the font rasteriser is a named
//! exception — ROADMAP — so the Java's white text cannot be drawn). PLY is
//! scanline-filled like the Java's white fill.

use eframe::egui::{ColorImage, TextureHandle, TextureOptions};
use emb_model::geom::Point;

/// The Java's thumbnail size (Main.java:476: `image(..., 42, 42)`).
pub const THUMB: usize = 42;

/// Rasterize one layer's elements into the thumbnail texture. The caller
/// owns the texture (a `TextureHandle` per layer, rebuilt when the document
/// changes — the Java re-rasterizes every frame, ours is cached).
pub fn layer_thumbnail_texture(
    ctx: &eframe::egui::Context,
    layer: &crate::doc::Layer,
) -> TextureHandle {
    let (min_x, min_y, max_x, max_y) = layer_bounds(layer);
    let (w, h) = ((max_x - min_x).max(1e-6), (max_y - min_y).max(1e-6));
    let sx = (THUMB as f32 - 2.0) / w;
    let sy = (THUMB as f32 - 2.0) / h;
    let to_px = |x: f32, y: f32| {
        (
            ((x - min_x) * sx + 1.0).round() as i32,
            ((y - min_y) * sy + 1.0).round() as i32,
        )
    };

    let mut px = vec![0u8; THUMB * THUMB];
    for element in &layer.elements {
        match element.kind {
            crate::doc::ElementKind::Text => {
                // The font rasteriser is deferred; a box at the anchor is the
                // raw-draft stand-in (the Java draws the white text).
                let at = element.data[0];
                let (x0, y0) = to_px(at.x, at.y);
                let (x1, y1) = to_px(at.x + element.param_f0 * 0.6, at.y + element.param_f0 * 0.8);
                fill_rect(&mut px, x0.min(x1), y0.min(y1), x0.max(x1), y0.max(y1));
            }
            crate::doc::ElementKind::Line => {
                for w in element.data.windows(2) {
                    draw_line(&mut px, to_px(w[0].x, w[0].y), to_px(w[1].x, w[1].y));
                }
            }
            crate::doc::ElementKind::Polygon => {
                if element.data.len() >= 3 {
                    fill_polygon(&mut px, &element.data, &to_px);
                }
            }
        }
    }

    let mut rgba = Vec::with_capacity(THUMB * THUMB * 4);
    for on in px {
        rgba.extend_from_slice(&if on > 0 {
            [255, 255, 255, 255]
        } else {
            [0, 0, 0, 255]
        });
    }
    ctx.load_texture(
        "layer-thumb",
        ColorImage::from_rgba_unmultiplied([THUMB, THUMB], &rgba),
        TextureOptions::NEAREST,
    )
}

fn layer_bounds(layer: &crate::doc::Layer) -> (f32, f32, f32, f32) {
    let mut min_x = f32::INFINITY;
    let mut min_y = f32::INFINITY;
    let mut max_x = f32::NEG_INFINITY;
    let mut max_y = f32::NEG_INFINITY;
    for element in &layer.elements {
        for p in &element.data {
            min_x = min_x.min(p.x);
            min_y = min_y.min(p.y);
            max_x = max_x.max(p.x);
            max_y = max_y.max(p.y);
        }
        if element.kind == crate::doc::ElementKind::Text {
            max_x = max_x.max(element.data[0].x + element.param_f0 * 0.6);
            max_y = max_y.max(element.data[0].y + element.param_f0 * 0.8);
        }
    }
    if !min_x.is_finite() {
        (0.0, 0.0, 1.0, 1.0)
    } else {
        (min_x, min_y, max_x, max_y)
    }
}

fn plot(px: &mut [u8], x: i32, y: i32) {
    if x >= 0 && y >= 0 && x < THUMB as i32 && y < THUMB as i32 {
        px[y as usize * THUMB + x as usize] = 255;
    }
}

fn draw_line(px: &mut [u8], (x0, y0): (i32, i32), (x1, y1): (i32, i32)) {
    let dx = (x1 - x0) as f32;
    let dy = (y1 - y0) as f32;
    let n = ((dx * dx + dy * dy).sqrt() / 0.7).ceil() as usize;
    for k in 0..=n {
        let t = k as f32 / n.max(1) as f32;
        plot(
            px,
            x0 + (dx * t).round() as i32,
            y0 + (dy * t).round() as i32,
        );
    }
}

fn fill_rect(px: &mut [u8], x0: i32, y0: i32, x1: i32, y1: i32) {
    for y in y0..=y1 {
        for x in x0..=x1 {
            plot(px, x, y);
        }
    }
}

/// Even-odd scanline fill of the polygon outline (the Java's white `fill`
/// on the PLY shape).
fn fill_polygon(px: &mut [u8], points: &[Point], to_px: &impl Fn(f32, f32) -> (i32, i32)) {
    for y in 0..THUMB {
        let yc = y as f32 + 0.5;
        let mut crossings: Vec<f32> = Vec::new();
        // The polygon is stored OPEN (elements keep their points as drawn);
        // the closing edge (last, first) must be added like the stitch path
        // closes PLY elements.
        for k in 0..points.len() {
            let (a, b) = (points[k], points[(k + 1) % points.len()]);
            let ya = to_px(0.0, a.y).1 as f32;
            let yb = to_px(0.0, b.y).1 as f32;
            if (ya <= yc) != (yb <= yc) {
                let t = (yc - ya) / (yb - ya);
                let xa = to_px(a.x, 0.0).0 as f32;
                let xb = to_px(b.x, 0.0).0 as f32;
                crossings.push(xa + (xb - xa) * t);
            }
        }
        crossings.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        // A pixel is filled when its centre (x + 0.5) lies strictly inside
        // the crossed interval (a, b).
        for pair in crossings.chunks(2) {
            if pair.len() == 2 {
                let x0 = (pair[0] - 0.5).ceil() as i32;
                let x1 = (pair[1] - 0.5).floor() as i32;
                for x in x0..=x1 {
                    plot(px, x, y as i32);
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::doc::{Element, Layer};
    use emb_model::geom::Point;

    #[test]
    fn polygon_fills_its_interior() {
        let mut layer = Layer::new();
        layer.elements.push(Element::polygon(vec![
            Point::new(0.0, 0.0),
            Point::new(10.0, 0.0),
            Point::new(10.0, 10.0),
            Point::new(0.0, 10.0),
        ]));
        let (min_x, min_y, max_x, max_y) = layer_bounds(&layer);
        assert_eq!((min_x, min_y, max_x, max_y), (0.0, 0.0, 10.0, 10.0));
        let sx = (THUMB as f32 - 2.0) / 10.0;
        let sy = (THUMB as f32 - 2.0) / 10.0;
        let to_px = |x: f32, y: f32| {
            (
                ((x - min_x) * sx + 1.0).round() as i32,
                ((y - min_y) * sy + 1.0).round() as i32,
            )
        };
        let mut px = vec![0u8; THUMB * THUMB];
        fill_polygon(&mut px, &layer.elements[0].data, &to_px);
        // The square's centre (5, 5) fills; a corner outside the square does
        // not.
        let (cx, cy) = to_px(5.0, 5.0);
        assert_eq!(px[cy as usize * THUMB + cx as usize], 255, "centre filled");
        assert_eq!(px[0], 0, "outside stays black");
        assert_eq!(px[THUMB - 1], 0, "far corner stays black");
    }

    #[test]
    fn line_draws_along_its_segment() {
        let mut px = vec![0u8; THUMB * THUMB];
        draw_line(&mut px, (1, 1), (30, 1));
        assert_eq!(px[THUMB + 15], 255, "midpoint on the line");
        assert_eq!(px[3 * THUMB + 15], 0, "off the line stays black");
    }
}
