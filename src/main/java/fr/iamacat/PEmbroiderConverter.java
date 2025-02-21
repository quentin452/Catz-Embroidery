package fr.iamacat;

import java.io.File;

import fr.iamacat.utils.Logger;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import processing.controlP5.ControlP5;
import processing.controlP5.Slider;
import processing.controlP5.Textfield;
import processing.controlP5.Toggle;
import processing.core.PApplet;
import processing.core.PImage;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderWriter;

import javax.swing.*;

public class PEmbroiderConverter extends PApplet implements Translatable {

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
    private boolean isColorMode = true;
    private boolean enableEscapeMenu = false;

    private boolean isDialogOpen = false;

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderConverter");
    }

    @Override
    public void settings() {
        size(currentWidth, currentHeight);
    }

    @Override
    public void setup() {
        Translator.getInstance().registerTranslatable(this);

        surface.setResizable(true);
        cp5 = new ControlP5(this);
        setupGUI();
        embroidery = new PEmbroiderGraphics(this, width, height);
    }

    private void setupGUI() {
        // --- Boutons ---
        cp5.addButton("loadImage")
                .setPosition(20, 20)
                .setSize(120, 30)
                .setLabel(Translator.getInstance().translate("load_image"))
                .onClick(event -> loadImage());

        cp5.addButton("saveFile")
                .setPosition(160, 20)
                .setSize(120, 30)
                .setLabel(Translator.getInstance().translate("saving"))
                .onClick(event -> saveFile());

        cp5.addButton("invertAlphas")
                .setPosition(300, 20)
                .setSize(120, 30)
                .setLabel(Translator.getInstance().translate("invert_alpha"))
                .onClick(event -> invertAlphas());

        // --- Sélecteur de format ---
        cp5.addDropdownList("formatSelector")
                .setPosition(440, 22)
                .setSize(135, 120)
                .addItems(formats)
                .setLabel(Translator.getInstance().translate("format_for_preview"))
                .onChange(event -> {
                    float[] values = event.getController().getArrayValue();
                    if (values.length > 0) {
                        int index = (int) values[0];
                        if (index >= 0 && index < formats.length) {
                            selectedFormat = formats[index];
                            Logger.getInstance().log(Logger.Project.Converter,"Format sélectionné: " + selectedFormat);
                        } else {
                            Logger.getInstance().log(Logger.Project.Converter,"Erreur : index hors limites (" + index + ")");
                        }
                    } else {
                        Logger.getInstance().log(Logger.Project.Converter,"Erreur : Aucune valeur sélectionnée !");
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
                    if (img != null) refreshPreview();
                });
        isColorModeField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-30)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate("color_mode"))
                .setColor(color(0));

        // --- Champs de texte ---
        Textfield stitchSpacingField = cp5.addTextfield("stitchSpacing")
                .setPosition(20, 120)
                .setSize(100, 30)
                .setText(str(currentSpacing))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        currentSpacing = Float.parseFloat(event.getController().getStringValue());
                        resetProgressBar();
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        Logger.getInstance().log(Logger.Project.Converter,"Invalid Value for Spacing");
                    }
                });
        stitchSpacingField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate("space_between_points"))
                .setColor(color(0));

        Textfield exportWidthField = cp5.addTextfield("exportWidth")
                .setPosition(20, 160)
                .setSize(100, 30)
                .setText(str(exportWidth))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        exportWidth = Float.parseFloat(event.getController().getStringValue());
                        Logger.getInstance().log(Logger.Project.Converter,"Largeur mise à jour : " + exportWidth);
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        Logger.getInstance().log(Logger.Project.Converter,"Valeur invalide pour la largeur");
                    }
                });
        exportWidthField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate("width_in_mm"))
                .setColor(color(0));

        Textfield exportHeightField = cp5.addTextfield("exportHeight")
                .setPosition(20, 200)
                .setSize(100, 30)
                .setText(str(exportHeight))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        exportHeight = Float.parseFloat(event.getController().getStringValue());
                        Logger.getInstance().log(Logger.Project.Converter,"Hauteur mise à jour : " + exportHeight);
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        Logger.getInstance().log(Logger.Project.Converter,"Valeur invalide pour la hauteur");
                    }
                });
        exportHeightField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate("height_in_mm"))
                .setColor(color(0));

        // --- Barre de progression ---
        progressBar = cp5.addSlider("progressBar")
                .setPosition(20, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel(Translator.getInstance().translate("progess"));
        progressBar.setVisible(false);
    }

    private void invertAlphas() {
        if (img != null && embroidery != null) {
            for (int i = 0; i < embroidery.colors.size(); i++) {
                int color = embroidery.colors.get(i);
                int alpha = (color >> 24) & 0xFF;
                int invertedAlpha = 255 - alpha;
                int newColor = (invertedAlpha << 24) | (color & 0x00FFFFFF);
                embroidery.colors.set(i, newColor);
            }
            refreshPreview();
        }
    }

    private void resetProgressBar() {
        progressBar.setValue(0);
        progressBar.setVisible(true);
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
            String fileName = selection.getName().toLowerCase();
            if (fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg") || fileName.endsWith(".bmp") || fileName.endsWith(".gif")) {
                img = loadImage(selection.getAbsolutePath());
                if (img != null) {
                    refreshPreview();
                    enableEscapeMenu = true;
                    showPreview = true;
                } else {
                    Logger.getInstance().log(Logger.Project.Converter,"Le fichier sélectionné n'est pas une image valide.");
                }
            } else {
                Logger.getInstance().log(Logger.Project.Converter,"Le fichier sélectionné n'est pas un fichier image valide.");
            }
        }
    }

    public void fileSaved(File selection) {
        isDialogOpen = false;
        if (selection != null) {
            new Thread(() -> {
                String path = selection.getAbsolutePath();
                if (!path.contains(".")) {
                    path += ".pes";
                }
                try {
                    resetProgressBar();
                    boolean isSVG = selectedFormat.equals("SVG");
                    progressBar.setValue(50);
                    embroidery.optimize();
                    PEmbroiderWriter.write(path, embroidery.polylines, embroidery.colors, width, height, isSVG);
                    progressBar.setValue(100);
                    progressBar.setVisible(false);
                } catch (Exception e) {
                    Logger.getInstance().log(Logger.Project.Converter,"Error during saving file : " + e.getMessage());
                } finally {
                    progressBar.setValue(0);
                    progressBar.setVisible(false);
                }
            }).start();
        }
    }

    private void processImageWithProgress() {
        if (embroidery == null) {
            embroidery = new PEmbroiderGraphics(this, img.width, img.height);
            embroidery.beginDraw();
        } else {
            embroidery.beginDraw();
            embroidery.clear();
        }
        img.resize((int) (exportWidth * 2.71430), (int) (exportHeight * 2.71430));
        // Use the Cull feature to make lines and strokes not overlap
        embroidery.beginCull();

        // Draw it once, filled.
        if (isColorMode) {
            embroidery.noStroke();
            embroidery.fill(0, 0, 255); // Blue fill
            embroidery.hatchMode(PEmbroiderGraphics.CROSS);
            embroidery.hatchSpacing(currentSpacing);
            embroidery.image(img, 860, 70);
        } else {
            // Draw it again, but just the stroke this time.
            embroidery.noFill();
            embroidery.strokeWeight(30);
            embroidery.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
            embroidery.strokeSpacing(3.0f);
            embroidery.stroke(0, 0, 255); // Blue stroke
            embroidery.image(img, 860, 70);
        }

        embroidery.endCull();
        // PEmbroiderGraphics.DRUNK; seem to be bugged ?
       /* embroidery.HATCH_MODE = PEmbroiderGraphics.CROSS; // TODO ADD A DROPDOWN TO CHOOSE SOME HATCH_MODE
        img.resize((int) (exportWidth * 2.71430), (int) (exportHeight * 2.71430));
        if (isColorMode) {
            embroidery.fill(0, 0, 0);
        } else {
            embroidery.noFill();
        }
        embroidery.image(img, 860, 70);*/
    }

    @Override
    public void draw() {
        background(240);
        if (img != null) {
            image(img, 190, 70, width / 2 - 40, height - 90);
        }
        if (showPreview) {
            embroidery.visualize(true, false,false, Integer.MAX_VALUE);
        }
    }

    @Override
    public void keyPressed() {
        if (key == 'p') {
            showPreview = !showPreview;
        }
    }


    private void showExitDialog() {
        if (!enableEscapeMenu) {
            exitApplication();
            return;
        }
        String[] options = {"Sauvegarder et quitter", "Quitter sans sauvegarder", "Annuler"};
        int option = JOptionPane.showOptionDialog(
                (java.awt.Component) this.getSurface().getNative(),
                "Vous n'avez pas sauvegardé vos données. Voulez-vous sauvegarder avant de quitter ?",
                "Confirmation de fermeture",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (option == 0) {
            saveFileAndExit();
        } else if (option == 1) {
            exitApplication();
        } else {
            isDialogOpen = false;
        }
    }

    private void saveFileAndExit() {
        if (!isDialogOpen) {
            isDialogOpen = true;
            selectOutput("Sauvegarder sous", "fileSaved");
        }
    }
    private void exitApplication() {
        Logger.getInstance().log(Logger.Project.Converter, "Fermeture de l'application");
        Logger.getInstance().archiveLogs();
        if (this.surface.isStopped()) {
            this.exitActual();
        } else if (this.looping) {
            this.finished = true;
            this.exitCalled = true;
        } else if (!this.looping) {
            this.dispose();
            this.exitActual();
        }
    }

    @Override
    public void exit() {
        showExitDialog();
    }

    @Override
    public void updateTranslations() {

    }

}