package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import fr.iamacat.utils.*;

public class Main implements Screen, Translatable {
    private Stage stage;

    public Main() {
        stage = new Stage();
        UIUtils.createButton(stage,"load_image",true,20 ,20,120,30, Color.LIGHT_GRAY,this::loadImage);
        /*CP5ComponentsUtil.createActionButton(cp5, 20, 20, 120, 30, "load_image", this::loadImage);


        CP5ComponentsUtil.createActionButton(cp5, 160, 20, 120, 30, "saving", this::saveOnDropboxOrLocally);
        CP5ComponentsUtil.createActionButton(cp5, 20, 320, 100, 30, "enable_fill_mode", this::updateFillMode);
        CP5ComponentsUtil.createDropdownList(cp5, "hatchModeSelector", 580, 22, 135, 120, hatchModes, "hatch_mode", true,
                index -> {
                    selectedHatchMode = hatchModes[index];
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createDropdownList(cp5, "colorMode", 310, 22, 100, 150,
                Arrays.stream(ColorType.values()).map(Enum::name).toArray(String[]::new), "", false,
                index -> {
                    colorType = ColorType.values()[index];
                    if (img != null) refreshPreview();
                });
        maxMultiColorTextField = CP5ComponentsUtil.createNumericTextField(cp5, "maxMultiColorField", 20, 280, 100, 30,
                color(255), color(0), str(embroidery.maxColors), "max_color",
                value -> {
                    embroidery.maxColors = Math.max(1, value.intValue());
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "stitchSpacing", 20, 120, 100, 30, color(255), color(0),
                str(currentSpacing), "space_between_points",
                value -> {
                    currentSpacing = value;
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "strokeWeight", 20, 240, 100, 30, color(255), color(0),
                str(currentStrokeWeight), "stroke_weight",
                value -> {
                    currentStrokeWeight = value;
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "exportWidthField", 20, 160, 100, 30, color(255), color(0),
                str(exportWidth), "width_in_mm",
                value -> {
                    exportWidth = value;
                    Logger.getInstance().log(Logger.Project.Converter, "Largeur mise à jour : " + exportWidth);
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "exportHeightField", 20, 200, 100, 30, color(255), color(0),
                str(exportHeight), "height_in_mm",
                value -> {
                    exportHeight = value;
                    Logger.getInstance().log(Logger.Project.Converter, "Hauteur mise à jour : " + exportHeight);
                    if (img != null) refreshPreview();
                });
        int centerX = width / 2 - 150;
        progressBar = cp5.addSlider("progressBar")
                .setPosition(centerX, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel(Translator.getInstance().translate("progess"))
                .setVisible(false);*/
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

    public void loadImage() {
        /*if (!isDialogOpen) {
            isDialogOpen = true;
            selectInput(Translator.getInstance().translate("select_an_image"), "imageSelected");
        }*/
    }
}
