//! The editor app: a canvas with the shared viewport, the tool rail (the
//! Java editor's 7 tools), the layer panel, and the undo/redo keys. The
//! canvas shows the stitched preview (what the save path will write) in the
//! drawing tools, and the raw elements + point handles in the edit tool
//! (docs/ROADMAP.md, M4).

use eframe::egui::{self, Color32, Pos2, Rect, Sense, Stroke};

use emb_draw::{DrawList, Viewport};
use emb_model::geom::Point;

use crate::doc::{
    Command as DocCommand, Document, Element, ElementKind, HatchMode, History, Layer,
    POINT_MIN_DIST_MM,
};

/// The Java editor's `TOOL_*` constants, as a closed set.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Tool {
    Freehand,
    Vertex,
    Paint,
    FatPaint,
    FineLine,
    Text,
    Edit,
}

impl Tool {
    /// The Java editor's toolbar letters.
    fn label(self) -> &'static str {
        match self {
            Tool::Freehand => "S",
            Tool::Vertex => "Z",
            Tool::Paint => "o",
            Tool::FatPaint => "O",
            Tool::FineLine => "FL",
            Tool::Text => "T",
            Tool::Edit => "E",
        }
    }

    fn tooltip(self) -> &'static str {
        match self {
            Tool::Freehand => "Freehand polygon",
            Tool::Vertex => "Vertex polygon (double-click to finish)",
            Tool::Paint => "Paint line",
            Tool::FatPaint => "Fat paint line",
            Tool::FineLine => "Fine line",
            Tool::Text => "Text",
            Tool::Edit => "Edit points (click to select, drag to move, Del to remove)",
        }
    }

    /// The Java's `newElementFromPolyBuff` kind switch: PAINT/FATPAINT/FINELINE
    /// make LIN elements, FREEHAND/VERTEX make PLY.
    fn makes_line(self) -> bool {
        matches!(self, Tool::Paint | Tool::FatPaint | Tool::FineLine)
    }

    /// The Java's `paramF0` per tool: FATPAINT 60, FINELINE 1, the rest 20.
    fn line_weight(self) -> f32 {
        match self {
            Tool::FatPaint => 60.0,
            Tool::FineLine => 1.0,
            _ => 20.0,
        }
    }
}

const TOOLS: [Tool; 7] = [
    Tool::Freehand,
    Tool::Vertex,
    Tool::Paint,
    Tool::FatPaint,
    Tool::FineLine,
    Tool::Text,
    Tool::Edit,
];

/// New-layer colours: the Java's `color(random(255)...)` made deterministic
/// (a palette instead of a RNG dependency).
const NEW_LAYER_COLORS: [u32; 8] = [
    0x0000FF, 0xFF0000, 0x00AA00, 0xAA00AA, 0x00AAAA, 0xAA5500, 0x555555, 0x000000,
];

pub struct EditorApp {
    doc: Document,
    history: History,
    tool: Tool,
    viewport: Viewport,
    /// The in-progress polyline (the Java `polyBuff`), in design mm.
    poly_buff: Vec<Point>,
    /// The edit tool's selection: (element index, point index) in the
    /// current layer. None = nothing selected.
    edit_sel: Option<(usize, usize)>,
    /// The text tool's draft dialog, opened by a canvas click.
    text_draft: Option<TextDraft>,
    /// The stitched preview: the document as the save path will write it,
    /// rendered through the shared draw list. Recomputed only when the
    /// document changes (the Java's `needsUpdate`).
    stitched: Option<DrawList>,
    needs_update: bool,
    needs_fit: bool,
    /// The infinite canvas mode: no hoop — the view fits the content, the
    /// save centres on the content (the Java has no equivalent; a greenfield
    /// editor feature, docs/ROADMAP.md M5).
    infinite: bool,
    status: Option<String>,
    /// The Java's exit dialog (DialogUtil.showExitDialog): save-and-quit /
    /// exit-without-save / cancel on window close.
    exit_dialog: emb_egui::exit_dialog::ExitDialog,
    /// Per-layer 42×42 thumbnails (the Java's `image(lay.render, 4, oy+4,
    /// 42, 42)`), rebuilt when the document changes.
    thumbnails: Vec<egui::TextureHandle>,
}

