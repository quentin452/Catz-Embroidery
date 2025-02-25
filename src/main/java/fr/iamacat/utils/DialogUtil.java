package fr.iamacat.utils;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.util.function.Consumer;

public class DialogUtil {

    public static void showExitDialog(
            boolean enableEscapeMenu,
            boolean hasDropbox,
            Runnable saveAndExitHandler,
            Consumer<File> dropboxSaveHandler,
            Runnable exitHandler
    ) {
        if (!enableEscapeMenu) {
            exitHandler.run();
            return;
        }

        // Construction des options
        String[] options;
        if (hasDropbox) {
            options = new String[]{
                    Translator.getInstance().translate("save_and_quit"),
                    Translator.getInstance().translate("save_to_dropbox"),
                    Translator.getInstance().translate("exit_without_save"),
                    Translator.getInstance().translate("cancel")
            };
        } else {
            options = new String[]{
                    Translator.getInstance().translate("save_and_quit"),
                    Translator.getInstance().translate("exit_without_save"),
                    Translator.getInstance().translate("cancel")
            };
        }

        // Affichage de la boîte de dialogue
        int option = JOptionPane.showOptionDialog(
                null,
                Translator.getInstance().translate("save_sentence_1"),
                Translator.getInstance().translate("confirm_closing"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );

        // Gestion des résultats
        if (hasDropbox) {
            handleDropboxOptions(option, saveAndExitHandler, dropboxSaveHandler, exitHandler);
        } else {
            handleBasicOptions(option, saveAndExitHandler, exitHandler);
        }
    }

    private static void handleDropboxOptions(
            int option,
            Runnable saveAndExit,
            Consumer<File> dropboxSave,
            Runnable exit
    ) {
        switch (option) {
            case 0: // Save and quit
                saveAndExit.run();
                break;
            case 1: // Save to Dropbox
                handleDropboxFileSelection(dropboxSave);
                break;
            case 2: // Exit without save
                exit.run();
                break;
            default: // Cancel
                break;
        }
    }

    private static void handleBasicOptions(
            int option,
            Runnable saveAndExit,
            Runnable exit
    ) {
        switch (option) {
            case 0: // Save and quit
                saveAndExit.run();
                break;
            case 1: // Exit without save
                exit.run();
                break;
            default: // Cancel
                break;
        }
    }

    private static void handleDropboxFileSelection(Consumer<File> dropboxSaveHandler) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a file to upload to Dropbox");
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            dropboxSaveHandler.accept(selectedFile);
        } else {
            JOptionPane.showMessageDialog(null, "No file selected.");
        }
    }

    public static void showSavingDialog(
            boolean hasDropbox,
            Runnable saveLocallyHandler, // Action de sauvegarde locale
            Consumer<File> saveToDropboxHandler // Action pour la sauvegarde sur Dropbox
    ) {
        String[] options = {
                Translator.getInstance().translate("save_locally"),
                Translator.getInstance().translate("save_to_dropbox"),
                Translator.getInstance().translate("cancel")
        };

        // Affichage de la boîte de dialogue
        int option = JOptionPane.showOptionDialog(
                null,
                Translator.getInstance().translate("save_sentence_1"),
                Translator.getInstance().translate("confirm_saving"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );

        // Gestion des actions en fonction de l'option choisie
        switch (option) {
            case 0: // Sauvegarde locale
                saveLocallyHandler.run();
                break;
            case 1: // Sauvegarde sur Dropbox (si Dropbox est présent)
                if (hasDropbox) {
                    handleDropboxFileSelection(saveToDropboxHandler);
                }
                break;
            default: // Annulation
                break;
        }
    }

}