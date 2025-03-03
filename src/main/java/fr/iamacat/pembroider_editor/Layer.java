package fr.iamacat.pembroider_editor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import fr.iamacat.utils.UIUtils;

import static fr.iamacat.manager.DialogManager.showEmptyDialog;

public class Layer {
    public VisTable table;
    public VisLabel nameLabel;
    private VisLabel label1, label2, label3;
    private boolean visible = true;
    private Main mainInstance;
    private Stage stage;

    public Layer(Main mainInstance,Stage stage, String name) {
        this.stage = stage;
        this.mainInstance = mainInstance;
        table = new VisTable();
        nameLabel = new VisLabel(name);

        // Création des labels
        label1 = new VisLabel("o");
        label2 = new VisLabel("<");
        label3 = new VisLabel(">");

        VisTable labelsTable = new VisTable();
        labelsTable.add(label2);
        labelsTable.add(label1);
        labelsTable.add(label3);

        // Configuration du header
        VisTable header = new VisTable();
        header.add(nameLabel).left();
        header.add(labelsTable).expandX().right();
        header.addSeparator().fillX();

        // Boutons
        VisTable buttonsTable = new VisTable();
        for (int i = 1; i <= 5; i++) {
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
        if (index == 1 || index == 2) {
            showEmptyDialog(stage,"Layer Action", "Button " + index + " clicked!");
        } else if (index == 4) {
            toggleVisibility();
        } else if (index == 5) {
            if (mainInstance.getLayers().size() > 1) {
                deleteLayer();
            } else {
                showEmptyDialog(stage,"Warning", "You cannot delete the last layer.");
            }
        }
    }

    private void toggleVisibility() {
        visible = !visible;
        updateLabels();
    }

    private void updateLabels() {
        if (visible) {
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
                    mainInstance.removeLayer(Layer.this);
                }
            }
        };

        dialog.text("Delete this layer?");
        dialog.button("Yes", true);
        dialog.button("No", false);

        // Afficher la boîte de dialogue
        dialog.show(stage);
    }
}
