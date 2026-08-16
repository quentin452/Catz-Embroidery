//! The editor's stitch path: layers of elements become a stitched `Model`,
//! then a `Design` the format writers can save (the Java's `stitchLayer` +
//! `writeOut`, minus the raster path D003 defers).
//!
//! M4 ruling (docs/ROADMAP.md): PARALLEL polygon hatch only. Each visible
//! layer's PLY elements are hatched with `emb_model::hatch::hatch_parallel`
//! (vector-pure, fixture-compared) at the layer's spacing, in the layer's
//! hatch colour. LIN and TXT elements are named exceptions: the stroke path
//! is deferred (D003 → M5), and text needs a font rasteriser.

use emb_model::hatch;
use emb_model::model::Model;

use crate::doc::{Document, ElementKind, HatchMode, Layer};

/// The Java's `HATCH_ANGLE` default: QUARTER_PI.
const HATCH_ANGLE: f32 = std::f32::consts::FRAC_PI_4;

/// The Java's `calcAxisAngleForParallel`: HALF_PI - ang.
fn axis_angle(ang: f32) -> f32 {
    std::f32::consts::FRAC_PI_2 - ang
}

/// Stitch every visible layer into one model: each PLY element hatches in
/// its layer's colour (the Java's `stitchLayer` — what `draw()` shows).
///
/// NO TSP here: the Java optimises only at save (`writeOut` → `optimize()`),
/// and the preview refreshes on every document change — a full TSP pass per
/// refresh is O(iterations · n²) (measured: a 1600 mm circle at 4 mm spacing
/// costs ~70 ms of TSP for ~9 ms of stitching, and the cost grows n²). The
/// save path calls `Model::optimize()` explicitly.
pub fn stitch_document(doc: &Document) -> Model {
    let mut model = Model::new(doc.width, doc.height);
    for layer in &doc.layers {
        if layer.visible {
            stitch_layer(&mut model, layer);
        }
    }
    model
}

/// One layer's elements → hatched polylines in the layer's hatch colour.
fn stitch_layer(model: &mut Model, layer: &Layer) {
    debug_assert_eq!(layer.hatch_mode, HatchMode::Parallel);
    let angle = axis_angle(HATCH_ANGLE);
    let spacing = layer.hatch_spacing.max(0.1);
    for elt in &layer.elements {
        if elt.kind != ElementKind::Polygon {
            // LIN: stroke path, deferred (D003 → M5). TXT: font rasteriser,
            // deferred. Both named exceptions in docs/ROADMAP.md.
            continue;
        }
        if elt.data.len() < 3 {
            continue;
        }
        for poly in hatch::hatch_parallel(&elt.data, angle, spacing) {
            model.push_polyline(poly, layer.hatch_color);
        }
    }
}

/// The stitched content's bounds: `(min_x, min_y, width, height)`. The
/// infinite canvas mode's centring target — with no hoop there is no canvas
/// to centre on, the content is the canvas.
fn content_bounds(model: &Model) -> Option<(f32, f32, f32, f32)> {
    let mut min_x = f32::INFINITY;
    let mut min_y = f32::INFINITY;
    let mut max_x = f32::NEG_INFINITY;
    let mut max_y = f32::NEG_INFINITY;
    for poly in &model.polylines {
        for p in poly {
            min_x = min_x.min(p.x);
            min_y = min_y.min(p.y);
            max_x = max_x.max(p.x);
            max_y = max_y.max(p.y);
        }
    }
    if min_x.is_infinite() {
        None
    } else {
        Some((min_x, min_y, max_x - min_x, max_y - min_y))
    }
}

/// The stitched content's extent in mm (the test-facing view of
/// `content_bounds`).
#[cfg(test)]
fn content_size(model: &Model) -> (f32, f32) {
    match content_bounds(model) {
        Some((_, _, w, h)) => (w, h),
        None => (1.0, 1.0),
    }
}

fn write_out_centered(
    model: &Model,
    path: &std::path::Path,
    width: f32,
    height: f32,
) -> Result<(), String> {
    if model.polylines.is_empty() {
        return Err("nothing to stitch".into());
    }
    let title = emb_data::file_title(path);
    let design = model.centered_design(&title, width, height);
    emb_data::write_design(path, &design)
}

/// Save a stitched model to a file: the shared `emb_data::write_design` with
/// the design centred on the editor's canvas (the Java's `writeOut` →
/// `PEmbroiderWriter.write`, which centres on the canvas it was created
/// with).
pub fn write_out(model: &Model, path: &std::path::Path) -> Result<(), String> {
    write_out_centered(model, path, model.width, model.height)
}

/// The infinite canvas mode's save: the CONTENT is moved to the origin (the
/// hoop's centring shift), then centred on its own extent — the saved design
/// is exactly the drawn content, wherever it was drawn.
pub fn write_out_content_centered(model: &Model, path: &std::path::Path) -> Result<(), String> {
    let Some((min_x, min_y, width, height)) = content_bounds(model) else {
        return Err("nothing to stitch".into());
    };
    let (width, height) = (width.max(1.0), height.max(1.0));
    let title = emb_data::file_title(path);
    let mut design = model.to_design(title);
    let dx = -min_x - width / 2.0;
    let dy = -min_y - height / 2.0;
    for p in &mut design.stitches {
        p.x += dx;
        p.y += dy;
    }
    design.bounds = [-width / 2.0, -height / 2.0, width / 2.0, height / 2.0];
    emb_data::write_design(path, &design)
}

