package fr.iamacat;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import fr.iamacat.pembroider_mainmenu.Main;
import fr.iamacat.utils.FontManager;
import fr.iamacat.utils.UIUtils;

import static fr.iamacat.pembroider_converter.Main.displayedImage;

public class PEmbroiderLauncher extends Game {
    public static int windowWidth = 1280;
    public static int windowHeight = 720;

    private boolean showFPS = true;
    private boolean vsyncEnabled = true;
    private Stage stage;
    private Label fpsLabel;

    @Override
    public void create() {
        if (!VisUI.isLoaded()) {
            VisUI.load();
        }
        // TODO FIX FONT QUALITY
        //VisUI.load(Gdx.files.internal("uiskins/cloud-form/skin/cloud-form-ui.json"));  // TODO
        UIUtils.visSkin = FontManager.createCustomSkin("fonts/Microsoft Yahei.ttf",16,VisUI.getSkin());
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        fpsLabel = UIUtils.createLabel(stage,"FPS: 0",false,80, windowHeight- 55,15,15, Align.top,Align.top,Color.BLACK ,"default");
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
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            return;  // On ne fait rien si Ctrl est pressé
        }

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
    public void resize(int width, int height) {
        super.resize(width, height);
        float fpsLabelX = 80;
        float fpsLabelY = height - 55;
        fpsLabel.setPosition(fpsLabelX, fpsLabelY);
        stage.getViewport().update(width, height, true);
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
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void filesDropped(String[] files) {
                for (String path : files) {
                    FileHandle file = Gdx.files.absolute(path);
                    if (isImageFile(file)) {
                        fr.iamacat.pembroider_converter.Main.enableEscapeMenu = true;
                        loadAndDisplayImage(file, fr.iamacat.pembroider_converter.Main.getStage());
                    }
                }
            }
            @Override
            public boolean closeRequested() {
                PEmbroiderLauncher launcher = (PEmbroiderLauncher) Gdx.app.getApplicationListener();
                if (launcher.getScreen() instanceof fr.iamacat.pembroider_converter.Main mainScreen) {
                    if (!fr.iamacat.pembroider_converter.Main.enableEscapeMenu) {
                        return true;
                    }
                    if (mainScreen.isExitConfirmed()) {
                        return true;
                    } else {
                        Gdx.app.postRunnable(mainScreen::handleExitRequest);
                        return false;
                    }
                }
                return true;
            }
        });
        new Lwjgl3Application(new PEmbroiderLauncher(), config);
    }
    private static boolean isImageFile(FileHandle file) {
        String ext = file.extension().toLowerCase();
        return ext.matches("png|jpg|jpeg|bmp|gif");
    }

    private static void loadAndDisplayImage(FileHandle file, Stage stage) {
        Texture texture = new Texture(file);
        if (displayedImage != null) {
            displayedImage.remove();
        }
        displayedImage = new Image(texture);
        displayedImage.setSize(500, 500);
        displayedImage.setPosition(
                (Gdx.graphics.getWidth() - displayedImage.getWidth()) / 2,
                (Gdx.graphics.getHeight() - displayedImage.getHeight()) / 2
        );
    }
}
