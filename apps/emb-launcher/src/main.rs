#![forbid(unsafe_code)]
//! The hub: launches the suite's apps (egui/eframe, glow backend — docs/decisions/D001.md).
//! A thin menu, no model knowledge (matrix.toml: `allow = []`).
//!
//! Port of `PEmbroiderLauncher.java` minus the parts nothing consumes yet:
//! the GitHub update check (no releases exist; queued with the packaging
//! phase) and the Dropbox connect (the save paths are local-only — queued in
//! docs/ROADMAP.md). The apps are native exes: the launcher spawns its
//! siblings (the Java launched them in-process via reflection).
//!
//! The infinite-draw app is a skeleton (M5); its button lands when it does.

mod app;

use eframe::egui;

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport: egui::ViewportBuilder::default()
            .with_title("Catz Embroidery — launcher")
            .with_inner_size([800.0, 600.0]),
        ..Default::default()
    };
    eframe::run_native(
        "emb-launcher",
        options,
        Box::new(|cc| Ok(Box::new(app::LauncherApp::new(cc)))),
    )
}
