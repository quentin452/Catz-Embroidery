//! The editor's stitch path: layers of elements become a stitched `Model`,
//! then a `Design` the format writers can save (the Java's `stitchLayer` +
//! `writeOut`).
//!
//! M4 ruling (docs/ROADMAP.md): PARALLEL polygon hatch only. Each visible
//! layer's PLY elements are hatched with `emb_model::hatch::hatch_parallel`
//! (vector-pure, fixture-compared) at the layer's spacing, in the layer's
//! hatch colour; a layer with cull on stitches its elements through their
//! masks minus every later layer's masks (the Java's raster cull, ported
//! 2026-08-16 with the D004-class raster oracles — the polygon fill and the
//! line's distance mask). LIN elements stitch through the ported
//! PERPENDICULAR stroke (D004); TXT needs a font rasteriser (deferred).
//!
//! **2026-08-17: CONCENTRIC entered** (the editor follow-up). The Java
//! editor's CONCENTRIC does NOT call hatchInset (the D003 deferral named the
//! vector path) — it calls `E.image()`, which routes CONCENTRIC to the
//! distance-transform isolines (`hatchRaster` → `isolines`). Both the
//! isolines and the contour tracer are already ported and fixture-compared
//! (M2), so the editor's CONCENTRIC is wiring, not an algorithm: each PLY
//! element's fill mask is reduced by the cull masks (as in PARALLEL), then
//! its concentric rings are the isolines of the remainder. The boundary
//! outline the Java pushes when `!isStroke` stays with the stroke-mode
//! toggle (the next follow-up); the Rust editor's PLY path is fill-only.

use emb_model::hatch;
use emb_model::hatch_raster;
use emb_model::model::Model;
use emb_model::raster::{self, Raster};
use emb_model::stroke::stroke_poly_normal;
use emb_model::trace;

use crate::doc::{Document, Element, ElementKind, HatchMode, Layer};

/// The Java's `HATCH_ANGLE` default: QUARTER_PI.
const HATCH_ANGLE: f32 = std::f32::consts::FRAC_PI_4;

/// The Java's `calcAxisAngleForParallel`: HALF_PI - ang.
fn axis_angle(ang: f32) -> f32 {
    std::f32::consts::FRAC_PI_2 - ang
}

/// The Java's `STROKE_SPACING` default: the editor never sets it, so the
/// LIN contour stroke runs at the model's default spacing.
const STROKE_SPACING: f32 = 4.0;

/// Stitch every visible layer into one model: each PLY element hatches in
/// its layer's colour (the Java's `stitchLayer` — what `draw()` shows).
/// A layer with cull on (the Java's default) stitches each element through
/// its mask minus every LATER layer's element masks, so later content cuts
/// holes out of earlier stitches (Main.java:244-259) — invisible layers
/// still cut, like the Java.
///
/// NO TSP here: the Java optimises only at save (`writeOut` → `optimize()`),
/// and the preview refreshes on every document change — a full TSP pass per
/// refresh is O(iterations · n²) (measured: a 1600 mm circle at 4 mm spacing
/// costs ~70 ms of TSP for ~9 ms of stitching, and the cost grows n²). The
/// save path calls `Model::optimize()` explicitly.
pub fn stitch_document(doc: &Document) -> Model {
    let mut model = Model::new(doc.width, doc.height);
    for (i, layer) in doc.layers.iter().enumerate() {
        if layer.visible {
            stitch_layer(&mut model, layer, &doc.layers[i + 1..]);
        }
    }
    model
}

