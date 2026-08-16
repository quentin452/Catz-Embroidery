#![forbid(unsafe_code)]
//! The interactive embroidery editor (egui/eframe, glow backend — docs/decisions/D001.md).
//! M4: layers of elements drawn with tools, edited with the edit tool, undo/redo.
//! The stitch path and save land in M4 slice 2 (docs/ROADMAP.md).

mod app;
mod doc;
mod stitch;

use eframe::egui;

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport: egui::ViewportBuilder::default()
            .with_title("Catz Embroidery — editor")
            .with_inner_size([1280.0, 800.0]),
        ..Default::default()
    };
    eframe::run_native(
        "emb-editor",
        options,
        Box::new(|cc| Ok(Box::new(app::EditorApp::new(cc)))),
    )
}
