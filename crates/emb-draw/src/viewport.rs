//! The design-space → screen transform (pan/zoom/fit), pure f32 math with no
//! UI types. The contract every app's viewport shares: the viewer and the
//! editor use the same transform so their pan/zoom behaviour cannot drift.

use crate::list::Bounds;

/// A design-space → screen transform. `scale` is screen px per mm; `origin` is
/// the screen position of design point (0, 0).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Viewport {
    pub scale: f32,
    pub origin_x: f32,
    pub origin_y: f32,
}

/// A screen rect as `(min_x, min_y, max_x, max_y)`, the panel an app hands to
/// the viewport (egui `Rect` fields, passed through — the viewport itself has
/// no UI dependency).
pub type ScreenRect = (f32, f32, f32, f32);

impl Viewport {
    pub const fn new(scale: f32, origin_x: f32, origin_y: f32) -> Self {
        Self {
            scale,
            origin_x,
            origin_y,
        }
    }

    /// Fit the design bounds into the panel with a 20 px margin, centred.
    pub fn fit(bounds: Bounds, panel: ScreenRect) -> Self {
        let w_mm = (bounds.max_x - bounds.min_x).max(0.001);
        let h_mm = (bounds.max_y - bounds.min_y).max(0.001);
        let (min_x, min_y, max_x, max_y) = panel;
        let w = max_x - min_x;
        let h = max_y - min_y;
        let scale = ((w - 40.0) / w_mm).min((h - 40.0) / h_mm);
        let origin_x = (min_x + max_x) / 2.0 - (bounds.min_x + bounds.max_x) / 2.0 * scale;
        let origin_y = (min_y + max_y) / 2.0 - (bounds.min_y + bounds.max_y) / 2.0 * scale;
        Self {
            scale,
            origin_x,
            origin_y,
        }
    }

    pub fn mm_to_screen(&self, x: f32, y: f32) -> (f32, f32) {
        (
            self.origin_x + x * self.scale,
            self.origin_y + y * self.scale,
        )
    }

    pub fn screen_to_mm(&self, x: f32, y: f32) -> (f32, f32) {
        (
            (x - self.origin_x) / self.scale,
            (y - self.origin_y) / self.scale,
        )
    }

    /// The design-space bounds visible in the panel.
    pub fn visible_mm(&self, panel: ScreenRect) -> Bounds {
        let (min_x, min_y) = self.screen_to_mm(panel.0, panel.1);
        let (max_x, max_y) = self.screen_to_mm(panel.2, panel.3);
        Bounds {
            min_x,
            min_y,
            max_x,
            max_y,
        }
    }

    pub fn pan(&mut self, dx: f32, dy: f32) {
        self.origin_x += dx;
        self.origin_y += dy;
    }

    /// Zoom by `factor` keeping the design point under the anchor fixed.
    pub fn zoom(&mut self, factor: f32, anchor_x: f32, anchor_y: f32) {
        let (mx, my) = self.screen_to_mm(anchor_x, anchor_y);
        self.scale = (self.scale * factor).clamp(0.001, 1_000_000.0);
        self.origin_x = anchor_x - mx * self.scale;
        self.origin_y = anchor_y - my * self.scale;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn bounds() -> Bounds {
        Bounds {
            min_x: 0.0,
            min_y: 0.0,
            max_x: 100.0,
            max_y: 80.0,
        }
    }

    #[test]
    fn fit_centres_the_bounds_in_the_panel() {
        let v = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0));
        // 100 mm wide in 460 px of room (500 - 40 margin) -> 4.6 px/mm.
        assert!((v.scale - 4.6).abs() < 1e-6);
        let (sx, sy) = v.mm_to_screen(50.0, 40.0);
        assert!((sx - 250.0).abs() < 1e-3);
        assert!((sy - 250.0).abs() < 1e-3);
    }

    #[test]
    fn fit_scales_to_the_limiting_axis() {
        // The 100 mm wide side limits before the 80 mm tall side.
        let v = Viewport::fit(bounds(), (0.0, 0.0, 100.0, 500.0));
        assert!((v.scale - 0.6).abs() < 1e-6);
    }

    #[test]
    fn round_trip_screen_mm() {
        let v = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0));
        let (sx, sy) = v.mm_to_screen(12.5, -3.0);
        let (mx, my) = v.screen_to_mm(sx, sy);
        assert!((mx - 12.5).abs() < 1e-4);
        assert!((my - -3.0).abs() < 1e-4);
    }

    #[test]
    fn visible_mm_inverts_the_panel() {
        let v = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0));
        let vis = v.visible_mm((0.0, 0.0, 500.0, 500.0));
        assert!(vis.min_x <= 0.0 && vis.max_x >= 100.0);
    }

    #[test]
    fn zoom_keeps_the_anchor_point_fixed() {
        let mut v = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0));
        let (ax, ay) = v.mm_to_screen(30.0, 20.0);
        v.zoom(2.0, ax, ay);
        let (mx, my) = v.screen_to_mm(ax, ay);
        assert!((mx - 30.0).abs() < 1e-3);
        assert!((my - 20.0).abs() < 1e-3);
    }

    #[test]
    fn pan_moves_everything() {
        let mut v = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0));
        v.pan(10.0, -5.0);
        let (sx, _) = v.mm_to_screen(0.0, 0.0);
        let (ox, _) = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0)).mm_to_screen(0.0, 0.0);
        assert!((sx - ox - 10.0).abs() < 1e-3);
    }

    #[test]
    fn zoom_is_clamped() {
        let mut v = Viewport::fit(bounds(), (0.0, 0.0, 500.0, 500.0));
        v.zoom(1e12, 0.0, 0.0);
        assert_eq!(v.scale, 1_000_000.0);
        v.zoom(1e-12, 0.0, 0.0);
        assert_eq!(v.scale, 0.001);
    }
}
