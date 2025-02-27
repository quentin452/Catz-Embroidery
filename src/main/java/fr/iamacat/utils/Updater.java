package fr.iamacat.utils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater {

    public static final String CURRENT_VERSION = "V0.2.0";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/quentin452/Catz-Embroidery/releases/latest";
    public static boolean isUpdateChecked = false;

    public static String getLatestVersionFromGitHub() throws IOException {
        if (!isUpdateChecked) {
            isUpdateChecked = true;
        } else  {
            return "";
        }
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder response = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) {
            response.append(output);
        }
        conn.disconnect();

        // Extraire la version des informations de la release
        Pattern pattern = Pattern.compile("\"tag_name\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(response.toString());
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("Impossible de trouver la version dans la réponse de l'API GitHub.");
        }
    }

    public static boolean isVersionOutdated(String currentVersion, String latestVersion) {
        return !currentVersion.equals(latestVersion);
    }

    public static void openBrowserToReleasesPage() throws IOException {
        URI uri = URI.create("https://github.com/quentin452/Catz-Embroidery/releases");
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(uri);
        } else {
            throw new RuntimeException("Le bureau n'est pas supporté. Veuillez ouvrir le navigateur manuellement à : " + uri.toString());
        }
    }

    public static void checkForUpdates(Stage stage) {
        if (Updater.isUpdateChecked) {
            return;
        }

        new Thread(() -> {
            try {
                String latestVersion = Updater.getLatestVersionFromGitHub();
                if (Updater.isVersionOutdated(Updater.CURRENT_VERSION, latestVersion)) {
                    Logger.getInstance().log(Logger.Project.Launcher, "Une nouvelle version est disponible : " + latestVersion);

                    // Afficher une boîte de dialogue modale avec deux boutons
                    Gdx.app.postRunnable(() -> {
                        // Créer un dialog pour informer l'utilisateur de la nouvelle version
                        Dialog dialog = new Dialog("Mise à jour disponible", UIUtils.visSkin) {
                            @Override
                            protected void result(Object object) {
                                // Si l'utilisateur clique sur "Oui"
                                if ((Boolean) object) {
                                    try {
                                        Updater.openBrowserToReleasesPage();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        };

                        // Ajouter du texte à la fenêtre de dialogue
                        dialog.text("Une nouvelle version (" + latestVersion + ") est disponible. Voulez-vous ouvrir la page des releases ?");
                        // Ajouter un bouton "Oui"
                        dialog.button("Oui", true);
                        // Ajouter un bouton "Non"
                        dialog.button("Non", false);
                        // Afficher le dialogue
                        dialog.show(stage);
                    });
                } else {
                    Logger.getInstance().log(Logger.Project.Launcher, "Vous avez la version la plus récente.");
                }
            } catch (IOException e) {
                Logger.getInstance().log(Logger.Project.Launcher, "Erreur lors de la vérification des mises à jour : " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
