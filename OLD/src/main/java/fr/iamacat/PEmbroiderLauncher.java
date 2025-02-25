package fr.iamacat;

import fr.iamacat.utils.*;
import processing.controlP5.ControlP5;
import processing.controlP5.DropdownList;
import processing.core.PApplet;

import java.awt.*;
import java.io.IOException;
import javax.swing.JOptionPane;

import static fr.iamacat.utils.DropboxUtil.dropboxClient;
import static fr.iamacat.utils.DropboxUtil.loadTokenFromJson;

public class PEmbroiderLauncher extends PApplet implements Translatable {

    private Button buttonEditor;
    private Button buttonConverter;
    private Button buttonConnectDropbox;
    ;
    // TODOprivate Button buttonViewer;
    private ControlP5 cp5;
    private DropdownList languageDropdown;
    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderLauncher");
    }

    @Override
    public void settings() {
        size(800, 600);
    }

    @Override
    public void setup() {
        Translator.getInstance().registerTranslatable(this);
        background(200);
        loadTokenFromJson();
        // Initialiser le logger
        Logger.getInstance().log(Logger.Project.Launcher,"Lancement de l'application");

        // Initialiser ControlP5
        cp5 = new ControlP5(this);

        cp5.addDropdownList("languages")
                .setPosition(width - 110, 10)
                .setSize(100, 100)
                .setItemHeight(20)
                .setBarHeight(20)
                .addItem("English", 0)
                .addItem("Français", 1)
                .close()
                .onChange(event -> {
                    int index = (int) event.getController().getValue();
                    String language = index == 0 ? "en" : "fr";
                    Logger.getInstance().log(Logger.Project.Launcher, "Language set to: " + language);
                    Translator.getInstance().setLanguage(language);
                });


        fill(0);
        textSize(12);
        textAlign(LEFT, TOP);
        text(Translator.getInstance().translate("version") + Updater.CURRENT_VERSION, 10, 10);

        // Vérifier les mises à jour
        checkForUpdates();
        buttonConnectDropbox = new Button(width / 2 - 390, height - 50, 200, 40,color(255, 0, 0), "Connect to Dropbox");
        buttonEditor = new Button(width / 2 - 100, height / 2 - 40, 200, 40,color(100, 200, 255), Translator.getInstance().translate("launch_editor"));
        // TODO  buttonViewer = new Button(width / 2 - 100, height / 2 - 60, 200, 40, Translator.getInstance().translate("launch_viewer"));
        buttonConverter = new Button(width / 2 - 100, height / 2 + 20, 200, 40,color(100, 200, 255), Translator.getInstance().translate("launch_converter"));
        fill(0);
        textSize(16);
        textAlign(CENTER, CENTER);
        text(Translator.getInstance().translate("choose_app"), width / 2, height / 2 - 80);
    }

    @Override
    public void draw() {
        // Affichage des boutons
        buttonEditor.display(this);
        // TODO   buttonViewer.display();
        buttonConverter.display(this);
        buttonConnectDropbox.display(this);
        if (dropboxClient != null) {
            buttonConnectDropbox.setColor(color(0, 255, 0)); // Couleur verte si connecté
        } else {
            buttonConnectDropbox.setColor(color(255, 0, 0)); // Couleur rouge si non connecté
        }
    }

    @Override
    public void mousePressed() {
        // Vérifier si les boutons sont cliqués
        if (buttonEditor.isPressed(mouseX, mouseY)) {
            Logger.getInstance().log(Logger.Project.Launcher,"Lancement de PEmbroiderEditor");
            runApplication("fr.iamacat.pembroider_editor.Main");
        } else if (buttonConverter.isPressed(mouseX, mouseY)) {
            Logger.getInstance().log(Logger.Project.Launcher, "Lancement de PEmbroiderConverter");
            runApplication("fr.iamacat.pembroider_converter.Main");
        } else if (buttonConnectDropbox.isPressed(mouseX, mouseY)) {
            DropboxUtil.connectToDropbox();
            // TODO
       /* } else if (buttonViewer.isPressed(mouseX, mouseY)) {
            Logger.getInstance().log(Logger.Project.Launcher,"Lancement de PEmbroiderViewer");
            runApplication("fr.iamacat.PEmbroiderViewer");*/
        }
    }

    private void runApplication(String mainClassName) {
        try {
            Class<?> clazz = Class.forName(mainClassName);
            clazz.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } catch (Exception e) {
            Logger.getInstance().log(Logger.Project.Launcher,"Erreur lors de l'exécution de " + mainClassName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void exit() {
        // Sauvegarder les logs et archiver le fichier log au départ
        Logger.getInstance().log(Logger.Project.Launcher,"Fermeture de l'application");
        Logger.getInstance().archiveLogs();
        super.exit();
    }

    private void checkForUpdates() {
        if (Updater.isUpdateChecked) {
            return;
        }
        new Thread(() -> {
            try {
                String latestVersion = Updater.getLatestVersionFromGitHub();
                if (Updater.isVersionOutdated(Updater.CURRENT_VERSION, latestVersion)) {
                    Logger.getInstance().log(Logger.Project.Launcher, "Une nouvelle version est disponible : " + latestVersion);

                    // Afficher un pop-up informant l'utilisateur
                    int response = JOptionPane.showConfirmDialog(null,
                            "Une nouvelle version (" + latestVersion + ") est disponible. Voulez-vous ouvrir la page des releases ?",
                            "Mise à jour disponible",
                            JOptionPane.YES_NO_OPTION);

                    // Si l'utilisateur clique sur "Oui", ouvrir le navigateur
                    if (response == JOptionPane.YES_OPTION) {
                        Updater.openBrowserToReleasesPage();
                    }
                } else {
                    Logger.getInstance().log(Logger.Project.Launcher, "Vous avez la version la plus récente.");
                }
            } catch (IOException e) {
                Logger.getInstance().log(Logger.Project.Launcher, "Erreur lors de la vérification des mises à jour : " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void updateTranslations() {
        background(200);
        fill(0);
        textSize(12);
        textAlign(LEFT, TOP);
        text(Translator.getInstance().translate("version") + Updater.CURRENT_VERSION, 10, 10);

        // Mettre à jour les boutons avec les nouvelles traductions
        buttonEditor.label = Translator.getInstance().translate("launch_editor");
        buttonConverter.label = Translator.getInstance().translate("launch_converter");
        buttonConnectDropbox.label = Translator.getInstance().translate("connect_to_dropbox");
        // TODO    buttonViewer.label = Translator.getInstance().translate("launch_viewer");


        fill(0);
        textSize(16);
        textAlign(CENTER, CENTER);
        text(Translator.getInstance().translate("choose_app"), width / 2, height / 2 - 80);
    }

    // Classe Button
    class Button {
        float x, y, w, h;
        String label;
        int currentColor;

        Button(float x, float y, float w, float h, int currentColor, String label) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.currentColor = currentColor;
        }

        void display(PApplet app) {
            app.fill(currentColor);
            app.rect(x, y, w, h, 10);
            app.fill(0);
            app.textSize(16);
            app.textAlign(PApplet.CENTER, PApplet.CENTER);
            app.text(label, x + w / 2, y + h / 2);
        }

        boolean isPressed(float mx, float my) {
            return mx > x && mx < x + w && my > y && my < y + h;
        }

        void setColor(int color) {
            this.currentColor = color;
        }
    }

}
