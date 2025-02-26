package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.utils.*;

import static fr.iamacat.PEmbroiderLauncher.windowHeight;
import static fr.iamacat.PEmbroiderLauncher.windowWidth;

public class Main implements Screen, Translatable {
    private Stage stage;
    Array<String> filesOptions = new Array<>();
    private SelectBox<String> fileDropdown;

    public Main() {
        stage = new Stage();
        filesOptions.add(Translator.getInstance().translate("load_image"));
        fileDropdown = UIUtils.createDropdown(stage,filesOptions,windowWidth - 20, windowHeight - 120,0,0,Color.GRAY,this::handleFileDropdown);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.8f, 0.8f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void updateTranslations() {}

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        stage.dispose();
    }


    public void handleFileDropdown() {
        String selectedLanguage = fileDropdown.getSelected();
        if (selectedLanguage == Translator.getInstance().translate("load_image")) {
            /*if (!isDialogOpen) {
                isDialogOpen = true;
                selectInput(Translator.getInstance().translate("select_an_image"), "imageSelected");
            }*/
        }
    }
}
