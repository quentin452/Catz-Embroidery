//! The close guard, ported from the Java's `DialogUtil.showExitDialog`
//! (minus the Dropbox option, which is a named exception — ROADMAP).
//!
//! The Java apps intercept the window close with a modal asking
//! save-and-quit / exit-without-save / cancel (`Main.exit()` →
//! `showExitDialog`). eframe has no close veto, so the port works through
//! the egui viewport: when the OS requests a close, the app answers
//! [`egui::ViewportCommand::CancelClose`] and opens the dialog; the choice
//! is returned to the caller, which runs its own save and finally sends
//! [`egui::ViewportCommand::Close`].

/// The dialog's strings. The suite is English-only outside the launcher
/// (i18n is a named exception, ROADMAP parity audit); the caller supplies
/// them so both apps can phrase the question for their content.
#[derive(Debug)]
pub struct ExitLabels {
    pub question: &'static str,
    pub save_and_quit: &'static str,
    pub exit_without_save: &'static str,
    pub cancel: &'static str,
}

/// What the user chose in the dialog (the Java's `JOptionPane` option
/// index).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExitChoice {
    SaveAndQuit,
    Quit,
    Cancel,
}

/// One-shot close guard: intercepts the first OS close request, keeps the
/// dialog open until a button is clicked, then stays out of the way.
#[derive(Debug)]
pub struct ExitDialog {
    open: bool,
}

impl ExitDialog {
    pub fn new() -> Self {
        Self { open: false }
    }

    /// Call once per frame, before the rest of the UI. Cancels any pending
    /// OS close request and shows the dialog; returns the button the user
    /// clicked. The caller runs its own save for [`ExitChoice::SaveAndQuit`]
    /// and, when the app may actually quit, sends [`Self::request_close`].
    pub fn frame(&mut self, ctx: &egui::Context, labels: &ExitLabels) -> Option<ExitChoice> {
        if ctx.input(|i| i.viewport().close_requested()) {
            ctx.send_viewport_cmd(egui::ViewportCommand::CancelClose);
            self.open = true;
        }
        let mut choice = None;
        if self.open {
            egui::Modal::new(egui::Id::new("exit_dialog")).show(ctx, |ui| {
                ui.label(labels.question);
                ui.add_space(8.0);
                ui.horizontal(|ui| {
                    if ui.button(labels.save_and_quit).clicked() {
                        choice = Some(ExitChoice::SaveAndQuit);
                    }
                    if ui.button(labels.exit_without_save).clicked() {
                        choice = Some(ExitChoice::Quit);
                    }
                    if ui.button(labels.cancel).clicked() {
                        choice = Some(ExitChoice::Cancel);
                    }
                });
            });
        }
        if choice.is_some() {
            self.open = false;
        }
        choice
    }

    /// Close the window for real (the "Exit without save" and the
    /// post-save quit both end here).
    pub fn request_close(ctx: &egui::Context) {
        ctx.send_viewport_cmd(egui::ViewportCommand::Close);
    }
}

impl Default for ExitDialog {
    fn default() -> Self {
        Self::new()
    }
}
