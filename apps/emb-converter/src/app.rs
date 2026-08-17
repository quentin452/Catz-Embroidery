//! The converter app: the Java converter's control surface (Main.java
//! setupGUI) over the shared pipeline (emb_model::convert). The canvas shows
//! the source image with the stitched preview overlaid through the shared
//! draw list (D001: every frontend renders the same list).
//!
//! Every knob change re-converts (the Java's refreshPreview). The export
//! mirrors fileSaved: optimize() → PEmbroiderWriter.write with the export mm
//! — the design is centred on the hoop, never scaled (D005 notes this).

use eframe::egui::{self, Color32, Pos2, Rect, Sense, Stroke};

use emb_draw::{DrawList, Viewport};
use emb_model::convert::{ColorMode, ConvertParams, HatchMode};

use crate::convert::WORK_SIZE;

/// The Java's default export size (Main.java:41-42) and its title rule.
const DEFAULT_EXPORT_MM: f32 = 95.0;

const PERLIN_DEFERRED: &str = "PERLIN is deferred (D003/D005): the Java walks app.noise through a Java2D raster, \
     seeded per run — its output is unreproducible, and no consumer demands it yet.";

fn hatch_label(mode: HatchMode) -> &'static str {
    match mode {
        HatchMode::Cross => "CROSS",
        HatchMode::Parallel => "PARALLEL",
        HatchMode::Concentric => "CONCENTRIC",
        HatchMode::Spiral => "SPIRAL",
        HatchMode::Perlin => "PERLIN",
    }
}

fn color_label(mode: ColorMode) -> &'static str {
    match mode {
        ColorMode::MultiColor => "MultiColor",
        ColorMode::BlackAndWhite => "BlackAndWhite",
        ColorMode::Realistic => "Realistic",
    }
}

fn pos((x, y): (f32, f32)) -> Pos2 {
    Pos2::new(x, y)
}

pub struct ConverterApp {
    ctx: egui::Context,
    /// The loaded source, resized to the pipeline's work size.
    source: Option<Source>,
    /// The stitched preview: the model (recomputed on the dirty flag) and its
    /// draw list (D001: one emission, every renderer).
    result: Option<Result<emb_model::model::Model, String>>,
    preview: Option<DrawList>,
    image_texture: Option<egui::TextureHandle>,
    needs_update: bool,
    needs_fit: bool,
    viewport: Viewport,
    params: ConvertParams,
    export_width: f32,
    export_height: f32,
    status: Option<String>,
    /// The Java's `showPreview`: the P key toggles the stitched overlay
    /// (Main.java:394-396).
    show_preview: bool,
    /// The Java's exit dialog (DialogUtil.showExitDialog): save-and-quit /
    /// exit-without-save / cancel on window close.
    exit_dialog: emb_egui::exit_dialog::ExitDialog,
    /// A conversion is running on a background thread (the Java's
    /// `processImageWithProgress`); the knobs are disabled while it does and
    /// the status bar shows `progress`.
    converting: bool,
    progress: f32,
    convert_rx: Option<std::sync::mpsc::Receiver<ConvertMsg>>,
}

/// The background conversion's messages to the UI thread (the Java's
/// `updateProgress` + `SwingUtilities.invokeLater`).
enum ConvertMsg {
    /// 0..1 through the pipeline stages.
    Progress(f32),
    /// The finished model, or the error string.
    Done(Result<emb_model::model::Model, String>),
}

struct Source {
    /// RGBA8 at WORK_SIZE x WORK_SIZE.
    pixels: Vec<u8>,
}

