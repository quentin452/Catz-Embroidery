#![forbid(unsafe_code)]
//! The shared egui renderer of the draw list: the apps' ONE paint path for
//! the stitched preview (docs/decisions/D001.md — "renderers cannot drift").
//!
//! All visible segments of a frame are batched into a single [`egui::Mesh`]
//! (one draw call), instead of one `painter.line_segment` tessellation per
//! segment — the per-segment path froze in proportion to the stitches on
//! screen (measured on a 1600-polyline design). Each segment becomes a
//! quad with square caps (a stitch is square-ish), per-vertex coloured.

pub mod exit_dialog;

use egui::{Color32, Painter, Pos2, Rect, Shape, epaint};
use emb_draw::{Command, DrawList, Viewport};
use emb_model::geom::Point;

fn screen(p: Point, viewport: &Viewport) -> Pos2 {
    let (x, y) = viewport.mm_to_screen(p.x, p.y);
    Pos2::new(x, y)
}

/// One segment as a square-capped quad: two perpendicular offsets at
/// half-width, endpoints extended by half-width along the segment. Degenerate
/// (zero-length) segments are skipped.
fn push_segment(mesh: &mut egui::Mesh, a: Pos2, b: Pos2, half: f32, color: Color32) {
    let d = b - a;
    let len = d.length();
    if !len.is_finite() || len <= f32::EPSILON {
        return;
    }
    let unit = d / len;
    let n = egui::vec2(-unit.y, unit.x) * half;
    let a0 = a - unit * half;
    let b0 = b + unit * half;
    let base = mesh.vertices.len() as u32;
    for p in [a0 + n, a0 - n, b0 - n, b0 + n] {
        mesh.vertices.push(epaint::Vertex::untextured(p, color));
    }
    mesh.indices
        .extend_from_slice(&[base, base + 1, base + 2, base, base + 2, base + 3]);
}

/// Build the frame's mesh: every polyline item whose bounds intersect the
/// visible region, every segment as a quad. Pure (no painter) so the culling
/// and the geometry are testable headlessly.
pub fn build_draw_list_mesh(viewport: &Viewport, draw_list: &DrawList, panel: Rect) -> egui::Mesh {
    let visible = viewport.visible_mm((panel.min.x, panel.min.y, panel.max.x, panel.max.y));
    let width_px = (draw_list.identity.stitch_width_mm * viewport.scale).max(1.0);
    let half = width_px * 0.5;
    let mut mesh = egui::Mesh::default();
    for item in &draw_list.items {
        if !item.bounds.intersects(visible) {
            continue;
        }
        if let Command::Polyline { points, color, .. } = &item.command {
            let c = Color32::from_rgb(color.r, color.g, color.b);
            for w in points.windows(2) {
                push_segment(
                    &mut mesh,
                    screen(w[0], viewport),
                    screen(w[1], viewport),
                    half,
                    c,
                );
            }
        }
    }
    mesh
}

/// The apps' one paint call for a stitched preview: cull by the visible
/// region, batch every visible segment into one mesh, add it as one shape.
pub fn paint_draw_list(painter: &Painter, viewport: &Viewport, draw_list: &DrawList, panel: Rect) {
    let mesh = build_draw_list_mesh(viewport, draw_list, panel);
    if !mesh.is_empty() {
        painter.add(Shape::mesh(mesh));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use emb_draw::{Bounds, DrawItem};

    fn viewport() -> Viewport {
        // 2 px per mm, origin at (100, 200).
        Viewport::new(2.0, 100.0, 200.0)
    }

    fn draw_list_with(points: Vec<Point>, color: [u8; 3]) -> DrawList {
        DrawList {
            identity: emb_draw::VisualIdentity::default(),
            items: vec![DrawItem {
                bounds: Bounds::of_points(&points),
                command: Command::Polyline {
                    points,
                    color: emb_draw::Rgb::new(color[0], color[1], color[2]),
                    bounds: Bounds {
                        min_x: 0.0,
                        min_y: 0.0,
                        max_x: 1.0,
                        max_y: 1.0,
                    },
                },
            }],
        }
    }

    #[test]
    fn a_segment_is_four_vertices_and_two_triangles() {
        let dl = draw_list_with(
            vec![Point::new(0.0, 0.0), Point::new(10.0, 0.0)],
            [255, 0, 0],
        );
        let mesh = build_draw_list_mesh(
            &viewport(),
            &dl,
            Rect::from_min_max(Pos2::ZERO, Pos2::new(1000.0, 1000.0)),
        );
        assert_eq!(mesh.vertices.len(), 4);
        assert_eq!(mesh.indices.len(), 6);
        // A horizontal segment at y=200, width 1 px: the quad spans y 199.5..200.5,
        // x from 100-0.5 (cap) to 120+0.5 (cap).
        let xs: Vec<f32> = mesh.vertices.iter().map(|v| v.pos.x).collect();
        let ys: Vec<f32> = mesh.vertices.iter().map(|v| v.pos.y).collect();
        assert!(
            xs.iter()
                .all(|&x| (x - 99.5).abs() < 0.01 || (x - 120.5).abs() < 0.01)
        );
        assert!(
            ys.iter()
                .all(|&y| (y - 199.5).abs() < 0.01 || (y - 200.5).abs() < 0.01)
        );
        assert!(
            mesh.vertices
                .iter()
                .all(|v| v.color == Color32::from_rgb(255, 0, 0))
        );
    }

    #[test]
    fn off_view_polylines_are_culled() {
        // The polyline sits at x 5000..6000, far outside the panel's view.
        let dl = draw_list_with(
            vec![Point::new(5000.0, 0.0), Point::new(6000.0, 0.0)],
            [0, 255, 0],
        );
        let panel = Rect::from_min_max(Pos2::new(0.0, 0.0), Pos2::new(800.0, 600.0));
        let mesh = build_draw_list_mesh(&viewport(), &dl, panel);
        assert!(mesh.is_empty());
    }

    #[test]
    fn degenerate_segments_are_skipped() {
        let dl = draw_list_with(
            vec![
                Point::new(5.0, 5.0),
                Point::new(5.0, 5.0),
                Point::new(5.0, 15.0),
            ],
            [0, 0, 255],
        );
        let panel = Rect::from_min_max(Pos2::ZERO, Pos2::new(1000.0, 1000.0));
        let mesh = build_draw_list_mesh(&viewport(), &dl, panel);
        // The zero-length first segment contributes nothing; the second is
        // one quad.
        assert_eq!(mesh.vertices.len(), 4);
    }

    #[test]
    fn segments_batch_into_one_mesh() {
        let dl = draw_list_with(
            vec![
                Point::new(0.0, 0.0),
                Point::new(10.0, 0.0),
                Point::new(10.0, 10.0),
                Point::new(20.0, 10.0),
            ],
            [1, 2, 3],
        );
        let panel = Rect::from_min_max(Pos2::ZERO, Pos2::new(1000.0, 1000.0));
        let mesh = build_draw_list_mesh(&viewport(), &dl, panel);
        // Three segments, one mesh: 12 vertices, 18 indices.
        assert_eq!(mesh.vertices.len(), 12);
        assert_eq!(mesh.indices.len(), 18);
    }
}
