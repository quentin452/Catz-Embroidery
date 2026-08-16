//! The viewer app: opens a PES file, renders it via the shared draw list.

use eframe::egui::{self, Align, Layout, RichText};

use emb_draw::DrawList;
use emb_model::model::Model;

use crate::overview::OverviewTexture;
use crate::view::RenderView;

pub struct ViewerApp {
    design: Option<LoadedDesign>,
    status: Option<String>,
    render: RenderView,
    /// Fit the design to the panel once on load (needs the panel rect).
    needs_fit: bool,
}

struct LoadedDesign {
    draw_list: DrawList,
    overview: OverviewTexture,
    source: String,
    stitched_mm: (f32, f32),
}

impl ViewerApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        cc.egui_ctx.set_visuals(egui::Visuals::dark());
        Self {
            design: None,
            status: None,
            render: RenderView::new(),
            needs_fit: false,
        }
    }

    fn load_file(&mut self, ctx: &egui::Context, path: &std::path::Path) {
        match std::fs::read(path) {
            Ok(bytes) => match Model::from_pes(&bytes) {
                Ok(model) => {
                    let stitched_mm = design_size(&model);
                    let draw_list = DrawList::from_model(&model);
                    let overview = OverviewTexture::new(ctx, &draw_list);
                    self.design = Some(LoadedDesign {
                        draw_list,
                        overview,
                        source: path.display().to_string(),
                        stitched_mm,
                    });
                    self.needs_fit = true;
                    self.status = None;
                }
                Err(e) => self.status = Some(format!("{}: {e}", path.display())),
            },
            Err(e) => self.status = Some(format!("{}: {e}", path.display())),
        }
    }
}

fn design_size(model: &Model) -> (f32, f32) {
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
    if min_x.is_infinite() {
        (0.0, 0.0)
    } else {
        (max_x - min_x, max_y - min_y)
    }
}

impl eframe::App for ViewerApp {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();

        // Drag-drop: a dropped .pes loads directly.
        let dropped: Vec<std::path::PathBuf> = ctx.input(|i| {
            i.raw
                .dropped_files
                .iter()
                .filter_map(|f| f.path.clone())
                .collect()
        });
        if let Some(path) = dropped.first() {
            self.load_file(&ctx, path);
        }

        egui::Panel::top(egui::Id::new("bar")).show(ui, |ui| {
            ui.horizontal(|ui| {
                if ui.button("Open…").clicked()
                    && let Some(path) = rfd::FileDialog::new()
                        .add_filter("PES designs", &["pes"])
                        .pick_file()
                {
                    self.load_file(&ctx, &path);
                }
                if let Some(d) = &self.design {
                    ui.separator();
                    ui.label(RichText::new(&d.source).weak());
                    ui.separator();
                    ui.label(format!(
                        "{} polylines · {}×{} mm stitched",
                        d.draw_list.items.len() - 1,
                        d.stitched_mm.0,
                        d.stitched_mm.1
                    ));
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if ui.button("Fit").clicked() {
                            self.needs_fit = true;
                        }
                        ui.label(format!("{:.1} px/mm", self.render.viewport.scale));
                    });
                }
            });
        });

        egui::CentralPanel::default_margins().show(ui, |ui| {
            if let Some(d) = &self.design {
                if self.needs_fit {
                    // Fit on the stitched motif, not the declared canvas: a
                    // design whose stitches sit far from the canvas (offsets,
                    // odd hoops) must still be centred on what is drawn.
                    let panel = ui.available_rect_before_wrap();
                    self.render.viewport = emb_draw::Viewport::fit(
                        d.draw_list.content_bounds(),
                        (panel.min.x, panel.min.y, panel.max.x, panel.max.y),
                    );
                    self.needs_fit = false;
                }
                self.render.ui(ui, &d.draw_list, Some(&d.overview));
            } else {
                ui.centered_and_justified(|ui| {
                    ui.label(RichText::new("Open a .pes file (button or drag-drop)").weak());
                });
            }
        });

        if let Some(status) = &self.status {
            let mut open = true;
            egui::Window::new("Load error")
                .open(&mut open)
                .collapsible(false)
                .show(&ctx, |ui| {
                    ui.label(status);
                });
            if !open {
                self.status = None;
            }
        }
    }
}
