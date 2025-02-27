package fr.iamacat.utils.uis;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.building.GridTableBuilder;

public class MyGridTableBuilder extends GridTableBuilder {
    public MyGridTableBuilder(int rowSize) {
        super(rowSize);
    }

    public void publicFillTable(Table table) {
        fillTable(table);  // Appel de la méthode protégée
    }
}
