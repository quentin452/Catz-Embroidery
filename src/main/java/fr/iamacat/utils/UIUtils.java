package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import java.util.Arrays;

public class UIUtils {
    public static Skin skin = new Skin(Gdx.files.internal("uiskins/cloud-form/skin/cloud-form-ui.json"));
    // Création de bouton avec callback immédiat
    public static TextButton createButton(Stage stage,String text,boolean translated, float x, float y, Runnable callback) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        TextButton button = new TextButton(text, skin);
        button.setPosition(x, y);
        addCallback(button, callback);
        checkComponentOutsideWindow(x, y, button.getWidth(), button.getHeight(),text);
        stage.addActor(button);
        return button;
    }

    // Version avec alignement et taille personnalisée
    public static TextButton createButton(Stage stage,String text,boolean translated, float x, float y,
                                          float width, float height, Runnable callback) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        TextButton button = new TextButton(text, skin);
        button.setPosition(x, y);
        button.setSize(width, height);
        button.getLabel().setAlignment(Align.center);
        addCallback(button, callback);
        checkComponentOutsideWindow(x, y, button.getWidth(), button.getHeight(),text);
        stage.addActor(button);
        return button;
    }

    // Ajout de callback à un bouton existant
    public static void addCallback(Button button, Runnable callback) {
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }
    public static void addCallback(SelectBox dropdown, Runnable callback) {
        dropdown.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }
    // Méthode générique pour créer des tables
    public static Table createTable(Stage stage, boolean fillParent) {
        Table table = new Table();
        if (fillParent) {
            table.setFillParent(true);
        }
        stage.addActor(table);
        return table;
    }

    // Création de label avec style configurable
    public static Label createLabel(Stage stage,String text, boolean translated, float x, float y, float width, float height,int labelAlign, int lineAlign, String styleName) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        Label label = new Label(text, UIUtils.skin.get(styleName, Label.LabelStyle.class));
        label.setPosition(x, y);
        label.setSize(width, height);
        label.setAlignment(labelAlign | lineAlign);
        stage.addActor(label);
        checkComponentOutsideWindow(x, y, label.getWidth(), label.getHeight(),text);
        return label;
    }

    public static <T> SelectBox<T> createDropdown(Array<T> options, Stage stage, float x, float y, float width, float height, ChangeListener callback) {
        SelectBox<T> dropdown = new SelectBox<>(skin);
        dropdown.setItems(options);
        dropdown.setPosition(x, y);
        dropdown.setSize(width, height);

        // Ajout du callback sur changement de sélection
        dropdown.addListener(callback);

        // Ajouter le dropdown à la scène
        stage.addActor(dropdown);
        checkComponentOutsideWindow(x, y, dropdown.getWidth(), dropdown.getHeight(),Arrays.toString(options.items));
        return dropdown;
    }
    public static <T> SelectBox<T> createDropdown(Array<T> options, Stage stage, float x, float y, float width, float height, Runnable callback) {
        SelectBox<T> dropdown = new SelectBox<>(skin);
        dropdown.setItems(options);
        dropdown.setPosition(x, y);
        dropdown.setSize(width, height);
        // Ajouter le dropdown à la scène
        stage.addActor(dropdown);
        addCallback(dropdown, callback);
        checkComponentOutsideWindow(x, y, dropdown.getWidth(), dropdown.getHeight(),Arrays.toString(options.items));
        return dropdown;
    }
    public static void checkComponentOutsideWindow(float x, float y, float width, float height,String name) {
        int windowWidth = Gdx.graphics.getWidth();
        int windowHeight = Gdx.graphics.getHeight();

        // Vérifier si le composant dépasse la fenêtre (en x, y, ou les deux)
        if (x + width > windowWidth || x < 0 || y + height > windowHeight || y < 0) {
            // Si le composant est en dehors de la fenêtre, affichez un avertissement
            Gdx.app.error("UIUtils", "Component "+ name +  " is outside the window bounds! (" +
                    "Position: (" + x + ", " + y + "), " +
                    "Size: (" + width + "x" + height + "), " +
                    "Window: (" + windowWidth + "x" + windowHeight + "))");
        }
    }

}