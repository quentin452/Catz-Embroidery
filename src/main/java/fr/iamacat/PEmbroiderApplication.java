package fr.iamacat;

import java.io.File;
import java.util.ArrayList;

import processing.controlP5.ControlP5;
import processing.controlP5.Slider;
import processing.controlP5.Textfield;
import processing.controlP5.Toggle;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderHatchSatin;
import processing.embroider.PEmbroiderWriter;

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
    private float currentSpacing = 1;
    private int currentWidth = 1280;
    private int currentHeight = 720;
    private boolean isColorMode = true;

    private boolean isDialogOpen = false;

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
        // --- Boutons ---
        cp5.addButton("loadImage")
                .setPosition(20, 20)
                .setSize(120, 30)
                .setLabel("Charger image")
                .onClick(event -> loadImage());

        cp5.addButton("saveFile")
                .setPosition(160, 20)
                .setSize(120, 30)
                .setLabel("Sauvegarder")
                .onClick(event -> saveFile());

        cp5.addButton("invertAlphas")
                .setPosition(300, 20)
                .setSize(120, 30)
                .setLabel("Inverser Alphas")
                .onClick(event -> invertAlphas());

        // --- Sélecteur de format ---
        cp5.addDropdownList("formatSelector")
                .setPosition(440, 20)
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

        // --- Mode Couleur ---
        Toggle isColorModeField = cp5.addToggle("isColorMode")
                .setPosition(45, 240)
                .setSize(50, 20)
                .setValue(isColorMode)
                .onChange(event -> {
                    isColorMode = event.getController().getValue() == 1;
                    println("Mode : " + (isColorMode ? "Couleur" : "Noir et Blanc"));
                    if (img != null) refreshPreview();
                });
        isColorModeField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-30)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText("Mode Couleur ?")
                .setColor(color(0));

        // --- Champs de texte ---
        Textfield stitchSpacingField = cp5.addTextfield("stitchSpacing")
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
        stitchSpacingField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText("Espacement des points")
                .setColor(color(0));

        Textfield exportWidthField = cp5.addTextfield("exportWidth")
                .setPosition(20, 160)
                .setSize(100, 30)
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
        exportWidthField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText("Largeur (mm)")
                .setColor(color(0));

        Textfield exportHeightField = cp5.addTextfield("exportHeight")
                .setPosition(20, 200)
                .setSize(100, 30)
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
        exportHeightField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText("Hauteur (mm)")
                .setColor(color(0));

        // --- Barre de progression ---
        progressBar = cp5.addSlider("progressBar")
                .setPosition(20, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel("Progression");
        progressBar.setVisible(false);
    }

    private void invertAlphas() {
        if (embroidery != null) {
            for (int i = 0; i < embroidery.colors.size(); i++) {
                int color = embroidery.colors.get(i);
                int alpha = (color >> 24) & 0xFF; // Extract alpha
                int invertedAlpha = 255 - alpha; // Invert alpha
                // Reconstruct the color with the inverted alpha
                int newColor = (invertedAlpha << 24) | (color & 0x00FFFFFF);
                embroidery.colors.set(i, newColor); // Update the color
            }
            refreshPreview(); // Refresh the preview to show changes
        }
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
        if (!isDialogOpen) {
            isDialogOpen = true;
            selectInput("Sélectionner une image", "imageSelected");
        }
    }

    public void saveFile() {
        if (!isDialogOpen) {
            if (embroidery != null) {
                isDialogOpen = true;
                selectOutput("Sauvegarder sous", "fileSaved");
            }
        }
    }

    public void imageSelected(File selection) {
        isDialogOpen = false;
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
        isDialogOpen = false;
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

            PImage processedImage = img.copy(); // Make a copy of the image

            if (!isColorMode) {
                // Convert the image to black and white (grayscale)
                processedImage.filter(GRAY); // Apply grayscale filter
            }

            embroidery.setStitch(currentSpacing, 5, 0);
            ArrayList<ArrayList<PVector>> stitches = PEmbroiderHatchSatin.hatchSatinRaster(processedImage, currentSpacing, 5);
            for (int i = 0; i < stitches.size(); i++) {
                ArrayList<PVector> stitch = stitches.get(i);
                ArrayList<PVector> validStitch = new ArrayList<>();

                for (PVector point : stitch) {
                    int x = (int) point.x;
                    int y = (int) point.y;

                    // Vérifier si le pixel est valide (alpha != 0)
                    if (processedImage.pixels[y * processedImage.width + x] != 0) {
                        validStitch.add(point);
                    }
                }

                if (!validStitch.isEmpty()) {
                    embroidery.beginShape();
                    for (PVector point : validStitch) {
                        embroidery.vertex(point.x, point.y);
                    }
                    embroidery.endShape();
                }

                progressBar.setValue(PApplet.map(i, 0, stitches.size(), 0, 100)); // Mise à jour de la progression
            }
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

        // Précalcul des couleurs des polylignes
        ArrayList<Integer> polylineColors = new ArrayList<>();
        for (ArrayList<PVector> polyline : embroidery.polylines) {
            int r = 0, g = 0, b = 0;
            int count = 0;
            for (PVector p : polyline) {
                int pixelColor = img.get((int) p.x, (int) p.y);
                int alpha = (pixelColor >> 24) & 0xFF;
                if (alpha > 0) { // Ignore les pixels transparents
                    r += (pixelColor >> 16) & 0xFF;
                    g += (pixelColor >> 8) & 0xFF;
                    b += pixelColor & 0xFF;
                    count++;
                }
            }
            if (count > 0) {
                r /= count;
                g /= count;
                b /= count;
                polylineColors.add(color(r, g, b));
            } else {
                polylineColors.add(color(0, 0, 0, 0)); // Transparent
            }
        }

        // Dessin des polylignes avec les couleurs précalculées
        for (int i = 0; i < embroidery.polylines.size(); i++) {
            ArrayList<PVector> polyline = embroidery.polylines.get(i);
            int colorToUse = isColorMode ? polylineColors.get(i) : color(0); // Noir en mode noir et blanc
            stroke(colorToUse);
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