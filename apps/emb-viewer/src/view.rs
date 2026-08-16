//! The design viewport: pan/zoom transform, viewport culling, and the two
//! render paths — the overview texture when the design is small on screen,
//! culled vectors when zoomed in (D001).
//!
//! The transform math lives in `emb_draw::Viewport` (shared with the editor);
//! this file is the egui glue: input handling and the paint calls.

use eframe::egui::{self, Color32, Painter, Rect, Sense, Stroke};

use emb_draw::{Command, DrawList, Viewport};

/// Handle input and draw one frame's worth of the design.
pub struct RenderView {
    pub viewport: Viewport,
}

impl RenderView {
    pub fn new() -> Self {
        Self {
            viewport: Viewport::new(1.0, 0.0, 0.0),
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
            let delta = response.drag_delta();
            self.viewport.pan(delta.x, delta.y);
        }
        let scroll = ui.ctx().input(|i| i.smooth_scroll_delta());
        if scroll.y != 0.0 {
            let anchor = ui
                .ctx()
                .input(|i| i.pointer.hover_pos())
                .unwrap_or(response.rect.center());
            let factor = (scroll.y * 0.002).exp();
            self.viewport.zoom(factor, anchor.x, anchor.y);
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
            pos2(self.viewport.mm_to_screen(canvas.min_x, canvas.min_y)),
            pos2(self.viewport.mm_to_screen(canvas.max_x, canvas.max_y)),
        );
        let motif_rect = Rect::from_min_max(
            pos2(self.viewport.mm_to_screen(motif.min_x, motif.min_y)),
            pos2(self.viewport.mm_to_screen(motif.max_x, motif.max_y)),
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
                Rect::from_min_max(egui::Pos2::ZERO, egui::Pos2::new(1.0, 1.0)),
                Color32::WHITE,
            );
            return;
        }

        let visible =
            self.viewport
                .visible_mm((panel.min.x, panel.min.y, panel.max.x, panel.max.y));
        let width_px = (identity.stitch_width_mm * self.viewport.scale).max(1.0);

        for item in &draw_list.items {
            if !item.bounds.intersects(visible) {
                continue;
            }
            if let Command::Polyline { points, color, .. } = &item.command {
                let stroke = Stroke::new(width_px, Color32::from_rgb(color.r, color.g, color.b));
                for w in points.windows(2) {
                    let a = pos2(self.viewport.mm_to_screen(w[0].x, w[0].y));
                    let b = pos2(self.viewport.mm_to_screen(w[1].x, w[1].y));
                    painter.line_segment([a, b], stroke);
                }
            }
        }
    }
}

/// `(f32, f32) -> Pos2` for the viewport's tuple results.
fn pos2(p: (f32, f32)) -> egui::Pos2 {
    egui::Pos2::new(p.0, p.1)
}