/// One layer's elements → stitched polylines in the layer's colours.
fn stitch_layer(model: &mut Model, layer: &Layer, later: &[Layer]) {
    let angle = axis_angle(HATCH_ANGLE);
    let spacing = layer.hatch_spacing.max(0.1);
    for elt in &layer.elements {
        match elt.kind {
            ElementKind::Polygon => {
                if elt.data.len() < 3 {
                    continue;
                }
                if layer.cull {
                    stitch_polygon_culled(model, layer, elt, later, angle, spacing);
                } else {
                    match layer.hatch_mode {
                        HatchMode::Parallel => {
                            for poly in hatch::hatch_parallel(&elt.data, angle, spacing) {
                                model.push_polyline(poly, layer.hatch_color);
                            }
                        }
                        HatchMode::Concentric => {
                            stitch_polygon_concentric(model, layer, elt, spacing);
                        }
                    }
                }
            }
            ElementKind::Line => {
                // The Java's editor rasterises the line at the element's
                // thickness (paramF0) and strokes its CONTOUR through
                // image() — PERPENDICULAR at the layer's stroke weight
                // (Main.java: rasterizeLayer + stitchLayer). Ported with the
                // D004 distance oracle (the rasterised line = every pixel
                // within half the thickness), the CONCENTRIC fill of the
                // line deferred with hatchInset (D003). TANGENT stroke
                // deferred; the layer's stroke mode is PERPENDICULAR.
                if layer.cull {
                    stitch_line_culled(model, layer, elt, later);
                } else {
                    stitch_line(model, layer, elt);
                }
            }
            ElementKind::Text => {
                // TXT: font rasteriser, deferred (D003/D005 class). Named
                // exception in docs/ROADMAP.md — text neither stitches nor
                // cuts later layers' masks.
            }
        }
    }
}

/// A culled PLY: the element's fill mask, minus every later element's mask,
/// hatched from the remainder. When nothing actually covers the element the
/// vector hatch runs untouched — the raster path only takes over when the
/// subtraction changes the mask (the Java always hatches the mask; the
/// element-local raster is the D004-class oracle, invariant-tested, not
/// fixture-compared).
fn stitch_polygon_culled(
    model: &mut Model,
    layer: &Layer,
    elt: &Element,
    later: &[Layer],
    angle: f32,
    spacing: f32,
) {
    let (x0, y0, w, h) = element_box(elt);
    let (mut mask, _, _) = element_mask(elt);
    let before = mask.pixels().iter().filter(|&&p| p).count();
    let cut = later_union(later, x0, y0, w, h);
    if cut.pixels().iter().any(|&p| p) {
        // The cut mask shares the element's box by construction — the size
        // error is unreachable.
        if mask.and_not(&cut).is_err() {
            unreachable!("cull masks share the element's box");
        }
    }
    let after = mask.pixels().iter().filter(|&&p| p).count();
    if before == after {
        match layer.hatch_mode {
            HatchMode::Parallel => {
                for poly in hatch::hatch_parallel(&elt.data, angle, spacing) {
                    model.push_polyline(poly, layer.hatch_color);
                }
            }
            HatchMode::Concentric => {
                stitch_polygon_concentric(model, layer, elt, spacing);
            }
        }
        return;
    }
    // The Java's `hatchRaster` on the culled mask: the holes the later
    // layers cut are part of the mask, so the raster hatch respects them
    // where a vector hatch of the polygon could not.
    match layer.hatch_mode {
        HatchMode::Parallel => {
            for poly in hatch_raster::hatch_parallel_raster(&mask, angle, spacing, 1.0) {
                let translated: Vec<emb_model::geom::Point> = poly
                    .iter()
                    .map(|p| emb_model::geom::Point::new(p.x + x0, p.y + y0))
                    .collect();
                model.push_polyline(translated, layer.hatch_color);
            }
        }
        HatchMode::Concentric => {
            let Ok(rings) = hatch::isolines(&mask, spacing) else {
                return;
            };
            for ring in rings {
                let translated: Vec<emb_model::geom::Point> = ring
                    .iter()
                    .map(|p| emb_model::geom::Point::new(p.x + x0, p.y + y0))
                    .collect();
                model.push_polyline(translated, layer.hatch_color);
            }
        }
    }
}

