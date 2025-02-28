package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import fr.iamacat.embroider.PEmbroiderGraphics;
import fr.iamacat.utils.*;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;
import fr.iamacat.utils.enums.SaveDropboxType;
import fr.iamacat.utils.enums.SaveLocallyType;

import javax.swing.*;

import static fr.iamacat.utils.UIUtils.*;
// TODO update progressBar during file loading/saving
public class Main extends MainBase {
    private PEmbroiderGraphics embroidery;
    private PopupMenu fileMenu,editMenu,colorModeMenu,hatchModeMenu,saveLocallyTypeMenu,saveToDropboxTypeMenu;
    private ColorType currentColorType = ColorType.MultiColor;
    private HatchModeType currentHatchModeType = HatchModeType.Parallel;
    private SaveLocallyType currentSaveLocallyType = SaveLocallyType.JPG;
    private SaveDropboxType currentSaveDropboxType = SaveDropboxType.JPG;
    private int spaceBetweenPoints = 10 , exportHeight = 95 , exportWidth = 95 , maxColors = 10 , strokeWeight = 20;
    public static Image displayedImage;
    private MenuItem saveLocallyButton , saveToDropboxButton;
    public boolean FillB = true;
    private VisTable rootTable;
    private boolean showPreview = false;
    public static boolean enableEscapeMenu = false;
    private Slider progressBar;
    private float visualizationWidth = 95;
    private float visualizationHeight = 95;
    private static boolean exitConfirmed = false;
    public Main() {
        rootTable = new VisTable();
        rootTable.setFillParent(true);
        getStage().addActor(rootTable);
        createMenu();
        embroidery = new PEmbroiderGraphics(this, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), visSkin);
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
        colorModeMenu = addSubmenu(editMenu, t("color_mode"), ColorType.class, this::setColorMode);
        hatchModeMenu = addSubmenu(editMenu, t("hatch_mode"), HatchModeType.class, this::setHatchMode);
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
        sliderContainer.setPosition(Gdx.graphics.getWidth() / 2 - 150, 240);
    }
    private void createSettingsPanel() {
        VisTable settingsTable = new VisTable();
        settingsTable.setBackground(VisUI.getSkin().getDrawable("menu-bg"));
        settingsTable.setColor(new Color(62f, 62f, 66f, 1f));
        createSettingsTable(settingsTable, "Space Between Points", String.valueOf(spaceBetweenPoints), 50, value -> {
            spaceBetweenPoints = value;
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
        createSettingsTable(settingsTable, "STROKE WEIGHT", String.valueOf(strokeWeight), 50, value -> {
            strokeWeight = value;
            refreshPreview();
        });
        createSettingsTable(settingsTable, "MAX COLORS", String.valueOf(maxColors), 50, value -> {
            maxColors = value;
            refreshPreview();
        });
        VisCheckBox checkBox = new VisCheckBox(t("fill_mode"));
        checkBox.setChecked(FillB);
        checkBox.addListener(event -> FillB = checkBox.isChecked());
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

    private void setHatchMode(HatchModeType type) {
        currentHatchModeType = type;
        setMenuItemChecked(hatchModeMenu, type.toString());
    }

    private void setColorMode(ColorType type) {
        currentColorType = type;
        setMenuItemChecked(colorModeMenu, type.toString());
    }

    public void refreshPreview(){
        if (displayedImage != null) {
            processImageWithProgress(); // TODO
        }
    }

    private void processImageWithProgress() {
        enableEscapeMenu = true;
        showPreview = false;
        updateProgress(0, true);
        new Thread(() -> {
            try {
                embroidery.popyLineMulticolor = currentColorType == ColorType.MultiColor;
                embroidery.beginDraw();
                embroidery.clear();
               //displayedImage.setSize(1000, 1000);
                embroidery.colorizeEmbroideryFromImage = currentColorType == ColorType.Realistic;
                if (embroidery.colorizeEmbroideryFromImage) {
                    Texture texture = ((TextureRegionDrawable) displayedImage.getDrawable()).getRegion().getTexture();
                    embroidery.extractedColors = embroidery.extractColorsFromImage(texture);
                }
                updateProgress(10);
                embroidery.beginCull();

                switch (currentHatchModeType) {
                    case Cross:
                        embroidery.hatchMode(PEmbroiderGraphics.CROSS);
                        break;
                    case Parallel:
                        embroidery.hatchMode(PEmbroiderGraphics.PARALLEL);
                        break;
                    case Concentric:
                        embroidery.hatchMode(PEmbroiderGraphics.CONCENTRIC);
                        break;
                    case Spiral:
                        embroidery.hatchMode(PEmbroiderGraphics.SPIRAL);
                        break;
                    case PerlinNoise:
                        embroidery.hatchMode(PEmbroiderGraphics.PERLIN);
                        break;
                    default:
                        embroidery.hatchMode(PEmbroiderGraphics.CROSS);
                }
                embroidery.hatchSpacing(spaceBetweenPoints);
                embroidery.strokeWeight(strokeWeight);
                embroidery.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
                embroidery.strokeSpacing(spaceBetweenPoints);
                embroidery.stroke(0, 0, 0);
                updateProgress(40);
                if (!FillB) {
                    embroidery.noFill();
                } else {
                    embroidery.fill(0, 0, 0);
                }
                Texture texture = ((TextureRegionDrawable) displayedImage.getDrawable()).getRegion().getTexture();
                texture.getTextureData().prepare();
                Pixmap pixmap = texture.getTextureData().consumePixmap();
                embroidery.image(pixmap, 860, 70);
                embroidery.endCull();
                updateProgress(80);
                SwingUtilities.invokeLater(() -> {
                    showPreview = true;
                    updateProgress(100, false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                });
            }
        }).start();
    }
    private void updateProgress(int value) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
        });
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
            if (embroidery.polylines != null && !embroidery.polylines.isEmpty() && !embroidery.colors.isEmpty()) {
                embroidery.visualize(true, false, false, Integer.MAX_VALUE,
                        visualizationWidth * 2.71430f,
                        visualizationHeight * 2.71430f, +550, 250);
            }
            if (embroidery.polylines.isEmpty()) {
                //System.out.println("polylines is empty"); // TODO FIX THIS BUG
            }
            if (embroidery.colors.isEmpty()) {
                //System.out.println("colors is empty"); // TODO FIX THIS BUG
            }
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
