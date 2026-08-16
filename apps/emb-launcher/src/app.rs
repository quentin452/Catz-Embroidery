//! The launcher UI: the Java's three buttons (editor, converter, viewer —
//! the Java's viewer was a TODO, ours exists) + the language dropdown
//! (en/fr, the Java's Translator) + the version line. Each button spawns
//! the sibling exe next to the launcher (`target/debug/` in dev, the
//! install dir when packaged). The update check (the Java's
//! `checkForUpdates` at startup) runs once on a background thread and pops
//! a modal dialog when a newer release exists.

use eframe::egui::{self, Color32};
use std::sync::mpsc;

use crate::update;

/// The launcher's strings, per language (the Java's Translator, scoped to
/// what the launcher says).
struct Strings {
    choose_app: &'static str,
    launch_editor: &'static str,
    launch_converter: &'static str,
    launch_viewer: &'static str,
    version: &'static str,
    language: &'static str,
    not_found: &'static str,
    update_available: &'static str,
    open_releases: &'static str,
    later: &'static str,
}

const EN: Strings = Strings {
    choose_app: "Choose an app",
    launch_editor: "Launch editor",
    launch_converter: "Launch converter",
    launch_viewer: "Launch viewer",
    version: "Version ",
    language: "Language",
    not_found: "not found next to the launcher — build the workspace first",
    update_available: "A new version is available:",
    open_releases: "Open the releases page",
    later: "Later",
};

const FR: Strings = Strings {
    choose_app: "Choisissez une application",
    launch_editor: "Lancer l'éditeur",
    launch_converter: "Lancer le convertisseur",
    launch_viewer: "Lancer le visualiseur",
    version: "Version ",
    language: "Langue",
    not_found: "introuvable à côté du lanceur — compilez le workspace d'abord",
    update_available: "Une nouvelle version est disponible :",
    open_releases: "Ouvrir la page des releases",
    later: "Plus tard",
};

/// The launch targets: the sibling exe names (the Java's reflection `main`
/// calls; the launcher spawns processes instead).
type AppEntry = (&'static str, fn(&Strings) -> &'static str);
const APPS: [AppEntry; 3] = [
    ("emb-editor", |s| s.launch_editor),
    ("emb-converter", |s| s.launch_converter),
    ("emb-viewer", |s| s.launch_viewer),
];

/// The one-shot update check outcome (the Java's `checkForUpdates` +
/// `JOptionPane`).
enum UpdateState {
    /// The background thread has not answered yet (the Java shows nothing
    /// while checking).
    Checking,
    UpToDate,
    Outdated {
        latest: String,
    },
    /// Network or response error; shown as a footer line, never a popup
    /// (the Java only logs).
    Failed(String),
}

pub struct LauncherApp {
    strings: &'static Strings,
    status: Option<String>,
    update: UpdateState,
    update_rx: mpsc::Receiver<Result<String, String>>,
}

impl LauncherApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        cc.egui_ctx.set_visuals(egui::Visuals::dark());
        let (tx, rx) = mpsc::channel();
        let ctx = cc.egui_ctx.clone();
        // The Java runs the check on a thread (the UI must not block on the
        // network); the dialog lands when the reply does.
        std::thread::spawn(move || {
            let result = update::fetch_latest_version();
            ctx.request_repaint();
            let _ = tx.send(result);
        });
        Self {
            strings: &EN,
            status: None,
            update: UpdateState::Checking,
            update_rx: rx,
        }
    }

    /// Spawn the sibling exe (the Java's `runApplication`, as a process).
    fn launch(&mut self, name: &str) {
        let exe = std::env::current_exe()
            .ok()
            .and_then(|p| p.parent().map(|d| d.to_path_buf()))
            .map(|dir| dir.join(format!("{name}{}", std::env::consts::EXE_SUFFIX)));
        let Some(exe) = exe else {
            self.status = Some(self.strings.not_found.into());
            return;
        };
        if !exe.exists() {
            self.status = Some(format!("{name}{}", self.strings.not_found));
            return;
        }
        match std::process::Command::new(&exe).spawn() {
            Ok(_) => self.status = Some(format!("{name} launched")),
            Err(e) => self.status = Some(format!("cannot launch {name}: {e}")),
        }
    }
}

impl eframe::App for LauncherApp {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        if let Ok(result) = self.update_rx.try_recv() {
            self.update = match result {
                Ok(latest) if update::is_outdated(env!("CARGO_PKG_VERSION"), &latest) => {
                    UpdateState::Outdated { latest }
                }
                Ok(_) => UpdateState::UpToDate,
                Err(e) => UpdateState::Failed(e),
            };
        }

        // The Java's JOptionPane, as a modal: offer the releases page once,
        // then go quiet either way.
        if let UpdateState::Outdated { latest } = &self.update {
            let mut done = false;
            egui::Modal::new(egui::Id::new("update_modal")).show(ui.ctx(), |ui| {
                ui.label(format!("{} {}", self.strings.update_available, latest));
                ui.add_space(8.0);
                ui.horizontal(|ui| {
                    if ui.button(self.strings.open_releases).clicked() {
                        update::open_releases_page();
                        done = true;
                    }
                    if ui.button(self.strings.later).clicked() {
                        done = true;
                    }
                });
            });
            if done {
                self.update = UpdateState::UpToDate;
            }
        }

        egui::Panel::top(egui::Id::new("top")).show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(format!(
                    "{}{}",
                    self.strings.version,
                    env!("CARGO_PKG_VERSION")
                ));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.label(self.strings.language);
                    egui::ComboBox::from_id_salt("language")
                        .selected_text(if std::ptr::eq(self.strings, &FR) {
                            "Français"
                        } else {
                            "English"
                        })
                        .show_ui(ui, |ui| {
                            if ui.selectable_label(false, "English").clicked() {
                                self.strings = &EN;
                            }
                            if ui.selectable_label(false, "Français").clicked() {
                                self.strings = &FR;
                            }
                        });
                });
            });
        });

        egui::CentralPanel::default_margins().show(ui, |ui| {
            ui.vertical_centered(|ui| {
                ui.add_space(60.0);
                ui.heading(self.strings.choose_app);
                ui.add_space(30.0);
                for (name, label) in APPS {
                    if ui
                        .add_sized([220.0, 40.0], egui::Button::new(label(self.strings)))
                        .clicked()
                    {
                        self.launch(name);
                    }
                    ui.add_space(8.0);
                }
                if let Some(status) = &self.status {
                    ui.add_space(20.0);
                    ui.colored_label(Color32::YELLOW, status);
                }
                // The Java only logs update-check errors; a discreet footer
                // line is the port's visible equivalent.
                if let UpdateState::Failed(e) = &self.update {
                    ui.add_space(8.0);
                    ui.colored_label(Color32::RED, e);
                }
            });
        });
    }
}