/// The text tool's modal: text + size, committed on OK.
struct TextDraft {
    text: String,
    size: String,
    at: Point,
}

impl EditorApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        cc.egui_ctx.set_visuals(egui::Visuals::dark());
        Self {
            doc: Document::new(),
            history: History::new(),
            tool: Tool::Freehand,
            viewport: Viewport::new(1.0, 0.0, 0.0),
            poly_buff: Vec::new(),
            edit_sel: None,
            text_draft: None,
            stitched: None,
            needs_update: true,
            needs_fit: true,
            infinite: false,
            status: None,
            exit_dialog: emb_egui::exit_dialog::ExitDialog::new(),
            thumbnails: Vec::new(),
        }
    }

    /// Re-stitch the preview when the document changed (the Java's
    /// `needsUpdate` gate in `draw()`). The preview is exactly what the save
    /// path produces — one stitch path, one visual.
    fn refresh_stitched(&mut self) {
        if self.needs_update {
            let model = crate::stitch::stitch_document(&self.doc);
            self.stitched = Some(emb_draw::DrawList::from_model(&model));
            self.needs_update = false;
        }
    }

    /// Design-space point under the pointer, or None when off the canvas.
    fn pointer_mm(&self, ui: &egui::Ui) -> Option<Point> {
        ui.ctx().input(|i| i.pointer.interact_pos()).map(|p| {
            let (x, y) = self.viewport.screen_to_mm(p.x, p.y);
            Point::new(x, y)
        })
    }

    /// The canvas click/drag semantics for the drawing tools: FREEHAND-like
    /// tools collect points while dragging, then commit on release.
    fn handle_draw_tool(&mut self, response: &egui::Response, ui: &egui::Ui) {
        if self.tool.makes_line() || self.tool == Tool::Freehand {
            if response.drag_started_by(egui::PointerButton::Primary)
                && let Some(p) = self.pointer_mm(ui)
            {
                self.poly_buff.push(p);
            }
            if response.dragged_by(egui::PointerButton::Primary)
                && let Some(p) = self.pointer_mm(ui)
                && self
                    .poly_buff
                    .last()
                    .is_none_or(|last| last.dist(p) > POINT_MIN_DIST_MM)
            {
                self.poly_buff.push(p);
            }
            if response.drag_stopped_by(egui::PointerButton::Primary) {
                self.commit_poly_buff();
            }
        }
    }

    /// The Java's `newElementFromPolyBuff` + `AddElementCommand`, wrapped in
    /// the undo history. Called with at least 3 points in the buffer.
    fn commit_poly_buff(&mut self) {
        if self.poly_buff.len() < 3 {
            self.poly_buff.clear();
            return;
        }
        let layer = self.doc.current_layer;
        let element = if self.tool.makes_line() {
            Element::line(std::mem::take(&mut self.poly_buff), self.tool.line_weight())
        } else {
            Element::polygon(std::mem::take(&mut self.poly_buff))
        };
        self.history
            .execute(&mut self.doc, DocCommand::AddElement { layer, element });
        self.needs_update = true;
    }

    /// The vertex tool: single click adds a point, double-click commits.
    fn handle_vertex_tool(&mut self, response: &egui::Response, ui: &egui::Ui) {
        if response.double_clicked_by(egui::PointerButton::Primary) {
            self.commit_poly_buff();
            return;
        }
        if response.clicked_by(egui::PointerButton::Primary)
            && let Some(p) = self.pointer_mm(ui)
        {
            self.poly_buff.push(p);
        }
    }

    /// The text tool: a canvas click opens the text dialog.
    fn handle_text_tool(&mut self, response: &egui::Response, ui: &egui::Ui) {
        if response.clicked_by(egui::PointerButton::Primary)
            && self.text_draft.is_none()
            && let Some(p) = self.pointer_mm(ui)
        {
            self.text_draft = Some(TextDraft {
                text: String::new(),
                size: "128".into(),
                at: p,
            });
        }
    }

    /// The edit tool: click near a point (Java: < 10 design units) selects
    /// it, dragging moves it, Del removes it. Mirrors the Java's
    /// `drawEditMode` + `deleteSelectedPoint`.
    fn handle_edit_tool(&mut self, response: &egui::Response, ui: &egui::Ui) {
        if response.drag_started_by(egui::PointerButton::Primary)
            && let Some(p) = self.pointer_mm(ui)
        {
            let layer = self.doc.current();
            let mut nearest: Option<(usize, usize, f32)> = None;
            for (ei, elt) in layer.elements.iter().enumerate() {
                for (pi, q) in elt.data.iter().enumerate() {
                    let d = q.dist(p);
                    if d < 10.0 && nearest.is_none_or(|(_, _, nd)| d < nd) {
                        nearest = Some((ei, pi, d));
                    }
                }
            }
            self.edit_sel = nearest.map(|(ei, pi, _)| (ei, pi));
        }

        // Dragging moves the selected point (not undoable — the Java moves it
        // directly in `drawEditMode`, only Del is a command). The stitched
        // preview is NOT refreshed per drag frame — edit mode shows the raw
        // elements live, and a per-frame re-stitch is the freeze on big
        // documents; the preview catches up when the drag stops.
        if response.dragged_by(egui::PointerButton::Primary)
            && let Some((ei, pi)) = self.edit_sel
            && let Some(p) = self.pointer_mm(ui)
        {
            let layer = self.doc.current_mut();
            if let Some(elt) = layer.elements.get_mut(ei)
                && let Some(q) = elt.data.get_mut(pi)
            {
                *q = p;
            }
        }
        if response.drag_stopped_by(egui::PointerButton::Primary) && self.edit_sel.is_some() {
            self.needs_update = true;
        }

        if ui.input(|i| i.key_pressed(egui::Key::Delete))
            && let Some((ei, pi)) = self.edit_sel
        {
            let layer = self.doc.current_layer;
            let elt = &self.doc.layers[layer].elements[ei];
            if elt.data.len() > 1 {
                let point = elt.data[pi];
                self.history.execute(
                    &mut self.doc,
                    DocCommand::RemovePoint {
                        layer,
                        element: ei,
                        index: pi,
                        point,
                    },
                );
                self.needs_update = true;
            }
            self.edit_sel = None;
        }
    }

    fn switch_tool(&mut self, tool: Tool) {
        self.tool = tool;
        self.poly_buff.clear();
        self.edit_sel = None;
    }

    fn add_layer(&mut self) {
        let n = self.doc.layers.len();
        let mut layer = Layer::new();
        layer.hatch_color = NEW_LAYER_COLORS[n % NEW_LAYER_COLORS.len()];
        self.doc.layers.push(layer);
        self.doc.current_layer = self.doc.layers.len() - 1;
        self.needs_update = true;
    }

    fn remove_layer(&mut self, i: usize) {
        if self.doc.layers.len() <= 1 {
            self.status = Some("Cannot delete the only layer".into());
            return;
        }
        if self.doc.current_layer == i {
            self.doc.current_layer = self.doc.current_layer.saturating_sub(1);
        }
        self.doc.layers.remove(i);
        if self.doc.current_layer >= self.doc.layers.len() {
            self.doc.current_layer = self.doc.layers.len() - 1;
        }
        self.edit_sel = None;
        self.needs_update = true;
    }

    fn draw_canvas(&mut self, ui: &mut egui::Ui) {
        let (response, painter) = ui.allocate_painter(ui.available_size(), Sense::click_and_drag());

        // Pan with the secondary button (the primary is the tools'), zoom
        // with the scroll wheel at the cursor.
        if response.dragged_by(egui::PointerButton::Secondary) {
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

        if self.needs_fit {
            let panel = (
                response.rect.min.x,
                response.rect.min.y,
                response.rect.max.x,
                response.rect.max.y,
            );
            let bounds = if self.infinite {
                // No hoop to fit: the drawn content (or a default area on an
                // empty document).
                self.doc.content_bounds().unwrap_or(emb_draw::Bounds {
                    min_x: 0.0,
                    min_y: 0.0,
                    max_x: 1000.0,
                    max_y: 1000.0,
                })
            } else {
                emb_draw::Bounds {
                    min_x: 0.0,
                    min_y: 0.0,
                    max_x: self.doc.width,
                    max_y: self.doc.height,
                }
            };
            self.viewport = Viewport::fit(bounds, panel);
            self.needs_fit = false;
        }

        match self.tool {
            Tool::Freehand | Tool::Paint | Tool::FatPaint | Tool::FineLine => {
                self.handle_draw_tool(&response, ui);
            }
            Tool::Vertex => self.handle_vertex_tool(&response, ui),
            Tool::Text => self.handle_text_tool(&response, ui),
            Tool::Edit => self.handle_edit_tool(&response, ui),
        }

        self.paint_canvas(&painter, response.rect);
    }

    fn paint_canvas(&self, painter: &egui::Painter, panel: Rect) {
        // Backdrop.
        painter.rect_filled(panel, 0.0, Color32::from_gray(60));

        // The canvas (hoop): white like the Java editor's background. The
        // infinite mode has no hoop — the content floats on the backdrop.
        if !self.infinite {
            let canvas = Rect::from_min_max(
                pos(self.viewport.mm_to_screen(0.0, 0.0)),
                pos(self.viewport.mm_to_screen(self.doc.width, self.doc.height)),
            );
            painter.rect_filled(canvas, 0.0, Color32::WHITE);
            painter.rect_stroke(
                canvas,
                0.0,
                Stroke::new(1.0, Color32::BLACK),
                egui::StrokeKind::Inside,
            );
        }

        let visible =
            self.viewport
                .visible_mm((panel.min.x, panel.min.y, panel.max.x, panel.max.y));

        if self.tool == Tool::Edit {
            // Edit mode shows the raw elements + point handles (the Java's
            // drawEditMode), so points are directly draggable.
            for layer in &self.doc.layers {
                if !layer.visible {
                    continue;
                }
                for elt in &layer.elements {
                    self.paint_element(painter, elt, Color32::BLACK, &visible);
                }
            }
        } else if let Some(draw_list) = &self.stitched {
            // The stitched preview: exactly what the save path will write,
            // rendered through the shared batched renderer (emb_egui: every
            // visible segment in ONE mesh per frame, viewport culling) —
            // the per-segment painter calls froze in proportion to the
            // stitches on screen.
            emb_egui::paint_draw_list(painter, &self.viewport, draw_list, panel);
            // TXT elements are not stitched (font rasteriser deferred, D003)
            // — the raw draft is drawn so the text tool gives feedback; the
            // save still skips them (named exception, docs/ROADMAP.md).
            for layer in &self.doc.layers {
                if !layer.visible {
                    continue;
                }
                for elt in &layer.elements {
                    if elt.kind == ElementKind::Text {
                        self.paint_element(
                            painter,
                            elt,
                            Color32::from_rgb(
                                ((layer.hatch_color >> 16) & 0xFF) as u8,
                                ((layer.hatch_color >> 8) & 0xFF) as u8,
                                (layer.hatch_color & 0xFF) as u8,
                            ),
                            &visible,
                        );
                    }
                }
            }
        }

        // The in-progress polyline (Java: the polyBuff preview on top).
        if !self.poly_buff.is_empty() {
            let stroke = Stroke::new(1.5, Color32::BLACK);
            for w in self.poly_buff.windows(2) {
                let a = pos(self.viewport.mm_to_screen(w[0].x, w[0].y));
                let b = pos(self.viewport.mm_to_screen(w[1].x, w[1].y));
                painter.line_segment([a, b], stroke);
            }
            for p in &self.poly_buff {
                let c = pos(self.viewport.mm_to_screen(p.x, p.y));
                painter.rect_filled(
                    Rect::from_center_size(c, egui::vec2(4.0, 4.0)),
                    0.0,
                    Color32::BLACK,
                );
            }
        }

        // Edit handles for the current layer (the Java's drawEditMode points).
        // Culled by the visible region: a big motif must not rasterise a
        // handle rect per point per frame when most points are off-view.
        if self.tool == Tool::Edit {
            let layer = self.doc.current();
            for (ei, elt) in layer.elements.iter().enumerate() {
                for (pi, p) in elt.data.iter().enumerate() {
                    if p.x < visible.min_x
                        || p.x > visible.max_x
                        || p.y < visible.min_y
                        || p.y > visible.max_y
                    {
                        continue;
                    }
                    let c = pos(self.viewport.mm_to_screen(p.x, p.y));
                    let selected = self.edit_sel == Some((ei, pi));
                    let size = if selected { 8.0 } else { 4.0 };
                    let color = if selected {
                        Color32::from_rgb(0, 0, 255)
                    } else {
                        Color32::from_gray(60)
                    };
                    painter.rect_filled(
                        Rect::from_center_size(c, egui::vec2(size, size)),
                        0.0,
                        color,
                    );
                }
            }
        }
    }

    fn paint_element(
        &self,
        painter: &egui::Painter,
        elt: &Element,
        color: Color32,
        visible: &emb_draw::Bounds,
    ) {
        let mut any_in_view = false;
        for p in &elt.data {
            if p.x >= visible.min_x
                && p.x <= visible.max_x
                && p.y >= visible.min_y
                && p.y <= visible.max_y
            {
                any_in_view = true;
                break;
            }
        }
        if !any_in_view && elt.data.len() > 1 {
            return;
        }

        match elt.kind {
            ElementKind::Line | ElementKind::Polygon => {
                let width = (elt.param_f0 * self.viewport.scale).max(1.0);
                let stroke = Stroke::new(width, color);
                for w in elt.data.windows(2) {
                    let a = pos(self.viewport.mm_to_screen(w[0].x, w[0].y));
                    let b = pos(self.viewport.mm_to_screen(w[1].x, w[1].y));
                    painter.line_segment([a, b], stroke);
                }
            }
            ElementKind::Text => {
                if let Some(p) = elt.data.first() {
                    let (x, y) = self.viewport.mm_to_screen(p.x, p.y);
                    painter.text(
                        Pos2::new(x, y),
                        egui::Align2::LEFT_TOP,
                        &elt.param_s0,
                        egui::FontId::proportional(elt.param_f0 * self.viewport.scale),
                        color,
                    );
                }
            }
        }
    }

    fn draw_toolbar(&mut self, ui: &mut egui::Ui) {
        for tool in TOOLS {
            let selected = self.tool == tool;
            if ui
                .selectable_label(selected, tool.label())
                .on_hover_text(tool.tooltip())
                .clicked()
            {
                self.switch_tool(tool);
            }
        }
        ui.separator();
        if ui
            .checkbox(&mut self.infinite, "Infinite")
            .on_hover_text(
                "Infinite canvas: no hoop — the view fits the content and the save centres on it",
            )
            .changed()
        {
            self.needs_fit = true;
        }
        if ui.button("Save").clicked() {
            self.save_dialog();
        }
    }

    /// The Java's `saveFile`: a save dialog with PES/SVG/DST filters, then
    /// `writeOut` (stitch → optimize → centre → write by extension). Returns
    /// whether a file was written (the exit dialog's "Save and quit" quits
    /// only on a successful save).
    fn save_dialog(&mut self) -> bool {
        let Some(path) = rfd::FileDialog::new()
            .add_filter("PES designs", &["pes"])
            .add_filter("SVG designs", &["svg"])
            .add_filter("DST designs", &["dst"])
            .set_file_name("design.pes")
            .save_file()
        else {
            return false;
        };
        let model = crate::stitch::stitch_document(&self.doc);
        if model.polylines.is_empty() {
            self.status = Some("Nothing to stitch yet — draw a polygon or a line first".into());
            return false;
        }
        // The Java's writeOut: optimize() then write. The preview skips the
        // TSP (stitch_document) — it runs here, once, at save.
        let mut model = model;
        model.optimize();
        let result = if self.infinite {
            crate::stitch::write_out_content_centered(&model, &path)
        } else {
            crate::stitch::write_out(&model, &path)
        };
        match result {
            Ok(()) => {
                self.status = Some(format!("Saved {}", path.display()));
                true
            }
            Err(e) => {
                self.status = Some(format!("Save failed: {e}"));
                false
            }
        }
    }

    fn draw_layer_panel(&mut self, ui: &mut egui::Ui) {
        // The thumbnails are cached: rebuilt only when the document changed
        // (the dirty flag that also re-stitches the preview — the Java
        // re-rasterizes them every frame in drawLayersGui).
        if self.needs_update {
            self.thumbnails.clear();
            for layer in &self.doc.layers {
                self.thumbnails
                    .push(crate::thumb::layer_thumbnail_texture(ui.ctx(), layer));
            }
        }
        let thumbs = &self.thumbnails;
        let mut remove: Option<usize> = None;
        let mut changed = false;
        for (i, layer) in self.doc.layers.iter_mut().enumerate() {
            let selected = i == self.doc.current_layer;
            ui.horizontal(|ui| {
                if let Some(texture) = thumbs.get(i) {
                    ui.image((texture.id(), egui::vec2(42.0, 42.0)));
                }
                if ui
                    .selectable_label(selected, format!("Layer {i}"))
                    .clicked()
                {
                    self.doc.current_layer = i;
                }
                changed |= ui.checkbox(&mut layer.visible, "show").changed();
                let mut c = [
                    ((layer.hatch_color >> 16) & 0xFF) as u8,
                    ((layer.hatch_color >> 8) & 0xFF) as u8,
                    (layer.hatch_color & 0xFF) as u8,
                ];
                if ui.color_edit_button_srgb(&mut c).changed() {
                    layer.hatch_color =
                        u32::from(c[0]) << 16 | u32::from(c[1]) << 8 | u32::from(c[2]);
                    changed = true;
                }
                changed |= ui
                    .add(
                        egui::DragValue::new(&mut layer.hatch_spacing)
                            .range(0.1..=100.0)
                            .speed(0.1)
                            .prefix("spacing "),
                    )
                    .changed();
                // The stroke settings entered with the LIN consumer (M5, the
                // M4 named exception): swatch + weight. The Java's stroke
                // mode row is PERPENDICULAR only (TANGENT deferred, D003);
                // the cull toggle stays deferred (raster compositing).
                let mut sc = [
                    ((layer.stroke_color >> 16) & 0xFF) as u8,
                    ((layer.stroke_color >> 8) & 0xFF) as u8,
                    (layer.stroke_color & 0xFF) as u8,
                ];
                if ui.color_edit_button_srgb(&mut sc).changed() {
                    layer.stroke_color =
                        u32::from(sc[0]) << 16 | u32::from(sc[1]) << 8 | u32::from(sc[2]);
                    changed = true;
                }
                changed |= ui
                    .add(
                        egui::DragValue::new(&mut layer.stroke_weight)
                            .range(1.0..=100.0)
                            .speed(0.1)
                            .prefix("stroke "),
                    )
                    .changed();
                if ui.button("X").clicked() {
                    remove = Some(i);
                }
            });
            // The Java's layer row also shows the stroke swatch, weight and
            // mode, and the cull toggle — they return with their consumers
            // (docs/ROADMAP.md, M4 named exceptions).
            ui.label(format!(
                "{} elements · {}",
                layer.elements.len(),
                match layer.hatch_mode {
                    HatchMode::Parallel => "parallel",
                }
            ));
        }
        if ui.button("+ Add layer").clicked() {
            self.add_layer();
        }
        if let Some(i) = remove {
            self.remove_layer(i);
        }
        if changed {
            self.needs_update = true;
        }
    }

    fn draw_text_dialog(&mut self, ctx: &egui::Context) {
        let Some(draft) = &mut self.text_draft else {
            return;
        };
        let mut open = true;
        let mut cancel = false;
        let mut commit = false;
        egui::Window::new("Text")
            .open(&mut open)
            .collapsible(false)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.label("Text");
                    ui.text_edit_singleline(&mut draft.text);
                });
                ui.horizontal(|ui| {
                    ui.label("Size");
                    ui.text_edit_singleline(&mut draft.size);
                });
                ui.horizontal(|ui| {
                    if ui.button("OK").clicked() {
                        commit = true;
                    }
                    if ui.button("Cancel").clicked() {
                        cancel = true;
                    }
                });
            });
        if cancel {
            open = false;
        }
        if commit && !draft.text.is_empty() {
            let size = draft.size.trim().parse::<f32>().unwrap_or(128.0);
            let at = draft.at;
            let element = Element::text(draft.text.clone(), size, at);
            let layer = self.doc.current_layer;
            self.history
                .execute(&mut self.doc, DocCommand::AddElement { layer, element });
            self.needs_update = true;
            open = false;
        }
        if !open {
            self.text_draft = None;
        }
    }
}