/// CONCENTRIC fill of one unculled PLY: the concentric rings are the
/// isolines of the element's fill mask (the Java editor's CONCENTRIC routes
/// through `E.image()` → `hatchRaster` → `isolines`; the isolines are the
/// distance-transform rings INSIDE the shape, fixture-compared in M2).
fn stitch_polygon_concentric(model: &mut Model, layer: &Layer, elt: &Element, spacing: f32) {
    let (x0, y0, _, _) = element_box(elt);
    let (mask, _, _) = element_mask(elt);
    let Ok(rings) = hatch::isolines(&mask, spacing) else {
        return;
    };
    for ring in rings {
        let translated: Vec<emb_model::geom::Point> = ring
            .iter()
            .map(|p| emb_model::geom::Point::new(p.x + x0, p.y + y0))
            .collect();
        model.push_polyline(translated, layer.hatch_color);
    }
}

/// A culled LIN: the line's thickness mask, minus every later element's
/// mask, then the PERPENDICULAR stroke of the remainder's contours — a later
/// polygon cutting through the line splits it into two stroked contours.
fn stitch_line_culled(model: &mut Model, layer: &Layer, elt: &Element, later: &[Layer]) {
    if elt.data.len() < 2 {
        return;
    }
    let (x0, y0, w, h) = element_box(elt);
    let (mut mask, _, _) = element_mask(elt);
    let before = mask.pixels().iter().filter(|&&p| p).count();
    let cut = later_union(later, x0, y0, w, h);
    if cut.pixels().iter().any(|&p| p) {
        // The cut mask shares the element's box by construction — the size
        // error is unreachable.
        if mask.and_not(&cut).is_err() {
            unreachable!("cull masks share the element's box");
        }
    }
    if before == mask.pixels().iter().filter(|&&p| p).count() {
        stitch_line(model, layer, elt);
        return;
    }
    let Ok(mut contours) = trace::find_contours(&mask) else {
        return;
    };
    contours.retain(|c| c.len() >= 3);
    let translated: Vec<Vec<emb_model::geom::Point>> = contours
        .iter()
        .map(|c| {
            c.iter()
                .map(|p| emb_model::geom::Point::new(p.x + x0, p.y + y0))
                .collect()
        })
        .collect();
    stroke_contours(model, layer, &translated);
}

/// The rasterised line as a mask: every pixel within `half` of the polyline
/// is ON (the D004 distance oracle), element-local at 1 px per mm — the
/// Java's layer render is the same resolution (W×H pixels over W×H mm).
/// The two endpoints get a disc oracle (the Java2D round caps). `(x0, y0,
/// w, h)` is the element's footprint from `element_box`.
fn line_mask(
    poly: &[emb_model::geom::Point],
    half: f32,
    x0: f32,
    y0: f32,
    w: usize,
    h: usize,
) -> Raster {
    let mut pixels = Vec::with_capacity(w * h);
    let last = poly[poly.len() - 1]; // the caller guarantees >= 2 points
    for py in 0..h as i32 {
        for px in 0..w as i32 {
            let p = emb_model::geom::Point::new(x0 + px as f32, y0 + py as f32);
            let d = poly
                .windows(2)
                .fold(f32::INFINITY, |best, seg| {
                    best.min(trace::point_distance_to_segment(p, seg[0], seg[1]))
                })
                .min(p.dist(poly[0]))
                .min(p.dist(last));
            pixels.push(d <= half);
        }
    }
    // The pixel count matches w*h by construction — the size error is
    // unreachable.
    match Raster::new(w, h, pixels) {
        Ok(raster) => raster,
        Err(_) => unreachable!("line mask pixels match the declared size"),
    }
}

/// The LIN stitch: rasterise the line (the D004 oracle: ON within half the
/// element's thickness), trace its contour, stroke the contour with the
/// layer's PERPENDICULAR settings — the Java's `image()` path, element-local
/// so the infinite canvas stays scalable.
fn stitch_line(model: &mut Model, layer: &Layer, elt: &Element) {
    if elt.data.len() < 2 {
        return;
    }
    let (mask, x0, y0) = element_mask(elt);
    let Ok(mut contours) = trace::find_contours(&mask) else {
        return;
    };
    contours.retain(|c| c.len() >= 3);
    // The contour is traced in the mask's LOCAL pixel space — translate
    // it back to the design before stroking (the mask starts at (ox, oy)).
    let contours: Vec<Vec<emb_model::geom::Point>> = contours
        .iter()
        .map(|c| {
            c.iter()
                .map(|p| emb_model::geom::Point::new(p.x + x0, p.y + y0))
                .collect()
        })
        .collect();
    stroke_contours(model, layer, &contours);
}

