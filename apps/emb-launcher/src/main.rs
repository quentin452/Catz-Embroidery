#![forbid(unsafe_code)]
//! The hub: launches the suite's apps (egui/eframe, glow backend — docs/decisions/D001.md).
//! A thin menu, no model knowledge (matrix.toml: `allow = []`).
//!
//! Port of `PEmbroiderLauncher.java`: the three app buttons, the en/fr
//! language dropdown, the version line, and the GitHub update check (this
//! app's `update` module — the Java's `Updater`, checked on a background
//! thread at startup). The Dropbox connect (the Java's button with its
//! green/red status) is not ported — named exception, queued in
//! docs/ROADMAP.md (the "Dropbox save" converter follow-up is the same
//! feature family). The apps are native exes: the launcher spawns its
//! siblings (the Java launched them in-process via reflection).

mod app;
mod update;

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
