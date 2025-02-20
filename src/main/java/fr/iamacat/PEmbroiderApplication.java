package fr.iamacat;

import processing.controlP5.ControlP5;
import processing.controlP5.Slider;
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
    private Slider progressBar;  // Utilisation d'un Slider comme barre de progression
    private String[] formats = {"PES"/*, "DST", "EXP", "SVG"*/};
    private String selectedFormat = "PES";
    private boolean showPreview = false;
    private float exportWidth = 95;  // Largeur par défaut en mm
    private float exportHeight = 95; // Hauteur par défaut en mm
    private float currentSpacing = 5;
    private int currentWidth = 1280;
    private int currentHeight = 720;
    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderApplication");
    }

    @Override
    public void settings() {
        size(currentWidth, currentHeight);
    }

    @Override
    public void setup() {
        surface.setResizable(true);
        cp5 = new ControlP5(this);
        setupGUI();
    }


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
                    resetProgressBar();  // Réinitialiser la barre de progression
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
                        resetProgressBar();
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        println("Invalid Value for Spacing");
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
                        if (img != null) refreshPreview();
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
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        println("Valeur invalide pour la hauteur");
                    }
                });

        // Utilisation d'un Slider comme barre de progression
        progressBar = cp5.addSlider("progressBar")
                .setPosition(20, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel("Progression");
        progressBar.setVisible(false);
    }

    private void resetProgressBar() {
        progressBar.setValue(0); // Réinitialiser la barre de progression à 0
        progressBar.setVisible(true); // Réactiver la barre de progression
    }

    private void refreshPreview() {
        processImageWithProgress();
        showPreview = true;
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
                resizeImageToDimensions();
                processImageWithProgress();
                showPreview = true;
            }
        }
    }

    private void resizeImageToDimensions() {
        if (img != null) {
            img.resize((int) (exportWidth * 2.71430), (int) (exportHeight * 2.71430));
        }
    }

    public void fileSaved(File selection) {
        if (selection != null) {
            new Thread(() -> {
                String path = selection.getAbsolutePath();
                try {
                    resetProgressBar();
                    boolean isSVG = selectedFormat.equals("SVG");
                    progressBar.setValue(50);
                    PEmbroiderWriter.write(path, embroidery.polylines, embroidery.colors, img.width, img.height, isSVG);
                    progressBar.setValue(100);
                    progressBar.setVisible(false);
                } catch (Exception e) {
                    println("Error during saving file : " + e.getMessage());
                } finally {
                    progressBar.setValue(0);
                    progressBar.setVisible(false);
                }
            }).start();
        }
    }

    private void processImageWithProgress() {
        new Thread(() -> {
            if (embroidery == null) {
                embroidery = new PEmbroiderGraphics(this, img.width, img.height);
            } else {
                embroidery.clear();
            }
            embroidery.setStitch(currentSpacing, 5, 0);
            ArrayList<ArrayList<PVector>> stitches = PEmbroiderHatchSatin.hatchSatinRaster(img, currentSpacing, 5);
            for (int i = 0; i < stitches.size(); i++) {
                ArrayList<PVector> stitch = stitches.get(i);
                if (!stitch.isEmpty()) {
                    embroidery.beginShape();
                    for (PVector point : stitch) embroidery.vertex(point.x, point.y);
                    embroidery.endShape();
                }
                progressBar.setValue(PApplet.map(i, 0, stitches.size(), 0, 100)); // Mise à jour de la progression
            }
            //embroidery.optimize();
            progressBar.setValue(100);
            progressBar.setVisible(false);
        }).start();
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