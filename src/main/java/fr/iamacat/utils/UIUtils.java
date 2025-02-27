package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;

import java.util.Arrays;
import java.util.function.Consumer;

import static fr.iamacat.utils.Translator.isReloadingLanguage;

public class UIUtils {
    public static Skin visSkin;
    // Création de bouton avec callback immédiat
    public static TextButton createButton(Stage stage,String text,boolean translated, float x, float y, Color color, Runnable callback) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        TextButton button = new TextButton(text, visSkin);
        button.setPosition(x, y);
        button.setColor(color);
        addCallback(button, callback);
        checkComponentOutsideWindow(x, y, button.getWidth(), button.getHeight(),text);
        stage.addActor(button);
        return button;
    }

    // Version avec alignement et taille personnalisée
    public static TextButton createButton(Stage stage,String text,boolean translated, float x, float y,
                                          float width, float height, Color color, Runnable callback) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        TextButton button = new TextButton(text, visSkin);
        button.setPosition(x, y);
        button.setColor(color);
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
                if (!isReloadingLanguage && callback != null) {
                    callback.run();
                }
            }
        });
    }
    public static void addCallback(SelectBox dropdown, Runnable callback) {
        dropdown.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!isReloadingLanguage && callback != null) {
                    callback.run();
                }
            }
        });
    }
    // Méthode générique pour créer des tables
    public static Table createTable(Stage stage, boolean fillParent, Color color) {
        Table table = new Table();
        if (fillParent) {
            table.setFillParent(true);
        }
        table.setColor(color);
        stage.addActor(table);
        return table;
    }

    // Création de label avec style configurable
    public static Label createLabel(Stage stage,String text, boolean translated, float x, float y, float width, float height,int labelAlign, int lineAlign, Color color, String styleName) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        Label label = new Label(text, UIUtils.visSkin.get(styleName, Label.LabelStyle.class));
        label.setPosition(x, y);
        label.setSize(width, height);
        label.setAlignment(labelAlign | lineAlign);
        label.setColor(color);
        stage.addActor(label);
        checkComponentOutsideWindow(x, y, label.getWidth(), label.getHeight(),text);
        return label;
    }
    public static <T> SelectBox<T> createDropdown(Stage stage,Array<T> options, float x, float y, float width, float height, Color color) {
        SelectBox<T> dropdown = new SelectBox<>(visSkin);
        dropdown.setItems(options);
        dropdown.setPosition(x, y);
        dropdown.setSize(width, height);
        dropdown.setColor(color);
        // Ajouter le dropdown à la scène
        stage.addActor(dropdown);
        checkComponentOutsideWindow(x, y, dropdown.getWidth(), dropdown.getHeight(),Arrays.toString(options.items));
        return dropdown;
    }
    public static <T> SelectBox<T> createDropdown(Stage stage,Array<T> options, float x, float y, float width, float height, Color color, ChangeListener callback) {
        SelectBox<T> dropdown = new SelectBox<>(visSkin);
        dropdown.setItems(options);
        dropdown.setPosition(x, y);
        dropdown.setSize(width, height);
        dropdown.setColor(color);
        // Ajout du callback sur changement de sélection
        dropdown.addListener(callback);
        // Ajouter le dropdown à la scène
        stage.addActor(dropdown);
        checkComponentOutsideWindow(x, y, dropdown.getWidth(), dropdown.getHeight(),Arrays.toString(options.items));
        return dropdown;
    }
    public static <T> SelectBox<T> createDropdown(Stage stage,Array<T> options ,float x, float y, float width, float height, Color color, Runnable callback) {
        SelectBox<T> dropdown = new SelectBox<>(visSkin);
        dropdown.setItems(options);
        dropdown.setPosition(x, y);
        dropdown.setSize(width, height);
        dropdown.setColor(color);
        // Ajouter le dropdown à la scène
        stage.addActor(dropdown);
        addCallback(dropdown, callback);
        checkComponentOutsideWindow(x, y, dropdown.getWidth(), dropdown.getHeight(),Arrays.toString(options.items));
        return dropdown;
    }
    public static MenuItem createMenuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(Translator.getInstance().translate(text));
        item.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return item;
    }


    public static PopupMenu createPopupMenu(String[] labels, Runnable... actions) {
        PopupMenu menu = new PopupMenu();
        for (int i = 0; i < labels.length; i++) {
            menu.addItem(createMenuItem(labels[i], actions[i]));
        }
        return menu;
    }

    public static VisTextButton createMenuButton(String text,boolean translated, PopupMenu menu, Stage stage) {
        if (translated) {
            text = Translator.getInstance().translate(text);
        }
        VisTextButton button = new VisTextButton(text);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                menu.showMenu(stage, button);
            }
        });
        return button;
    }
    // Méthode générique pour créer un menu à partir de n'importe quel enum
    public static <T extends Enum<T>> PopupMenu createEnumMenu(Class<T> enumClass, Consumer<T> onChange) {
        PopupMenu menu = new PopupMenu();

        // Parcours toutes les valeurs de l'enum
        for (T type : enumClass.getEnumConstants()) {
            MenuItem item = createEnumMenuItem(type, onChange);
            menu.addItem(item);
        }

        return menu;
    }

    // Méthode générique pour créer un MenuItem à partir d'un enum
    public static <T extends Enum<T>> MenuItem createEnumMenuItem(T type, Consumer<T> onChange) {

        return new MenuItem(type.toString(), new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                onChange.accept(type);  // Appel de la méthode sur changement de sélection
            }
        });
    }
    public static void setMenuItemChecked(PopupMenu menu, String typeStr) {
        // Créer une liste des éléments avant de les modifier
        Array<MenuItem> menuItems = new Array<>();
        for (Actor actor : menu.getChildren()) {
            if (actor instanceof MenuItem) {
                menuItems.add((MenuItem) actor);
            }
        }

        // Appliquer les modifications après l'itération
        for (MenuItem item : menuItems) {
            item.setChecked(item.getText().equals(typeStr));
        }
    }

    public static  <T extends Enum<T>> MenuItem addMenuItem(PopupMenu menu, String text, Class<T> enumType, Consumer<T> onChange) {
        PopupMenu subMenu = UIUtils.createEnumMenu(enumType, onChange);
        MenuItem menuItem = new MenuItem(text);
        menuItem.setSubMenu(subMenu);
        menu.addItem(menuItem);
        return menuItem;
    }

    public static void addMenuItem(PopupMenu menu, String text, Runnable action) {
        MenuItem menuItem = new MenuItem(text);
        menuItem.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        menu.addItem(menuItem);
    }
    public static void addMenuCheckbox(PopupMenu menu, String text, boolean initialState, Consumer<Boolean> onChange) {
        VisCheckBox checkBox = new VisCheckBox(text, initialState);

        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onChange.accept(checkBox.isChecked());
            }
        });

        MenuItem menuItem = new MenuItem("");
        menuItem.addActor(checkBox); // Ajoute la checkbox au menu
        menu.addItem(menuItem);
    }

    public static <T extends Enum<T>> void addSubmenu(PopupMenu menu, String text, Class<T> enumType, Consumer<T> onChange) {
        PopupMenu subMenu = UIUtils.createEnumMenu(enumType, onChange);
        MenuItem menuItem = new MenuItem(text);
        menuItem.setSubMenu(subMenu);
        menu.addItem(menuItem);
    }

    public static String t(String key) {
        return Translator.getInstance().translate(key);
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