package fr.iamacat;

import java.io.File;
import java.util.ArrayList;

import fr.iamacat.utils.Logger;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import processing.controlP5.*;
import processing.core.PApplet;
import processing.core.PImage;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderWriter;

import javax.swing.*;
// TODO PRINT STATISTICS
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
    private float currentSpacing = 8;
    private float currentStrokeWeight = 8;
    private int currentWidth = 1280;
    private int currentHeight = 720;
    private CColor selectedColor = new CColor().setForeground(color(255, 0, 0)); // Use a default initial color
    private boolean enableEscapeMenu = false;
    private boolean isDialogOpen = false;
    private ColorType colorType = ColorType.MultiColor;
    private enum ColorType {
        MonoColor,
        MultiColor,
        BlackAndWhite
    }

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
        embroidery = new PEmbroiderGraphics(this, width, height);
        setupGUI();
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
        cp5.addButton("refreshPreview")
                .setPosition(500, 20)
                .setSize(120, 30)
                .setLabel(/*Translator.getInstance().translate("invert_alpha")*/"Refresh Preview")
                .onClick(event -> refreshPreview());

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
                    if (embroidery != null) {
                        showPreview = false; // Masquer la prévisualisation pour la redessiner
                        showPreview = true;  // Forcer l'affichage de la nouvelle prévisualisation
                    }
                });
        // Créez d'abord le champ de texte maxMultiColorTextField
        Textfield maxMultiColorTextField = cp5.addTextfield("maxMultiColorField")
                .setPosition(20, 320)
                .setSize(100, 30)
                .setText(str(embroidery.maxMultiColors))
                .setAutoClear(false);

        // Ajoutez ensuite le gestionnaire d'événements
        maxMultiColorTextField.onChange(event -> {
            try {
                embroidery.maxMultiColors = Integer.parseInt(event.getController().getStringValue());
                if (embroidery.maxMultiColors < 1) embroidery.maxMultiColors = 1; // Éviter les nombres trop bas
                if (img != null) refreshPreview();

                // Désactiver le champ maxMultiColorField si MonoColor est sélectionné
                if (colorType == ColorType.MonoColor) {
                    maxMultiColorTextField.setVisible(false);  // Masquer le champ
                } else {
                    maxMultiColorTextField.setVisible(true);  // Le rendre visible dans les autres modes
                }
            } catch (NumberFormatException e) {
                Logger.getInstance().log(Logger.Project.Converter,"Invalid value for max multi color");
            }
        });

        // Créez et gérez le contrôleur pour monoColorPicker
        Controller<?> monoColorController = cp5.getController("monoColorPicker");
        if (monoColorController != null) {
            monoColorController.addListener(event -> {
                if (event.getController().getName().equals("monoColorPicker")) {
                    selectedColor = event.getController().getColor();
                    if (img != null) refreshPreview();

                    // Masquer ou rendre visible le champ monoColorPicker en fonction du mode de couleur
                    if (colorType == ColorType.MultiColor) {
                        monoColorController.setVisible(false);  // Masquer le champ
                    } else {
                        monoColorController.setVisible(true);  // Le rendre visible dans les autres modes
                    }
                }
            });
        } else {
            Logger.getInstance().log(Logger.Project.Converter, "Controller for monoColorPicker is null");
        }


        DropdownList colorModeDropdown = cp5.addDropdownList("colorMode")
                .setPosition(45, 240)
                .setSize(100, 150)
                .setBarHeight(20)
                .setItemHeight(20)
                .addItems(new String[] {"Mono Color", "Multi Color", "Black and White"}) // Ajoutez ici les options de votre liste déroulante
                .setValue(0) // Par défaut, la première option est sélectionnée
                .onChange(event -> {
                    int selectedIndex = (int) event.getController().getValue();
                    // Logique pour gérer les différentes options sélectionnées
                    switch (selectedIndex) {
                        case 0:
                            colorType = ColorType.MonoColor;
                            break;
                        case 1:
                            colorType = ColorType.MultiColor;
                            break;
                        case 2:
                            colorType = ColorType.BlackAndWhite;
                            break;
                    }
                    if (img != null) refreshPreview();
                });

        colorModeDropdown.getCaptionLabel()
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
        Textfield strokeWeightField = cp5.addTextfield("strokeWeight")
                .setPosition(20, 0)
                .setSize(100, 30)
                .setText(str(currentStrokeWeight))
                .setAutoClear(false)
                .onChange(event -> {
                    try {
                        currentStrokeWeight = Float.parseFloat(event.getController().getStringValue());
                        if (img != null) refreshPreview();
                    } catch (NumberFormatException e) {
                        Logger.getInstance().log(Logger.Project.Converter,"Invalid Value for currentStrokeWeight");
                    }
                });
        strokeWeightField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText("Stroke weight")
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
                    PEmbroiderWriter.write(path, embroidery.polylines, embroidery.colors, (int) exportWidth, (int) exportHeight, isSVG);
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

        img.resize(1000, 1000); // Ajuster pour des images petites
        embroidery.beginCull();

        // Configuration commune
        embroidery.hatchMode(PEmbroiderGraphics.CROSS);
        embroidery.hatchSpacing(currentSpacing);

        // Mode MonoColor : une seule couleur
        if (colorType == ColorType.MonoColor) {
            embroidery.noStroke();
            embroidery.popyLineMulticolor = false;
            embroidery.fill(selectedColor.getForeground()); // Couleur choisie
            embroidery.image(img, 860, 70);
        }
        // Mode MultiColor : plusieurs couleurs aléatoires
        else if (colorType == ColorType.MultiColor) {
            embroidery.noFill();
            embroidery.popyLineMulticolor = true;
            embroidery.strokeWeight(currentStrokeWeight);
            embroidery.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
            embroidery.strokeSpacing(currentSpacing);
            embroidery.image(img, 860, 70);
        }
        // Mode BlackAndWhite : noir et blanc
        else if (colorType == ColorType.BlackAndWhite) {
            embroidery.fill(0, 0, 0); // Noir
            embroidery.noStroke();
            embroidery.popyLineMulticolor = false;
            embroidery.image(img, 860, 70);
        }

        embroidery.endCull();
       // embroidery.HATCH_MODE = PEmbroiderGraphics.CROSS; // TODO ADD A DROPDOWN TO CHOOSE SOME HATCH_MODE and use stroke or not
    }

    @Override
    public void draw() {
        background(240);
        if (img != null) {
            image(img, 190, 70, width / 2 - 40, height - 90);
        }
        if (showPreview && embroidery != null) {
            // Centrer le rendu dans l'espace de visualisation
            float scaleX = exportWidth / width;
            float scaleY = exportHeight / height;
            float scale = max(scaleX, scaleY); // Utiliser le même facteur pour X et Y

            float offsetX = (exportWidth - width * scale) / 2;
            float offsetY = (exportHeight - height * scale) / 2;
            offsetX += 600;

            // Assurez-vous que la taille de la liste est suffisante pour éviter les erreurs d'index
            if (embroidery.polylines.size() > 0 && embroidery.colors.size() > 0) {
                embroidery.visualize(true, false, false, Integer.MAX_VALUE,
                        (float)((int)exportWidth * 2.71430),
                        (float)((int)exportHeight * 2.71430), offsetX, offsetY);
            }
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