//! The editor's document model: layers of vector elements (lines, polygons,
//! text) with per-layer hatch settings, plus the undo/redo command stack.
//!
//! Mirrors the Java editor's `Layer`/`Element`/`UndoableCommand` shape (the
//! Java suite is the model, never a source). The Java editor is not loaded
//! from disk — it draws from scratch and saves; the Rust editor does the same
//! (docs/ROADMAP.md, M4).

use emb_model::geom::Point;

/// The Java editor's canvas: `W = currentWidth / 1.25`, `H = currentHeight`.
pub const CANVAS_WIDTH_MM: f32 = 1024.0;
pub const CANVAS_HEIGHT_MM: f32 = 720.0;

/// The Java editor's point-snapping threshold: a new point is only added when
/// it is more than 10 design units (mm here) from the previous one.
pub const POINT_MIN_DIST_MM: f32 = 10.0;

/// The Java editor's `LIN`/`PLY`/`TXT` element kinds.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ElementKind {
    /// A stroked line (`LIN`): `param_f0` is the stroke weight in mm.
    Line,
    /// A filled polygon (`PLY`): hatched by the stitch path.
    Polygon,
    /// A text element (`TXT`): `param_s0` is the text, `param_f0` the size.
    Text,
}

/// One drawing element in a layer.
#[derive(Debug, Clone, PartialEq)]
pub struct Element {
    pub kind: ElementKind,
    pub data: Vec<Point>,
    /// `paramF0` in the Java: stroke weight (Line), unused (Polygon), or
    /// text size (Text).
    pub param_f0: f32,
    /// `paramS0` in the Java: the text content (Text only).
    pub param_s0: String,
}

impl Element {
    pub fn line(points: Vec<Point>, weight: f32) -> Self {
        Self {
            kind: ElementKind::Line,
            data: points,
            param_f0: weight,
            param_s0: String::new(),
        }
    }

    pub fn polygon(points: Vec<Point>) -> Self {
        Self {
            kind: ElementKind::Polygon,
            data: points,
            param_f0: 20.0,
            param_s0: String::new(),
        }
    }

    pub fn text(text: String, size: f32, at: Point) -> Self {
        Self {
            kind: ElementKind::Text,
            data: vec![at],
            param_f0: size,
            param_s0: text,
        }
    }
}

/// Hatch modes the stitch path can produce. Closed set (pin 4): only the
/// ported modes exist. PARALLEL is the vector hatch (M4). CONCENTRIC entered
/// 2026-08-17 with the editor's follow-up (the Java editor's CONCENTRIC goes
/// through the raster path — boundary contour + distance-transform isolines —
/// both already ported and fixture-compared in M2; the D003 deferral named
/// hatchInset, which the editor does NOT call: it calls `E.image()`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HatchMode {
    Parallel,
    Concentric,
}

/// The layer's stroke mode (the Java's `Layer.strokeMode`), a closed set
/// (pin 4). PERPENDICULAR is the D004 geometric-oracle stroke (M5); TANGENT
/// entered 2026-08-17 (D012) as the concentric-offset oracle; ANGLED is
/// the D013 rotated-bar variant of the PERPENDICULAR oracle. The Java
/// editor's default is TANGENT (Main.java:175); the Rust editor's default is
/// PERPENDICULAR (the M4 ruling kept it; the toggle lets the user switch).
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum StrokeMode {
    Perpendicular,
    Tangent,
    Angled { angle: f32 },
}

/// A layer: elements plus the hatch and stroke settings the stitch path
/// consumes. The stroke settings entered with their consumer (M5, D003's
/// ruling): the LIN elements stitch through the ported PERPENDICULAR stroke
/// (D004). **2026-08-17: the stroke mode toggle entered (D012)** — the
/// layer's TANGENT mode stitches the same contours through the concentric
/// offset oracle.
#[derive(Debug, Clone, PartialEq)]
pub struct Layer {
    pub hatch_mode: HatchMode,
    /// The LIN/TXT stroke mode (the Java's `Layer.strokeMode`): which oracle
    /// stitches a contour. Java editor default TANGENT; Rust default
    /// PERPENDICULAR (the M4 ruling).
    pub stroke_mode: StrokeMode,
    /// Packed `0x00RRGGBB`. The Java default is blue `color(0, 0, 255)`.
    pub hatch_color: u32,
    /// The Java default `hatchSpacing = 4` mm.
    pub hatch_spacing: f32,
    /// The LIN elements' stitch colour. The Java default is red
    /// `color(255, 0, 0)`.
    pub stroke_color: u32,
    /// The LIN contour stroke's weight, mm. The Java default is 10.
    pub stroke_weight: f32,
    /// Preview-only in the Java: hidden layers are not stitched on save.
    pub visible: bool,
    /// The Java's `Layer.cull` (default true): the layer's stitches are cut
    /// where ANY later layer covers them — every later layer's element mask
    /// is subtracted from this layer's element masks before stitching
    /// (Main.java:244-259). The Java subtracts regardless of visibility;
    /// the port keeps that (recorded, docs/ROADMAP.md).
    pub cull: bool,
    pub elements: Vec<Element>,
}

