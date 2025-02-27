package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import fr.iamacat.PEmbroiderLauncher;
import fr.iamacat.utils.enums.SaveDropboxType;
import fr.iamacat.utils.enums.SaveLocallyType;
import jdk.jshell.execution.Util;

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
                    } catch (Exception e) {
                        System.out.println("Failed to load image: " + e.getMessage());
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

    public static void showSaveDialog(final SaveLocallyType saveType, final Stage stage, final Image imageToSave) {
        // Créer un FileChooser en mode SAVE
        final FileChooser fileChooser = new FileChooser(FileChooser.Mode.SAVE);
        fileChooser.setSelectionMode(FileChooser.SelectionMode.FILES);

        // Filtrer les fichiers selon l'extension correspondant au type de sauvegarde
        String ext = saveType.toString().toLowerCase();  // par exemple "png" ou "pes"
        fileChooser.setFileFilter(file -> {
            FileHandle fh = new FileHandle(file);
            return file.isDirectory() || fh.extension().toLowerCase().equals(ext);
        });

        // Définir un nom de fichier par défaut
        fileChooser.setDefaultFileName("embroidery" + "." + ext);

        // Écouteur de sélection
        fileChooser.setListener(new FileChooserAdapter() {
            @Override
            public void selected(Array<FileHandle> files) {
                // Save to local files
                FileHandle selectedFile = files.first();
                if (selectedFile != null) {
                    String filePath = selectedFile.file().getAbsolutePath();
                    System.out.println("Fichier sélectionné pour sauvegarde : " + filePath);
                    saveImageToFile(stage,selectedFile,saveType,imageToSave);
                }
            }
        });

        // Définir taille et position du FileChooser
        fileChooser.setSize(Gdx.graphics.getWidth() * 0.75f, Gdx.graphics.getHeight() * 0.75f);
        fileChooser.setPosition(Gdx.graphics.getWidth() * 0.125f, Gdx.graphics.getHeight() * 0.125f);

        // Afficher le FileChooser
        stage.addActor(fileChooser.fadeIn());
    }

    // Méthode pour sauvegarder l'image dans le fichier sélectionné
    private static boolean saveImageToFile(Stage stage, FileHandle file, SaveLocallyType saveType, Image imageToSave) {
        try {
            // Récupérer la texture depuis l'image (en supposant un TextureRegionDrawable)
            TextureRegionDrawable drawable = (TextureRegionDrawable) imageToSave.getDrawable();
            Texture texture = drawable.getRegion().getTexture();

            // Vérifier et préparer la texture si nécessaire
            if (!texture.getTextureData().isPrepared()) {
                texture.getTextureData().prepare();
            }

            // Extraire le Pixmap de la texture
            Pixmap pixmap = texture.getTextureData().consumePixmap();
            // TODO SUPPORT PES ETC..
            if (saveType == SaveLocallyType.PES) {
                // Implémentation spécifique pour le format PES (à remplacer par ton propre code)
                System.out.println("Sauvegarde en PES - implémentation spécifique requise !");
                // Exemple : FileUtils.writeByteArrayToFile(file.file(), convertToPES(pixmap));
                showErrorDialog(stage, "Le format PES nécessite une implémentation spécifique !");
                return false;
            } else {
                // Pour PNG, JPG, etc.
                PixmapIO.writePNG(file, pixmap);
            }

            // Nettoyage du Pixmap après utilisation pour éviter les fuites mémoire
            pixmap.dispose();

            return true;
        } catch (Exception e) {
            showErrorDialog(stage, "Erreur lors de la sauvegarde : " + e.getMessage());
            return false;
        }
    }

    // Méthode qui affiche une boîte de dialogue pour uploader sur Dropbox
    public static void showUploadDialog(final SaveDropboxType saveType, final Stage stage, final Image imageToSave) {
        final TextField fileNameField = new TextField("", UIUtils.visSkin);

        VisDialog dialog = new VisDialog(Translator.getInstance().translate("save_options")) {
            @Override
            protected void result(Object object) {
                if ("DROPBOX".equals(object)) {
                    // Récupérer le nom du fichier saisi par l'utilisateur
                    String fileName = fileNameField.getText(); // Assurez-vous que fileNameField est la référence au champ texte
                    if (fileName.isEmpty()) {
                        Gdx.app.postRunnable(() -> showMessage(stage, UIUtils.visSkin, "Le nom du fichier ne peut pas être vide."));
                    } else {
                        File imageFile = convertImageToFile(imageToSave, saveType, fileName);
                        if (imageFile != null) {
                            DropboxUtil.uploadToDropbox(stage, imageFile);
                        } else {
                            Gdx.app.postRunnable(() -> showMessage(stage, UIUtils.visSkin, "Erreur lors de la conversion de l'image."));
                        }
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
    private static File convertImageToFile(Image image, SaveDropboxType saveType, String fileName) {
        // TODO SUPPORT PES ETC..
        File file = new File(Gdx.files.getLocalStoragePath(), fileName + "." + saveType.toString().toLowerCase());

        try {
            // Récupérer la texture originale
            Texture texture = ((TextureRegionDrawable) image.getDrawable()).getRegion().getTexture();

            // Extraire les données brutes comme pour la sauvegarde locale
            if (!texture.getTextureData().isPrepared()) {
                texture.getTextureData().prepare();
            }

            Pixmap pixmap = texture.getTextureData().consumePixmap();

            // Sauvegarder le Pixmap original
            PixmapIO.writePNG(new FileHandle(file), pixmap);

            pixmap.dispose();

            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Drawable getImageThumbnail(FileHandle fileHandle) {
        Texture texture = new Texture(fileHandle);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }
}
