package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.embroider.PEmbroiderGraphicsLibgdx;
import fr.iamacat.utils.*;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;
import fr.iamacat.utils.enums.SaveDropboxType;
import fr.iamacat.utils.enums.SaveLocallyType;

import javax.swing.*;

import static fr.iamacat.utils.UIUtils.*;
// TODO update progressBar during file loading/saving
public class Main extends MainBase {
    private final PEmbroiderGraphicsLibgdx embroidery;
    private PopupMenu fileMenu,editMenu;
    private SaveLocallyType currentSaveLocallyType = SaveLocallyType.JPG;
    private SaveDropboxType currentSaveDropboxType = SaveDropboxType.JPG;
    private int exportHeight = 95 , exportWidth = 95,broderyHeight = 95 , broderyWidth = 95;
    public static Image displayedImage;
    private MenuItem saveLocallyButton , saveToDropboxButton;
    private VisTable rootTable;
    private boolean showPreview = false;
    public static boolean enableEscapeMenu = false;
    private Slider progressBar;
    private float visualizationWidth = 95;
    private static boolean exitConfirmed = false;
    private ShapeRenderer shapeRenderer;
    public Main() {
        embroidery = new PEmbroiderGraphicsLibgdx();
        shapeRenderer = new ShapeRenderer();
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
        menuBar.add(fileButton).expandX().fillX().pad(0).left();

        // EDIT MENU
        editMenu = new PopupMenu();
        addSubmenu(editMenu, t("color_mode"), ColorType.class, embroidery::setColorMode);
        addSubmenu(editMenu, t("hatch_mode"), HatchModeType.class, embroidery::setHatchMode);
        VisTextButton editButton = UIUtils.createMenuButton("edit", true, editMenu, getStage());
        menuBar.add(editButton).expandX().fillX().pad(0).left();

        // OTHER
        createSettingsPanel();
        progressBar = new VisSlider(0, 100, 1, false); // Min: 0, Max: 100, Step: 1, Horizontal
        progressBar.setValue(0);
        progressBar.setVisible(false);

        VisTable sliderContainer = new VisTable();
        sliderContainer.add(new VisLabel(Translator.getInstance().translate("progress"))).padRight(10);
        sliderContainer.add(progressBar).width(300).height(20);
        sliderContainer.setPosition((float) Gdx.graphics.getWidth() / 2 - 150, 240);
    }
    private void createSettingsPanel() {
        VisTable settingsTable = new VisTable();
        settingsTable.setBackground(VisUI.getSkin().getDrawable("menu-bg"));
        settingsTable.setColor(new Color(62f, 62f, 66f, 1f));
        createSettingsTable(settingsTable, "Space Between Strokes", String.valueOf(embroidery.getStrokeSpacing()), 50, value -> {
            embroidery.setStrokeSpacing(value);
            refreshPreview();
        });
        createSettingsTable(settingsTable, "Space Between Points", String.valueOf(embroidery.getHatchSpacing()), 50, value -> {
            embroidery.setHatchSpacing(value);
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
        createSettingsTable(settingsTable, "STROKE WEIGHT", String.valueOf(embroidery.getStrokeWeight()), 50, value -> {
            embroidery.setStrokeWeight(value);
            refreshPreview();
        });
        createSettingsTable(settingsTable, "MAX COLORS", String.valueOf(embroidery.getMaxColors()), 50, value -> {
            embroidery.setMaxColors(value);
            refreshPreview();
        });
        VisCheckBox checkBox = new VisCheckBox(t("fill_mode"));
        checkBox.setChecked(embroidery.getFill());
        checkBox.addListener(event -> {embroidery.setFill(checkBox.isChecked());return true;});
        settingsTable.add(checkBox).left().padLeft(10).padRight(10).row();

        ScrollPane scrollPane = new ScrollPane(settingsTable);
        scrollPane.setScrollingDisabled(false, true);
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
        displayedImage.setPosition((Gdx.graphics.getWidth() - displayedImage.getWidth()) / 2, (Gdx.graphics.getHeight() - displayedImage.getHeight()) / 2);
    }

    public void refreshPreview(){
        if (displayedImage != null) {
            processImage();
        }
    }
    private void processImage() {
        enableEscapeMenu = true;
        showPreview = false;
        updateProgress(0, true);
        embroidery.beginDraw();
        embroidery.setHatchMode(HatchModeType.Parallel);
        embroidery.setHatchSpacing(embroidery.getHatchSpacing());
        updateProgress(10);
        Texture texture = ((TextureRegionDrawable) displayedImage.getDrawable()).getRegion().getTexture();
        texture.getTextureData().prepare();
        Pixmap pixmap = texture.getTextureData().consumePixmap();
        embroidery.setColorMode(ColorType.Realistic);
        embroidery.image(pixmap, 0, 0,broderyWidth,broderyHeight);

        embroidery.endDraw();

        pixmap.dispose();
        updateProgress(100);
        showPreview = true;
    }

    private void updateProgress(int value) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(value));
    }

    // Surcharge pour gérer la visibilité
    private void updateProgress(int value, boolean visible) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            progressBar.setVisible(visible);
        });
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
            embroidery.visualize(
                    shapeRenderer,
                    550,
                    250
            );
        }
    }

    private void showLoadDialog() {
        DialogUtil.showFileChooserDialog(getStage(), selectedImage -> {
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
            DialogUtil.showExitConfirmationDialog(
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

    private void showSaveLocallyDialog(SaveLocallyType type, Runnable onSuccess) {
        currentSaveLocallyType = type;
        DialogUtil.showSaveDialog(currentSaveLocallyType, getStage(), displayedImage, success -> {
            if (success) {
                enableEscapeMenu = false;
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        });
    }

    private void showDropboxDialog(SaveDropboxType type, Runnable onSuccess) {
        currentSaveDropboxType = type;
        DialogUtil.showUploadDialog(currentSaveDropboxType, getStage(), displayedImage, success -> {
            if (success) {
                enableEscapeMenu = false;
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        });
    }
    private void showSaveLocallyDialog(SaveLocallyType type) {
        currentSaveLocallyType = type;
        showSaveLocallyDialog(currentSaveLocallyType, null);
    }
    private void showDropboxDialog(SaveDropboxType type) {
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
        DialogUtil.showExitConfirmationDialog(
                getStage(),
                () -> confirmExit(), // Exit direct
                () -> showSaveLocallyDialog(currentSaveLocallyType, Main::confirmExit), // Sauvegarde locale puis exit
                () -> showDropboxDialog(currentSaveDropboxType, Main::confirmExit) // Upload puis exit
        );
    }
}
