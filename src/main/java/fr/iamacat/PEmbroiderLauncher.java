package fr.iamacat;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class PEmbroiderLauncher implements ApplicationListener {

    private SpriteBatch batch;
    private Texture img;

    @Override
    public void create() {
        batch = new SpriteBatch();
        img = new Texture("icons/catz-embroidery-logo-2.png");  // Exemple d'image
    }

    @Override
    public void render() {
        // Nettoyer l'écran avec GL20 (fonctionne après l'initialisation du contexte OpenGL/Vulkan)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Dessiner le contenu
        batch.begin();
        batch.draw(img, 0, 0);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        // Gérer le redimensionnement de la fenêtre
    }

    @Override
    public void pause() {
        // Mettre en pause l'application
    }

    @Override
    public void resume() {
        // Reprendre l'application
    }

    @Override
    public void dispose() {
        batch.dispose();
        img.dispose();
    }

    public static void main (String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Vulkan Example");
        config.setWindowedMode(800, 600);
        config.setWindowIcon("icons/catz-embroidery-logo-2.png");
        new Lwjgl3Application(new PEmbroiderLauncher(), config);
    }
}
