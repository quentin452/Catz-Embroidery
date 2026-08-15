#![forbid(unsafe_code)]
//! The design viewer (egui/eframe, glow backend — docs/decisions/D001.md).
//! M3: renders a real design loaded from a PES file via the shared draw list.

mod app;
mod overview;
mod view;

use eframe::egui;

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport: egui::ViewportBuilder::default()
            .with_title("Catz Embroidery — viewer")
            .with_inner_size([1280.0, 800.0]),
        ..Default::default()
    };
    eframe::run_native(
        "emb-viewer",
        options,
        Box::new(|cc| Ok(Box::new(app::ViewerApp::new(cc)))),
    )
}
