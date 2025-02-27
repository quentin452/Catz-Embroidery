package fr.iamacat.pembroider_editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
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

    private void addNewLayer(String name) {
        Layer newLayer = new Layer(this,getStage(),name);
        layers.add(newLayer);
        updateRightTableLayout();
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

    public List<Layer> getLayers() {
        return layers;
    }

    void removeLayer(Layer layer) {
        layers.remove(layer);
        reorganizeLayers();
    }

    private void reorganizeLayers() {
        for (int i = 0; i < layers.size(); i++) {
            layers.get(i).nameLabel.setText("Layer " + i);
        }
        updateRightTableLayout();
    }

}
