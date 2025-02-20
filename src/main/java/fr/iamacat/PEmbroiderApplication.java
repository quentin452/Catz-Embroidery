package fr.iamacat;

import processing.controlP5.ControlP5;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.*;

import java.io.File;
import java.util.ArrayList;

public class PEmbroiderApplication extends PApplet {

    private PImage img;
    private PEmbroiderGraphics embroidery;
    private ControlP5 cp5;
    private String[] formats = {"PES", "DST", "EXP", "SVG"};
    private String selectedFormat = "PES";
    private boolean showPreview = false;

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
        setupGUI();
    }

    private void setupGUI() {
        // Ajouter un bouton pour charger l'image
        cp5.addButton("loadImage")
                .setPosition(20, 20)
                .setSize(120, 30)
                .setLabel("Charger image")
                .onClick(event -> loadImage());

        // Ajouter un bouton pour sauvegarder l'image
        cp5.addButton("saveFile")
                .setPosition(280, 20)
                .setSize(120, 30)
                .setLabel("Sauvegarder")
                .onClick(event -> saveFile());

        // Ajouter un menu déroulant pour sélectionner le format de sortie
        cp5.addDropdownList("formatSelector")
                .setPosition(160, 20)
                .setSize(100, 120)
                .addItems(formats)
                .setLabel("Format de sortie")
                .onChange(event -> {
                    // Mise à jour du format sélectionné
                    selectedFormat = formats[(int) event.getController().getValue()];
                    println("Format sélectionné: " + selectedFormat);

                    // Recharger la prévisualisation avec le nouveau format
                    if (embroidery != null) {
                        showPreview = false; // Masquer la prévisualisation pour la redessiner
                        showPreview = true;  // Forcer l'affichage de la nouvelle prévisualisation
                    }
                });

        // Activer l'onglet "Principal"
        cp5.getTab("default").activateEvent(true).setLabel("Principal").setId(0);
    }

    public void loadImage() {
        selectInput("Sélectionner une image", "imageSelected");
    }

    public void saveFile() {
        if (embroidery != null) {
            // Ouvrir la boîte de dialogue pour choisir l'emplacement de sauvegarde
            selectOutput("Sauvegarder sous", "fileSaved");
        }
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

    public void fileSaved(File selection) {
        if (selection != null) {
            String path = selection.getAbsolutePath();
            try {
                // Vérifier le format sélectionné avant d'appeler PEmbroiderWriter
                boolean isSVG = selectedFormat.equals("SVG");
                PEmbroiderWriter.write(path, embroidery.polylines, embroidery.colors, embroidery.width, embroidery.height, isSVG);
                println("Fichier sauvegardé : " + path);
            } catch (Exception e) {
                println("Erreur de sauvegarde : " + e.getMessage());
            }
        }
    }

    private void processImage() {
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

    @Override
    public void draw() {
        background(240);

        if (img != null) {
            image(img, 20, 70, width / 2 - 40, height - 90);
        }

        if (showPreview) {
            drawEmbroideryPreview(width / 2 + 20, 70, width / 2 - 40, height - 90);
        }
    }

    private void drawEmbroideryPreview(float x, float y, float w, float h) {
        pushMatrix();
        translate(x, y);
        scale(w / embroidery.width, h / embroidery.height);

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
