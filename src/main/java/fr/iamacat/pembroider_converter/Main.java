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

import static fr.iamacat.utils.DropboxUtil.dropboxClient;
import static fr.iamacat.utils.DropboxUtil.uploadToDropbox;

public class Main extends PApplet implements Translatable {

    private PImage img;
    private PEmbroiderGraphics embroidery;
    private ControlP5 cp5;
    private Slider progressBar;
    private boolean showPreview = false;
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
        CP5ComponentsUtil.createNumericTextField(cp5, "exportWidth", 20, 160, 100, 30, color(255), color(0),
                str(exportWidth), "width_in_mm",
                value -> {
                    exportWidth = value;
                    Logger.getInstance().log(Logger.Project.Converter, "Largeur mise à jour : " + exportWidth);
                    if (img != null) refreshPreview();
                });
        CP5ComponentsUtil.createNumericTextField(cp5, "exportHeight", 20, 200, 100, 30, color(255), color(0),
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

    private void resetProgressBar() {
        progressBar.setValue(0);
        progressBar.setVisible(true);
    }

    private void  refreshPreview(){
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
        if (dropboxClient == null && !isDialogOpen && embroidery != null) {
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
                    if (Objects.equals(selectedHatchMode, "CROSS") && embroidery.colorizeEmbroideryFromImage) {
                        selectedHatchMode = "PARALLEL";
                    }
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
                resetProgressBar();
                boolean isSVG = path.contains(".svg");
                progressBar.setValue(50);
                embroidery.optimize();
                PEmbroiderWriter.write(this,path, embroidery.polylines, embroidery.colors, (int) exportWidth, (int) exportHeight, isSVG);
                progressBar.setValue(100);
                progressBar.setVisible(false);
            } catch (Exception e) {
                Logger.getInstance().log(Logger.Project.Converter,"Error during saving file : " + e.getMessage());
            } finally {
                progressBar.setValue(0);
                progressBar.setVisible(false);
            }
        }
    }

    private void processImageWithProgress() {
        setComponentsEnabled(false);
        showPreview = false;
        progressBar.setValue(0);
        progressBar.setVisible(true);
        new Thread(() -> {
            try {
                if (embroidery == null) {
                    embroidery = new PEmbroiderGraphics(this, img.width, img.height);
                }
                embroidery.beginDraw();
                embroidery.clear();
                img.resize(1000, 1000);
                embroidery.extractedColors = embroidery.extractColorsFromImage(img);
                progressBar.setValue(10);
                embroidery.beginCull();
                embroidery.colorizeEmbroideryFromImage = colorType == ColorType.Realistic;
                switch (selectedHatchMode) { // TODO FIX "CROSS" hatchMode Destroy Realistic colors with fill
                    case "CROSS":
                        embroidery.hatchMode(embroidery.colorizeEmbroideryFromImage ? PEmbroiderGraphics.PARALLEL : PEmbroiderGraphics.CROSS);
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
                        embroidery.hatchMode(Objects.equals(selectedHatchMode, "CROSS") && embroidery.colorizeEmbroideryFromImage ? PEmbroiderGraphics.PARALLEL : PEmbroiderGraphics.CROSS);
                }
                embroidery.hatchSpacing(currentSpacing);
                embroidery.strokeWeight(currentStrokeWeight);
                embroidery.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
                embroidery.strokeSpacing(currentSpacing);
                embroidery.stroke(0, 0, 0);
                progressBar.setValue(40);
                if (!FillB) {
                    embroidery.noFill();
                } else {
                    embroidery.fill(0, 0, 0);
                }
                embroidery.popyLineMulticolor = colorType == ColorType.MultiColor;
                embroidery.image(img, 860, 70);
                embroidery.endCull();
                progressBar.setValue(80);
                showPreview = true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                setComponentsEnabled(true);
                progressBar.setValue(100);
                progressBar.setVisible(false);
            }
        }).start();
    }

    @Override
    public void draw() {
        background(240);
        if (img != null && img.width > 0 && img.height > 0) {
            float maxDisplayWidth = max((float) width / 2 - 40, 10);
            float maxDisplayHeight = max(height - 90, 10);
            float displayWidth = min(maxDisplayWidth, img.width);
            float displayHeight = min(maxDisplayHeight, img.height);
            float x = constrain(190, 0, width - displayWidth);
            float y = constrain(70, 0, height - displayHeight);
            try {
                image(img, x, y, displayWidth, displayHeight);
            } catch (Exception e) {
                println("Erreur d'affichage : " + e.getMessage());
            }
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
            }
        }
        if (CP5ComponentsUtil.showTooltip) {
            textAlign(CENTER, BOTTOM);
            textFont(tooltipFont);
            fill(0);
            text(CP5ComponentsUtil.hoverText, (float) width / 2, height - 20);
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