impl Layer {
    pub fn new() -> Self {
        Self {
            hatch_mode: HatchMode::Parallel,
            stroke_mode: StrokeMode::Perpendicular,
            hatch_color: 0x0000FF,
            hatch_spacing: 4.0,
            stroke_color: 0xFF0000,
            stroke_weight: 10.0,
            visible: true,
            cull: true,
            elements: Vec::new(),
        }
    }
}

impl Default for Layer {
    fn default() -> Self {
        Self::new()
    }
}

/// The open document: layers on a `width x height` mm canvas, with the
/// current layer the tools draw into.
#[derive(Debug, Clone, PartialEq)]
pub struct Document {
    pub layers: Vec<Layer>,
    pub current_layer: usize,
    pub width: f32,
    pub height: f32,
}

impl Document {
    /// A fresh document with one empty layer, the Java editor's canvas size.
    pub fn new() -> Self {
        Self {
            layers: vec![Layer::new()],
            current_layer: 0,
            width: CANVAS_WIDTH_MM,
            height: CANVAS_HEIGHT_MM,
        }
    }

    pub fn current(&self) -> &Layer {
        &self.layers[self.current_layer]
    }

    pub fn current_mut(&mut self) -> &mut Layer {
        &mut self.layers[self.current_layer]
    }

    /// The bounds of every drawn element (all layers): the fit target of the
    /// infinite canvas mode, which has no canvas rect to fit (the hoop is a
    /// bounded-mode concept). `None` when the document has no points.
    pub fn content_bounds(&self) -> Option<emb_draw::Bounds> {
        let mut pts = Vec::new();
        for layer in &self.layers {
            for elt in &layer.elements {
                pts.extend_from_slice(&elt.data);
            }
        }
        if pts.is_empty() {
            None
        } else {
            Some(emb_draw::Bounds::of_points(&pts))
        }
    }
}

impl Default for Document {
    fn default() -> Self {
        Self::new()
    }
}

/// An undoable mutation of the document. Each command carries everything its
/// inverse needs (the Java's `UndoableCommand` pairs, plus the removed value
/// for `RemoveElement`/`RemovePoint`, which the Java captures in the command).
#[derive(Debug, Clone, PartialEq)]
pub enum Command {
    AddElement {
        layer: usize,
        element: Element,
    },
    RemoveElement {
        layer: usize,
        index: usize,
        element: Element,
    },
    RemovePoint {
        layer: usize,
        element: usize,
        index: usize,
        point: Point,
    },
}

impl Command {
    pub fn apply(&self, doc: &mut Document) {
        match self {
            Command::AddElement { layer, element } => {
                doc.layers[*layer].elements.push(element.clone());
            }
            Command::RemoveElement { layer, index, .. } => {
                doc.layers[*layer].elements.remove(*index);
            }
            Command::RemovePoint {
                layer,
                element,
                index,
                ..
            } => {
                doc.layers[*layer].elements[*element].data.remove(*index);
            }
        }
    }

    pub fn undo(&self, doc: &mut Document) {
        match self {
            Command::AddElement { layer, .. } => {
                doc.layers[*layer].elements.pop();
            }
            Command::RemoveElement {
                layer,
                index,
                element,
            } => {
                doc.layers[*layer].elements.insert(*index, element.clone());
            }
            Command::RemovePoint {
                layer,
                element,
                index,
                point,
            } => {
                doc.layers[*layer].elements[*element]
                    .data
                    .insert(*index, *point);
            }
        }
    }
}

/// The undo/redo stacks. A command executed on the document is pushed here;
/// undo pops it and inverts it, redo replays it. Executing a new command
/// clears the redo stack (the Java's `redoStack.clear()`).
#[derive(Debug, Default)]
pub struct History {
    undo_stack: Vec<Command>,
    redo_stack: Vec<Command>,
}

impl History {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn execute(&mut self, doc: &mut Document, cmd: Command) {
        cmd.apply(doc);
        self.undo_stack.push(cmd);
        self.redo_stack.clear();
    }

    pub fn undo(&mut self, doc: &mut Document) -> bool {
        let Some(cmd) = self.undo_stack.pop() else {
            return false;
        };
        cmd.undo(doc);
        self.redo_stack.push(cmd);
        true
    }

    pub fn redo(&mut self, doc: &mut Document) -> bool {
        let Some(cmd) = self.redo_stack.pop() else {
            return false;
        };
        cmd.apply(doc);
        self.undo_stack.push(cmd);
        true
    }

    pub fn can_undo(&self) -> bool {
        !self.undo_stack.is_empty()
    }

