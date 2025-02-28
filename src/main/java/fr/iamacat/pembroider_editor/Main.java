package fr.iamacat.pembroider_editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import fr.iamacat.pembroider_editor.enums.Tool;
import fr.iamacat.utils.MainBase;
import fr.iamacat.utils.UIUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class Main extends MainBase {
    private final VisTable leftTable;
    private final VisTable rightTable;
    private final BitSet buttonStates = new BitSet(Tool.values().length);
    private List<Layer> layers = new ArrayList<>();
    private int currentLayerCount = 0;
    private VisTextButton addLayerButton;
    private static final float RIGHT_PANEL_WIDTH = 200f;
    private VisScrollPane scrollPane;
    float backgroundWidthLeft = 50;
    float backgroundWidthRight = 200;
    float backgroundHeight;

    public Main() {
        leftTable = new VisTable();
        leftTable.top().left();
        leftTable.setFillParent(true);
        getStage().addActor(leftTable);

        rightTable = new VisTable();
        rightTable.top().right();
        rightTable.setFillParent(true);
        getStage().addActor(rightTable);
        backgroundHeight = getStage().getHeight();
        Image leftBg = UIUtils.createBackground(getStage(), new Color(0.2f, 0.2f, 0.2f, 1f), backgroundWidthLeft, backgroundHeight);
        Image rightBg = UIUtils.createBackground(getStage(), new Color(0.2f, 0.2f, 0.2f, 1f), backgroundWidthRight, backgroundHeight);

        Image leftBorder = UIUtils.createBorder(getStage(), Color.BLACK, 2, backgroundHeight);
        Image rightBorder = UIUtils.createBorder(getStage(), Color.BLACK, 2, backgroundHeight);
        Image topBorder = UIUtils.createBorder(getStage(), Color.BLACK, getStage().getWidth(), 2);

        leftBg.setPosition(0, 0);
        leftBorder.setPosition(backgroundWidthLeft, 0);

        rightBg.setPosition(getStage().getWidth() - backgroundWidthRight, 0);
        rightBorder.setPosition(getStage().getWidth() - backgroundWidthRight - 2, 0);

        topBorder.setPosition(0, getStage().getHeight() - 2);

        addRadioButtonsToTable(leftTable);
        setupLayersSystem();
    }

    private void setupLayersSystem() { // TODO ENABLE SCROLLING WITHOUT NEEDING TO CLICK ON THE SCROLLPANE , TODO FIX WE CAN SCROLL EVEN IF WE SCROOL OUTSIDE OF THE SCROLLPANE
        // Configuration de la table de droite
        rightTable.defaults().pad(5).width(RIGHT_PANEL_WIDTH - 20);
        rightTable.top().right();

        // Create the container for layers
        VisTable layersTable = new VisTable();
        scrollPane = new VisScrollPane(layersTable);
        // Set the same width and height as the background
        scrollPane.setOverscroll(false, true);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setForceScroll(false, true);

        // Create the first layer
        addNewLayer("Layer 0");

        // Button for adding a new layer
        addLayerButton = new VisTextButton("+");
        addLayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                addNewLayer("Layer " + (++currentLayerCount));
            }
        });
        // Add the layers to the scrollable pane
        updateLayersTable(layersTable);

        // Add the scroll pane to the right table
        rightTable.add(scrollPane).width(RIGHT_PANEL_WIDTH).height(getStage().getHeight() - 80).padBottom(10).row();
        rightTable.add(addLayerButton).padTop(20);
    }

    private void addNewLayer(String name) {
        Layer newLayer = new Layer(this, getStage(), name);
        layers.add(newLayer);
        updateLayersTable((VisTable) scrollPane.getWidget());
    }

    private void updateLayersTable(VisTable layersTable) {
        layersTable.clear();
        for (Layer layer : layers) {
            layersTable.add(layer.table).padBottom(15).row();
        }
    }

    private void addRadioButtonsToTable(VisTable table) {
        ButtonGroup<VisTextButton> buttonGroup = new ButtonGroup<>();
        Tool[] tools = Tool.values();
        table.add().height(100).row();
        table.defaults().size(40).pad(5);
        final Tool[] currentTool = {null};
        for (Tool tool : tools) {
            VisTextButton button = new VisTextButton(tool.name());
            buttonGroup.add(button);
            button.setColor(Color.LIGHT_GRAY);
            table.add(button).row();
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    VisTextButton clickedButton = (VisTextButton) actor;
                    if (clickedButton.isChecked()) {
                        Tool selectedTool = Tool.valueOf(clickedButton.getText().toString());
                        if (currentTool[0] != selectedTool) {
                            currentTool[0] = selectedTool;
                            buttonStates.set(selectedTool.ordinal(), true);
                        }
                    }
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
        updateLayersTable((VisTable) scrollPane.getWidget());
    }
}
