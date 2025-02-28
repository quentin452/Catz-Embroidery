package fr.iamacat.embroider;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;

public class PEmbroiderGraphicsLibgdx {

    private ColorType colorMode = ColorType.MultiColor;
    private HatchModeType hatchMode = HatchModeType.Cross;
    private int width = 200;
    private int height = 200;
    private int hatchSpacing = 10;
    private int strokeWeight = 20;
    private int maxColors = 10;
    private int strokeSpacing = 5;
    private boolean fillEnabled = true;
    private float embroideryScale = 1.0f;
    // Données de broderie
    private Array<Array<Vector2>> polylines = new Array<>();
    private Array<Color> colors = new Array<>();
    private Color currentColor = Color.BLACK;

    // État du dessin
    private boolean drawing = false;
    private Array<Vector2> currentPolyline;

    public void beginDraw() {
        polylines.clear();
        colors.clear();
        drawing = true;
    }

    public void endDraw() {
        drawing = false;
        optimizeStitchPaths();
    }
    public void setColorMode(ColorType mode) {
        this.colorMode = mode;
    }

    public void setHatchMode(HatchModeType mode) {
        this.hatchMode = mode;
    }

    public void setHatchSpacing(int spacing) {
        this.hatchSpacing = spacing;
    }

    public void setStrokeWeight(int weight) {
        this.strokeWeight = weight;
    }

    public void setStrokeSpacing(int spacing) {
        this.strokeSpacing = spacing;
    }

    public void setFill(boolean fill) {
        this.fillEnabled = fill;
    }
    public void setEmbroideryScale(float scale) {
        this.embroideryScale = scale;
    }
    public void setMaxColors(Integer value) {
        this.maxColors = value;
    }
    public ColorType getColorMode() {
        return this.colorMode;
    }

    public HatchModeType getHatchMode() {
        return this.hatchMode;
    }

    public int getHatchSpacing() {
        return this.hatchSpacing;
    }

    public int getStrokeWeight() {
        return this.strokeWeight;
    }

    public int getStrokeSpacing() {
       return this.strokeSpacing;
    }

    public boolean getFill() {
      return this.fillEnabled;
    }

    public float getEmbroideryScale() {
        return this.embroideryScale;
    }

    public int getMaxColors() {
        return maxColors;
    }
    public void beginShape() {
        currentPolyline = new Array<>();
    }

    public void vertex(float x, float y) {
        if (currentPolyline != null) {
            currentPolyline.add(new Vector2(x, y));
        }
    }

    public void endShape() {
        if (currentPolyline != null && currentPolyline.size > 0) {
            polylines.add(currentPolyline);
            colors.add(currentColor.cpy());
        }
        currentPolyline = null;
    }

    public void image(Pixmap pixmap, float x, float y, int width, int height) {
        System.out.println("Color mode" + colorMode);
        System.out.println("Hatch mode" + hatchMode);
        this.width = width;
        this.height = height;

        // Compute scaled width and height
        int scaledWidth = (int) (this.width * this.embroideryScale);
        int scaledHeight = (int) (this.height * this.embroideryScale);

        // Check if the scaled image is outside the window
        if (x < 0 || y < 0 || x + scaledWidth > Gdx.graphics.getWidth() || y + scaledHeight > Gdx.graphics.getHeight()) {
            System.out.println("Warning: Image is partially or completely outside the window boundaries!");
        }

        beginShape();

        // Appliquer le mode de hachure
        applyHatchMode(pixmap, x, y, scaledWidth, scaledHeight);

        endShape();
    }

    /**
     * Détermine la couleur du pixel en fonction du mode sélectionné.
     */
    private Color getColorForPixel(int pixel) {
        Color color = new Color();
        Color.rgba8888ToColor(color, pixel);

        switch (this.colorMode) {
            case Realistic:
                return color; // Utilise la couleur exacte de l'image

            case BlackAndWhite:
                return Color.BLACK; // Tout en noir

            case MultiColor:
                return new Color(
                        (float) Math.random(),
                        (float) Math.random(),
                        (float) Math.random(),
                        1
                ); // Couleur aléatoire

            default:
                return Color.BLACK;
        }
    }
    /**
     * Applique le mode de hachure sélectionné.
     */
    private void applyHatchMode(Pixmap pixmap, float x, float y, int scaledWidth, int scaledHeight) {
        switch (hatchMode) {
            case Cross:
                applyCrossHatch(pixmap, x, y, scaledWidth, scaledHeight);
                break;
            case Parallel:
                applyParallelHatch(pixmap, x, y, scaledWidth, scaledHeight);
                break;
            case Concentric:
                applyConcentricHatch(pixmap, x, y, scaledWidth, scaledHeight);
                break;
            case Spiral:
                applySpiralHatch(pixmap, x, y, scaledWidth, scaledHeight);
                break;
            case PerlinNoise:
                applyPerlinHatch(pixmap, x, y, scaledWidth, scaledHeight);
                break;
        }
    }

