package fr.iamacat.pembroider_converter;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.iamacat.utils.ApplicationUtil;
import fr.iamacat.utils.Logger;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import processing.controlP5.*;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderReader;
import processing.embroider.PEmbroiderWriter;
import processing.event.KeyEvent;

import javax.swing.*;
public class Main extends PApplet implements Translatable {

    private PImage img;
    private PEmbroiderGraphics embroidery;
    private ControlP5 cp5;
    private Slider progressBar;
    private final String selectedFormat = "PES";
    private boolean showPreview = false;
    private float exportWidth = 95;  // Largeur par défaut en mm
    private float exportHeight = 95; // Hauteur par défaut en mm
    private float currentSpacing = 10;
    private float currentStrokeWeight = 25;
    private int currentWidth = 1280;
    private int currentHeight = 720;
    private CColor selectedColor = new CColor().setForeground(color(255, 0, 0));
    private boolean enableEscapeMenu = false;
    private boolean isDialogOpen = false;
    private ColorType colorType = ColorType.MultiColor;

    private final String[] hatchModes = {"CROSS", "PARALLEL", "CONCENTRIC" , "SPIRAL" , "PERLIN"};
    private String selectedHatchMode = "CROSS";

    public boolean FillB= false;

    String hoverText = "";
    boolean showTooltip = false;
    PFont tooltipFont;


