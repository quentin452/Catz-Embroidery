package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.utils.*;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;
import fr.iamacat.utils.enums.SaveDropboxType;
import fr.iamacat.utils.enums.SaveLocallyType;

import static fr.iamacat.utils.UIUtils.*;

public class Main extends MainBase {
    private PopupMenu fileMenu,editMenu,colorModeMenu,hatchModeMenu,saveLocallyTypeMenu,saveToDropboxTypeMenu;
    private ColorType currentColorType = ColorType.MultiColor;
    private HatchModeType currentHatchModeType = HatchModeType.Parallel;
    private SaveLocallyType currentSaveLocallyType = SaveLocallyType.JPG;
    private SaveDropboxType currentSaveDropboxType = SaveDropboxType.JPG;
    private int spaceBetweenPoints = 10 , exportHeight = 95 , exportWidth = 95 , maxColors = 10 , strokeWeight = 20;
    public static Image displayedImage;
    private MenuItem saveLocallyButton , saveToDropboxButton;
    public boolean FillB = false;
    private VisTable rootTable;
    public Main() {
        rootTable = new VisTable();
        rootTable.setFillParent(true);
        getStage().addActor(rootTable);
        createMenu();
    }

    private void createMenu() {
        VisTable menuBar = new VisTable();
        rootTable.bottom().top();
        rootTable.add(menuBar).expandX().fillX();
        menuBar.setBackground(VisUI.getSkin().getDrawable("default-pane"));

        // FILE MENU
        fileMenu = new PopupMenu();
        saveLocallyButton = addMenuItem(fileMenu, t("save_locally"), SaveLocallyType.class, this::showSaveLocallyDialog);
        if (DropboxUtil.dropboxClient != null) {
            saveToDropboxButton = addMenuItem(fileMenu, t("save_to_dropbox"), SaveDropboxType.class, this::showDropboxDialog);
        }
        addMenuItem(fileMenu, t("load_file"), this::showLoadDialog);
        addMenuItem(fileMenu, t("exit"), Gdx.app::exit);
        VisTextButton fileButton = UIUtils.createMenuButton("file", true, fileMenu, getStage());
        fileButton.getLabel().setAlignment(Align.left);
        menuBar.add(fileButton).expandX().fillX().pad(0).left();

        // EDIT MENU
        editMenu = new PopupMenu();
        colorModeMenu = addSubmenu(editMenu, t("color_mode"), ColorType.class, this::setColorMode);
        hatchModeMenu = addSubmenu(editMenu, t("hatch_mode"), HatchModeType.class, this::setHatchMode);
        VisTextButton editButton = UIUtils.createMenuButton("edit", true, editMenu, getStage());
        editButton.getLabel().setAlignment(Align.left);
        menuBar.add(editButton).expandX().fillX().pad(0).left();
        createSettingsPanel();
    }
    private void createSettingsPanel() {
        // Créer la table et la personnaliser
        VisTable settingsTable = new VisTable();
        settingsTable.setBackground(VisUI.getSkin().getDrawable("menu-bg"));
        settingsTable.setColor(new Color(62f, 62f, 66f, 1f));

        // Ajouter les composants de la table
        createSettingsTable(settingsTable,"Space Between Points", String.valueOf(spaceBetweenPoints), 50,  this::setSpaceBetweenPoints);
        createSettingsTable(settingsTable,"WIDTH (MM)", String.valueOf(exportWidth), 50,  this::setExportWidth);
        createSettingsTable(settingsTable,"HEIGHT (MM)", String.valueOf(exportHeight), 50,  this::setExportHeight);
        createSettingsTable(settingsTable,"STROKE WEIGHT", String.valueOf(strokeWeight), 50,  this::setStrokeWeight);
        createSettingsTable(settingsTable,"MAX COLORS", String.valueOf(maxColors), 50,  this::setMaxColors);

        // Ajouter une checkbox pour "fill_mode"
        VisCheckBox checkBox = new VisCheckBox(t("fill_mode"));
        checkBox.setChecked(FillB);
        checkBox.addListener(event -> FillB = checkBox.isChecked());
        settingsTable.add(checkBox).left().padLeft(10).padRight(10).row();

        // Créer un ScrollPane pour la table
        ScrollPane scrollPane = new ScrollPane(settingsTable);
        scrollPane.setScrollingDisabled(false, true);
        scrollPane.setPosition(50, 50);
        scrollPane.setSize(250, 200);

        getStage().addActor(scrollPane); // Ajouter le ScrollPane à l'écran
    }
    private void updateDisplayedImage(Texture texture) {
        if (displayedImage != null) {
            displayedImage.remove();
        }
        displayedImage = new Image(texture);
        displayedImage.setSize(500, 500);
        displayedImage.setPosition((Gdx.graphics.getWidth() - displayedImage.getWidth()) / 2, (Gdx.graphics.getHeight() - displayedImage.getHeight()) / 2);
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

    private void setSpaceBetweenPoints(int intz) {
        spaceBetweenPoints = intz;
    }
    private void setMaxColors(int intz) {
        maxColors = intz;
    }
    private void setExportHeight(int intz) {
        exportHeight = intz;
    }
    private void setExportWidth(int intz) {
        exportWidth = intz;
    }
    private void setStrokeWeight(int intz) {
        strokeWeight = intz;
    }

    @Override
    public void render(float delta) {
        super.render(delta);
        boolean isImageAvailable = (displayedImage != null);
        if (saveLocallyButton != null) {
            saveLocallyButton.setDisabled(!isImageAvailable);
        }
        if (saveToDropboxButton != null) {
            saveToDropboxButton.setDisabled(!isImageAvailable);
        }
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
        DialogUtil.showFileChooserDialog(getStage(), selectedImage -> {
            if (selectedImage != null) {
                // Remove old image if necessary
                if (displayedImage != null) {
                    displayedImage.remove();
                }
                // Update displayed image
                displayedImage = selectedImage;
                getStage().addActor(displayedImage);
            } else {
                System.out.println("displayed image = null ❌");
            }
        });
    }

    private void showSaveLocallyDialog(SaveLocallyType type) {
        currentSaveLocallyType = type;
        DialogUtil.showSaveDialog(currentSaveLocallyType,getStage(),displayedImage);
    }
    private void showDropboxDialog(SaveDropboxType type) {
        currentSaveDropboxType = type;
        DialogUtil.showUploadDialog(currentSaveDropboxType,getStage(),displayedImage);
    }
    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        getStage().getViewport().update(width, height, true);
    }
}
