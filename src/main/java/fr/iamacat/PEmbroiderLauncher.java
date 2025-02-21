package fr.iamacat;

import fr.iamacat.utils.Logger;
import processing.core.PApplet;

public class PEmbroiderLauncher extends PApplet {

    private Button buttonEditor;
    private Button buttonConverter;

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderLauncher");
    }

    public void settings() {
        size(400, 300);
    }

    public void setup() {
        background(200);

        // Initialiser le logger
        Logger.getInstance().log(Logger.Project.Launcher,"Lancement de l'application");

        // Créer des boutons
        buttonEditor = new Button(width / 2 - 100, height / 2 - 40, 200, 40, "PEmbroiderEditor");
        buttonConverter = new Button(width / 2 - 100, height / 2 + 20, 200, 40, "PEmbroiderConverter");

        fill(0);
        textSize(16);
        textAlign(CENTER, CENTER);
        text("Choisissez l'application à lancer:", width / 2, height / 2 - 80);
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
