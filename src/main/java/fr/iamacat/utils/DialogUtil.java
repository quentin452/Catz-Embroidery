package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.BroideryReader;
import fr.iamacat.embroider.libgdx.utils.BroideryWriter;
import fr.iamacat.utils.enums.SaveType;

import java.io.File;
import java.util.function.Consumer;

import static com.kotcrab.vis.ui.util.dialog.Dialogs.showErrorDialog;
import static fr.iamacat.utils.DropboxUtil.showMessage;
import static fr.iamacat.utils.UIUtils.t;

public class DialogUtil {

    public static void showFileChooserDialog(Stage stage, Consumer<Image> onImageSelected) {
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
            return file.isDirectory() || (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif")|| ext.equals("svg"));
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
                if (ext.equals("svg")) {
                    Drawable thumbnail = this.style.iconFileImage;
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
                // Implement necessary actions if needed
            }

            @Override
            public void viewModeChanged(FileChooser.ViewMode viewMode) {
                // Implement necessary actions if needed
            }
        });

        // Ajouter un écouteur pour gérer la sélection des fichiers
        fileChooser.setListener(new FileChooserAdapter() {
            @Override
            public void selected(Array<FileHandle> files) {
                if (files.size == 0) {
                    System.out.println("No file selected ❌");
                    return;
                }

                FileHandle selectedFile = files.first();
                if (selectedFile != null) {
                    try {
                        System.out.println("File selected: " + selectedFile.file().getAbsolutePath());

                        String filePath = selectedFile.file().getAbsolutePath();
                        String extension = BroideryReader.getFileExtension(filePath);

                        if ("SVG".equalsIgnoreCase(extension)) {
                            // Handle SVG file
                            Texture texture = BroideryReader.readAsTexture(filePath,500,500);
                            Image newImage = new Image(texture);

                            // Resize and position the image
                            float windowWidth = Gdx.graphics.getWidth();
                            float windowHeight = Gdx.graphics.getHeight();
                            newImage.setSize(500, 500);
                            newImage.setPosition((windowWidth - newImage.getWidth()) / 2,
                                    (windowHeight - newImage.getHeight()) / 2);

                            // Pass the new image to the callback
                            onImageSelected.accept(newImage);
                        } else {
                            // Handle non-SVG files as images
                            Texture texture = new Texture(selectedFile);
                            Image newImage = new Image(texture);

                            // Resize and position the image
                            float windowWidth = Gdx.graphics.getWidth();
                            float windowHeight = Gdx.graphics.getHeight();
                            newImage.setSize(500, 500);
                            newImage.setPosition((windowWidth - newImage.getWidth()) / 2,
                                    (windowHeight - newImage.getHeight()) / 2);

                            // Pass the new image to the callback
                            onImageSelected.accept(newImage);
                        }
                    } catch (Exception e) {
                        System.out.println("Failed to load file: " + e.getMessage());
                    }
                }
            }
        });


        fileChooser.setSize(Gdx.graphics.getWidth() * 0.75f, Gdx.graphics.getHeight() * 0.75f);

        // Centrer le FileChooser sur l'écran
        fileChooser.setPosition(Gdx.graphics.getWidth() * 0.125f, Gdx.graphics.getHeight() * 0.125f);

