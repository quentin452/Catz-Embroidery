package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.files.FileHandle;

import javax.imageio.ImageIO;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ApplicationUtil {

    public static Texture copyImage(int keycode) {
        if (keycode == Input.Keys.V && Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            Image pastedImage = ApplicationUtil.pasteImageFromClipboard();
            if (pastedImage != null) {
                Drawable drawable = pastedImage.getDrawable();
                if (drawable instanceof TextureRegionDrawable) {
                    Texture texture = ((TextureRegionDrawable) drawable).getRegion().getTexture();
                    return texture;
                } else {
                    System.out.println("Drawable is not a TextureRegionDrawable.");
                }
            }
            return null;
        }
        return null;
    }

    public static Image pasteImageFromClipboard() {
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);

            if (transferable != null) {
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        BufferedImage bufferedImage = ImageIO.read(file);
                        if (bufferedImage != null) {
                            return createImageFromBufferedImage(bufferedImage);
                        }
                    }
                }

                if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    java.awt.Image awtImage = (java.awt.Image) transferable.getTransferData(DataFlavor.imageFlavor);
                    BufferedImage bufferedImage = (BufferedImage) awtImage;
                    return createImageFromBufferedImage(bufferedImage);
                }

                Logger.getInstance().log(Logger.Project.Converter, "No image found in the clipboard.");
                return null;

            } else {
                Logger.getInstance().log(Logger.Project.Converter, "Clipboard is empty or inaccessible.");
                return null;
            }
        } catch (Exception e) {
            Logger.getInstance().log(Logger.Project.Converter, "Error when pasting image: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static Image createImageFromBufferedImage(BufferedImage bufferedImage) {
        try {
            // Sauvegarder l'image en fichier temporaire
            File tempFile = File.createTempFile("clipboard_image_", ".png"); // TODO AVOID CREATE TEMP FILE
            ImageIO.write(bufferedImage, "PNG", tempFile);

            // Charger l'image depuis le fichier temporaire
            FileHandle fileHandle = new FileHandle(tempFile);
            Texture texture = new Texture(fileHandle);
            Image image = new Image(texture);

            image.setSize(500, 500); // Taille fixe de l'image, ajuste selon tes besoins

            float x = (1280 - image.getWidth()) / 2;
            float y = (720 - image.getHeight()) / 2;
            image.setPosition(x, y);

            // Optionnel : Supprime le fichier temporaire une fois l'image chargée
            tempFile.deleteOnExit();

            return image;
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to create libGDX Image from BufferedImage", e);
        }
    }
}
