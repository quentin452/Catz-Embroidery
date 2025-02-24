package fr.iamacat.pembroider_converter;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.*;
import java.util.*;
import java.util.List;

import fr.iamacat.utils.*;
import processing.awt.PSurfaceAWT;
import processing.controlP5.*;
import processing.controlP5.Button;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderReader;
import processing.embroider.PEmbroiderWriter;
import processing.event.KeyEvent;

import javax.swing.*;

import static fr.iamacat.utils.ApplicationUtil.pasteImageFromClipboard;
import static fr.iamacat.utils.DropboxUtil.dropboxClient;
import static fr.iamacat.utils.DropboxUtil.uploadToDropbox;

public class Main extends PApplet implements Translatable {

    private PImage img;
    private PEmbroiderGraphics embroidery;
    private ControlP5 cp5;
    private Slider progressBar;
    private boolean showPreview = false;
    private float visualizationWidth = 95;
    private float visualizationHeight = 95;
    private float exportWidth = 95;  // Largeur par défaut en mm
    private float exportHeight = 95; // Hauteur par défaut en mm
    private float currentSpacing = 10;
    private float currentStrokeWeight = 25;
    private int currentWidth = 1280;
    private int currentHeight = 720;
    private boolean enableEscapeMenu = false;
    private boolean isDialogOpen = false;
    private ColorType colorType = ColorType.MultiColor;

    private final String[] hatchModes = {"CROSS", "PARALLEL", "CONCENTRIC" , "SPIRAL" , "PERLIN"};
    private String selectedHatchMode = "CROSS";

    public boolean FillB= false;

    PFont tooltipFont;

    Textfield maxMultiColorTextField;
    private enum ColorType {
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

