package fr.iamacat.pembroider_converter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import fr.iamacat.utils.UIUtils;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class Main implements Screen, Translatable {
    private Stage stage;
    private PopupMenu fileMenu;
    private PopupMenu editMenu;
    private PopupMenu colorModeMenu;
    private ColorType currentColorType = ColorType.MultiColor;
    private Image displayedImage;

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

        colorModeMenu = new PopupMenu();
        colorModeMenu.addItem(createColorMenuItem(ColorType.MultiColor));
        colorModeMenu.addItem(createColorMenuItem(ColorType.BlackAndWhite));
        colorModeMenu.addItem(createColorMenuItem(ColorType.Realistic));

        // Création du menu "Edit"
        editMenu = UIUtils.createPopupMenu(
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
        // Mettre à jour l'affichage du menu
        colorModeMenu.getChildren().forEach(actor -> {
            MenuItem item = (MenuItem) actor;
            item.setChecked(item.getText().equals(type.toString()));
        });
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
        // Définir le nom des préférences pour les emplacements favoris
        String userHome = System.getProperty("user.home");
        FileChooser.setFavoritesPrefsName(userHome + "/.pembroider_converter");

        // Créer l'instance du FileChooser
        final FileChooser fileChooser = new FileChooser(FileChooser.Mode.OPEN);

        // Configurer le FileChooser pour sélectionner uniquement les fichiers
        fileChooser.setSelectionMode(FileChooser.SelectionMode.FILES);

        // Ajouter un filtre pour n'afficher que les fichiers PES et PNG
        fileChooser.setFileFilter(file -> {
            FileHandle fileHandle = new FileHandle(file);
            String ext = fileHandle.extension().toLowerCase();
            return file.isDirectory() || (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif"));
        });

        // Définir un fournisseur d'icônes personnalisé
        fileChooser.setIconProvider(new FileChooser.DefaultFileIconProvider(fileChooser) {
            @Override
            public Drawable provideIcon(FileChooser.FileItem item) {
                float iconSize = 48f;

                // Si l'élément est un répertoire
                if (item.isDirectory()) {
                    return getDirIcon(item);
                }

                // Récupérer le fichier et son extension
                FileHandle file = item.getFile();
                String ext = file.extension().toLowerCase();

                // Si c'est un fichier d'image, afficher la miniature
                if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif")) {
                    Drawable thumbnail = getImageThumbnail(file); // Crée la miniature
                    thumbnail.setMinWidth(iconSize);  // Définir la largeur de l'icône
                    thumbnail.setMinHeight(iconSize); // Définir la hauteur de l'icône
                    return thumbnail;
                }

                // Si c'est un autre type de fichier, afficher l'icône par défaut
                Drawable defaultIcon = getDefaultIcon(item);
                defaultIcon.setMinWidth(iconSize);  // Définir la largeur de l'icône
                defaultIcon.setMinHeight(iconSize); // Définir la hauteur de l'icône
                return defaultIcon;
            }


            @Override
            public boolean isThumbnailModesSupported() {
                return true;
            }

            @Override
            public void directoryChanged(FileHandle fileHandle) {

            }

            @Override
            public void viewModeChanged(FileChooser.ViewMode viewMode) {

            }
        });

        // Ajouter un écouteur pour gérer la sélection des fichiers
        fileChooser.setListener(new FileChooserAdapter() {
            @Override
            public void selected(Array<FileHandle> files) {
                // Gérer le fichier sélectionné
                FileHandle selectedFile = files.first();  // Récupérer le premier fichier sélectionné
                if (selectedFile != null) {
                    String filePath = selectedFile.file().getAbsolutePath();
                    System.out.println("Fichier sélectionné : " + filePath);

                    // Charger l'image sélectionnée et l'afficher au centre de l'écran
                    if (displayedImage != null) {
                        displayedImage.remove();  // Supprimer l'image précédente si elle existe
                    }

                    Texture texture = new Texture(selectedFile);  // Charger l'image en texture
                    displayedImage = new Image(texture);  // Créer un Image avec cette texture

                    // Redimensionner l'image en fonction de la taille de la fenêtre
                    float windowWidth = Gdx.graphics.getWidth();
                    float windowHeight = Gdx.graphics.getHeight();

                    // Redimensionner l'image
                    displayedImage.setSize(500, 500);

                    // Centrer l'image sur l'écran
                    float x = (windowWidth - displayedImage.getWidth()) / 2;
                    float y = (windowHeight - displayedImage.getHeight()) / 2;
                    displayedImage.setPosition(x, y);

                    stage.addActor(displayedImage);  // Ajouter l'image à la scène
                }
            }
        });

        fileChooser.setSize(Gdx.graphics.getWidth() * 0.75f, Gdx.graphics.getHeight() * 0.75f);

        // Centrer le FileChooser sur l'écran
        fileChooser.setPosition(Gdx.graphics.getWidth() * 0.125f, Gdx.graphics.getHeight() * 0.125f);

        // Ajouter le FileChooser à la scène et l'afficher
        stage.addActor(fileChooser.fadeIn());
    }
    private Drawable getImageThumbnail(FileHandle fileHandle) {
        Texture texture = new Texture(fileHandle);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }


    private void changeColorMode() {

    }
}