impl ConverterApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        cc.egui_ctx.set_visuals(egui::Visuals::dark());
        Self {
            ctx: cc.egui_ctx.clone(),
            source: None,
            result: None,
            preview: None,
            image_texture: None,
            needs_update: false,
            needs_fit: true,
            viewport: Viewport::new(1.0, 0.0, 0.0),
            params: ConvertParams::default(),
            export_width: DEFAULT_EXPORT_MM,
            export_height: DEFAULT_EXPORT_MM,
            status: None,
            show_preview: true,
            exit_dialog: emb_egui::exit_dialog::ExitDialog::new(),
            converting: false,
            progress: 0.0,
            convert_rx: None,
        }
    }

    fn set_source(&mut self, pixels: Vec<u8>, name: String) {
        let size = [WORK_SIZE as usize, WORK_SIZE as usize];
        let color = egui::ColorImage::from_rgba_unmultiplied(size, &pixels);
        self.image_texture = Some(self.ctx.load_texture(
            &name,
            color,
            egui::TextureOptions::LINEAR,
        ));
        self.source = Some(Source { pixels });
        self.needs_update = true;
        self.needs_fit = true;
        self.status = Some(format!("Loaded {name}"));
    }

    /// Load a source file (the load button, the drag-drop and the CLI arg
    /// all land here): an image (jpg/png/jpeg/bmp/gif) or a `.pes` design,
    /// which is rasterized back into the pipeline's work image (the Java's
    /// `imageSelected` PES branch).
    pub fn load_file(&mut self, path: &std::path::Path) {
        let is_pes = path
            .extension()
            .map(|e| e.to_string_lossy().eq_ignore_ascii_case("pes"))
            .unwrap_or(false);
        let result = if is_pes {
            crate::convert::load_design_pixels(path)
        } else {
            crate::convert::load_image(path)
        };
        match result {
            Ok(pixels) => self.set_source(pixels, path.display().to_string()),
            Err(e) => self.status = Some(e),
        }
    }

    /// Ctrl+V: paste an image from the clipboard (the Java's ApplicationUtil
    /// paste). arboard delivers BGRA8; the pipeline wants RGBA8.
    fn load_clipboard(&mut self) {
        let mut clipboard = match arboard::Clipboard::new() {
            Ok(c) => c,
            Err(e) => {
                self.status = Some(format!("clipboard: {e}"));
                return;
            }
        };
        let img = match clipboard.get_image() {
            Ok(img) => img,
            Err(e) => {
                self.status = Some(format!("clipboard has no image: {e}"));
                return;
            }
        };
        let mut rgba = Vec::with_capacity(img.bytes.len());
        for bgra in img.bytes.chunks_exact(4) {
            rgba.extend_from_slice(&[bgra[2], bgra[1], bgra[0], bgra[3]]);
        }
        let dynamic = image::DynamicImage::ImageRgba8(
            match image::RgbaImage::from_raw(img.width as u32, img.height as u32, rgba) {
                Some(img) => img,
                None => {
                    self.status = Some("clipboard image has a bad pixel count".into());
                    return;
                }
            },
        );
        let pixels = crate::convert::to_work_rgba(&dynamic);
        self.set_source(pixels, "clipboard".into());
    }

    fn handle_dropped_files(&mut self, ctx: &egui::Context) {
        let dropped: Vec<std::path::PathBuf> = ctx.input(|i| {
            i.raw
                .dropped_files
                .iter()
                .filter_map(|f| f.path.clone())
                .collect()
        });
        if let Some(path) = dropped.into_iter().next() {
            self.load_file(&path);
        }
        if ctx.input(|i| i.modifiers.command && i.key_pressed(egui::Key::V)) {
            self.load_clipboard();
        }
    }

    /// Re-convert when the knobs changed (the Java's refreshPreview), on a
    /// background thread so a heavy stroke never freezes the window — the
    /// Java's `processImageWithProgress` (thread + `updateProgress`). If a
    /// conversion is already running, the dirty flag stays set and the next
    /// completion re-runs with the newest params.
    fn refresh(&mut self) {
        if self.converting || !self.needs_update {
            return;
        }
        let Some(src) = &self.source else { return };
        self.converting = true;
        self.progress = 0.0;
        self.needs_update = false;
        let pixels = src.pixels.clone();
        let params = self.params;
        let (tx, rx) = std::sync::mpsc::channel();
        self.convert_rx = Some(rx);
        let ctx = self.ctx.clone();
        std::thread::spawn(move || {
            let result = emb_model::convert::convert_image_with_progress(
                &pixels,
                WORK_SIZE as usize,
                WORK_SIZE as usize,
                &params,
                &mut |p| {
                    if tx.send(ConvertMsg::Progress(p)).is_ok() {
                        ctx.request_repaint();
                    }
                },
            );
            let _ = tx.send(ConvertMsg::Done(result.map_err(|e| e.to_string())));
            ctx.request_repaint();
        });
    }

    /// Drain the conversion channel: progress ticks move the bar; the done
    /// message lands the model and its draw list.
    fn poll_conversion(&mut self) {
        let Some(rx) = &self.convert_rx else { return };
        let mut done = None;
        while let Ok(msg) = rx.try_recv() {
            match msg {
                ConvertMsg::Progress(p) => self.progress = p,
                ConvertMsg::Done(res) => done = Some(res),
            }
        }
        if let Some(res) = done {
            self.converting = false;
            self.convert_rx = None;
            let preview = res.as_ref().ok().map(DrawList::from_model);
            self.result = Some(res);
            self.preview = preview;
        }
    }

    /// The Java's fileSaved: TSP-optimize, then the writer with the export mm
    /// (centred, not scaled). Returns whether the design was written (the
    /// exit dialog's "Save and quit" quits only on a successful save).
    fn save_dialog(&mut self) -> bool {
        let Some(model) = self.result.as_ref().and_then(|r| r.as_ref().ok()) else {
            self.status = Some("Nothing to save — load an image first".into());
            return false;
        };
        if model.polylines.is_empty() {
            self.status = Some("Nothing to stitch — the image converted to an empty design".into());
            return false;
        }
        let Some(path) = rfd::FileDialog::new()
            .add_filter("PES designs", &["pes"])
            .add_filter("SVG designs", &["svg"])
            .add_filter("DST designs", &["dst"])
            .set_file_name("design.pes")
            .save_file()
        else {
            return false;
        };
        let mut model = model.clone();
        model.optimize();
        let title = emb_data::file_title(&path);
        let design = model.centered_design(&title, self.export_width, self.export_height);
        match emb_data::write_design(&path, &design) {
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

    fn load_dialog(&mut self) {
        let Some(path) = rfd::FileDialog::new()
            .add_filter(
                "Images (jpg/png/bmp/gif) and PES designs",
                &["jpg", "jpeg", "png", "bmp", "gif", "pes"],
            )
            .pick_file()
        else {
            return;
        };
        self.load_file(&path);
    }

    fn draw_controls(&mut self, ui: &mut egui::Ui) {
        let mut changed = false;
        // The Java's setComponentsEnabled(false) while converting: the knobs
        // are locked so the running conversion's params cannot be torn out
        // from under it.
        ui.add_enabled_ui(!self.converting, |ui| {
            ui.horizontal(|ui| {
                if ui.button("Load image").on_hover_text("Open a jpg/png/jpeg/bmp/gif or a .pes design").clicked() {
                    self.load_dialog();
                }
                if ui.button("Save").on_hover_text("Export the stitched design (PES/DST/SVG)").clicked() {
                    self.save_dialog();
                }
                ui.separator();
                if ui
                    .checkbox(&mut self.params.fill, "Fill mode")
                    .on_hover_text("Off: outline only (the PERPENDICULAR stroke of the contours). On: outline + hatch fill.")
                    .changed()
                {
                    changed = true;
                }
                if ui
                    .checkbox(&mut self.params.invert, "Invert")
                    .on_hover_text("Rust-only knob (the Java has none): stitch the dark pixels instead of the bright ones — for photos with a dark subject on a bright background, whose parity mask converts the background blob.")
                    .changed()
                {
                    changed = true;
                }
                ui.separator();
                ui.label("Hatch:");
                egui::ComboBox::from_id_salt("hatch_mode")
                    .selected_text(hatch_label(self.params.hatch_mode))
                    .show_ui(ui, |ui| {
                        for mode in [
                            HatchMode::Cross,
                            HatchMode::Parallel,
                            HatchMode::Concentric,
                            HatchMode::Spiral,
                        ] {
                            if ui.selectable_value(&mut self.params.hatch_mode, mode, hatch_label(mode)).changed()
                            {
                                changed = true;
                            }
                        }
                        ui.add_enabled_ui(false, |ui| {
                            ui.selectable_label(false, hatch_label(HatchMode::Perlin))
                                .on_hover_text(PERLIN_DEFERRED);
                        });
                    });
                ui.label("Color:");
                egui::ComboBox::from_id_salt("color_mode")
                    .selected_text(color_label(self.params.color_mode))
                    .show_ui(ui, |ui| {
                        for mode in [
                            ColorMode::MultiColor,
                            ColorMode::BlackAndWhite,
                            ColorMode::Realistic,
                        ] {
                            if ui.selectable_value(&mut self.params.color_mode, mode, color_label(mode)).changed()
                            {
                                changed = true;
                            }
                        }
                    });
            });
            ui.horizontal(|ui| {
                if ui
                    .add(
                        egui::DragValue::new(&mut self.params.spacing)
                            .range(0.1..=1000.0)
                            .prefix("Spacing: "),
                    )
                    .changed()
                {
                    changed = true;
                }
                if ui
                    .add(
                        egui::DragValue::new(&mut self.params.stroke_weight)
                            .range(1.0..=64.0)
                            .prefix("Stroke weight: "),
                    )
                    .on_hover_text(
                        "The PERPENDICULAR stroke's sample count grows with the weight² (the Java's ray fans) — capped at 64 px so a drag cannot freeze the UI for seconds.",
                    )
                    .changed()
                {
                    changed = true;
                }
                if ui
                    .add(
                        egui::DragValue::new(&mut self.params.max_colors)
                            .range(1..=256)
                            .prefix("Max colors: "),
                    )
                    .changed()
                {
                    changed = true;
                }
                ui.separator();
                if ui
                    .add(
                        egui::DragValue::new(&mut self.export_width)
                            .range(1.0..=500.0)
                            .prefix("Export width mm: "),
                    )
                    .changed()
                {
                    changed = true;
                }
                if ui
                    .add(
                        egui::DragValue::new(&mut self.export_height)
                            .range(1.0..=500.0)
                            .prefix("Export height mm: "),
                    )
                    .changed()
                {
                    changed = true;
                }
            });
        });
        if changed {
            self.needs_update = true;
        }
    }

    fn draw_canvas(&mut self, ui: &mut egui::Ui) {
        let (response, painter) = ui.allocate_painter(ui.available_size(), Sense::click_and_drag());

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
            self.viewport = Viewport::fit(
                emb_draw::Bounds {
                    min_x: 0.0,
                    min_y: 0.0,
                    max_x: WORK_SIZE as f32,
                    max_y: WORK_SIZE as f32,
                },
                panel,
            );
            self.needs_fit = false;
        }

        self.paint_canvas(&painter, response.rect);
    }

    fn paint_canvas(&self, painter: &egui::Painter, panel: Rect) {
        painter.rect_filled(panel, 0.0, Color32::from_gray(60));

        let canvas = Rect::from_min_max(
            pos(self.viewport.mm_to_screen(0.0, 0.0)),
            pos(self
                .viewport
                .mm_to_screen(WORK_SIZE as f32, WORK_SIZE as f32)),
        );
        painter.rect_filled(canvas, 0.0, Color32::WHITE);
        painter.rect_stroke(
            canvas,
            0.0,
            Stroke::new(1.0, Color32::BLACK),
            egui::StrokeKind::Inside,
        );

        // The source image under the stitches (the Java draws the image at
        // (860, 70) then the stitched design over it; here both share the
        // 0..WORK_SIZE design space, the offset being export-irrelevant).
        if let Some(texture) = &self.image_texture {
            painter.image(
                texture.id(),
                canvas,
                Rect::from_min_max(Pos2::new(0.0, 0.0), Pos2::new(1.0, 1.0)),
                Color32::WHITE,
            );
        }

        // The stitched preview: exactly what the save path will write,
        // through the shared batched renderer (emb_egui: every visible
        // segment in ONE mesh per frame, viewport culling) — the per-segment
        // painter calls froze in proportion to the stitches on screen. The
        // P key toggles it (the Java's `showPreview`).
        if self.show_preview
            && let Some(draw_list) = &self.preview
        {
            emb_egui::paint_draw_list(painter, &self.viewport, draw_list, panel);
        }
    }
}

