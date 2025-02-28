package fr.iamacat.pembroider_converter;

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

    Textfield maxMultiColorTextField;

    @Override
    public void setup() {
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
    }

    private void setupGUI() {
        int centerX = width / 2 - 150;
        progressBar = cp5.addSlider("progressBar")
                .setPosition(centerX, 240)
                .setSize(300, 20)
                .setRange(0, 100)
                .setValue(0)
                .setLabel(Translator.getInstance().translate("progress"))
                .setVisible(false);
    }


    private void refreshPreview(){
        processImageWithProgress();
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
}