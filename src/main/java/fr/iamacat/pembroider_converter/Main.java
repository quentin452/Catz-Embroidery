package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.PEmbroiderLauncher;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import fr.iamacat.utils.UIUtils;

import static fr.iamacat.PEmbroiderLauncher.windowHeight;

public class Main implements Screen, Translatable {
    private Stage stage;
    private Table menuTable;
    private TextButton fileButton;
    private TextButton saveLocallyButton;
    private Table saveDropdownTable;
    private TextButton saveAsPdfButton;
    private TextButton saveAsImageButton;
    private SpriteBatch batch;
    private Texture img;
    private PEmbroiderLauncher game;
    Label versionLabel;
    Skin skin;
    private VisTable rootTable;
    public Main() {

        // Charger VisUI
        if (!VisUI.isLoaded()) {
            VisUI.load();
        }
        skin = VisUI.getSkin();
        stage = new Stage();
        Gdx.input.setInputProcessor(stage);

        // Créer un tableau principal
        rootTable = new VisTable();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        // Ajouter le menu
        createMenu();
    }

    private void createMenu() {
        // Barre de menu
        VisTable menuBar = new VisTable();
        menuBar.setBackground(VisUI.getSkin().getDrawable("default-pane"));

        // Création du menu "File" en une seule ligne
        PopupMenu fileMenu = UIUtils.createPopupMenu(
                new String[]{"save_locally", "exit"},
                this::showSaveDialog, // Action pour "Save Locally"
                () -> Gdx.app.exit()  // Action pour "Exit"
        );

        // Création du bouton "File" avec son menu déroulant
        VisTextButton fileButton = UIUtils.createMenuButton("file", fileMenu, stage);

        // Ajouter les boutons à la barre de menu
        menuBar.add(fileButton).pad(5);

        // Ajouter la barre de menu en haut de l'interface
        rootTable.top().left();
        rootTable.add(menuBar).expandX().fillX();
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
    public void updateTranslations() {
        // Mettre à jour les traductions
        fileButton.setText(Translator.getInstance().translate("file"));
        saveLocallyButton.setText(Translator.getInstance().translate("save_locally"));
        saveAsPdfButton.setText(Translator.getInstance().translate("save_as_pes"));
        saveAsImageButton.setText(Translator.getInstance().translate("save_as_png"));
    }

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
    private void showSaveDialog() {
        VisDialog dialog = new VisDialog("Save Options") {
            @Override
            protected void result(Object object) {
                if ("PES".equals(object)) {
                    System.out.println("Saving as PES...");
                    // Logique pour sauvegarder en PES
                } else if ("PNG".equals(object)) {
                    System.out.println("Saving as PNG...");
                    // Logique pour sauvegarder en PNG
                }
            }
        };

        dialog.text("Choose a save option:");
        dialog.button(Translator.getInstance().translate("save_as_pes"), "PES");
        dialog.button(Translator.getInstance().translate("save_as_png"), "PNG");

        dialog.show(stage);
    }

    private void toggleSaveDropdown(boolean show) {
        // Afficher ou masquer le sous-menu en fonction de l'état
        saveDropdownTable.setVisible(show);
        if (show) {
            // Positionner le sous-menu en dessous du bouton "Save Locally"
            saveDropdownTable.setPosition(saveLocallyButton.getX() + saveLocallyButton.getWidth(), windowHeight - 85);
        }
    }
}