/// `(f32, f32) -> Pos2` for the viewport's tuple results.
fn pos(p: (f32, f32)) -> Pos2 {
    Pos2::new(p.0, p.1)
}

impl eframe::App for EditorApp {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();

        // The Java's exit dialog (Main.java:875-899): save-and-quit /
        // exit-without-save / cancel on window close. "Save and quit" quits
        // only when the save actually wrote a file (the Java's cancelled
        // save stays in the app).
        use emb_egui::exit_dialog::{ExitChoice, ExitDialog, ExitLabels};
        let labels = ExitLabels {
            question: "Save the design before quitting?",
            save_and_quit: "Save and quit",
            exit_without_save: "Exit without saving",
            cancel: "Cancel",
        };
        match self.exit_dialog.frame(&ctx, &labels) {
            Some(ExitChoice::SaveAndQuit) => {
                if self.save_dialog() {
                    ExitDialog::request_close(&ctx);
                }
            }
            Some(ExitChoice::Quit) => ExitDialog::request_close(&ctx),
            _ => {}
        }

        // Undo/redo: Ctrl+Z / Ctrl+Y (the Java's keyPressed).
        let (undo, redo) = ctx.input(|i| {
            let ctrl = i.modifiers.command;
            let z = i.key_pressed(egui::Key::Z);
            let y = i.key_pressed(egui::Key::Y);
            let shift = i.modifiers.shift;
            (ctrl && z && !shift, ctrl && (y || (z && shift)))
        });
        if undo && self.history.undo(&mut self.doc) {
            self.needs_update = true;
        }
        if redo && self.history.redo(&mut self.doc) {
            self.needs_update = true;
        }

