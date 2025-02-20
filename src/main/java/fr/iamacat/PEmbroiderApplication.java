package fr.iamacat;

import processing.controlP5.ControlP5;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderHatchSatin;
import processing.embroider.PEmbroiderWriter;

import java.io.File;
import java.util.ArrayList;

public class PEmbroiderApplication extends PApplet {

    private PImage img;
    private PEmbroiderGraphics embroidery;
    private ControlP5 cp5;
    private String[] formats = {"PES"/*, "DST", "EXP", "SVG"*/};
    private String selectedFormat = "PES";
    private boolean showPreview = false;
    private float exportWidth = 100;  // Largeur par défaut en mm
    private float exportHeight = 100; // Hauteur par défaut en mm

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
    private float currentSpacing = 5;

    private void setupGUI() {
        cp5.addButton("loadImage").setPosition(20, 20).setSize(120, 30).setLabel("Charger image").onClick(event -> loadImage());

        cp5.addButton("saveFile").setPosition(280, 20).setSize(120, 30).setLabel("Sauvegarder").onClick(event -> saveFile());

        cp5.addDropdownList("formatSelector")
                .setPosition(160, 20)
                .setSize(100, 120)
                .addItems(formats)
                .setLabel("Format de sortie")
                .onChange(event -> {
                    float[] values = event.getController().getArrayValue();
                    if (values.length > 0) {
                        int index = (int) values[0];
                        if (index >= 0 && index < formats.length) {
                            selectedFormat = formats[index];
                            println("Format sélectionné: " + selectedFormat);
                        } else {
                            println("Erreur : index hors limites (" + index + ")");
                        }
                    } else {
                        println("Erreur : Aucune valeur sélectionnée !");
                    }

                    // Recharger la prévisualisation avec le nouveau format
                    if (embroidery != null) {
                        showPreview = false; // Masquer la prévisualisation pour la redessiner
                        showPreview = true;  // Forcer l'affichage de la nouvelle prévisualisation
                    }
                });

        cp5.addTextfield("stitchSpacing")
                .setPosition(20, 120)
                .setSize(100, 30)
                .setLabel("Espacement des points")
                .setText(str(currentSpacing))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        currentSpacing = Float.parseFloat(event.getController().getStringValue());
                        println("Espacement mis à jour : " + currentSpacing);
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        println("Valeur invalide pour l'espacement");
                    }
                });

        cp5.addTextfield("exportWidth")
                .setPosition(20, 160)
                .setSize(100, 30)
                .setLabel("Largeur (mm)")
                .setText(str(exportWidth))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        exportWidth = Float.parseFloat(event.getController().getStringValue());
                        println("Largeur mise à jour : " + exportWidth);
                    } catch (NumberFormatException e) {
                        println("Valeur invalide pour la largeur");
                    }
                });

        cp5.addTextfield("exportHeight")
                .setPosition(20, 200)
                .setSize(100, 30)
                .setLabel("Hauteur (mm)")
                .setText(str(exportHeight))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        exportHeight = Float.parseFloat(event.getController().getStringValue());
                        println("Hauteur mise à jour : " + exportHeight);
                    } catch (NumberFormatException e) {
                        println("Valeur invalide pour la hauteur");
                    }
                });

    }
    private void refreshPreview() {
        processImage();
        showPreview = true;
        redraw(); // Force le redessin immédiat
        println("currentSpacing : " + currentSpacing);
    }

    public void loadImage() {
        selectInput("Sélectionner une image", "imageSelected");
    }

    public void saveFile() {
        if (embroidery != null) {
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
                boolean isSVG = selectedFormat.equals("SVG");

                int pesWidth = (int) (exportWidth * 10);  // Convertir en unités PES (1 unité = 0.1 mm)
                int pesHeight = (int) (exportHeight * 10);

                PEmbroiderWriter.write(path, embroidery.polylines, embroidery.colors, pesWidth, pesHeight, isSVG);
                println("Fichier PES sauvegardé avec taille : " + exportWidth + "mm x " + exportHeight + "mm");
            } catch (Exception e) {
                println("Erreur de sauvegarde : " + e.getMessage());
            }
        }
    }



    private void processImage() {
        float scaleX = exportWidth / 100.0f;  // Conversion mm en facteur d'échelle
        float scaleY = exportHeight / 100.0f;

        if (embroidery == null) {
            embroidery = new PEmbroiderGraphics(this, (int)(img.width * scaleX), (int)(img.height * scaleY));
        } else {
            embroidery.clear();
        }

        embroidery.setStitch(currentSpacing, 5, 0);
        ArrayList<ArrayList<PVector>> stitches = PEmbroiderHatchSatin.hatchSatinRaster(img, currentSpacing, 5);

        embroidery.beginDraw();
        for (ArrayList<PVector> stitch : stitches) {
            if (!stitch.isEmpty()) {
                embroidery.beginShape();
                for (PVector point : stitch) embroidery.vertex(point.x * scaleX, point.y * scaleY);
                embroidery.endShape();
            }
        }
        embroidery.optimize();
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

        noFill();
        for (int i = 0; i < embroidery.polylines.size(); i++) {
            ArrayList<PVector> polyline = embroidery.polylines.get(i);
            int color = embroidery.colors.get(i);
            stroke(color);
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