    /**
     * Mode **Cross Hatch** : lignes diagonales croisées.
     */
    private void applyCrossHatch(Pixmap pixmap, float x, float y, int width, int height) {
        for (int i = 0; i < width; i += hatchSpacing) {
            for (int j = 0; j < height; j += hatchSpacing) {
                addStitchIfVisible(pixmap, x + i, y + j);
                addStitchIfVisible(pixmap, x + i, y + height - j);
            }
        }
    }

    /**
     * Mode **Parallel Hatch** : lignes parallèles horizontales.
     */
    private void applyParallelHatch(Pixmap pixmap, float x, float y, int width, int height) {
        for (int j = 0; j < height; j += hatchSpacing) {
            for (int i = 0; i < width; i += strokeSpacing) {
                addStitchIfVisible(pixmap, x + i, y + j);
            }
        }
    }

    /**
     * Mode **Concentric Hatch** : cercles concentriques.
     */
    private void applyConcentricHatch(Pixmap pixmap, float x, float y, int width, int height) {
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        for (float r = hatchSpacing; r < Math.max(width, height) / 2.0f; r += hatchSpacing) {
            for (float angle = 0; angle < 360; angle += 10) {
                float rad = (float) Math.toRadians(angle);
                float px = centerX + r * (float) Math.cos(rad);
                float py = centerY + r * (float) Math.sin(rad);
                addStitchIfVisible(pixmap, px, py);
            }
        }
    }

    /**
     * Mode **Spiral Hatch** : une spirale partant du centre.
     */
    private void applySpiralHatch(Pixmap pixmap, float x, float y, int width, int height) {
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        float theta = 0;
        float radius = 0;
        float radiusMax = Math.max(width, height) / 2.0f;

        while (radius < radiusMax) {
            float px = centerX + radius * (float) Math.cos(theta);
            float py = centerY + radius * (float) Math.sin(theta);
            addStitchIfVisible(pixmap, px, py);

            theta += 0.1f;
            radius += 0.1f;
        }
    }

    /**
     * Mode **Perlin Noise Hatch** : points positionnés aléatoirement avec Perlin Noise.
     */
    private void applyPerlinHatch(Pixmap pixmap, float x, float y, int width, int height) {
        for (int i = 0; i < width; i += hatchSpacing) {
            for (int j = 0; j < height; j += hatchSpacing) {
                float noiseValue = (float) Math.random(); // À remplacer par un vrai Perlin Noise
                if (noiseValue > 0.5) { // Seulement certains points sont tracés
                    addStitchIfVisible(pixmap, x + i, y + j);
                }
            }
        }
    }

    /**
     * Ajoute un point de broderie si le pixel est visible.
     */
    // TODO FIX THIS METHOD
    private void addStitchIfVisible(Pixmap pixmap, float px, float py) {
        int originalX = (int) ((px - (float) this.width / 2) / this.embroideryScale);
        int originalY = (int) ((py - (float) this.height / 2) / this.embroideryScale);

       // if (originalX >= 0 && originalY >= 0 && originalX < this.width && originalY < this.height) {
            int pixel = pixmap.getPixel(originalX, originalY);
            Color pixelColor = getColorForPixel(pixel);

            //if (pixelColor.a > 0) { // Ensure the pixel is not transparent
                this.currentColor = pixelColor;
                vertex(px, py);
            //}
        //}
    }


    public void visualize(ShapeRenderer renderer, float offsetX, float offsetY) {
        renderer.begin(ShapeRenderer.ShapeType.Line);

        for (int i = 0; i < polylines.size; i++) {
            Array<Vector2> poly = polylines.get(i);
            Color color = colors.get(i);

            renderer.setColor(color);
            for (int j = 1; j < poly.size; j++) {
                Vector2 p1 = poly.get(j-1);
                Vector2 p2 = poly.get(j);
                renderer.line(
                        p1.x + offsetX,
                        p1.y + offsetY,
                        p2.x + offsetX,
                        p2.y + offsetY
                );
            }
        }

        renderer.end();
    }

    private void optimizeStitchPaths() {
        // Optimisation des chemins pour minimiser les sauts
        // (Implémentation simplifiée)
        Array<Array<Vector2>> optimized = new Array<>();
        for (Array<Vector2> path : polylines) {
            if (path.size > 1) {
                optimized.add(path);
            }
        }
        polylines = optimized;
    }

    // Getters pour accéder aux données
    public Array<Array<Vector2>> getPolylines() {
        return polylines;
    }

    public Array<Color> getColors() {
        return colors;
    }
}