        // Escape removes the current layer's last element (the Java's
        // `removeElementFromPolyBuff`, reached through the escape menu).
        if ctx.input(|i| i.key_pressed(egui::Key::Escape)) {
            let layer = self.doc.current_layer;
            if let Some(element) = self.doc.layers[layer].elements.last().cloned() {
                let index = self.doc.layers[layer].elements.len() - 1;
                self.history.execute(
                    &mut self.doc,
                    DocCommand::RemoveElement {
                        layer,
                        index,
                        element,
                    },
                );
                self.needs_update = true;
            }
        }

        egui::Panel::left(egui::Id::new("tools"))
            .exact_size(44.0)
            .show(ui, |ui| {
                self.draw_toolbar(ui);
                ui.separator();
                ui.label(format!("{:.0} px/mm", self.viewport.scale));
            });

        egui::Panel::right(egui::Id::new("layers"))
            .default_size(220.0)
            .show(ui, |ui| {
                ui.heading("Layers");
                self.draw_layer_panel(ui);
                ui.separator();
                ui.label(format!(
                    "Undo: {} · Redo: {}",
                    self.history.can_undo(),
                    self.history.can_redo()
                ));
            });

        egui::Panel::bottom(egui::Id::new("status")).show(ui, |ui| {
            ui.label(self.tool.tooltip());
            if let Some(status) = &self.status {
                ui.colored_label(Color32::YELLOW, status);
            }
        });

        egui::CentralPanel::default_margins().show(ui, |ui| {
            self.refresh_stitched();
            self.draw_canvas(ui);
        });

        self.draw_text_dialog(&ctx);
    }
}