    private enum ColorType {
        MonoColor,
        MultiColor,
        BlackAndWhite,
        Realistic
    }

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.pembroider_converter.Main");
    }

    @Override
    public void settings() {
        size(currentWidth, currentHeight);
    }

    @Override
    public void setup() {
        tooltipFont = createFont("Arial", 24);

        Translator.getInstance().registerTranslatable(this);

        surface.setResizable(true);
        cp5 = new ControlP5(this);
        embroidery = new PEmbroiderGraphics(this, width, height);
        setupGUI();

        new DropTarget((Component) this.getSurface().getNative(), new java.awt.dnd.DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    Transferable transferable = event.getTransferable();
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File file = files.get(0);
                            showPreview = false;
                            imageSelected(file);
                            showPreview = true;
                        }
                    }
                } catch (Exception e) {
                    Logger.getInstance().log(Logger.Project.Converter, "Erreur lors du glisser-déposer : " + e.getMessage());
                }
            }
        });
    }

    private void setupGUI() {
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
        cp5.addButton("enableFillMode")
                .setPosition(20, 320)
                .setSize(100, 30)
                .setLabel(Translator.getInstance().translate("enable_fill_mode"))
                .onClick(event -> updateFillMode());
        cp5.addDropdownList("hatchModeSelector")
                .setPosition(580, 22)
                .setSize(135, 120)
                .addItems(hatchModes)
                .setLabel(Translator.getInstance().translate("hatch_mode"))
                .onChange(event -> {
                    int index = (int) event.getController().getValue();
                    if (index >= 0 && index < hatchModes.length) {
                        selectedHatchMode = hatchModes[index];
                        if (img != null) refreshPreview();
                    }
                });
        Textfield maxMultiColorTextField = cp5.addTextfield("maxMultiColorField")
                .setPosition(20, 280)
                .setSize(100, 30)
                .setColor(color(255))
                .setText(str(embroidery.maxColors))
                .setAutoClear(false);

        maxMultiColorTextField.onChange(event -> {
            try {
                embroidery.maxColors = Integer.parseInt(event.getController().getStringValue());
                if (embroidery.maxColors < 1) embroidery.maxColors = 1;
                if (img != null) refreshPreview();
                maxMultiColorTextField.setVisible(colorType != ColorType.MonoColor);  // TODO FIX THIS DOESN'T WORK
            } catch (NumberFormatException e) {
                Logger.getInstance().log(Logger.Project.Converter,"Invalid value for max multi color");
            }
        });
        maxMultiColorTextField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate("max_color_multicolor_feature"))
                .setColor(color(0));

        maxMultiColorTextField.onEnter(event -> {
            hoverText = Translator.getInstance().translate("max_color_multicolor_feature");
            showTooltip = true;
        });

        maxMultiColorTextField.onLeave(event -> {
            showTooltip = false;
        });
        Controller<?> monoColorController = cp5.getController("monoColorPicker");
        if (monoColorController != null) {
            monoColorController.addListener(event -> {
                if (event.getController().getName().equals("monoColorPicker")) {
                    selectedColor = event.getController().getColor();
                    if (img != null) refreshPreview();
                    monoColorController.setVisible(colorType != ColorType.MultiColor); // TODO FIX THIS DOESN'T WORK
                }
            });
        } else {
            Logger.getInstance().log(Logger.Project.Converter, "Controller for monoColorPicker is null");
        }
        // TODO FIX monoColorController is null
        /*assert monoColorController != null;
        monoColorController.onEnter(event -> {
            hoverText = Translator.getInstance().translate("monoColorPicker");
            showTooltip = true;
        });

        monoColorController.onLeave(event -> {
            showTooltip = false;
        });*/
        DropdownList colorModeDropdown = cp5.addDropdownList("colorMode")
                .setPosition(310, 22)
                .setSize(100, 150)
                .setBarHeight(20)
                .setItemHeight(20)
                .addItems(new String[] {"Mono Color", "Multi Color", "Black and White", "Realistic"})
                .setValue(0)
                .onChange(event -> {
                    int selectedIndex = (int) event.getController().getValue();
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
                        case 3:
                            colorType = ColorType.Realistic;
                            break;
                    }
                    if (img != null) refreshPreview();
                });
        colorModeDropdown.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-30)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate("color_mode"))
                .setColor(color(255));
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
        stitchSpacingField.onEnter(event -> {
            hoverText = Translator.getInstance().translate("space_between_points");
            showTooltip = true;
        });

        stitchSpacingField.onLeave(event -> {
            showTooltip = false;
        });
        Textfield strokeWeightField = cp5.addTextfield("strokeWeight")
                .setPosition(20, 240)
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
                .setText(Translator.getInstance().translate("stroke_weight"))
                .setColor(color(0));
        strokeWeightField.onEnter(event -> {
            hoverText = Translator.getInstance().translate("stroke_weight");
            showTooltip = true;
        });

        strokeWeightField.onLeave(event -> {
            showTooltip = false;
        });
        Textfield exportWidthField = cp5.addTextfield("exportWidth")
                .setPosition(20, 160)
                .setSize(100, 30)
                .setText(str(exportWidth))
                .setAutoClear(false)
                .setLock(true)// TODO FIX https://github.com/quentin452/Catz-Embroidery/issues/1
                .setVisible(false)// TODO FIX https://github.com/quentin452/Catz-Embroidery/issues/1
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
        exportWidthField.onEnter(event -> {
            hoverText = Translator.getInstance().translate("width_in_mm");
            showTooltip = true;
        });

        exportWidthField.onLeave(event -> {
            showTooltip = false;
        });
        Textfield exportHeightField = cp5.addTextfield("exportHeight")
                .setPosition(20, 200)
                .setSize(100, 30)
                .setText(str(exportHeight))
                .setAutoClear(false)
                .setLock(true)// TODO FIX https://github.com/quentin452/Catz-Embroidery/issues/1
                .setVisible(false)// TODO FIX https://github.com/quentin452/Catz-Embroidery/issues/1
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
        exportHeightField.onEnter(event -> {
            hoverText = Translator.getInstance().translate("height_in_mm");
            showTooltip = true;
        });

        exportHeightField.onLeave(event -> {
            showTooltip = false;
        });
        progressBar = cp5.addSlider("progressBar")
                .setPosition(20, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel(Translator.getInstance().translate("progess"));
        progressBar.setVisible(false);
    }

    private void updateFillMode()
    {
        FillB = !FillB;
        if (img != null) refreshPreview();
    }

    private void resetProgressBar() {
        progressBar.setValue(0);
        progressBar.setVisible(true);
    }

    private void refreshPreview() {
        showPreview = false;
        processImageWithProgress();
        showPreview = true;
    }

    public void loadImage() {
        if (!isDialogOpen) {
            isDialogOpen = true;
            selectInput(Translator.getInstance().translate("select_an_image"), "imageSelected");
        }
    }

    public void saveFile() {
        if (!isDialogOpen) {
            if (embroidery != null) {
                isDialogOpen = true;
                selectOutput(Translator.getInstance().translate("save_as"), "fileSaved");
            }
        }
    }

    public void imageSelected(File selection) {
        isDialogOpen = false;
        if (selection != null) {
            String fileName = selection.getName().toLowerCase();
            if (fileName.endsWith(".jpg") || fileName.endsWith(".png") ||
                    fileName.endsWith(".jpeg") || fileName.endsWith(".bmp") ||
                    fileName.endsWith(".gif")) {

                img = loadImage(selection.getAbsolutePath());

                if (img != null) {
                    // New: Edge analysis logging
                    logImageEdges(img, selection.getName());
                    embroidery.extractedColors = extractColors(img);
                    refreshPreview();
                    enableEscapeMenu = true;
                    showPreview = true;
                } else {
                    Logger.getInstance().log(Logger.Project.Converter,
                            "Le fichier sélectionné n'est pas une image valide.");
                }

            } else if (fileName.endsWith(".pes")) {
                PEmbroiderReader.EmbroideryData data = PEmbroiderReader.read(selection.getAbsolutePath(), width, height);
                ArrayList<ArrayList<PVector>> polylines = data.getPolylines();
                ArrayList<Integer> colors = data.getColors();
                println("Polylines: " + polylines);
                println("Colors: " + colors);

                if (polylines != null && colors != null) {
                    img = createImageFromPolylines(polylines, colors, width, height);

                    if (img != null) {
                        // New: Edge analysis for generated images
                        logImageEdges(img, "Generated from PES");
                        refreshPreview();
                        enableEscapeMenu = true;
                        showPreview = true;
                    } else {
                        Logger.getInstance().log(Logger.Project.Converter,
                                "Erreur lors de la création de l'image depuis les polylines et couleurs.");
                    }
                }
            } else {
                Logger.getInstance().log(Logger.Project.Converter,
                        "Le fichier sélectionné n'est pas un fichier image valide.");
            }
        }
    }

    public Set<Integer> extractColors(PImage img) {
        Set<Integer> colors = new HashSet<>();
        img.loadPixels();
        for (int pixel : img.pixels) {
            colors.add(pixel);
        }
        return colors;
    }

    // New edge analysis method
    private void logImageEdges(PImage im, String sourceName) {
        println("Edge analysis for: " + sourceName);
        println("Dimensions: " + im.width + "x" + im.height);

        analyzeEdge(im, "Top edge", 0, 0, im.width, 0);          // Top border
        analyzeEdge(im, "Bottom edge", 0, im.height-1, im.width, im.height-1); // Bottom
        analyzeEdge(im, "Left edge", 0, 0, 0, im.height);        // Left border
        analyzeEdge(im, "Right edge", im.width-1, 0, im.width-1, im.height); // Right
    }

    private void analyzeEdge(PImage im, String edgeName, int x1, int y1, int x2, int y2) {
        int samples = 10;
        int transparentCount = 0;
        int blackCount = 0;
        int whiteCount = 0;
        int otherCount = 0;

        for (int i = 0; i < samples; i++) {
            float t = (float)i/(samples-1);
            int x = (int)lerp(x1, x2, t);
            int y = (int)lerp(y1, y2, t);
            x = constrain(x, 0, im.width-1);
            y = constrain(y, 0, im.height-1);

            int c = im.get(x, y);
            int a = (c >> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            if (a < 50) {
                transparentCount++;
            } else if (r < 50 && g < 50 && b < 50) {
                blackCount++;
            } else if (r > 200 && g > 200 && b > 200) {
                whiteCount++;
            } else {
                otherCount++;
            }
        }

        String logMsg = String.format(
                "%s: Transparent=%d (%.0f%%) | Black=%d | White=%d | Other=%d",
                edgeName,
                transparentCount,
                (transparentCount * 100.0f)/samples,
                blackCount,
                whiteCount,
                otherCount
        );

        println(logMsg);
    }

    private PImage createImageFromPolylines(ArrayList<ArrayList<PVector>> polylines, ArrayList<Integer> colors, int width, int height) {
        PImage image = createImage(width, height, PApplet.RGB);
        image.loadPixels();

        // Effacer l'image pour obtenir un fond blanc
        for (int i = 0; i < image.pixels.length; i++) {
            image.pixels[i] = color(255);
        }

        // Dessiner les polylines sur l'image
        for (int i = 0; i < polylines.size(); i++) {
            ArrayList<PVector> polyline = polylines.get(i);
            int color = colors.get(i);

            for (int j = 0; j < polyline.size() - 1; j++) {
                PVector p1 = polyline.get(j);
                PVector p2 = polyline.get(j + 1);
                drawLineOnImage(image, p1, p2, color);
            }
        }

        image.updatePixels();
        return image;
    }

    private void drawLineOnImage(PImage image, PVector p1, PVector p2, int color) {
        int x1 = (int) PApplet.map(p1.x, 0, width, 0, image.width);
        int y1 = (int) PApplet.map(p1.y, 0, height, 0, image.height);
        int x2 = (int) PApplet.map(p2.x, 0, width, 0, image.width);
        int y2 = (int) PApplet.map(p2.y, 0, height, 0, image.height);

        // Algorithme de tracé de ligne de Bresenham
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x1 >= 0 && x1 < image.width && y1 >= 0 && y1 < image.height) {
                image.set(x1, y1, color);
            }

            if (x1 == x2 && y1 == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
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
        }
        embroidery.beginDraw();
        embroidery.clear();
        img.resize(1000, 1000);
        embroidery.beginCull();
        switch (selectedHatchMode) {
            case "CROSS":
                embroidery.hatchMode(PEmbroiderGraphics.CROSS);
                break;
            case "PARALLEL":
                embroidery.hatchMode(PEmbroiderGraphics.PARALLEL);
                break;
            case "CONCENTRIC":
                embroidery.hatchMode(PEmbroiderGraphics.CONCENTRIC);
                break;
            case "SPIRAL":
                embroidery.hatchMode(PEmbroiderGraphics.SPIRAL);
                break;
            case "PERLIN":
                embroidery.hatchMode(PEmbroiderGraphics.PERLIN);
                break;
            default:
                embroidery.hatchMode(PEmbroiderGraphics.CROSS);
        }
        embroidery.hatchSpacing(currentSpacing);
        embroidery.colorizeEmbroideryFromImage = false;
        embroidery.strokeWeight(currentStrokeWeight);

        if (colorType == ColorType.MonoColor) {
            if (!FillB) {
                embroidery.noFill();
                embroidery.stroke(0,0,0);
            } else {
                embroidery.fill(selectedColor.getForeground());
            }
            embroidery.popyLineMulticolor = false;
        }
        else if (colorType == ColorType.MultiColor) {
            if (!FillB) {
                embroidery.noFill();
            } else {
                embroidery.fill(0,0,0);
            }
            embroidery.stroke(0,0,0);
            embroidery.popyLineMulticolor = true;
            embroidery.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
            embroidery.strokeSpacing(currentSpacing);
        }
        else if (colorType == ColorType.BlackAndWhite) {
            if (!FillB) {
                embroidery.noFill();
                embroidery.stroke(0,0,0);
            } else {
                embroidery.fill(0, 0, 0);
            }
            embroidery.popyLineMulticolor = false;
        } else if (colorType == ColorType.Realistic) {
            embroidery.colorizeEmbroideryFromImage = true;
            if (!FillB) {
                embroidery.noFill();
            } else {
                embroidery.fill(0,0,0);
            }
            embroidery.stroke(0,0,0);
            embroidery.popyLineMulticolor = false;
        }
        embroidery.image(img, 860, 70);
        embroidery.endCull();
    }

    @Override
    public void draw() {
        background(240);
        if (img != null && img.width > 0 && img.height > 0) {
            image(img, 190, 70, (float) width / 2 - 40, height - 90);
        }
        if (showPreview && embroidery != null) {
            float scaleX = exportWidth / width;
            float scaleY = exportHeight / height;
            float scale = max(scaleX, scaleY);
            float offsetX = (exportWidth - width * scale) / 2;
            float offsetY = (exportHeight - height * scale) / 2;
            offsetX += 600;
            if (img != null && embroidery.polylines != null && embroidery.colors != null && !embroidery.polylines.isEmpty() && !embroidery.colors.isEmpty()) {
                embroidery.visualize(true, false, false, Integer.MAX_VALUE,
                        (float) ((int) exportWidth * 2.71430),
                        (float) ((int) exportHeight * 2.71430), offsetX, offsetY);
            } else {
                Logger.getInstance().log(Logger.Project.Converter, "Erreur: embroidery.polylines ou embroidery.colors est vide.");
            }
        }
        if (showTooltip) {
            textAlign(CENTER, BOTTOM);
            textFont(tooltipFont);
            fill(0);
            text(hoverText, width / 2, height - 20);
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if ((event.isControlDown() || event.isMetaDown()) && event.getKeyCode() == java.awt.event.KeyEvent.VK_V) {
            pasteImageFromClipboard();
        } else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_P) {
            showPreview = !showPreview;
        }
    }

    private void pasteImageFromClipboard() {
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);

            if (transferable != null) {
                boolean imageProcessed = false;

                // Vérifier si le presse-papiers contient un fichier image
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        java.awt.Image awtImage = javax.imageio.ImageIO.read(file);
                        img = new PImage(awtImage);
                        refreshPreview();
                        imageProcessed = true;
                        enableEscapeMenu = true;
                    }
                }

                // Vérifier si le presse-papiers contient une image directement
                if (!imageProcessed && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    java.awt.Image awtImage = (java.awt.Image) transferable.getTransferData(DataFlavor.imageFlavor);
                    img = new PImage(awtImage);
                    refreshPreview();
                    imageProcessed = true;
                    enableEscapeMenu = true;
                }

                if (!imageProcessed) {
                    Logger.getInstance().log(Logger.Project.Converter, "Aucune image trouvée dans le presse-papiers.");
                }

            } else {
                Logger.getInstance().log(Logger.Project.Converter, "Le presse-papiers est vide ou inaccessible.");
            }
        } catch (Exception e) {
            Logger.getInstance().log(Logger.Project.Converter, "Erreur lors du collage de l'image : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showExitDialog() {
        if (!enableEscapeMenu) {
            ApplicationUtil.exitApplication(this);
            return;
        }
        String[] options = {Translator.getInstance().translate("save_and_quit"), Translator.getInstance().translate("exit_without_save"),Translator.getInstance().translate("cancel")};
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
            ApplicationUtil.exitApplication(this);
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

    @Override
    public void exit() {
        showExitDialog();
    }

    @Override
    public void updateTranslations() {

    }

}