package fr.iamacat;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import fr.iamacat.pembroider_mainmenu.Main;
import fr.iamacat.utils.UIUtils;

public class PEmbroiderLauncher extends Game {
    public static int windowWidth = 1280;
    public static int windowHeight = 720;
    private boolean showFPS = true;
    private boolean vsyncEnabled = true;

    private Stage stage;
    private Label fpsLabel;

    @Override
    public void create() {
        // Initialisation du Stage et du Skin
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // Création du Label FPS
        fpsLabel = new Label("FPS: 0", UIUtils.skin);
        fpsLabel.setVisible(false); // Caché par défaut

        // Placement du label en haut à gauche avec une Table
        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        table.add(fpsLabel).pad(10);

        stage.addActor(table);
        fpsLabel.setVisible(showFPS);
        setScreen(new Main(this));
    }

    @Override
    public void render() {
        handleInput();
        super.render();

        // Mise à jour de l'affichage FPS si activé
        if (showFPS) {
            fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());
        }

        // Dessiner l'UI
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    private void handleInput() {
        // Afficher/Cacher les FPS avec "V"
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            showFPS = !showFPS;
            fpsLabel.setVisible(showFPS);
        }

        // Activer/Désactiver la V-Sync avec "F"
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            vsyncEnabled = !vsyncEnabled;
            Gdx.graphics.setVSync(vsyncEnabled);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Catz Embroidery Launcher");
        config.setWindowedMode(windowWidth, windowHeight);
        config.setWindowIcon("icons/catz-embroidery-logo-2.png");
        config.useVsync(true);
        new Lwjgl3Application(new PEmbroiderLauncher(), config);
    }
}
