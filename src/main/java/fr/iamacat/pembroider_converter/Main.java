package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.utils.*;

public class Main implements Screen, Translatable , InputProcessor {
    public static Stage stage;
    private PopupMenu fileMenu;
    private PopupMenu editMenu;
    private PopupMenu colorModeMenu;
    private ColorType currentColorType = ColorType.MultiColor;
    public static Image displayedImage;
    private DragAndDrop dragAndDrop;
    public boolean FillB = false;

    private VisTable rootTable;
    private enum ColorType {
        MultiColor,
        BlackAndWhite,
        Realistic
    }
    public Main() {
        stage = new Stage();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);
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
        String fillModeStr = Translator.getInstance().translate("enable_fill_mode");

        // Création du menu "File"
        fileMenu = UIUtils.createPopupMenu(
                new String[]{savelocStr, loadFileStr, exitStr},
                this::showSaveDialog, // Action pour "Save Locally"
                this::showLoadDialog, // Action pour "Load File"
                () -> Gdx.app.exit()  // Action pour "Exit"
        );

        colorModeMenu = new PopupMenu();
        colorModeMenu.addItem(createColorMenuItem(ColorType.MultiColor));
        colorModeMenu.addItem(createColorMenuItem(ColorType.BlackAndWhite));
        colorModeMenu.addItem(createColorMenuItem(ColorType.Realistic));

        // Création du menu "Edit"
        editMenu = UIUtils.createPopupMenu(
                new String[]{colorModeStr,fillModeStr},
                this::changeColorMode,
                this::updateFillMode
        );
        editMenu = new PopupMenu();
        MenuItem colorModeItem = new MenuItem(colorModeStr);
        colorModeItem.setSubMenu(colorModeMenu);
        editMenu.addItem(colorModeItem);

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
    private void updateDisplayedImage(Texture texture) {
        if (displayedImage != null) {
            displayedImage.remove();  // Remove the old image if it exists
        }

        // Créer une nouvelle image
        displayedImage = new Image(texture);

        // Positionner l'image manuellement sans affecter la disposition du VisTable
        float windowWidth = Gdx.graphics.getWidth();
        float windowHeight = Gdx.graphics.getHeight();
        displayedImage.setSize(500, 500);
        displayedImage.setPosition((windowWidth - displayedImage.getWidth()) / 2, (windowHeight - displayedImage.getHeight()) / 2);

        // Ajouter l'image au stage sans l'ajouter au rootTable
        stage.addActor(displayedImage);
    }


    private MenuItem createColorMenuItem(ColorType type) {
        // Utilisation d'un ChangeListener pour l'action du clic
        MenuItem item = new MenuItem(type.toString(), new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                setColorMode(type);  // Appel de la méthode pour changer le mode de couleur
            }
        });
        item.setStyle(createColorMenuItemStyle(type));  // Application du style personnalisé pour l'élément
        return item;
    }

    private MenuItem.MenuItemStyle createColorMenuItemStyle(ColorType type) {
        MenuItem.MenuItemStyle style = new MenuItem.MenuItemStyle(VisUI.getSkin().get(MenuItem.MenuItemStyle.class));
        switch(type) {
            case MultiColor:
                break;
            case BlackAndWhite:
                break;
            case Realistic:
                break;
        }
        return style;
    }
    private void setColorMode(ColorType type) {
        currentColorType = type;

        // Créer une liste des éléments avant de les modifier
        Array<MenuItem> menuItems = new Array<>();
        for (Actor actor : colorModeMenu.getChildren()) {
            if (actor instanceof MenuItem) {
                menuItems.add((MenuItem) actor);
            }
        }

        // Appliquer les modifications après l'itération
        for (MenuItem item : menuItems) {
            item.setChecked(item.getText().equals(type.toString()));
        }
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
        // Mise à jour du menu Edit
        MenuItem colorModeItem = (MenuItem) editMenu.getChildren().first();
        colorModeItem.setText(Translator.getInstance().translate("color_mode"));

        // Mise à jour du sous-menu
        int index = 0;
        for(ColorType type : ColorType.values()) {
            MenuItem item = (MenuItem) colorModeMenu.getChildren().get(index++);
            item.setText(Translator.getInstance().translate(type.name().toLowerCase()));
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
    @Override
    public boolean keyDown(int keycode) {
        System.out.println("keyDown triggered, keycode: " + keycode);

        // Listen for Ctrl+V
        if (keycode == Input.Keys.V && Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            System.out.println("Ctrl+V detected!");

            // Paste image from clipboard
            Image pastedImage = ApplicationUtil.pasteImageFromClipboard();
            if (pastedImage != null) {
                System.out.println("Image pasted from clipboard!");

                // Get the texture from the pasted image
                Drawable drawable = pastedImage.getDrawable();
                if (drawable instanceof TextureRegionDrawable) {
                    System.out.println("Drawable is a TextureRegionDrawable.");

                    Texture texture = ((TextureRegionDrawable) drawable).getRegion().getTexture();
                    updateDisplayedImage(texture);
                    System.out.println("Texture updated with the pasted image.");
                } else {
                    System.out.println("Drawable is not a TextureRegionDrawable.");
                }
            } else {
                System.out.println("No image found in clipboard.");
            }
            return true;
        }
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

    private void showLoadDialog() {
        DialogUtil.ImageWrapper imageWrapper = new DialogUtil.ImageWrapper();
        DialogUtil.showFileChooserDialog(stage, imageWrapper);
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


    private void updateFillMode()
    {
        FillB = !FillB;
    }

    private void changeColorMode() {

    }

    public static Stage getStage() {
        return stage;
    }
}