        PSurfaceAWT awtSurface = (PSurfaceAWT) getSurface();
        PSurfaceAWT.SmoothCanvas canvas = (PSurfaceAWT.SmoothCanvas) awtSurface.getNative();
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(canvas);
        if (frame != null) {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        }
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
        CP5ComponentsUtil.createActionButton(cp5, 20, 20, 120, 30, "load_image", this::loadImage);
        CP5ComponentsUtil.createActionButton(cp5, 160, 20, 120, 30, "saving", this::saveOnDropboxOrLocally);
        CP5ComponentsUtil.createActionButton(cp5, 20, 320, 100, 30, "enable_fill_mode", this::updateFillMode);
        CP5ComponentsUtil.createDropdownList(cp5, "hatchModeSelector", 580, 22, 135, 120, hatchModes, "hatch_mode", true,
                index -> {
                    selectedHatchMode = hatchModes[index];
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createDropdownList(cp5, "colorMode", 310, 22, 100, 150,
                Arrays.stream(ColorType.values()).map(Enum::name).toArray(String[]::new), "", false,
                index -> {
                    colorType = ColorType.values()[index];
                    if (img != null) refreshPreview();
                });
        maxMultiColorTextField = CP5ComponentsUtil.createNumericTextField(cp5, "maxMultiColorField", 20, 280, 100, 30,
                color(255), color(0), str(embroidery.maxColors), "max_color",
                value -> {
                    embroidery.maxColors = Math.max(1, value.intValue());
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "stitchSpacing", 20, 120, 100, 30, color(255), color(0),
                str(currentSpacing), "space_between_points",
                value -> {
                    currentSpacing = value;
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "strokeWeight", 20, 240, 100, 30, color(255), color(0),
                str(currentStrokeWeight), "stroke_weight",
                value -> {
                    currentStrokeWeight = value;
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "exportWidthField", 20, 160, 100, 30, color(255), color(0),
                str(exportWidth), "width_in_mm",
                value -> {
                    exportWidth = value;
                    Logger.getInstance().log(Logger.Project.Converter, "Largeur mise à jour : " + exportWidth);
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "exportHeightField", 20, 200, 100, 30, color(255), color(0),
                str(exportHeight), "height_in_mm",
                value -> {
                    exportHeight = value;
                    Logger.getInstance().log(Logger.Project.Converter, "Hauteur mise à jour : " + exportHeight);
                    if (img != null) refreshPreview();
                });
        int centerX = width / 2 - 150;
        progressBar = cp5.addSlider("progressBar")
                .setPosition(centerX, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel(Translator.getInstance().translate("progess"))
                .setVisible(false);
    }

    private void updateFillMode()
    {
        FillB = !FillB;
        if (img != null) refreshPreview();
    }

    private void refreshPreview(){
        processImageWithProgress();
    }

    public void loadImage() {
        if (!isDialogOpen) {
            isDialogOpen = true;
            selectInput(Translator.getInstance().translate("select_an_image"), "imageSelected");
        }
    }

    public void saveOnDropboxOrLocally()
    {
        if (dropboxClient != null) {
            showSavingDialog();
        }else {
            saveFile();
        }
    }
    public void saveFile() {
        if (!isDialogOpen && embroidery != null) {
            isDialogOpen = true;
            selectOutput(Translator.getInstance().translate("save_as"), "fileSaved");
        }
    }
    public void setComponentsEnabled(boolean enabled) {
        cp5.getAll().forEach(controller -> {
            if (controller instanceof Button) {
                ((Button) controller).setVisible(enabled);
            } else if (controller instanceof DropdownList) {
                ((DropdownList) controller).setVisible(enabled);
            } else if (controller instanceof Textfield) {
                ((Textfield) controller).setVisible(enabled);
            }
        });
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
                if (polylines != null && colors != null) {
                    img = PEmbroiderReader.createImageFromPolylines(polylines, colors, width, height,this);

                    refreshPreview();
                    enableEscapeMenu = true;
                    showPreview = true;
                }
            } else {
                Logger.getInstance().log(Logger.Project.Converter,
                        "Le fichier sélectionné n'est pas un fichier image valide.");
            }
        }
    }

    public void fileSaved(File selection) {
        isDialogOpen = false;
        if (selection != null) {
            String path = selection.getAbsolutePath();
            if (!path.contains(".")) {
                path += ".pes";
            }
            try {
                updateProgress(0, true);
                boolean isSVG = path.contains(".svg");
                updateProgress(50);
                embroidery.optimize();
                PEmbroiderWriter.write(this,path, embroidery.polylines, embroidery.colors, (int) exportWidth, (int) exportHeight, isSVG);
                updateProgress(100,false);
            } catch (Exception e) {
                Logger.getInstance().log(Logger.Project.Converter,"Error during saving file : " + e.getMessage());
            } finally {
                progressBar.setVisible(false);
            }
        }
    }

    private void processImageWithProgress() {
        setComponentsEnabled(false);
        showPreview = false;
        updateProgress(0, true);
        new Thread(() -> {
            try {
                embroidery.popyLineMulticolor = colorType == ColorType.MultiColor;
                embroidery.beginDraw();
                embroidery.clear();
                img.resize(1000, 1000);
                embroidery.colorizeEmbroideryFromImage = colorType == ColorType.Realistic;
                if (embroidery.colorizeEmbroideryFromImage) {
                    embroidery.extractedColors = embroidery.extractColorsFromImage(img);
                }
                updateProgress(10);
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
                embroidery.strokeWeight(currentStrokeWeight);
                embroidery.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
                embroidery.strokeSpacing(currentSpacing);
                embroidery.stroke(0, 0, 0);
                updateProgress(40);
                if (!FillB) {
                    embroidery.noFill();
                } else {
                    embroidery.fill(0, 0, 0);
                }
                embroidery.image(img, 860, 70);
                embroidery.endCull();
                updateProgress(80);
                SwingUtilities.invokeLater(() -> {
                    showPreview = true;
                    setComponentsEnabled(true);
                    updateProgress(100, false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    setComponentsEnabled(true);
                });
            }
        }).start();
    }
    private void updateProgress(int value) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
        });
    }

    // Surcharge pour gérer la visibilité
    private void updateProgress(int value, boolean visible) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            progressBar.setVisible(visible);
        });
    }
    @Override
    public void draw() {
        background(240);
        float maxDisplayWidth = max((float) width / 2 - 40, 10);
        float maxDisplayHeight = max(height - 90, 10);
        float x = 190;
        float y = 70;
        if (img != null && img.width > 0 && img.height > 0) {
            float displayWidth = min(maxDisplayWidth, img.width);
            float displayHeight = min(maxDisplayHeight, img.height);
            x = constrain(x, 0, width - displayWidth);
            y = constrain(y, 0, height - displayHeight);

            try {
                image(img, x, y, displayWidth, displayHeight);
            } catch (Exception e) {
                println("Erreur d'affichage : " + e.getMessage());
            }
        }
        if (showPreview && embroidery != null) {
            if (img != null && embroidery.polylines != null && !embroidery.polylines.isEmpty() && !embroidery.colors.isEmpty()) {
                embroidery.visualize(true, false, false, Integer.MAX_VALUE,
                        visualizationWidth * 2.71430f,
                        visualizationHeight * 2.71430f, +550, 250);
            }
        }

        if (CP5ComponentsUtil.showTooltip) {
            textAlign(CENTER, BOTTOM);
            textFont(tooltipFont);
            fill(0);
            text(CP5ComponentsUtil.hoverText, width / 2f, height - 20);
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if ((event.isControlDown() || event.isMetaDown()) && event.getKeyCode() == java.awt.event.KeyEvent.VK_V) {
            Image awtImage = pasteImageFromClipboard();
            img = new PImage(awtImage);
            refreshPreview();
            enableEscapeMenu = true;
        } else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_P) {
            showPreview = !showPreview;
        }
    }

    private void showSavingDialog() {
        DialogUtil.showSavingDialog(
                (Component) this.getSurface().getNative(), // Composant parent
                (dropboxClient != null), // Vérifier si Dropbox est disponible
                this::saveFile, // Action de sauvegarde locale
                selectedFile -> { // Action de sauvegarde sur Dropbox
                    fileSaved(selectedFile);
                    uploadToDropbox(selectedFile);
                }
        );
    }

    private void showExitDialog() {
        DialogUtil.showExitDialog(
                (Component) this.getSurface().getNative(),
                enableEscapeMenu,
                (dropboxClient != null),
                this::saveFileAndExit,
                selectedFile -> {
                    fileSaved(selectedFile);
                    uploadToDropbox(selectedFile);
                },
                () -> ApplicationUtil.exitApplication(this)
        );
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