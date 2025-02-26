package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.utils.*;

import static fr.iamacat.utils.UIUtils.setMenuItemChecked;

public class Main extends MainBase {
    private PopupMenu fileMenu;
    private PopupMenu editMenu;
    private PopupMenu colorModeMenu;
    private PopupMenu hatchModeMenu;
    private ColorType currentColorType = ColorType.MultiColor;
    private HatchModeType currentHatchModeType = HatchModeType.Parallel;
    public static Image displayedImage;
    public boolean FillB = false;

    private VisTable rootTable;
    private enum ColorType {
        MultiColor,
        BlackAndWhite,
        Realistic
    }
    private enum HatchModeType {
        Cross,
        Parallel,
        Concentric,
        Spiral,
        Perlin
    }

    public Main() {
        rootTable = new VisTable();
        rootTable.setFillParent(true);
        getStage().addActor(rootTable);
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

        String hatchModeStr = Translator.getInstance().translate("hatch_mode");

        // Création du menu "Edit"
        editMenu = UIUtils.createPopupMenu(
                new String[]{colorModeStr, fillModeStr},
                this::changeColorMode,
                this::updateFillMode
        );
        editMenu = new PopupMenu();

        // Création du menu "File"
        fileMenu = UIUtils.createPopupMenu(
                new String[]{savelocStr, loadFileStr, exitStr},
                this::showSaveDialog, // Action pour "Save Locally"
                this::showLoadDialog, // Action pour "Load File"
                () -> Gdx.app.exit()  // Action pour "Exit"
        );

        // Création des sous-menus pour les modes de couleur
        colorModeMenu = UIUtils.createEnumMenu(ColorType.class, this::setColorMode);

        MenuItem colorModeItem = new MenuItem(colorModeStr);
        colorModeItem.setSubMenu(colorModeMenu);
        editMenu.addItem(colorModeItem);

        // Création des sous-menus pour les modes de hachure
        hatchModeMenu = UIUtils.createEnumMenu(HatchModeType.class, this::setHatchMode);

        MenuItem hatchModeItem = new MenuItem(hatchModeStr);
        hatchModeItem.setSubMenu(hatchModeMenu);
        editMenu.addItem(hatchModeItem);

        // Création du bouton "File" avec son menu déroulant
        VisTextButton fileButton = UIUtils.createMenuButton("file", true, fileMenu, getStage());

        // Création du bouton "Edit" avec son menu déroulant
        VisTextButton editButton = UIUtils.createMenuButton("edit", true, editMenu, getStage());

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
        getStage().addActor(displayedImage);
    }

    private void setHatchMode(HatchModeType type) {
        currentHatchModeType = type;
        setMenuItemChecked(hatchModeMenu, type.toString());
    }

    private void setColorMode(ColorType type) {
        currentColorType = type;
        setMenuItemChecked(colorModeMenu, type.toString());
    }

    @Override
    public void dispose() {
        getStage().dispose();
    }
    @Override
    public boolean keyDown(int keycode) {
        Texture texture =  ApplicationUtil.copyImage(keycode);
        if (texture != null) {
            updateDisplayedImage(texture);
        }
        return false;
    }

    private void showLoadDialog() {
        DialogUtil.ImageWrapper imageWrapper = new DialogUtil.ImageWrapper();
        DialogUtil.showFileChooserDialog(getStage(), imageWrapper);
    }

    private void showSaveDialog() {
       DialogUtil.showSaveDialog(getStage());
    }


    private void updateFillMode()
    {
        FillB = !FillB;
    }

    private void changeColorMode() {

    }
}
