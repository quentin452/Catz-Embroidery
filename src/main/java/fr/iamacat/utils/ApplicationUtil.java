package fr.iamacat.utils;

import processing.core.PApplet;
import processing.core.PImage;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

public class ApplicationUtil {
    public static void exitApplication(PApplet p) {
        Logger.getInstance().log(Logger.Project.Converter, Translator.getInstance().translate("closing_the_app"));
        Logger.getInstance().archiveLogs();
        if (p.getSurface().isStopped()) {
            p.exitActual();
        } else {
            p.dispose();
            p.exitActual();
        }
    }

    public static java.awt.Image pasteImageFromClipboard() {
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);

            if (transferable != null) {

                // Vérifier si le presse-papiers contient un fichier image
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        java.awt.Image awtImage = javax.imageio.ImageIO.read(file);
                        if (awtImage != null) {
                            return awtImage;
                        }
                    }
                }

                // Vérifier si le presse-papiers contient une image directement
                if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    return (java.awt.Image) transferable.getTransferData(DataFlavor.imageFlavor);
                }

                // Si aucune image trouvée
                Logger.getInstance().log(Logger.Project.Converter, "Aucune image trouvée dans le presse-papiers.");
                return null;

            } else {
                Logger.getInstance().log(Logger.Project.Converter, "Le presse-papiers est vide ou inaccessible.");
                return null;
            }
        } catch (Exception e) {
            Logger.getInstance().log(Logger.Project.Converter, "Erreur lors du collage de l'image : " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
