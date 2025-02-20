package fr.iamacat;

import processing.controlP5.ControlP5;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.*;

import java.io.File;
import java.util.ArrayList;

public class PEmbroiderApplication extends PApplet {
    PImage img;
    PEmbroiderGraphics embroidery;
    ControlP5 cp5;
    String[] formats = {"PES", "DST", "EXP", "SVG"};
    String selectedFormat = "PES";
    boolean showPreview = false;
    int previewType = 0;

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderApplication");
    }

    @Override
    public void settings() {
        size(1200, 800);
    }

    @Override
    public void setup() {
        cp5 = new ControlP5(this);
        createGUI();
    }

    void createGUI() {
        cp5.addButton("loadImage")
                .setPosition(20, 20)
                .setSize(120, 30)
                .setLabel("Charger image");

        cp5.addDropdownList("formatSelector")
                .setPosition(160, 20)
                .setSize(100, 120)
                .addItems(formats)
                .setLabel("Format de sortie");

        cp5.addButton("saveFile")
                .setPosition(280, 20)
                .setSize(120, 30)
                .setLabel("Sauvegarder");

        cp5.addTab("preview")
                .setLabel("Aperçu")
                .setWidth(120)
                .setHeight(30)
                .activateEvent(true)
                .setId(1);

        cp5.getTab("default")
                .activateEvent(true)
                .setLabel("Principal")
                .setId(0);
    }

    public void loadImage() {
        selectInput("Sélectionner une image", "imageSelected");
    }

    public void saveFile() {
        if (embroidery != null) {
            selectOutput("Sauvegarder sous", "fileSaved", null, "embroidery." + selectedFormat.toLowerCase());
        }
    }

    public void formatSelector(int n) {
        selectedFormat = formats[n];
    }

    public void imageSelected(File selection) {
        if (selection != null) {
            img = loadImage(selection.getAbsolutePath());
            if (img != null) {
                processImage();
                showPreview = true;
            }
        }
    }

    void processImage() {
        embroidery = new PEmbroiderGraphics(this, img.width, img.height);
        embroidery.setStitch(10, 5, 0);

        ArrayList<ArrayList<PVector>> stitches = PEmbroiderHatchSatin.hatchSatinRaster(img, 10, 5);

        embroidery.beginDraw();
        for (ArrayList<PVector> stitch : stitches) {
            if (stitch.size() > 1) {
                embroidery.beginShape();
                for (PVector point : stitch) {
                    embroidery.vertex(point.x, point.y);
                }
                embroidery.endShape();
            }
        }
        embroidery.optimize();
        embroidery.endDraw();
    }

    public void fileSaved(File selection) {
        if (selection != null) {
            String path = selection.getAbsolutePath();
            try {
                PEmbroiderWriter.write(path,
                        embroidery.polylines,
                        embroidery.colors,
                        embroidery.width,
                        embroidery.height,
                        selectedFormat.equals("SVG"));

                println("Fichier sauvegardé : " + path);
            } catch (Exception e) {
                println("Erreur de sauvegarde : " + e.getMessage());
            }
        }
    }

    @Override
    public void draw() {
        background(240);

        if (cp5.getTab("default").isActive()) {
            drawMainInterface();
        } else if (cp5.getTab("preview").isActive()) {
            drawPreview();
        }
    }

    void drawMainInterface() {
        if (img != null) {
            image(img, 20, 70, width/2 - 40, height - 90);
        }

        if (showPreview) {
            drawEmbroideryPreview(width/2 + 20, 70, width/2 - 40, height - 90);
        }
    }

    void drawPreview() {
        // Implémenter différents types de prévisualisation ici
        textSize(20);
        textAlign(CENTER, CENTER);
        text("Prévisualisation " + selectedFormat, width/2, height/2);
    }

    void drawEmbroideryPreview(float x, float y, float w, float h) {
        pushMatrix();
        translate(x, y);
        scale(w/embroidery.width, h/embroidery.height);

        stroke(255, 0, 0);
        noFill();
        for (ArrayList<PVector> polyline : embroidery.polylines) {
            beginShape();
            for (PVector p : polyline) {
                vertex(p.x, p.y);
            }
            endShape();
        }
        popMatrix();
    }

    @Override
    public void keyPressed() {
        if (key == 'p') {
            showPreview = !showPreview;
        }
    }
}