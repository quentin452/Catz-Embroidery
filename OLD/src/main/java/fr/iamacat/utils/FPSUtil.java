package fr.iamacat.utils;

import processing.core.PApplet;
import processing.awt.PSurfaceAWT;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

import static processing.core.PApplet.println;

public class FPSUtil {

    private boolean showFPS = true;
    private boolean vsyncEnabled = true;
    private PApplet app;
    private int lastRefreshRate = 60; // Taux de rafraîchissement par défaut

    public FPSUtil(PApplet app) {
        this.app = app;
        app.registerMethod("draw", this); // Enregistrement pour l'affichage des FPS
        initializeFrameRate();
    }

    /**
     * Initialise le frame rate en fonction de la V-Sync et du taux de rafraîchissement de l'écran.
     */
    private void initializeFrameRate() {
        if (vsyncEnabled) {
            lastRefreshRate = getScreenRefreshRate();
            app.frameRate(lastRefreshRate);
        } else {
            app.frameRate(-1); // Désactive la V-Sync
        }
    }

    /**
     * Active ou désactive l'affichage des FPS.
     */
    public void toggleFPSDisplay() {
        showFPS = !showFPS;
    }

    /**
     * Active ou désactive la V-Sync.
     */
    public void toggleVSync() {
        vsyncEnabled = !vsyncEnabled;
        setVSync(vsyncEnabled);
    }

    /**
     * Définit l'état de la V-Sync.
     * @param enabled true pour activer, false pour désactiver.
     */
    public void setVSync(boolean enabled) {
        vsyncEnabled = enabled;
        if (enabled) {
            lastRefreshRate = getScreenRefreshRate();
            app.frameRate(lastRefreshRate);
        } else {
            app.frameRate(-1); // Désactive la V-Sync
        }
    }

    /**
     * Récupère le taux de rafraîchissement de l'écran sur lequel la fenêtre est affichée.
     * @return Le taux de rafraîchissement en Hz, ou 60 par défaut si inconnu.
     */
    public int getScreenRefreshRate() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        Object nativeSurface = app.getSurface().getNative();

        // Récupère la position absolue de la fenêtre sur l'écran
        Rectangle windowBounds = null;
        if (nativeSurface instanceof PSurfaceAWT.SmoothCanvas canvas) {
            // Accède au JFrame parent
            java.awt.Window window = SwingUtilities.getWindowAncestor(canvas);
            if (window != null) {
                // Récupère les coordonnées absolues de la fenêtre
                java.awt.Point windowLocation = window.getLocationOnScreen();
                windowBounds = new Rectangle(
                        windowLocation.x,
                        windowLocation.y,
                        window.getWidth(),
                        window.getHeight()
                );
            }
        }

        if (windowBounds == null) {
            return 60; // Valeur par défaut si la fenêtre n'est pas trouvée
        }

        // Recherche l'écran sur lequel la fenêtre est affichée
        for (GraphicsDevice gd : gs) {
            Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();
            if (screenBounds.contains(windowBounds)) { // Utilise contains() au lieu de intersects()
                DisplayMode dm = gd.getDisplayMode();
                int refreshRate = dm.getRefreshRate();
                return (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN) ? 60 : refreshRate;
            }
        }

        return 60; // Valeur par défaut si aucun écran correspondant n'est trouvé
    }

    /**
     * Gestion des touches clavier.
     * @param key Touche pressée.
     */
    public void keyPressed(char key) {
        if (key == 'f' || key == 'F') {
            toggleFPSDisplay();
        } else if (key == 'v' || key == 'V') {
            toggleVSync();
        }
    }

    /**
     * Affiche les FPS à l'écran.
     */
    public void draw() {
        if (showFPS) {
            app.fill(0); // Couleur du texte (noir)
            app.textSize(14);
            app.textAlign(PApplet.LEFT, PApplet.TOP);
            app.text("FPS: " + (int) app.frameRate, 10, 2);
        }

        // Vérifie dynamiquement le taux de rafraîchissement de l'écran
        SwingUtilities.invokeLater(this::handleDisplayChange);
    }

    /**
     * Gère les changements de configuration d'affichage (par exemple, déplacement de la fenêtre vers un autre écran).
     */
    private void handleDisplayChange() {
        if (vsyncEnabled) {
            int currentRefreshRate = getScreenRefreshRate();
            if (currentRefreshRate != lastRefreshRate) {
                lastRefreshRate = currentRefreshRate;
                app.frameRate(lastRefreshRate);
            }
        }
    }
}