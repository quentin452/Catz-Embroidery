package fr.iamacat;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.utils.*;

import java.io.IOException;

import static fr.iamacat.utils.DropboxUtil.dropboxClient;
import static fr.iamacat.utils.DropboxUtil.loadTokenFromJson;

public class PEmbroiderLauncher extends Game implements Translatable {
    private TextButton dropboxButton,editorButton,converterButton;
    private SelectBox<String> languageDropdown;
    Array<String> languages = new Array<>();

    private SpriteBatch batch;
    private Texture img;
    private Stage stage;
    private static int windowWidth = 1280;
    private static int windowHeight = 720;

    @Override
    public void create() {
        checkForUpdates();
        loadTokenFromJson();
        // Création de la fenêtre et des objets de base
        batch = new SpriteBatch();
        img = new Texture("icons/catz-embroidery-logo-2.png");  // Exemple d'image


        // Initialiser le Stage (la scène qui va gérer les éléments UI)
        stage = new Stage();
        Gdx.input.setInputProcessor(stage); // Le stage gère les entrées de l'utilisateur

        // Créer un Skin pour les éléments de l'UI (comme les boutons)
        dropboxButton = UIUtils.createButton(stage,"connect_to_dropbox",true, 10,10, 200, 40, this::connectToDropbox);
        editorButton = UIUtils.createButton(stage,"launch_editor",true, windowWidth / 2 - 100, windowHeight/ 2 - 20, 200, 40, this::launchEditor);
        converterButton = UIUtils.createButton(stage,"launch_converter",true, windowWidth / 2 - 100, windowHeight/ 2 - 60, 200, 40, this::launchConverter);

        languages.add("English");
        languages.add("Français");
        languageDropdown = UIUtils.createDropdown(languages, stage, windowWidth - 155, windowHeight - 35, 150, 30,this::updateLanguage);
        float versionLabelWidth = new Label("Version", UIUtils.skin).getPrefWidth();
        UIUtils.createLabel(stage, "Version", true, 10, windowHeight - 10, 0,0,Align.left,Align.top,"default");
        UIUtils.createLabel(stage, ": " + Updater.CURRENT_VERSION, false, 10 + versionLabelWidth + 4, windowHeight - 10, 0, 0,Align.left,Align.top, "default");
        UIUtils.createLabel(stage,"choose_app",true,windowWidth / 2 - 6, windowHeight / 2 + 35 ,15, 15,Align.center, Align.center,"default");

        // Ajout à la scène
        updateLanguage();
        Translator.getInstance().registerTranslatable(this);
    }
    private void launchEditor() {
        runApplication("fr.iamacat.pembroider_editor.Main");
    }
    private void launchConverter() {
        runApplication("fr.iamacat.pembroider_converter.Main");
    }
    private void updateLanguage() {
        // Get the selected language from the dropdown
        String selectedLanguage = languageDropdown.getSelected();
        // Set language code based on the selection
        String languageCode = selectedLanguage.equals("English") ? "en" : "fr";
        // Update the Translator's language
        Translator.getInstance().setLanguage(languageCode);
    }

    private void connectToDropbox() {
        new Thread(() -> {
            DropboxUtil.connectToDropbox(stage, UIUtils.skin);
        }).start();
    }
    @Override
    public void render() {
        Gdx.gl.glClearColor(0.8f, 0.8f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Dessiner l'image
        batch.begin();
        batch.draw(img, windowWidth - img.getWidth() - 10, 10);
        batch.end();
        if (dropboxClient != null) {
            dropboxButton.setColor(0f, 1f, 0f,1f); // Couleur verte si connecté
        } else {
            dropboxButton.setColor(1f, 0f, 0f,1f); // Couleur rouge si non connecté
        }
        // Dessiner le stage (c'est là que le bouton sera rendu)
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));  // Met à jour la scène (stage)
        stage.draw();  // Dessine la scène (le bouton et autres UI)
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);  // Gérer le redimensionnement de la fenêtre
    }

    @Override
    public void pause() {
        // Mettre en pause l'application
    }

    @Override
    public void resume() {
        // Reprendre l'application
    }

    @Override
    public void dispose() {
        batch.dispose();
        img.dispose();
        stage.dispose();  // N'oublie pas de libérer la mémoire du stage
        Logger.getInstance().log(Logger.Project.Launcher,"Fermeture de l'application");
        Logger.getInstance().archiveLogs();
    }

    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Catz Embroidery Launcher");
        config.setWindowedMode(windowWidth, windowHeight);
        config.setWindowIcon("icons/catz-embroidery-logo-2.png");
        new Lwjgl3Application(new PEmbroiderLauncher(), config);
    }
    @Override
    public void updateTranslations() {
        dropboxButton.setText(Translator.getInstance().translate("connect_to_dropbox"));
    }
    private void runApplication(String mainClassName) {
        try {
            Class<?> clazz = Class.forName(mainClassName);
            clazz.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } catch (Exception e) {
            Logger.getInstance().log(Logger.Project.Launcher,"Erreur lors de l'exécution de " + mainClassName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void checkForUpdates() {
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
                        Dialog dialog = new Dialog("Mise à jour disponible", UIUtils.skin) {
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