/// The PERPENDICULAR stroke of already-translated contours: the Java's
/// `_stroke(polys, true)` — half-weight = strokeWeight/2, spacing =
/// STROKE_SPACING (4), closed contours, connected.
fn stroke_contours(model: &mut Model, layer: &Layer, contours: &[Vec<emb_model::geom::Point>]) {
    for contour in contours {
        let contour = trace::approx_poly_dp(contour, 1.0);
        for bar in stroke_poly_normal(
            &contour,
            (layer.stroke_weight / 2.0).max(0.5),
            STROKE_SPACING,
            true,
            true,
        ) {
            model.push_polyline(bar, layer.stroke_color);
        }
    }
}

/// The raster footprint of an element at 1 px per mm: the box its mask
/// occupies, with the thickness margin the Java's `rasterizeLayer` gives a
/// LIN (a fat line's mask sticks out half its weight around its spine).
/// TXT keeps its anchor box; its mask stays empty (the font rasteriser is
/// deferred — text neither stitches nor cuts).
fn element_box(elt: &Element) -> (f32, f32, usize, usize) {
    let half = match elt.kind {
        ElementKind::Line => (elt.param_f0 / 2.0).max(0.5),
        _ => 0.0,
    };
    let mut min_x = f32::INFINITY;
    let mut min_y = f32::INFINITY;
    let mut max_x = f32::NEG_INFINITY;
    let mut max_y = f32::NEG_INFINITY;
    for p in &elt.data {
        min_x = min_x.min(p.x);
        min_y = min_y.min(p.y);
        max_x = max_x.max(p.x);
        max_y = max_y.max(p.y);
    }
    let margin = half + 1.0;
    let x0 = (min_x - margin).floor();
    let y0 = (min_y - margin).floor();
    let w = ((max_x - min_x + 2.0 * margin).ceil() as i32).max(1) as usize;
    let h = ((max_y - min_y + 2.0 * margin).ceil() as i32).max(1) as usize;
    (x0, y0, w, h)
}

/// The element's mask plus its design-space origin: PLY = the polygon fill
/// (the Java2D fill's center-in-polygon oracle), LIN = the D004 distance
/// oracle, TXT = empty.
fn element_mask(elt: &Element) -> (Raster, f32, f32) {
    let (x0, y0, w, h) = element_box(elt);
    match elt.kind {
        ElementKind::Polygon => (raster::fill_polygon(&elt.data, x0, y0, w, h), x0, y0),
        ElementKind::Line => (
            line_mask(&elt.data, (elt.param_f0 / 2.0).max(0.5), x0, y0, w, h),
            x0,
            y0,
        ),
        ElementKind::Text => (
            // The box comes from element_box — the size error is unreachable.
            match Raster::new(w, h, vec![false; w * h]) {
                Ok(r) => r,
                Err(_) => unreachable!("the text mask is all false"),
            },
            x0,
            y0,
        ),
    }
}