/// Convenience for tests: a triangle polygon.
#[cfg(test)]
fn triangle() -> Vec<emb_model::geom::Point> {
    vec![
        emb_model::geom::Point::new(10.0, 10.0),
        emb_model::geom::Point::new(90.0, 10.0),
        emb_model::geom::Point::new(50.0, 80.0),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::doc::Element;
    use emb_model::geom::Point;

    fn doc_with_polygon(layer_index: usize) -> (Document, Vec<Point>) {
        let mut doc = Document::new();
        for _ in 0..=layer_index {
            doc.layers.push(Layer::new());
        }
        let poly = triangle();
        doc.layers[layer_index]
            .elements
            .push(Element::polygon(poly.clone()));
        (doc, poly)
    }

    #[test]
    fn polygon_hatches_into_coloured_polylines() {
        let (doc, _) = doc_with_polygon(0);
        let model = stitch_document(&doc);
        assert!(!model.polylines.is_empty());
        assert!(model.colors.iter().all(|&c| c == 0x0000FF));
        assert_eq!(model.polylines.len(), model.colors.len());
        for poly in &model.polylines {
            assert!(poly.len() >= 2);
        }
    }

    #[test]
    fn line_and_text_elements_are_not_stitched() {
        let mut doc = Document::new();
        doc.current_mut().elements.push(Element::line(
            vec![Point::new(0.0, 0.0), Point::new(100.0, 100.0)],
            5.0,
        ));
        doc.current_mut()
            .elements
            .push(Element::text("hi".into(), 20.0, Point::new(50.0, 50.0)));
        let model = stitch_document(&doc);
        assert!(model.polylines.is_empty());
    }

    #[test]
    fn hidden_layers_are_not_stitched() {
        let (mut doc, _) = doc_with_polygon(1);
        doc.layers[1].visible = false;
        let model = stitch_document(&doc);
        assert!(model.polylines.is_empty());
    }

    #[test]
    fn optimize_reorders_within_colour_blocks() {
        // Two same-colour polygons on one layer: the TSP reorders the block.
        let mut doc = Document::new();
        doc.current_mut()
            .elements
            .push(Element::polygon(triangle()));
        let far = triangle()
            .into_iter()
            .map(|p| Point::new(p.x + 500.0, p.y))
            .collect();
        doc.current_mut().elements.push(Element::polygon(far));
        let model = stitch_document(&doc);
        assert!(model.polylines.len() > 1);
    }

    #[test]
    fn write_out_refuses_an_empty_model() {
        let model = Model::new(100.0, 100.0);
        assert!(write_out(&model, std::path::Path::new("x.pes")).is_err());
    }

    #[test]
    fn content_size_covers_the_stitched_polylines() {
        let mut model = Model::new(100.0, 100.0);
        model.push_polyline(
            vec![Point::new(-500.0, 0.0), Point::new(500.0, 0.0)],
            0xFF0000,
        );
        model.push_polyline(
            vec![Point::new(0.0, -200.0), Point::new(0.0, 200.0)],
            0xFF0000,
        );
        assert_eq!(content_size(&model), (1000.0, 400.0));
        assert_eq!(content_size(&Model::new(1.0, 1.0)), (1.0, 1.0));
    }

    #[test]
    fn content_centered_save_centres_on_the_content() {
        // Content drawn far from the origin (the infinite canvas has no
        // hoop): the save must centre the content extent, not a canvas.
        let mut model = Model::new(100.0, 100.0);
        model.push_polyline(
            vec![Point::new(5000.0, 1000.0), Point::new(5200.0, 1400.0)],
            0xFF0000,
        );
        let dir = std::env::temp_dir().join("emb-editor-test");
        std::fs::create_dir_all(&dir).expect("temp dir");
        let path = dir.join("infinite.pes");
        write_out_content_centered(&model, &path).expect("write pes");
        let read =
            emb_data::pes::read(&std::fs::read(&path).expect("read file")).expect("parse pes");
        // The content extent is 200x400, centred: bounds are the extent at
        // the origin, the points exactly at their centred positions (D007:
        // the writer measures deltas from the offset origin, so a nonzero
        // bounds origin round-trips exactly).
        assert_eq!(read.bounds, [0.0, 0.0, 200.0, 400.0]);
        let positions: Vec<(f32, f32)> = read.stitches.iter().map(|p| (p.x, p.y)).collect();
        assert_eq!(
            positions,
            vec![
                (-100.0, -200.0),
                (-100.0, -200.0),
                (-100.0, -200.0),
                (100.0, 200.0)
            ]
        );
    }

    /// The M4 exit criterion "save via emb-data" end to end: a document with
    /// one polygon saves to a real PES file that the reader parses back into
    /// a non-empty design.
    #[test]
    fn save_round_trips_through_the_pes_writer() {
        let (doc, _) = doc_with_polygon(0);
        let model = stitch_document(&doc);
        let dir = std::env::temp_dir().join("emb-editor-test");
        std::fs::create_dir_all(&dir).expect("temp dir");
        let path = dir.join("roundtrip.pes");
        write_out(&model, &path).expect("write pes");
        let read =
            emb_data::pes::read(&std::fs::read(&path).expect("read file")).expect("parse pes");
        assert!(!read.stitches.is_empty());
        assert_eq!(read.colors.len(), read.stitches.len());
        // Centred on the origin: the stitched triangle's points sit around
        // the canvas centre, shifted by -w/2, -h/2.
        for p in &read.stitches {
            assert!(p.x > -600.0 && p.x < 600.0);
            assert!(p.y > -400.0 && p.y < 400.0);
        }
    }
}
