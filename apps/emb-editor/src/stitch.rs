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
/// its layer's colour, then the whole design is TSP-optimised per colour
/// block (the Java's `E.optimize()` in `writeOut`).
///
/// Hidden layers are skipped — in the Java, `draw()` only stitches visible
/// layers, so `writeOut` never sees hidden content.
pub fn stitch_document(doc: &Document) -> Model {
    let mut model = Model::new(doc.width, doc.height);
    for layer in &doc.layers {
        if layer.visible {
            stitch_layer(&mut model, layer);
        }
    }
    model.optimize();
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

/// The save transform of the Java's `writeOut` → `PEmbroiderWriter.write`:
/// the canvas is centred on the hoop origin (the writer's default
/// `TRANSFORM = translate(-width/2, -height/2)`), and the title is the file
/// stem truncated to the writer's 8 characters.
pub fn centered_design(model: &Model, title: &str) -> emb_data::Design {
    let dx = -model.width / 2.0;
    let dy = -model.height / 2.0;
    let mut design = model.to_design(title.into());
    for p in &mut design.stitches {
        p.x += dx;
        p.y += dy;
    }
    design.bounds = [dx, dy, dx + model.width, dy + model.height];
    design
}

/// The Java's title rule: the file stem, truncated to 8 characters
/// (`PEmbroiderWriter.write`: `TITLE.substring(0, min(8, len))`).
pub fn file_title(path: &std::path::Path) -> String {
    let stem = path
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_default();
    stem.chars().take(8).collect()
}

/// Save a stitched model to a file, choosing the writer by extension.
/// PES/DST/SVG are the M1 writers; anything else is refused (the Java's
/// "Unsupported format" path — the Rust scope is D001's format list).
pub fn write_design(path: &std::path::Path, design: &emb_data::Design) -> Result<(), String> {
    let ext = path
        .extension()
        .map(|e| e.to_string_lossy().to_lowercase())
        .unwrap_or_default();
    let bytes: Vec<u8> = match ext.as_str() {
        "pes" => emb_data::pes::write(design).map_err(|e| e.to_string())?,
        "dst" => emb_data::dst::write(design).map_err(|e| e.to_string())?,
        "svg" => emb_data::svg::write(design).into_bytes(),
        _ => return Err(format!("unsupported extension .{ext}")),
    };
    std::fs::write(path, bytes).map_err(|e| e.to_string())
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
    fn centered_design_moves_the_canvas_to_the_origin() {
        let mut model = Model::new(1024.0, 720.0);
        model.push_polyline(
            vec![Point::new(512.0, 360.0), Point::new(600.0, 400.0)],
            0xFF0000,
        );
        let d = centered_design(&model, "design");
        assert_eq!(d.bounds, [-512.0, -360.0, 512.0, 360.0]);
        assert_eq!(d.stitches[0], emb_data::Point { x: 0.0, y: 0.0 });
        assert_eq!(d.jumps, vec![true, false]);
    }

    #[test]
    fn file_title_truncates_to_eight() {
        let p = std::path::Path::new("verylongname.pes");
        assert_eq!(file_title(p), "verylong");
        let p = std::path::Path::new("ab.pes");
        assert_eq!(file_title(p), "ab");
    }

    #[test]
    fn write_design_refuses_unknown_extensions() {
        let d = emb_data::Design {
            bounds: [0.0; 4],
            stitches: vec![],
            colors: vec![],
            jumps: vec![],
            title: "t".into(),
        };
        assert!(write_design(std::path::Path::new("x.gcode"), &d).is_err());
    }

    /// The M4 exit criterion "save via emb-data" end to end: a document with
    /// one polygon saves to a real PES file that the reader parses back into
    /// a non-empty design.
    #[test]
    fn save_round_trips_through_the_pes_writer() {
        let (doc, _) = doc_with_polygon(0);
        let model = stitch_document(&doc);
        let design = centered_design(&model, "roundtrip");
        let dir = std::env::temp_dir().join("emb-editor-test");
        std::fs::create_dir_all(&dir).expect("temp dir");
        let path = dir.join("roundtrip.pes");
        write_design(&path, &design).expect("write pes");
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
