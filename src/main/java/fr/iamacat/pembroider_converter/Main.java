package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.*;
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
        addSubmenu(editMenu, t("color_mode"), ColorType.class, this::setColorMode);
        addSubmenu(editMenu, t("hatch_mode"), HatchModeType.class, this::setHatchMode);
        addMenuCheckbox(editMenu, t("fill_mode"), FillB, checked -> FillB = checked);
        VisTextButton editButton = UIUtils.createMenuButton("edit", true, editMenu, getStage());
        editButton.getLabel().setAlignment(Align.left);
        menuBar.add(editButton).expandX().fillX().pad(0).left();
    }


    private void updateDisplayedImage(Texture texture) {
        if (displayedImage != null) {
            displayedImage.remove();
        }
        displayedImage = new Image(texture);
        float windowWidth = Gdx.graphics.getWidth();
        float windowHeight = Gdx.graphics.getHeight();
        displayedImage.setSize(500, 500);
        displayedImage.setPosition((windowWidth - displayedImage.getWidth()) / 2, (windowHeight - displayedImage.getHeight()) / 2);
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
}