/// The union mask of every later element overlapping the element's box: the
/// Java's cull loop (`Main.java:244-259`) — every later layer is rasterised
/// and SUBTRACT-blended into this layer's render, visible or not. Elements
/// whose footprint does not touch the box are skipped.
fn later_union(later_layers: &[Layer], x0: f32, y0: f32, w: usize, h: usize) -> Raster {
    let mut cut = match Raster::new(w, h, vec![false; w * h]) {
        Ok(r) => r,
        Err(_) => unreachable!("the cut mask is all false"),
    };
    for layer in later_layers {
        for other in &layer.elements {
            let (ox, oy, ow, oh) = element_box(other);
            if ox + ow as f32 <= x0
                || ox >= x0 + w as f32
                || oy + oh as f32 <= y0
                || oy >= y0 + h as f32
            {
                continue;
            }
            let (mask, mx, my) = element_mask(other);
            cut.overlay_or(&mask, (mx - x0).round() as i32, (my - y0).round() as i32);
        }
    }
    cut
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
    fn line_elements_stitch_and_text_does_not() {
        // LIN: the PERPENDICULAR stroke of the rasterised line's contour
        // (the M5 consumer of the D004 stroke). TXT: font rasteriser,
        // deferred (D003) — not stitched.
        let mut doc = Document::new();
        doc.current_mut().elements.push(Element::line(
            vec![Point::new(0.0, 0.0), Point::new(100.0, 100.0)],
            20.0,
        ));
        doc.current_mut()
            .elements
            .push(Element::text("hi".into(), 20.0, Point::new(50.0, 50.0)));
        let model = stitch_document(&doc);
        assert!(!model.polylines.is_empty());
        assert!(model.colors.iter().all(|&c| c == 0xFF0000));
        for poly in &model.polylines {
            assert!(poly.len() >= 2);
        }
        let doc2 = {
            let mut d = Document::new();
            d.current_mut()
                .elements
                .push(Element::text("hi".into(), 20.0, Point::new(50.0, 50.0)));
            d
        };
        assert!(stitch_document(&doc2).polylines.is_empty());
    }

    #[test]
    fn line_stitch_keeps_the_design_projection() {
        // The mask is element-local: the traced contours must be translated
        // back to the design before stroking — a line far from the origin
        // must stitch near ITS position, not collapse towards (0, 0).
        let mut doc = Document::new();
        doc.current_mut().elements.push(Element::line(
            vec![Point::new(2000.0, 3000.0), Point::new(2200.0, 3000.0)],
            20.0,
        ));
        let model = stitch_document(&doc);
        assert!(!model.polylines.is_empty());
        let (mut min_x, mut min_y, mut max_x, mut max_y) = (
            f32::INFINITY,
            f32::INFINITY,
            f32::NEG_INFINITY,
            f32::NEG_INFINITY,
        );
        for poly in &model.polylines {
            for p in poly {
                min_x = min_x.min(p.x);
                min_y = min_y.min(p.y);
                max_x = max_x.max(p.x);
                max_y = max_y.max(p.y);
            }
        }
        // The stitched line sits at the line's position (x 2000..2200),
        // with the stroke bars a few mm around it.
        assert!(min_x > 1900.0 && max_x < 2300.0, "x span {min_x}..{max_x}");
        assert!(min_y > 2900.0 && max_y < 3100.0, "y span {min_y}..{max_y}");
    }

    #[test]
    fn hidden_layers_are_not_stitched() {
        let (mut doc, _) = doc_with_polygon(1);
        doc.layers[1].visible = false;
        let model = stitch_document(&doc);
        assert!(model.polylines.is_empty());
    }

    /// The cull cut: layer 1's rectangle covers the RIGHT HALF of layer 0's
    /// rectangle; with cull on (the default), no stitched polyline of layer 0
    /// may reach into the covered region; with cull off, its hatch reaches the
    /// full width. Layer 1 gets a distinct hatch colour so the tests can read
    /// each layer's polylines apart.
    fn two_overlapping_rects() -> Document {
        let mut doc = Document::new();
        doc.layers.push(Layer::new());
        doc.layers[0].elements.push(Element::polygon(vec![
            Point::new(10.0, 10.0),
            Point::new(90.0, 10.0),
            Point::new(90.0, 80.0),
            Point::new(10.0, 80.0),
        ]));
        doc.layers[1].hatch_color = 0x00FF00;
        doc.layers[1].elements.push(Element::polygon(vec![
            Point::new(50.0, 10.0),
            Point::new(90.0, 10.0),
            Point::new(90.0, 80.0),
            Point::new(50.0, 80.0),
        ]));
        doc
    }

    /// The max design-space x of the polylines in one colour (a layer's
    /// polylines carry its colour).
    fn max_x_of(model: &Model, color: u32) -> f32 {
        model
            .polylines
            .iter()
            .zip(&model.colors)
            .filter(|(_, c)| **c == color)
            .flat_map(|(p, _)| p.iter())
            .map(|p| p.x)
            .fold(f32::NEG_INFINITY, f32::max)
    }

    #[test]
    fn cull_cuts_the_lower_layer_out_of_the_overlap() {
        let mut doc = two_overlapping_rects();
        let culled = stitch_document(&doc);
        let culled_max = max_x_of(&culled, 0x0000FF);
        assert!(
            culled_max < 60.0,
            "the hatch must not cross into the covered right half: {culled_max}"
        );
        doc.layers[0].cull = false;
        let unculled = stitch_document(&doc);
        let unculled_max = max_x_of(&unculled, 0x0000FF);
        assert!(
            unculled_max > 80.0,
            "without cull the hatch reaches the polygon's full width: {unculled_max}"
        );
    }

    #[test]
    fn cull_without_overlap_keeps_the_vector_hatch_unchanged() {
        // Two polygons far apart: the subtraction changes nothing, so the
        // stitched polylines must be EXACTLY the cull-off vector hatch.
        let mut doc = Document::new();
        doc.layers.push(Layer::new());
        doc.layers[0].elements.push(Element::polygon(vec![
            Point::new(0.0, 0.0),
            Point::new(40.0, 0.0),
            Point::new(40.0, 40.0),
            Point::new(0.0, 40.0),
        ]));
        doc.layers[1].elements.push(Element::polygon(vec![
            Point::new(300.0, 0.0),
            Point::new(340.0, 0.0),
            Point::new(340.0, 40.0),
            Point::new(300.0, 40.0),
        ]));
        let on = stitch_document(&doc);
        doc.layers[0].cull = false;
        doc.layers[1].cull = false;
        let off = stitch_document(&doc);
        assert_eq!(on.polylines, off.polylines);
        assert_eq!(on.colors, off.colors);
    }

    #[test]
    fn cull_splits_a_line_where_a_later_polygon_covers_it() {
        // A fat horizontal LIN in layer 0 (red strokes), a triangle in layer 1
        // (blue hatch) covering its middle. The cut triangle at the line's
        // spine spans x 40..60; the PERPENDICULAR stroke may poke a few mm
        // past the cut edge (its oracle reach — the Java's `_stroke` does the
        // same), so the invariant is: the surviving bars form a left and a
        // right cluster, and no single bar spans ACROSS the removed zone.
        let mut doc = Document::new();
        doc.layers.push(Layer::new());
        doc.layers[0].elements.push(Element::line(
            vec![Point::new(10.0, 50.0), Point::new(90.0, 50.0)],
            20.0,
        ));
        doc.layers[1].elements.push(Element::polygon(vec![
            Point::new(30.0, 35.0),
            Point::new(70.0, 35.0),
            Point::new(50.0, 65.0),
        ]));
        let cut = stitch_document(&doc);
        let red: Vec<&Vec<Point>> = cut
            .polylines
            .iter()
            .zip(&cut.colors)
            .filter(|(_, c)| **c == 0xFF0000)
            .map(|(p, _)| p)
            .collect();
        assert!(red.len() >= 2, "the cut splits the line into two pieces");
        let has_left = red.iter().any(|p| p.iter().all(|q| q.x <= 53.0));
        let has_right = red.iter().any(|p| p.iter().all(|q| q.x >= 47.0));
        assert!(has_left, "the left part of the line survives");
        assert!(has_right, "the right part of the line survives");
        for poly in &red {
            let (mut lo, mut hi) = (f32::INFINITY, f32::NEG_INFINITY);
            for q in poly.iter() {
                lo = lo.min(q.x);
                hi = hi.max(q.x);
            }
            assert!(
                !(lo < 42.0 && hi > 58.0),
                "no bar spans the removed zone: x {lo}..{hi}"
            );
        }
    }

    #[test]
    fn invisible_layers_still_cut_the_layers_below() {
        // The Java's cull loop subtracts every later layer's render, visible
        // or not (Main.java:248-252) — kept as recorded.
        let mut doc = two_overlapping_rects();
        doc.layers[1].visible = false;
        let model = stitch_document(&doc);
        assert!(
            !model.polylines.is_empty(),
            "the invisible layer does not stitch, but layer 0's left half does"
        );
        assert!(
            max_x_of(&model, 0x0000FF) < 60.0,
            "the invisible layer still cuts: {}",
            max_x_of(&model, 0x0000FF)
        );
        doc.layers[0].cull = false;
        let model = stitch_document(&doc);
        assert!(
            max_x_of(&model, 0x0000FF) > 80.0,
            "without cull the lower layer reaches its full width"
        );
    }

    #[test]
    fn text_elements_do_not_cut_later_masks() {
        // TXT contributes no mask (font rasteriser deferred): a text element
        // over a polygon leaves the polygon's hatch untouched.
        let mut doc = Document::new();
        doc.layers.push(Layer::new());
        doc.layers[0].elements.push(Element::polygon(vec![
            Point::new(10.0, 10.0),
            Point::new(90.0, 10.0),
            Point::new(90.0, 80.0),
            Point::new(10.0, 80.0),
        ]));
        doc.layers[1]
            .elements
            .push(Element::text("hi".into(), 40.0, Point::new(50.0, 50.0)));
        let with_text = stitch_document(&doc);
        doc.layers[1].elements.clear();
        let without_text = stitch_document(&doc);
        assert_eq!(with_text.polylines, without_text.polylines);
    }

    #[test]
    fn concentric_hatches_a_polygon_into_rings() {
        // CONCENTRIC: the isolines of the element's fill mask — at least one
        // closed ring inside a 80x70 triangle (the M2 isolines port, fixture-
        // compared). The rings sit INSIDE the shape (the distance transform's
        // polarity, pinned by the isolines fixture).
        let mut doc = Document::new();
        doc.current_mut().hatch_mode = HatchMode::Concentric;
        doc.current_mut()
            .elements
            .push(Element::polygon(triangle()));
        let model = stitch_document(&doc);
        assert!(!model.polylines.is_empty());
        assert!(model.colors.iter().all(|&c| c == 0x0000FF));
        let (mut min_x, mut min_y, mut max_x, mut max_y) = (
            f32::INFINITY,
            f32::INFINITY,
            f32::NEG_INFINITY,
            f32::NEG_INFINITY,
        );
        for poly in &model.polylines {
            for p in poly {
                min_x = min_x.min(p.x);
                min_y = min_y.min(p.y);
                max_x = max_x.max(p.x);
                max_y = max_y.max(p.y);
            }
        }
        // The rings stay strictly inside the triangle (10,10)-(90,10)-(50,80):
        // isolines are at a distance from the boundary, never outside it.
        assert!(min_x >= 8.0 && max_x <= 92.0, "x span {min_x}..{max_x}");
        assert!(min_y >= 8.0 && max_y <= 82.0, "y span {min_y}..{max_y}");
    }

    #[test]
    fn concentric_rings_follow_the_mask_after_a_cull_cut() {
        // A CONCENTRIC layer 0 rectangle, a later rectangle covering the right
        // half: the concentric rings of the culled mask must not reach into
        // the covered region (the holes the cull cuts are part of the mask the
        // isolines read).
        let mut doc = two_overlapping_rects();
        doc.layers[0].hatch_mode = HatchMode::Concentric;
        let model = stitch_document(&doc);
        let cut_max = max_x_of(&model, 0x0000FF);
        assert!(
            cut_max < 60.0,
            "the concentric rings must not cross into the covered right half: {cut_max}"
        );
        doc.layers[0].cull = false;
        let model = stitch_document(&doc);
        let uncut_max = max_x_of(&model, 0x0000FF);
        assert!(
            uncut_max > 80.0,
            "without cull the rings reach the polygon's full width: {uncut_max}"
        );
    }

    #[test]
    fn concentric_unchanged_by_a_far_cull() {
        // Two polygons far apart with CONCENTRIC on the lower layer: the cull
        // subtraction changes nothing, so the rings are EXACTLY the unculled
        // concentric output (the same `before == after` fast path PARALLEL
        // uses).
        let mut doc = Document::new();
        doc.layers.push(Layer::new());
        doc.layers[0].hatch_mode = HatchMode::Concentric;
        doc.layers[0].elements.push(Element::polygon(vec![
            Point::new(0.0, 0.0),
            Point::new(40.0, 0.0),
            Point::new(40.0, 40.0),
            Point::new(0.0, 40.0),
        ]));
        doc.layers[1].elements.push(Element::polygon(vec![
            Point::new(300.0, 0.0),
            Point::new(340.0, 0.0),
            Point::new(340.0, 40.0),
            Point::new(300.0, 40.0),
        ]));
        let on = stitch_document(&doc);
        doc.layers[0].cull = false;
        doc.layers[1].cull = false;
        let off = stitch_document(&doc);
        assert_eq!(on.polylines, off.polylines);
        assert_eq!(on.colors, off.colors);
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

    /// The save path end to end with a REALISTIC document (a polygon and a
    /// LIN element), bounded AND infinite, through all three writers: the
    /// PES reads back with the right geometry, the DST/SVG write sane
    /// headers and carry the stitches. The whole flow the Save button runs.
    #[test]
    fn save_path_round_trips_a_realistic_document() {
        let mut doc = Document::new();
        doc.current_mut()
            .elements
            .push(Element::polygon(triangle()));
        doc.current_mut().elements.push(Element::line(
            vec![Point::new(500.0, 300.0), Point::new(700.0, 450.0)],
            20.0,
        ));
        let dir = std::env::temp_dir().join("emb-editor-test");
        std::fs::create_dir_all(&dir).expect("temp dir");

        let model = stitch_document(&doc);
        let write_out_fn = write_out as fn(&Model, &std::path::Path) -> Result<(), String>;
        for (label, path, write) in [
            ("bounded", dir.join("bounded.pes"), write_out_fn),
            (
                "infinite",
                dir.join("infinite.pes"),
                write_out_content_centered,
            ),
        ] {
            let mut m = model.clone();
            m.optimize();
            write(&m, &path).expect("write pes");

            let read = emb_data::pes::read(&std::fs::read(&path).expect("read")).expect("parse");
            assert!(!read.stitches.is_empty(), "{label} has stitches");
            assert_eq!(read.colors.len(), read.stitches.len());
            let (mut min_x, mut min_y, mut max_x, mut max_y) = (
                f32::INFINITY,
                f32::INFINITY,
                f32::NEG_INFINITY,
                f32::NEG_INFINITY,
            );
            for p in &read.stitches {
                min_x = min_x.min(p.x);
                min_y = min_y.min(p.y);
                max_x = max_x.max(p.x);
                max_y = max_y.max(p.y);
            }
            if label == "bounded" {
                // Centred on the 1024x720 canvas.
                assert_eq!(read.bounds, [0.0, 0.0, 1024.0, 720.0]);
            }
            assert!(max_x - min_x > 100.0, "{label} spans real content");
        }

        // DST: sane header, stitch records follow — the third byte of the
        // first record carries the stitch/jump flags (b2 = 0b...11).
        let dst_path = dir.join("bounded.dst");
        write_out(&model, &dst_path).expect("write dst");
        let dst = std::fs::read(&dst_path).expect("read dst");
        assert_eq!(&dst[0..3], b"LA:", "DST header starts with LA:");
        assert!(dst.len() > 512);
        assert!(dst[514] & 0x03 == 0x03, "first record is a stitch/jump");

        // SVG: a viewBox over the content, paths carry the stitches.
        let svg_path = dir.join("bounded.svg");
        write_out(&model, &svg_path).expect("write svg");
        let svg = std::fs::read_to_string(&svg_path).expect("read svg");
        assert!(svg.contains("viewBox="), "svg has a viewBox");
        assert!(svg.contains("<path"), "svg has paths");
        assert!(svg.ends_with("</svg>"));
    }
}
