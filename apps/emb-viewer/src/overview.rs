//! The overview texture cache (D001): the stitched motif rasterised once,
//! drawn scaled instead of vector-drawn when the motif is small on screen.

use eframe::egui::{self, Color32, ColorImage, TextureHandle, TextureOptions};

use emb_draw::{Bounds, Command, DrawList};
use emb_model::geom::Point;

/// Longest side of the cached texture, in texels. The motif is scaled to fit.
const MAX_TEXELS: usize = 2048;

pub struct OverviewTexture {
    pub handle: TextureHandle,
    /// Texel size of the cached image.
    pub size: [usize; 2],
}

impl OverviewTexture {
    /// Rasterise the stitched motif once. Called on load only — the design is
    /// static between edits (D001), so the per-frame cost stays near zero.
    ///
    /// The texture covers the MOTIF (`content_bounds`), not the canvas: a
    /// design whose stitches sit far from its declared canvas (offsets, odd
    /// hoops) must still be rasterised in full. The canvas is painted by the
    /// viewport as a background rect, not baked into the texture.
    pub fn new(ctx: &egui::Context, draw_list: &DrawList) -> Self {
        let bounds = draw_list.content_bounds();
        let (w_mm, h_mm) = (bounds.max_x - bounds.min_x, bounds.max_y - bounds.min_y);
        let scale = (MAX_TEXELS as f32 / w_mm.max(h_mm).max(1.0)).max(1.0);
        let size = [
            ((w_mm * scale).round() as usize).max(1),
            ((h_mm * scale).round() as usize).max(1),
        ];
        let mut image = ColorImage::filled(size, Color32::TRANSPARENT);
        for item in &draw_list.items {
            if let Command::Polyline { points, color, .. } = &item.command {
                let c = color32(color.r, color.g, color.b);
                for w in points.windows(2) {
                    stroke_line(
                        &mut image.pixels,
                        size[0],
                        size[1],
                        translate(w[0], &bounds),
                        translate(w[1], &bounds),
                        scale,
                        c,
                    );
                }
            }
        }
        let handle = ctx.load_texture("overview", image, TextureOptions::LINEAR);
        Self { handle, size }
    }
}

/// The motif's origin may be far from (0, 0) — offset points so the texture
/// pixel grid starts at the motif's own corner.
fn translate(p: Point, bounds: &Bounds) -> Point {
    Point::new(p.x - bounds.min_x, p.y - bounds.min_y)
}

/// Stamp a thick line between two design-space points into the pixel buffer.
fn stroke_line(
    pixels: &mut [Color32],
    w: usize,
    h: usize,
    a: Point,
    b: Point,
    scale: f32,
    color: Color32,
) {
    let (px0, py0) = (a.x * scale, a.y * scale);
    let (px1, py1) = (b.x * scale, b.y * scale);
    let len = ((px1 - px0).powi(2) + (py1 - py0).powi(2)).sqrt();
    if len == 0.0 {
        return;
    }
    let (dx, dy) = ((px1 - px0) / len, (py1 - py0) / len);
    let steps = len.ceil() as u32;
    for i in 0..=steps {
        let t = i as f32 / steps as f32;
        let (x, y) = (px0 + dx * len * t, py0 + dy * len * t);
        set_pixel(pixels, w, h, x.round() as i32, y.round() as i32, color);
    }
}

fn set_pixel(pixels: &mut [Color32], w: usize, h: usize, x: i32, y: i32, color: Color32) {
    if x < 0 || y < 0 || x as usize >= w || y as usize >= h {
        return;
    }
    pixels[y as usize * w + x as usize] = color;
}

fn color32(r: u8, g: u8, b: u8) -> Color32 {
    Color32::from_rgb(r, g, b)
}
