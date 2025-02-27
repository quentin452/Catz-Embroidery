package fr.iamacat.utils;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;

import com.badlogic.gdx.Gdx;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static fr.iamacat.utils.UIUtils.t;

public class DropboxUtil {
    public static DbxClientV2 dropboxClient;

    public static void loadTokenFromJson() {
        try {
            String savedToken = Saving.loadDropboxToken();
            if (savedToken != null) {
                DbxRequestConfig config = DbxRequestConfig.newBuilder("Catz-Embroidery").build();
                DropboxUtil.dropboxClient = new DbxClientV2(config, savedToken);
                // Validate the token (you may need to adjust this part according to Dropbox SDK)
                if (validateDropboxToken(DropboxUtil.dropboxClient)) {
                    Logger.getInstance().log(Logger.Project.NONE, "Connexion Dropbox automatique réussie !");
                } else {
                    dropboxClient = null;
                    Logger.getInstance().log(Logger.Project.NONE, "Le token Dropbox est invalide.");
                }
            } else {
                Logger.getInstance().log(Logger.Project.NONE, "Aucun token Dropbox trouvé.");
            }
        } catch (Exception e) {
            Logger.getInstance().log(Logger.Project.NONE, "Erreur lors de la connexion automatique à Dropbox: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean validateDropboxToken(DbxClientV2 client) {
        try {
            // Perform an API call to validate the token
            client.users().getCurrentAccount();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public static void connectToDropbox(final Stage stage, final Skin skin) {
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
            // Open the authorization URL in the browser
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.browse(new URI(authorizeUrl));
            } else {
                Logger.getInstance().log(Logger.Project.Launcher, "Desktop not supported, please open the following URL manually: " + authorizeUrl);
            }

            // Create the dialog to ask for the authorization code
            final Dialog dialog = new Dialog("Dropbox Authorization", skin) {
                @Override
                protected void result(Object object) {
                    // This prevents the dialog from closing on any default actions.
                    // We will handle closing manually based on button clicks.
                }
            };
            dialog.text(t("enter_auth_code_bellow"));

            final TextField authCodeField = new TextField("", skin);
            dialog.getContentTable().add(authCodeField).width(200).padBottom(10);

            // Buttons
            dialog.button(t("submit"), true);
            dialog.button(t("cancel"), false);

            // Add ESC key listener
            dialog.addListener(new InputListener() {
                @Override
                public boolean keyUp(InputEvent event, int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                        dialog.hide(); // Close on ESC
                        return true;
                    }
                    return false;
                }
            });

            // Show the dialog
            dialog.show(stage);

            // Handle button clicks
            dialog.getButtonTable().getCells().get(0).getActor().addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // Handle the submission of the authorization code
                    String code = authCodeField.getText();
                    if (!code.isEmpty()) {
                        try {
                            DbxAuthFinish authFinish = webAuth.finishFromCode(code);
                            String accessToken = authFinish.getAccessToken();
                            dropboxClient = new DbxClientV2(config, accessToken);
                            Saving.saveDropboxToken(accessToken);
                            Logger.getInstance().log(Logger.Project.Launcher, "Successfully connected to Dropbox.");
                            dialog.hide(); // Hide the dialog when successful
                            Gdx.app.postRunnable(() -> showMessage(stage, skin, t("connection_ok_dropbox")));
                        } catch (DbxException e) {
                            Logger.getInstance().log(Logger.Project.Launcher, "Error connecting to Dropbox: " + e.getMessage());
                            Gdx.app.postRunnable(() -> showMessage(stage, skin, t("error_connecting_dropbox") + e.getMessage()));
                        }
                    } else {
                        Gdx.app.postRunnable(() -> showMessage(stage, skin, t("pls_valid_auth_code")));
                    }
                }
            });

            // Handle "Cancel" button
            dialog.getButtonTable().getCells().get(1).getActor().addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    dialog.hide(); // Hide on cancel
                }
            });

        } catch (IOException | URISyntaxException e) {
            Logger.getInstance().log(Logger.Project.Launcher, "Error opening browser: " + e.getMessage());
            Gdx.app.postRunnable(() -> showMessage(stage, skin, t("error_opening_browser") + e.getMessage()));
        }
    }


    static void showMessage(Stage stage, Skin skin, String message) {
        final Dialog messageDialog = new Dialog("Message", skin);
        messageDialog.text(message);
        messageDialog.button(Translator.getInstance().translate("ok"), true);
        messageDialog.show(stage);
    }

    public static void uploadToDropbox(Stage stage,File localFile) {
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
                Gdx.app.postRunnable(() -> showMessage(stage, UIUtils.visSkin, t("file_uploaded_dropbox") + metadata.getPathDisplay()));
            }
        } catch (DbxException | IOException e) {
            Logger.getInstance().log(Logger.Project.Launcher, "Erreur lors de l'upload sur Dropbox : " + e.getMessage());
            Gdx.app.postRunnable(() -> showMessage(stage, UIUtils.visSkin, t("error_uploading_dropbox")));
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