        // Ajouter le FileChooser à la scène et l'afficher
        stage.addActor(fileChooser.fadeIn());
    }

    public static void showSaveDialog(final SaveType saveType, final Stage stage, final PEmbroiderGraphicsLibgdx brodery, float saveWidth, float saveHeight, Consumer<Boolean> onResult) {
        final FileChooser fileChooser = new FileChooser(FileChooser.Mode.SAVE);
        fileChooser.setSelectionMode(FileChooser.SelectionMode.FILES);
        String ext = saveType.toString().toLowerCase();
        fileChooser.setFileFilter(file -> {
            FileHandle fh = new FileHandle(file);
            return file.isDirectory() || fh.extension().equalsIgnoreCase(ext);
        });
        fileChooser.setDefaultFileName("embroidery." + ext);
        fileChooser.setListener(new FileChooserAdapter() {
            @Override
            public void selected(Array<FileHandle> files) {
                if (files.size > 0) {
                    FileHandle file = files.first();
                    saveBroderyFile(stage, file.path(), brodery, saveWidth, saveHeight);
                } else {
                    if (onResult != null) {
                        onResult.accept(false);
                    }
                }
            }
        });
        fileChooser.setSize(Gdx.graphics.getWidth() * 0.75f, Gdx.graphics.getHeight() * 0.75f);
        fileChooser.setPosition(Gdx.graphics.getWidth() * 0.125f, Gdx.graphics.getHeight() * 0.125f);
        stage.addActor(fileChooser.fadeIn());
    }
    private static File saveBroderyFile(Stage stage, String filePath, PEmbroiderGraphicsLibgdx brodery, float saveWidth, float saveHeight) {
        try {
            File file = new File(filePath);
            BroideryWriter.write(file.getAbsolutePath(), brodery.bezierShapes, saveWidth /* * 3.67f*/, saveHeight/*  * 3.67f*/);
            return file;
        } catch (Exception e) {
            showErrorDialog(stage, "Error while saving: " + e.getMessage());
            return null;
        }
    }

    public static void showUploadDialog(final SaveType saveType, final Stage stage, PEmbroiderGraphicsLibgdx brodery, float saveWidth,float saveHeight, Consumer<Boolean> onResult) {
        final TextField fileNameField = new TextField("", UIUtils.visSkin);
        VisDialog dialog = new VisDialog(Translator.getInstance().translate("save_options")) {
            @Override
            protected void result(Object object) {
                if ("DROPBOX".equals(object)) {
                    String fileName = fileNameField.getText();
                    if (fileName.isEmpty()) {
                        showMessage(stage, UIUtils.visSkin, "Filename cannot be empty.");
                        if (onResult != null) {
                            onResult.accept(false);
                        }
                    } else {
                        File imageFile = saveBroderyFile(stage, fileName + "." + saveType.toString(),brodery, saveWidth,saveHeight);
                        if (imageFile != null) {
                            boolean success = DropboxUtil.uploadToDropbox(stage, imageFile);
                            if (onResult != null) {
                                onResult.accept(success);
                            }
                        } else {
                            if (onResult != null) {
                                onResult.accept(false);
                            }
                        }
                    }
                } else {
                    if (onResult != null) {
                        onResult.accept(false);
                    }
                }
            }
        };
        dialog.getContentTable().row().pad(10);
        dialog.getContentTable().add(new Label(t("file_name"), UIUtils.visSkin)).padRight(10);
        dialog.getContentTable().add(fileNameField).width(200);
        dialog.button(Translator.getInstance().translate("save_to_dropbox"), "DROPBOX");
        dialog.button(Translator.getInstance().translate("cancel"), "CANCEL");
        dialog.show(stage);
    }

    public static void showExitConfirmationDialog(Stage stage, Runnable onExit, Runnable onSaveLocally, Runnable onUpload) {
        VisDialog dialog = new VisDialog("Exit Application") {
            @Override
            protected void result(Object object) {
                if (object instanceof String) {
                    String result = (String) object;
                    switch (result) {
                        case "SAVE_LOCALLY":
                            onSaveLocally.run();
                            break;
                        case "UPLOAD":
                            onUpload.run();
                            break;
                        case "EXIT":
                            onExit.run();
                            break;
                        case "CANCEL":
                            // Do nothing
                            break;
                    }
                    hide();
                }
            }
        };

        dialog.text("Do you want to save your work before exiting?");
        dialog.button("Save Locally", "SAVE_LOCALLY");
        dialog.button("Upload to Dropbox", "UPLOAD");
        dialog.button("Exit", "EXIT");
        dialog.button("Cancel", "CANCEL");

        dialog.show(stage);
    }

    public static void showEmptyDialog(Stage stage,String title, String message) {
        new Dialog(title, UIUtils.visSkin) {{
            text(message);
            button("OK");
        }}.show(stage);
    }

    private static Drawable getImageThumbnail(FileHandle fileHandle) {
        Texture texture = new Texture(fileHandle);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }
}
