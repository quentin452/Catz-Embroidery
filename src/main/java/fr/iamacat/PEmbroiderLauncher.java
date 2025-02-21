package fr.iamacat;

import fr.iamacat.utils.Logger;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import fr.iamacat.utils.Updater;
import processing.controlP5.CallbackEvent;
import processing.controlP5.CallbackListener;
import processing.controlP5.ControlP5;
import processing.controlP5.DropdownList;
import processing.core.PApplet;

import java.io.IOException;
import javax.swing.JOptionPane;

public class PEmbroiderLauncher extends PApplet implements Translatable {

    private Button buttonEditor;
    private Button buttonConverter;
    private ControlP5 cp5;
    private DropdownList languageDropdown;

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderLauncher");
    }

    public void settings() {
        size(800, 600);
    }

    public void setup() {
        Translator.getInstance().registerTranslatable(this);
        background(200);

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

        // Créer des boutons
        buttonEditor = new Button(width / 2 - 100, height / 2 - 40, 200, 40, Translator.getInstance().translate("launch_editor"));
        buttonConverter = new Button(width / 2 - 100, height / 2 + 20, 200, 40, Translator.getInstance().translate("launch_converter"));

        fill(0);
        textSize(16);
        textAlign(CENTER, CENTER);
        text(Translator.getInstance().translate("choose_app"), width / 2, height / 2 - 80);
    }

    public void draw() {
        // Affichage des boutons
        buttonEditor.display();
        buttonConverter.display();
    }

    public void mousePressed() {
        // Vérifier si les boutons sont cliqués
        if (buttonEditor.isPressed(mouseX, mouseY)) {
            Logger.getInstance().log(Logger.Project.Launcher,"Lancement de PEmbroiderEditor");
            runApplication("fr.iamacat.PEmbroiderEditor");
        } else if (buttonConverter.isPressed(mouseX, mouseY)) {
            Logger.getInstance().log(Logger.Project.Launcher,"Lancement de PEmbroiderConverter");
            runApplication("fr.iamacat.PEmbroiderConverter");
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

    public void exit() {
        // Sauvegarder les logs et archiver le fichier log au départ
        Logger.getInstance().log(Logger.Project.Launcher,"Fermeture de l'application");
        Logger.getInstance().archiveLogs();
        super.exit();
    }

    private void checkForUpdates() {
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
        clear();
        setup();
    }

    // Classe Button
    class Button {
        float x, y, w, h;
        String label;

        Button(float x, float y, float w, float h, String label) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
        }

        void display() {
            fill(100, 200, 255);
            rect(x, y, w, h, 10);
            fill(0);
            textSize(16);
            textAlign(CENTER, CENTER);
            text(label, x + w / 2, y + h / 2);
        }

        boolean isPressed(float mx, float my) {
            return mx > x && mx < x + w && my > y && my < y + h;
        }
    }
}
