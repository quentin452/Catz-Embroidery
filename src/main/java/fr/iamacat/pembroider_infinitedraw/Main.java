package fr.iamacat.pembroider_infinitedraw;

import fr.iamacat.utils.Translatable;
import processing.controlP5.ControlP5;
import processing.core.PApplet;
import processing.embroider.PEmbroiderGraphics;

public class Main extends PApplet implements Translatable {
    private ControlP5 cp5;
    PEmbroiderGraphics embroidery;
    public static void main(String[] args) {
        PApplet.main("fr.iamacat.pembroider_infinitedraw.Main");
    }
    @Override
    public void settings() {
        size(1280, 720,P2D);
    }
    @Override
    public void draw() {
        background(255);
    }
    @Override
    public void updateTranslations() {

    }

}
