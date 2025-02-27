package fr.iamacat.pembroider_editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
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

public class Main extends MainBase {
    private final VisTable leftTable;
    private final VisTable rightTable;
    private final HashMap<String, Boolean> buttonStates = new HashMap<>();
    private List<VisTable> layers = new ArrayList<>();
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
    private void addNewLayer(String layerName) {
        VisTable layerTable = new VisTable();
        //layerTable.setBackground(UIUtils.createDebugDrawable(Color.DARK_GRAY));

        // Header du layer
        VisTable header = new VisTable();
        header.add(new VisLabel(layerName)).left().row();
        header.addSeparator();

        // Boutons du layer
        VisTable buttonsTable = new VisTable();
        for(int i = 1; i <= 5; i++) {
            VisTextButton btn = new VisTextButton(String.valueOf(i));
            btn.setColor(Color.LIGHT_GRAY);
            buttonsTable.add(btn).width(30).height(30).pad(2);
        }
        buttonsTable.center();

        layerTable.add(header).growX().row();
        layerTable.add(buttonsTable).padTop(10).row();
        layers.add(layerTable);

        updateRightTableLayout();
    }

    private void updateRightTableLayout() {
        rightTable.clear();

        // Ajout des layers existants
        for(VisTable layer : layers) {
            rightTable.add(layer).padBottom(15).row();
        }

        // Ajout du bouton "+" en bas
        rightTable.add(addLayerButton)
                .width(40)
                .height(40)
                .padTop(20);
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
}
