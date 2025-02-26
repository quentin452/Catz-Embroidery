package fr.iamacat.pembroider_mainmenu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
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
import fr.iamacat.PEmbroiderLauncher;
import fr.iamacat.utils.*;

import java.io.IOException;

import static fr.iamacat.PEmbroiderLauncher.windowHeight;
import static fr.iamacat.PEmbroiderLauncher.windowWidth;
import static fr.iamacat.utils.DropboxUtil.dropboxClient;
import static fr.iamacat.utils.DropboxUtil.loadTokenFromJson;

public class Main implements Screen, Translatable {
    private TextButton dropboxButton,editorButton,converterButton;
    private SelectBox<String> languageDropdown;
    Array<String> languagesOptions = new Array<>();
    private SpriteBatch batch;
    private Texture img;
    private Stage stage;
    private PEmbroiderLauncher game;
    Label versionLabel;
    public Main(PEmbroiderLauncher game) {
        this.game = game;
        stage = new Stage();
        checkForUpdates();
        loadTokenFromJson();
        // Création de la fenêtre et des objets de base
        batch = new SpriteBatch();
        img = new Texture("icons/catz-embroidery-logo-2.png");  // Exemple d'image


        // Initialiser le Stage (la scène qui va gérer les éléments UI)
        stage = new Stage();
        Gdx.input.setInputProcessor(stage); // Le stage gère les entrées de l'utilisateur

        // Créer un Skin pour les éléments de l'UI (comme les boutons)
        dropboxButton = UIUtils.createButton(stage,"connect_to_dropbox",true, 10,10, 200, 40,Color.RED, this::connectToDropbox);
        editorButton = UIUtils.createButton(stage,"launch_editor",true, windowWidth / 2 - 100, windowHeight/ 2 - 20, 200, 40,Color.LIGHT_GRAY, this::launchEditor);
        converterButton = UIUtils.createButton(stage,"launch_converter",true, windowWidth / 2 - 100, windowHeight/ 2 - 60, 200, 40,Color.LIGHT_GRAY, this::launchConverter);

        languagesOptions.add(Translator.getInstance().translate("english"));
        languagesOptions.add(Translator.getInstance().translate("french"));

        languageDropdown = UIUtils.createDropdown(stage,languagesOptions, windowWidth - 155, windowHeight - 35, 150, 30,Color.GRAY,this::updateLanguage);

        versionLabel = UIUtils.createLabel(stage, "version", true, windowWidth / 2 - 25, windowHeight - 10, 0,0, Align.top,Align.top,Color.BLACK,"default");
        UIUtils.createLabel(stage, Updater.CURRENT_VERSION, false, windowWidth / 2 + 40, windowHeight - 10, 0, 0,Align.left,Align.top,Color.BLACK, "default");
        UIUtils.createLabel(stage,"choose_app",true,windowWidth / 2 - 6, windowHeight / 2 + 35 ,15, 15,Align.center, Align.center,Color.BLACK,"default");

        // Ajout à la scène
        Translator.getInstance().registerTranslatable(this);
        updateLanguage();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.8f, 0.8f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Dessiner l'image
        batch.begin();
        batch.draw(img, windowWidth - img.getWidth() - 10, 10);
        batch.end();
        if (dropboxClient != null) {
            dropboxButton.setColor(Color.GREEN);
        } else {
            dropboxButton.setColor(Color.RED);
        }
        // Dessiner le stage (c'est là que le bouton sera rendu)
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));  // Met à jour la scène (stage)
        stage.draw();  // Dessine la scène (le bouton et autres UI)
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        batch.dispose();
        img.dispose();
        stage.dispose();
        UIUtils.skin.dispose();
        Logger.getInstance().log(Logger.Project.Launcher,"Fermeture de l'application");
        Logger.getInstance().archiveLogs();
    }
    @Override
    public void updateTranslations() {
        dropboxButton.setText(Translator.getInstance().translate("connect_to_dropbox"));
        editorButton.setText(Translator.getInstance().translate("launch_editor"));
        converterButton.setText(Translator.getInstance().translate("launch_converter"));
        versionLabel.setText(Translator.getInstance().translate("version"));
        languagesOptions.clear();
        languagesOptions.add(Translator.getInstance().translate("english"));
        languagesOptions.add(Translator.getInstance().translate("french"));
        languageDropdown.setItems(languagesOptions);
    }

    private void launchEditor() {
        //this.setScreen(new fr.iamacat.pembroider_editor.Main()); // TODO
    }
    private void launchConverter() {
        game.setScreen(new fr.iamacat.pembroider_converter.Main());
    }
    private void updateLanguage() {
        String selected = languageDropdown.getSelected();
        if (selected.equalsIgnoreCase(Translator.getInstance().translate("english"))) {
            Translator.getInstance().setLanguage("en");
        } else if (selected.equalsIgnoreCase(Translator.getInstance().translate("french"))) {
            Translator.getInstance().setLanguage("fr");
        }
    }

    private void connectToDropbox() {
        new Thread(() -> {
            DropboxUtil.connectToDropbox(stage, UIUtils.skin);
        }).start();
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
