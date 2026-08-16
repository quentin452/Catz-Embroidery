//! The launcher UI: the Java's three buttons (editor, converter, viewer —
//! the Java's viewer was a TODO, ours exists) + the language dropdown
//! (en/fr, the Java's Translator) + the version line. Each button spawns
//! the sibling exe next to the launcher (`target/debug/` in dev, the
//! install dir when packaged).

use eframe::egui::{self, Color32};

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
}

const EN: Strings = Strings {
    choose_app: "Choose an app",
    launch_editor: "Launch editor",
    launch_converter: "Launch converter",
    launch_viewer: "Launch viewer",
    version: "Version ",
    language: "Language",
    not_found: "not found next to the launcher — build the workspace first",
};

const FR: Strings = Strings {
    choose_app: "Choisissez une application",
    launch_editor: "Lancer l'éditeur",
    launch_converter: "Lancer le convertisseur",
    launch_viewer: "Lancer le visualiseur",
    version: "Version ",
    language: "Langue",
    not_found: "introuvable à côté du lanceur — compilez le workspace d'abord",
};

/// The launch targets: the sibling exe names (the Java's reflection `main`
/// calls; the launcher spawns processes instead).
type AppEntry = (&'static str, fn(&Strings) -> &'static str);
const APPS: [AppEntry; 3] = [
    ("emb-editor", |s| s.launch_editor),
    ("emb-converter", |s| s.launch_converter),
    ("emb-viewer", |s| s.launch_viewer),
];

pub struct LauncherApp {
    strings: &'static Strings,
    status: Option<String>,
}

impl LauncherApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        cc.egui_ctx.set_visuals(egui::Visuals::dark());
        Self {
            strings: &EN,
            status: None,
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
            });
        });
    }
}
