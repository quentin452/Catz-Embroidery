package fr.iamacat.utils;

import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class DropboxUtil {
    public static DbxClientV2 dropboxClient;

    public static void loadTokenFromJson()
    {
        String savedToken = Saving.loadDropboxToken();
        if (savedToken != null) {
            DbxRequestConfig config = DbxRequestConfig.newBuilder("Catz-Embroidery").build();
            DropboxUtil.dropboxClient = new DbxClientV2(config, savedToken);
            Logger.getInstance().log(Logger.Project.NONE, "Connexion Dropbox automatique réussie !");
        }
    }

    public static void connectToDropbox() {
        String APP_KEY = "wypcm2tcp2tufdf";
        String APP_SECRET = "5izs475if2flun6";

        DbxRequestConfig config = DbxRequestConfig.newBuilder("Catz-Embroidery").build();
        DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
        DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);

        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .build();

        String authorizeUrl = webAuth.authorize(webAuthRequest);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.browse(new URI(authorizeUrl));
            } else {
                Logger.getInstance().log(Logger.Project.Launcher, "Desktop not supported, please open the following URL manually: " + authorizeUrl);
            }
        } catch (IOException | URISyntaxException e) {
            Logger.getInstance().log(Logger.Project.Launcher, "Error opening browser: " + e.getMessage());
        }

        Logger.getInstance().log(Logger.Project.Launcher, "1. Go to: " + authorizeUrl);
        Logger.getInstance().log(Logger.Project.Launcher, "2. Click \"Allow\" (you might have to log in first)");
        Logger.getInstance().log(Logger.Project.Launcher, "3. Copy the authorization code.");

        String code = JOptionPane.showInputDialog("Enter the authorization code here:");
        if (code == null) {
            return;
        }
        try {
            DbxAuthFinish authFinish = webAuth.finishFromCode(code);
            String accessToken = authFinish.getAccessToken();
            dropboxClient = new DbxClientV2(config, accessToken);
            Saving.saveDropboxToken(accessToken);
            Logger.getInstance().log(Logger.Project.Launcher, "Successfully connected to Dropbox.");
        } catch (DbxException e) {
            Logger.getInstance().log(Logger.Project.Launcher, "Error connecting to Dropbox: " + e.getMessage());
        }
    }


    public static void uploadToDropbox(File localFile) {
        if (dropboxClient == null) {
            JOptionPane.showMessageDialog(null, "Connectez-vous à Dropbox d'abord.");
            return;
        }
        String fileName = localFile.getName();
        String dropboxPath = "/Catz-Embroidery/" + fileName;
        try {
            int counter = 1;
            String originalFileName = fileName;
            while (fileExistsInDropbox(dropboxPath)) {
                fileName = originalFileName.replaceFirst("(\\.[^\\.]+)$", "_" + counter + "$1");
                dropboxPath = "/Catz-Embroidery/" + fileName;
                counter++;
            }
            try (InputStream inputStream = new FileInputStream(localFile)) {
                FileMetadata metadata = dropboxClient.files().uploadBuilder(dropboxPath)
                        .withMode(WriteMode.ADD)
                        .uploadAndFinish(inputStream);
                JOptionPane.showMessageDialog(null, "Fichier uploadé sur Dropbox : " + metadata.getPathDisplay());
            }
        } catch (DbxException | IOException e) {
            Logger.getInstance().log(Logger.Project.Launcher, "Erreur lors de l'upload sur Dropbox : " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Erreur lors de l'upload du fichier sur Dropbox.");
        }
    }

    private static boolean fileExistsInDropbox(String dropboxPath) {
        try {
            ListFolderResult result = dropboxClient.files().listFolder("/Catz-Embroidery");
            for (Metadata metadata : result.getEntries()) {
                if (metadata.getPathLower().equals(dropboxPath.toLowerCase())) {
                    return true;
                }
            }
        } catch (DbxException e) {
            Logger.getInstance().log(Logger.Project.Launcher, "Erreur lors de la vérification de l'existence du fichier : " + e.getMessage());
        }
        return false;
    }
}
