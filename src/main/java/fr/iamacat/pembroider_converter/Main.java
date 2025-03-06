package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.EmbroideryMachine;
import fr.iamacat.manager.DialogManager;
import fr.iamacat.utils.*;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;
import fr.iamacat.utils.enums.SaveType;

import static fr.iamacat.utils.UIUtils.*;
public class Main extends MainBase {
    private static PEmbroiderGraphicsLibgdx embroidery = null;
    private PopupMenu fileMenu,editMenu,broderyMachineMenu;
    private SaveType currentSaveLocallyType = SaveType.SVG;
    private SaveType currentSaveDropboxType = SaveType.SVG;
    private static int exportHeight = 95;
    private static int exportWidth = 95;
    private int visualizeHeight = 320;
    private int visualizeWidth = 320;
    public static Image displayedImage;
    private MenuItem saveLocallyButton , saveToDropboxButton;
    private VisTable rootTable;
    private static boolean showPreview = false;
    public static boolean enableEscapeMenu = false;
    private static boolean exitConfirmed = false;
    private ShapeRenderer shapeRenderer;
    private VisLabel statsLabel;

    public Main() {
        shapeRenderer = new ShapeRenderer();
        embroidery = new PEmbroiderGraphicsLibgdx(shapeRenderer);
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
        saveLocallyButton = addMenuItem(fileMenu, t("save_locally"), SaveType.class, this::showSaveLocallyDialog);
        if (DropboxUtil.dropboxClient != null) {
            saveToDropboxButton = addMenuItem(fileMenu, t("save_to_dropbox"), SaveType.class, this::showDropboxDialog);
        }
        addMenuItem(fileMenu, t("load_file"), this::showLoadDialog);
        addMenuItem(fileMenu, t("exit"), Gdx.app::exit);
        VisTextButton fileButton = createMenuButton("file", true, fileMenu, getStage());
        menuBar.add(fileButton).expandX().fillX().pad(0).left();

        // EDIT MENU
        editMenu = new PopupMenu();
        addSubmenu(editMenu, t("color_mode"), ColorType.class, value -> {
            embroidery.colorMode = value;
            refreshPreview();
        });
        addSubmenu(editMenu, t("hatch_mode"), HatchModeType.class, value -> {
            embroidery.hatchMode = value;
            refreshPreview();
        });
        VisTextButton editButton = createMenuButton("edit", true, editMenu, getStage());
        menuBar.add(editButton).expandX().fillX().pad(0).left();

        // BRODERY MACHINE MENU
        broderyMachineMenu = new PopupMenu();
        addSubmenu(broderyMachineMenu, t("brodery_tab"), EmbroideryMachine.class, value -> {
            embroidery.selectedMachine = value;
        });
        VisTextButton broderyMachineButton = createMenuButton("brodery_tab", true, broderyMachineMenu, getStage());
        menuBar.add(broderyMachineButton).expandX().fillX().pad(0).left();

        // OTHER
        createSettingsPanel();
        if (statsLabel == null) {
            statsLabel = new VisLabel();
            VisLabel.LabelStyle labelStyle = new VisLabel.LabelStyle();
            labelStyle.font = VisUI.getSkin().getFont("default-font");  // Assurez-vous d'utiliser la bonne police
            labelStyle.fontColor = Color.BLACK;  // Changer la couleur du texte à noir
            statsLabel.setStyle(labelStyle);
            statsLabel.setPosition(910, 130);
            getStage().addActor(statsLabel);
        }
    }
    private void createSettingsPanel() {
        VisTable settingsTable = new VisTable();
        settingsTable.setBackground(VisUI.getSkin().getDrawable("menu-bg"));
        settingsTable.setColor(new Color(62f, 62f, 66f, 1f));
        createSettingsTable(settingsTable, "Space Between Points", String.valueOf(embroidery.hatchSpacing), 50, value -> {
            embroidery.hatchSpacing = value;
            refreshPreview();
        });
        createSettingsTable(settingsTable, "WIDTH (MM)", String.valueOf(exportWidth), 50, value -> {
            exportWidth = value;
            refreshPreview();
        });
        createSettingsTable(settingsTable, "HEIGHT (MM)", String.valueOf(exportHeight), 50, value -> {
            exportHeight = value;
            refreshPreview();
        });
        createSettingsTable(settingsTable, "MAX COLORS", String.valueOf(embroidery.maxColors), 50, value -> {
            embroidery.maxColors = value;
            refreshPreview();
        });
        VisCheckBox checkBox = new VisCheckBox(t("fill_mode"));
        checkBox.setChecked(embroidery.fillEnabled);
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                embroidery.fillEnabled = checkBox.isChecked();
                refreshPreview();
            }
        });
        settingsTable.add(checkBox).left().padLeft(10).padRight(10).row();
        ScrollPane scrollPane = new ScrollPane(settingsTable);
        scrollPane.setScrollingDisabled(true, true);
        scrollPane.setPosition(50, 50);
        scrollPane.setSize(250, 200);

        getStage().addActor(scrollPane);
    }
    private void updateDisplayedImage(Texture texture) {
        if (displayedImage != null) {
            displayedImage.remove();
        }
        displayedImage = new Image(texture);
        displayedImage.setSize(500, 500);
        displayedImage.setPosition((1280 - displayedImage.getWidth()) / 2, (720 - displayedImage.getHeight()) / 2);
    }

    public static void refreshPreview(){
        if (displayedImage != null) {
           processImage(); // TODO THIS CAUSE NOT ENOUGHT MEMORY
        }
    }
    private static void processImage() {
        enableEscapeMenu = true;
        showPreview = false;
        embroidery.beginDraw();
        Texture texture = ((TextureRegionDrawable) displayedImage.getDrawable()).getRegion().getTexture();
        Pixmap pixmap;
        TextureData textureData = texture.getTextureData();
        if (!textureData.isPrepared()) {
            textureData.prepare();
        }
        pixmap = textureData.consumePixmap();
        embroidery.image(pixmap, 400, -139, exportWidth, exportHeight);
        embroidery.endDraw();
        pixmap.dispose();
        showPreview = true;
    }

    @Override
    public void render(float delta) {
        super.render(delta);
        boolean isImageAvailable = (displayedImage != null);
        if (isImageAvailable && displayedImage.getParent() == null) {
            getStage().addActor(displayedImage);
        }
        if (saveLocallyButton != null) {
            saveLocallyButton.setDisabled(!isImageAvailable);
        }
        if (saveToDropboxButton != null) {
            saveToDropboxButton.setDisabled(!isImageAvailable);
        }
        if (showPreview && embroidery != null) {
            embroidery.visualizeCache(910,190,visualizeWidth,visualizeHeight);
        }
        if (showPreview && embroidery != null) {
            statsLabel.setText(embroidery.getStatsText());
            statsLabel.setColor(Color.WHITE);
        }
    }

    private void showLoadDialog() {
        DialogManager.showFileChooserDialog(getStage(), selectedImage -> {
            if (selectedImage != null) {
                if (displayedImage != null) {
                    displayedImage.remove();
                }
                displayedImage = selectedImage;
                refreshPreview();
            }
        });
    }
    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE && enableEscapeMenu) {
            // Show exit confirmation dialog
            DialogManager.showExitConfirmationDialog(
                    getStage(),
                    () -> Gdx.app.exit(), // Exit immediately
                    () -> showSaveLocallyDialog(currentSaveLocallyType, () -> Gdx.app.exit()), // Save locally then exit
                    () -> showDropboxDialog(currentSaveDropboxType, () -> Gdx.app.exit()) // Upload then exit
            );
            return true; // Consume the event
        }
        // Existing key handling for image copy
        Texture texture = ApplicationUtil.copyImage(keycode);
        if (texture != null) {
            updateDisplayedImage(texture);
        }
        return false;
    }

    private void showSaveLocallyDialog(SaveType type, Runnable onSuccess) {
        currentSaveLocallyType = type;
        DialogManager.showSaveDialog(currentSaveLocallyType, getStage(), embroidery, embroidery.width, embroidery.height, success -> {
            if (success) {
                enableEscapeMenu = false;
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        });
    }

    private void showDropboxDialog(SaveType type, Runnable onSuccess) {
        currentSaveDropboxType = type;
        DialogManager.showUploadDialog(currentSaveDropboxType, getStage(), embroidery, embroidery.width,embroidery.height, success -> {
            if (success) {
                enableEscapeMenu = false;
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        });
    }
    private void showSaveLocallyDialog(SaveType type) {
        currentSaveLocallyType = type;
        showSaveLocallyDialog(currentSaveLocallyType, null);
    }
    private void showDropboxDialog(SaveType type) {
        currentSaveDropboxType = type;
        showDropboxDialog(currentSaveDropboxType, null);
    }
    public boolean isExitConfirmed() {
        return exitConfirmed;
    }

    public static void confirmExit() {
        exitConfirmed = true;
        Gdx.app.exit(); // Relance la fermeture, cette fois autorisée
    }

    public void handleExitRequest() {
        DialogManager.showExitConfirmationDialog(
                getStage(),
                Main::confirmExit, // Exit direct
                () -> showSaveLocallyDialog(currentSaveLocallyType, Main::confirmExit), // Sauvegarde locale puis exit
                () -> showDropboxDialog(currentSaveDropboxType, Main::confirmExit) // Upload puis exit
        );
    }
}