    pub fn can_redo(&self) -> bool {
        !self.redo_stack.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn points() -> Vec<Point> {
        vec![
            Point::new(10.0, 10.0),
            Point::new(50.0, 10.0),
            Point::new(50.0, 50.0),
        ]
    }

    #[test]
    fn new_document_has_one_default_layer() {
        let doc = Document::new();
        assert_eq!(doc.layers.len(), 1);
        assert_eq!(doc.width, CANVAS_WIDTH_MM);
        assert_eq!(doc.height, CANVAS_HEIGHT_MM);
        let l = doc.current();
        assert_eq!(l.hatch_mode, HatchMode::Parallel);
        assert_eq!(l.hatch_color, 0x0000FF);
        assert_eq!(l.hatch_spacing, 4.0);
        assert!(l.visible);
        assert!(l.cull, "the Java's Layer.cull default is true");
        assert!(l.elements.is_empty());
    }

    #[test]
    fn content_bounds_covers_every_element() {
        let mut doc = Document::new();
        assert_eq!(doc.content_bounds(), None);
        doc.current_mut().elements.push(Element::polygon(vec![
            Point::new(-300.0, -100.0),
            Point::new(200.0, 400.0),
        ]));
        doc.layers.push(Layer::new());
        doc.current_mut().elements.push(Element::line(
            vec![Point::new(0.0, 0.0), Point::new(50.0, 60.0)],
            5.0,
        ));
        let b = doc.content_bounds().expect("content bounds");
        assert_eq!(
            (b.min_x, b.min_y, b.max_x, b.max_y),
            (-300.0, -100.0, 200.0, 400.0)
        );
    }

    #[test]
    fn add_remove_element_round_trip() {
        let mut doc = Document::new();
        let mut hist = History::new();
        let elt = Element::polygon(points());

        hist.execute(
            &mut doc,
            Command::AddElement {
                layer: 0,
                element: elt.clone(),
            },
        );
        assert_eq!(doc.current().elements.len(), 1);

        hist.undo(&mut doc);
        assert!(doc.current().elements.is_empty());
        assert!(hist.can_redo());

        hist.redo(&mut doc);
        assert_eq!(doc.current().elements, vec![elt]);
    }

    #[test]
    fn remove_element_restores_the_original_index() {
        let mut doc = Document::new();
        let mut hist = History::new();
        for p in [
            points(),
            points()
                .iter()
                .map(|q| Point::new(q.x + 1.0, q.y))
                .collect(),
        ] {
            hist.execute(
                &mut doc,
                Command::AddElement {
                    layer: 0,
                    element: Element::polygon(p),
                },
            );
        }
        let removed = doc.current().elements[1].clone();

        hist.execute(
            &mut doc,
            Command::RemoveElement {
                layer: 0,
                index: 1,
                element: removed.clone(),
            },
        );
        assert_eq!(doc.current().elements.len(), 1);

        hist.undo(&mut doc);
        assert_eq!(doc.current().elements.len(), 2);
        assert_eq!(doc.current().elements[1], removed);
    }

    #[test]
    fn remove_point_restores_the_point_and_index() {
        let mut doc = Document::new();
        let mut hist = History::new();
        hist.execute(
            &mut doc,
            Command::AddElement {
                layer: 0,
                element: Element::polygon(points()),
            },
        );

        let original = points();
        hist.execute(
            &mut doc,
            Command::RemovePoint {
                layer: 0,
                element: 0,
                index: 1,
                point: original[1],
            },
        );
        assert_eq!(doc.current().elements[0].data.len(), 2);

        hist.undo(&mut doc);
        assert_eq!(doc.current().elements[0].data, original);
    }

    #[test]
    fn executing_a_new_command_clears_redo() {
        let mut doc = Document::new();
        let mut hist = History::new();
        hist.execute(
            &mut doc,
            Command::AddElement {
                layer: 0,
                element: Element::polygon(points()),
            },
        );
        hist.undo(&mut doc);
        assert!(hist.can_redo());

        hist.execute(
            &mut doc,
            Command::AddElement {
                layer: 0,
                element: Element::line(points(), 3.0),
            },
        );
        assert!(!hist.can_redo());
    }

    #[test]
    fn undo_redo_on_empty_history_does_nothing() {
        let mut doc = Document::new();
        let mut hist = History::new();
        assert!(!hist.undo(&mut doc));
        assert!(!hist.redo(&mut doc));
    }

    #[test]
    fn commands_target_any_layer() {
        let mut doc = Document::new();
        doc.layers.push(Layer::new());
        let mut hist = History::new();
        hist.execute(
            &mut doc,
            Command::AddElement {
                layer: 1,
                element: Element::polygon(points()),
            },
        );
        assert_eq!(doc.layers[1].elements.len(), 1);
        assert!(doc.layers[0].elements.is_empty());
        hist.undo(&mut doc);
        assert!(doc.layers[1].elements.is_empty());
    }
}
