package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import fr.iamacat.pembroider_converter.Main;

public class MainBase implements Screen, Translatable , InputProcessor {

    private static Stage stage;


    public MainBase() {
        stage = new Stage();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public boolean keyDown(int i) {
        return false;
    }

    @Override
    public boolean keyUp(int i) {
        return false;
    }

    @Override
    public boolean keyTyped(char c) {
        return false;
    }

    @Override
    public boolean touchDown(int i, int i1, int i2, int i3) {
        return false;
    }

    @Override
    public boolean touchUp(int i, int i1, int i2, int i3) {
        return false;
    }

    @Override
    public boolean touchCancelled(int i, int i1, int i2, int i3) {
        return false;
    }

    @Override
    public boolean touchDragged(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean mouseMoved(int i, int i1) {
        return false;
    }

    @Override
    public boolean scrolled(float v, float v1) {
        return false;
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.8f, 0.8f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public void updateTranslations() {
        /*for (int i = 0; i < this.fileMenu.getChildren().size; i++) {
            Actor actor = this.fileMenu.getChildren().get(i);
            if (actor instanceof MenuItem item) {
                if (i == 0) {
                    item.setText(Translator.getInstance().translate("save_locally"));
                } else if (i == 1) {
                    item.setText(Translator.getInstance().translate("load_file"));
                } else if (i == 2) {
                    item.setText(Translator.getInstance().translate("exit"));
                }
            }
        }
        // Mise à jour du menu Edit
        MenuItem colorModeItem = (MenuItem) editMenu.getChildren().first();
        colorModeItem.setText(Translator.getInstance().translate("color_mode"));

        // Mise à jour du sous-menu
        int index = 0;
        for(ColorType type : ColorType.values()) {
            MenuItem item = (MenuItem) colorModeMenu.getChildren().get(index++);
            item.setText(Translator.getInstance().translate(type.name().toLowerCase()));
        }*/
    }

    public static Stage getStage() {
        return stage;
    }
}