impl eframe::App for ConverterApp {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        self.handle_dropped_files(&ctx);

        // The Java's P key (Main.java:394-396): toggle the stitched overlay.
        if ctx.input(|i| i.key_pressed(egui::Key::P)) {
            self.show_preview = !self.show_preview;
        }

        // The Java's exit dialog (Main.java:412-436): save-and-quit /
        // exit-without-save / cancel on window close.
        use emb_egui::exit_dialog::{ExitChoice, ExitLabels};
        let labels = ExitLabels {
            question: "Save the design before quitting?",
            save_and_quit: "Save and quit",
            exit_without_save: "Exit without saving",
            cancel: "Cancel",
        };
        match self.exit_dialog.frame(&ctx, &labels) {
            Some(ExitChoice::SaveAndQuit) => {
                if self.save_dialog() {
                    self.exit_dialog.request_close(&ctx);
                }
            }
            Some(ExitChoice::Quit) => self.exit_dialog.request_close(&ctx),
            _ => {}
        }

        self.poll_conversion();
        self.refresh();
        egui::Panel::top(egui::Id::new("controls")).show(ui, |ui| self.draw_controls(ui));
        egui::Panel::bottom(egui::Id::new("status")).show(ui, |ui| {
            if self.converting {
                ui.horizontal(|ui| {
                    ui.label("Converting…");
                    ui.add(egui::ProgressBar::new(self.progress).desired_width(300.0));
                });
            }
            if let Some(s) = &self.status {
                ui.colored_label(Color32::YELLOW, s);
            }
        });
        egui::CentralPanel::default_margins().show(ui, |ui| self.draw_canvas(ui));
    }
}
