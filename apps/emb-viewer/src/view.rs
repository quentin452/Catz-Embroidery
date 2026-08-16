//! The design viewport: pan/zoom transform, viewport culling, and the two
//! render paths — the overview texture when the design is small on screen,
//! culled vectors when zoomed in (D001).

use eframe::egui::{self, Color32, Painter, Pos2, Rect, Sense, Stroke, Vec2};

use emb_draw::{Bounds, Command, DrawList};

/// A design-space → screen transform. `scale` is screen px per mm; `origin`
/// is the screen position of design point (0, 0).
#[derive(Debug, Clone, Copy)]
pub struct Viewport {
    pub scale: f32,
    pub origin: Vec2,
}

impl Viewport {
    /// Fit the design bounds into the panel with a small margin, centred.
    pub fn fit(bounds: Bounds, panel: Rect) -> Self {
        let w_mm = (bounds.max_x - bounds.min_x).max(0.001);
        let h_mm = (bounds.max_y - bounds.min_y).max(0.001);
        let scale = ((panel.width() - 40.0) / w_mm).min((panel.height() - 40.0) / h_mm);
        let origin = panel.center().to_vec2()
            - Vec2::new(
                (bounds.min_x + bounds.max_x) / 2.0 * scale,
                (bounds.min_y + bounds.max_y) / 2.0 * scale,
            );
        Self { scale, origin }
    }

    pub fn mm_to_screen(&self, x: f32, y: f32) -> Pos2 {
        Pos2::new(
            self.origin.x + x * self.scale,
            self.origin.y + y * self.scale,
        )
    }

    pub fn screen_to_mm(&self, p: Pos2) -> (f32, f32) {
        (
            (p.x - self.origin.x) / self.scale,
            (p.y - self.origin.y) / self.scale,
        )
    }

    /// The design-space bounds visible in the panel.
    pub fn visible_mm(&self, panel: Rect) -> Bounds {
        let (min_x, min_y) = self.screen_to_mm(panel.min);
        let (max_x, max_y) = self.screen_to_mm(panel.max);
        Bounds {
            min_x,
            min_y,
            max_x,
            max_y,
        }
    }

    pub fn pan(&mut self, delta: Vec2) {
        self.origin += delta;
    }

    /// Zoom by `factor` keeping the design point under `anchor` fixed.
    pub fn zoom(&mut self, factor: f32, anchor: Pos2) {
        let (mx, my) = self.screen_to_mm(anchor);
        self.scale = (self.scale * factor).clamp(0.001, 1_000_000.0);
        self.origin = anchor.to_vec2() - Vec2::new(mx * self.scale, my * self.scale);
    }
}

/// Handle input and draw one frame's worth of the design.
pub struct RenderView {
    pub viewport: Viewport,
}

impl RenderView {
    pub fn new() -> Self {
        Self {
            viewport: Viewport {
                scale: 1.0,
                origin: Vec2::ZERO,
            },
        }
    }

    pub fn ui(
        &mut self,
        ui: &mut egui::Ui,
        draw_list: &DrawList,
        overview: Option<&crate::overview::OverviewTexture>,
    ) {
        let (response, painter) = ui.allocate_painter(ui.available_size(), Sense::click_and_drag());

        // Pan with the primary button; zoom with the scroll wheel at the cursor.
        if response.dragged_by(egui::PointerButton::Primary) {
            self.viewport.pan(response.drag_delta());
        }
        let scroll = ui.ctx().input(|i| i.smooth_scroll_delta());
        if scroll.y != 0.0 {
            let anchor = ui
                .ctx()
                .input(|i| i.pointer.hover_pos())
                .unwrap_or(response.rect.center());
            let factor = (scroll.y * 0.002).exp();
            self.viewport.zoom(factor, anchor);
        }

        self.paint(&painter, response.rect, draw_list, overview);
    }

    fn paint(
        &self,
        painter: &Painter,
        panel: Rect,
        draw_list: &DrawList,
        overview: Option<&crate::overview::OverviewTexture>,
    ) {
        let identity = &draw_list.identity;
        let canvas = draw_list.bounds();
        let motif = draw_list.content_bounds();

        // Backdrop behind the canvas.
        painter.rect_filled(
            panel,
            0.0,
            Color32::from_rgb(
                identity.backdrop.r,
                identity.backdrop.g,
                identity.backdrop.b,
            ),
        );

        let canvas_rect = Rect::from_min_max(
            self.viewport.mm_to_screen(canvas.min_x, canvas.min_y),
            self.viewport.mm_to_screen(canvas.max_x, canvas.max_y),
        );
        let motif_rect = Rect::from_min_max(
            self.viewport.mm_to_screen(motif.min_x, motif.min_y),
            self.viewport.mm_to_screen(motif.max_x, motif.max_y),
        );

        painter.rect_filled(
            canvas_rect,
            0.0,
            Color32::from_rgb(identity.canvas.r, identity.canvas.g, identity.canvas.b),
        );

        // Overview LOD: when the MOTIF is small on screen, the cached texture
        // (which covers the motif, not the canvas) has all the detail we need
        // and vector work drops to zero.
        if let Some(o) = overview
            && motif_rect.width() < o.size[0] as f32
            && motif_rect.height() < o.size[1] as f32
        {
            painter.image(
                o.handle.id(),
                motif_rect,
                Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
                Color32::WHITE,
            );
            return;
        }

        let visible = self.viewport.visible_mm(panel);
        let width_px = (identity.stitch_width_mm * self.viewport.scale).max(1.0);

        for item in &draw_list.items {
            if !item.bounds.intersects(visible) {
                continue;
            }
            if let Command::Polyline { points, color, .. } = &item.command {
                let stroke = Stroke::new(width_px, Color32::from_rgb(color.r, color.g, color.b));
                for w in points.windows(2) {
                    let a = self.viewport.mm_to_screen(w[0].x, w[0].y);
                    let b = self.viewport.mm_to_screen(w[1].x, w[1].y);
                    painter.line_segment([a, b], stroke);
                }
            }
        }
    }
}
