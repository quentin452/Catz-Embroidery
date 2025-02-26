package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import fr.iamacat.utils.UIUtils;

public class Main implements Screen, Translatable {
    private Stage stage;
    private PopupMenu fileMenu;
    private VisTable rootTable;
    private enum ColorType {
        MultiColor,
        BlackAndWhite,
        Realistic
    }
    public Main() {
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

        // Traductions des éléments du menu "File"
        String savelocStr = Translator.getInstance().translate("save_locally");
        String loadFileStr = Translator.getInstance().translate("load_file");
        String exitStr = Translator.getInstance().translate("exit");

        // Traductions des éléments du menu "Edit"
        String colorModeStr = Translator.getInstance().translate("color_mode");

        // Création du menu "File"
        fileMenu = UIUtils.createPopupMenu(
                new String[]{savelocStr, loadFileStr, exitStr},
                this::showSaveDialog, // Action pour "Save Locally"
                this::showLoadDialog, // Action pour "Load File"
                () -> Gdx.app.exit()  // Action pour "Exit"
        );

        // Création du menu "Edit"
        PopupMenu editMenu = UIUtils.createPopupMenu(
                new String[]{colorModeStr},
                this::changeColorMode
        );

        // Création du bouton "File" avec son menu déroulant
        VisTextButton fileButton = UIUtils.createMenuButton("file", true, fileMenu, stage);

        // Création du bouton "Edit" avec son menu déroulant
        VisTextButton editButton = UIUtils.createMenuButton("edit", true, editMenu, stage);

        // Ajouter les boutons à la barre de menu
        menuBar.add(fileButton).expandX().fillX().pad(0).left();  // Le bouton "File" prendra toute la largeur disponible et sera aligné à gauche
        menuBar.add(editButton).expandX().fillX().pad(0).left();  // Le bouton "Edit" prendra toute la largeur disponible et sera aligné à gauche

        // Ajouter la barre de menu en haut de l'interface
        rootTable.bottom().top();
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
        for (int i = 0; i < this.fileMenu.getChildren().size; i++) {
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
        VisDialog dialog = new VisDialog(Translator.getInstance().translate("save_options")) {
            @Override
            protected void result(Object object) {
                if ("PES".equals(object)) {
                    System.out.println("Saving as PES...");
                } else if ("PNG".equals(object)) {
                    System.out.println("Saving as PNG...");
                } else if ("CANCEL".equals(object)) {
                    System.out.println("Cancelled");
                }
            }
        };

        dialog.button(Translator.getInstance().translate("save_as_pes"), "PES");
        dialog.button(Translator.getInstance().translate("save_as_png"), "PNG");
        dialog.button(Translator.getInstance().translate("cancel"), "CANCEL");
        dialog.show(stage);
    }

    private void showLoadDialog() {
        VisDialog dialog = new VisDialog(Translator.getInstance().translate("load_options")) {
            @Override
            protected void result(Object object) {
                if ("PES".equals(object)) {
                    System.out.println("load as PES...");
                } else if ("PNG".equals(object)) {
                    System.out.println("load as PNG...");
                } else if ("CANCEL".equals(object)) {
                    System.out.println("Cancelled");
                }
            }
        };

        dialog.button(Translator.getInstance().translate("load_pes"), "PES");
        dialog.button(Translator.getInstance().translate("load_png"), "PNG");
        dialog.button(Translator.getInstance().translate("cancel"), "CANCEL");
        dialog.show(stage);
    }
    private void changeColorMode() {

    }
}
