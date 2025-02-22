package fr.iamacat;

import fr.iamacat.utils.Translatable;
import processing.controlP5.ControlP5;
import processing.core.PApplet;
import processing.embroider.PEmbroiderGraphics;

public class PembroiderInfiniteDraw extends PApplet implements Translatable {
    private ControlP5 cp5;
    PEmbroiderGraphics embroidery;
    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PembroiderInfiniteDraw");
    }
    @Override
    public void updateTranslations() {

    }
}
