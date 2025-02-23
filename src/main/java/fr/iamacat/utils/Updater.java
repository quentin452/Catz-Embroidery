package fr.iamacat.utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater {

    public static final String CURRENT_VERSION = "V0.1.2";
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
}
