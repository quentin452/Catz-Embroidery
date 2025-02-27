package fr.iamacat.pembroider_editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import fr.iamacat.utils.MainBase;
import fr.iamacat.utils.UIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
// TODO ADD SCROLLBAR TO LAYERS
public class Main extends MainBase {
    private final VisTable leftTable;
    private final VisTable rightTable;
    private final HashMap<String, Boolean> buttonStates = new HashMap<>();
    private List<Layer> layers = new ArrayList<>();
    private int currentLayerCount = 0;
    private VisTextButton addLayerButton;
    private static final float RIGHT_PANEL_WIDTH = 200f;
    public Main() {
        leftTable = new VisTable();
        leftTable.top().left();
        leftTable.setFillParent(true);
        getStage().addActor(leftTable);

        rightTable = new VisTable();
        rightTable.top().right();
        rightTable.setFillParent(true);
        getStage().addActor(rightTable);
        float backgroundWidthLeft = 50;
        float backgroundWidthRight = 200;
        float backgroundHeight = getStage().getHeight();
        Image leftBg = UIUtils.createBackground(getStage(),new Color(0.2f, 0.2f, 0.2f, 1f), backgroundWidthLeft, backgroundHeight);
        Image rightBg = UIUtils.createBackground(getStage(),new Color(0.2f, 0.2f, 0.2f, 1f), backgroundWidthRight, backgroundHeight);

        Image leftBorder = UIUtils.createBorder(getStage(),Color.BLACK, 2, backgroundHeight);
        Image rightBorder = UIUtils.createBorder(getStage(),Color.BLACK, 2, backgroundHeight);
        Image topBorder = UIUtils.createBorder(getStage(),Color.BLACK, getStage().getWidth(), 2);

        leftBg.setPosition(0, 0);
        leftBorder.setPosition(backgroundWidthLeft, 0);

        rightBg.setPosition(getStage().getWidth() - backgroundWidthRight, 0);
        rightBorder.setPosition(getStage().getWidth() - backgroundWidthRight - 2, 0);

        topBorder.setPosition(0, getStage().getHeight() - 2);

        addRadioButtonsToTable(leftTable);
        setupLayersSystem();
    }
    private void setupLayersSystem() {
        // Configuration de la table de droite
        rightTable.defaults().pad(5).width(RIGHT_PANEL_WIDTH - 20);
        rightTable.top().right();

        // Création du layer initial
        addNewLayer("Layer 0");

        // Bouton d'ajout de layer
        addLayerButton = new VisTextButton("+");
        addLayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                addNewLayer("Layer " + (++currentLayerCount));
            }
        });

        updateRightTableLayout();
    }
    private void reorganizeLayers() {
        for(int i = 0; i < layers.size(); i++) {
            layers.get(i).nameLabel.setText("Layer " + i);
        }
        updateRightTableLayout();
    }

    private void addNewLayer(String name) {
        Layer newLayer = new Layer(name);
        layers.add(newLayer);
        updateRightTableLayout();
    }

    private void showDialog(String title, String message) {
        new Dialog(title, UIUtils.visSkin) {{
            text(message);
            button("OK");
        }}.show(getStage());
    }

    private void updateRightTableLayout() {
        rightTable.clear();
        for(Layer layer : layers) {
            rightTable.add(layer.table).padBottom(15).row();
        }
        rightTable.add(addLayerButton).padTop(20);
    }

    private void addRadioButtonsToTable(VisTable table) {
        ButtonGroup<VisTextButton> buttonGroup = new ButtonGroup<>();
        String[] buttonNames = {"S", "Z", "o", "O", "FL", "T", "E"};
        table.add().height(100).row();
        table.defaults().width(40).height(40).pad(5);
        table.top();
        for (String name : buttonNames) {
            VisTextButton button = new VisTextButton(name);
            buttonGroup.add(button);
            button.setColor(Color.LIGHT_GRAY);
            table.add(button).row();
            buttonStates.put(name, false);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                    buttonGroup.getButtons().forEach(b ->
                            buttonStates.put(b.getText().toString(), b.isChecked())
                    );
                }
            });
        }

        buttonGroup.setMaxCheckCount(1);
        buttonGroup.setMinCheckCount(0);
        buttonGroup.setUncheckLast(true);
    }
    class Layer {
        VisTable table;
        VisLabel nameLabel;
        VisLabel label1, label2, label3;
        boolean visible = true;

        public Layer(String name) {
            table = new VisTable();
            nameLabel = new VisLabel(name);

            // Création des labels
            label1 = new VisLabel("o");
            label2 = new VisLabel("<");
            label3 = new VisLabel(">");

            VisTable labelsTable = new VisTable();
            labelsTable.add(label2).padRight(5); // < à gauche avec espacement
            labelsTable.add(label1);             // o au centre
            labelsTable.add(label3).padLeft(5);  // > à droite avec espacement

            // Configuration du header
            VisTable header = new VisTable();
            header.add(nameLabel).left().padLeft(10); // Alignement à gauche avec marge
            header.add(labelsTable).expandX().right().padRight(10); // Alignement à droite avec marge
            header.addSeparator().fillX();


            // Boutons
            VisTable buttonsTable = new VisTable();
            for(int i = 1; i <= 5; i++) {
                VisTextButton btn = new VisTextButton(String.valueOf(i));
                setupButton(btn, i);
                buttonsTable.add(btn).width(30).height(30).pad(2);
            }

            table.add(header).growX().row();
            table.add(buttonsTable).padTop(10).row();
        }

        private void setupButton(VisTextButton btn, int index) {
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    handleButtonClick(index);
                }
            });
        }
        private void handleButtonClick(int index) {
            switch(index) {
                case 1: case 2:
                    showDialog("Layer Action", "Button " + index + " clicked!");
                    break;
                case 4:
                    toggleVisibility();
                    break;
                case 5:
                    if (layers.size() > 1) {
                        deleteLayer();
                    } else {
                        showDialog("Warning", "You cannot delete the last layer.");
                    }
                    break;
            }
        }

        private void toggleVisibility() {
            visible = !visible;
            updateLabels();
        }

        private void updateLabels() {
            if(visible) {
                label1.setText("o");
                label2.setText("<");
                label3.setText(">");
            } else {
                label1.setText("=");
                label2.setText("-");
                label3.setText("-");
            }
        }
        private void deleteLayer() {
            // Création de la boîte de dialogue de confirmation
            Dialog dialog = new Dialog("Confirmation", UIUtils.visSkin) {
                @Override
                protected void result(Object r) {
                    boolean isConfirmed = (Boolean) r;
                    if (isConfirmed) {
                        // Suppression du layer si confirmé
                        layers.remove(Layer.this);
                        reorganizeLayers();
                    }
                }
            };

            dialog.text("Delete this layer?");
            dialog.button("Yes", true);
            dialog.button("No", false);

            // Afficher la boîte de dialogue
            dialog.show(getStage());
        }
    }
}
