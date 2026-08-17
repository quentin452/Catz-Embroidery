#![forbid(unsafe_code)]
//! The image → embroidery converter (egui/eframe, glow backend — docs/decisions/D001.md).
//! M5 slice 1 (docs/ROADMAP.md): load/drag-drop/clipboard, the 5 hatch modes
//! (PERLIN disabled — D005), the 3 color modes, the fill toggle, mm export
//! settings, the stitched preview, PES/DST/SVG export.

mod app;
mod convert;

use eframe::egui;

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport: egui::ViewportBuilder::default()
            .with_title("Catz Embroidery — converter")
            .with_inner_size([1280.0, 720.0]),
        ..Default::default()
    };
    eframe::run_native(
        "emb-converter",
        options,
        Box::new(|cc| {
            let mut app = app::ConverterApp::new(cc);
            // An optional first CLI arg loads a source at startup (an image
            // or a .pes design; the Java converter has no equivalent — a
            // convenience for testing).
            if let Some(path) = std::env::args().nth(1) {
                app.load_file(std::path::Path::new(&path));
            }
            Ok(Box::new(app))
        }),
    )
}
