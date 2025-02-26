package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;

public class DialogUtil {
    public static void showFileChooserDialog(Stage stage, final ImageWrapper imageWrapper) {
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
                // Gérer le fichier sélectionné
                FileHandle selectedFile = files.first();  // Récupérer le premier fichier sélectionné
                if (selectedFile != null) {
                    String filePath = selectedFile.file().getAbsolutePath();
                    System.out.println("Fichier sélectionné : " + filePath);

                    // Charger l'image sélectionnée et l'afficher au centre de l'écran
                    if (imageWrapper.image != null) {
                        imageWrapper.image.remove();  // Supprimer l'image précédente si elle existe
                    }

                    Texture texture = new Texture(selectedFile);  // Charger l'image en texture
                    Image newImage = new Image(texture);  // Créer un Image avec cette texture

                    // Redimensionner l'image en fonction de la taille de la fenêtre
                    float windowWidth = Gdx.graphics.getWidth();
                    float windowHeight = Gdx.graphics.getHeight();

                    // Redimensionner l'image
                    newImage.setSize(500, 500);

                    // Centrer l'image sur l'écran
                    float x = (windowWidth - newImage.getWidth()) / 2;
                    float y = (windowHeight - newImage.getHeight()) / 2;
                    newImage.setPosition(x, y);

                    stage.addActor(newImage);  // Ajouter l'image à la scène

                    // Update the image reference in the wrapper
                    imageWrapper.image = newImage;
                }
            }
        });

        fileChooser.setSize(Gdx.graphics.getWidth() * 0.75f, Gdx.graphics.getHeight() * 0.75f);

        // Centrer le FileChooser sur l'écran
        fileChooser.setPosition(Gdx.graphics.getWidth() * 0.125f, Gdx.graphics.getHeight() * 0.125f);

        // Ajouter le FileChooser à la scène et l'afficher
        stage.addActor(fileChooser.fadeIn());
    }

    private static Drawable getImageThumbnail(FileHandle fileHandle) {
        Texture texture = new Texture(fileHandle);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    // Wrapper class to hold Image reference
    public static class ImageWrapper {
        public Image image;
    }
}
