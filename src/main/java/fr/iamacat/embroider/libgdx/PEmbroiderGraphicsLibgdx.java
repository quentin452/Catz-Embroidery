package fr.iamacat.embroider.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.hatchmode.*;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;

import java.util.ArrayList;
import java.util.List;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;
// TODO FIX SAVING CAUSING BUGS
public class PEmbroiderGraphicsLibgdx {
    private final CrossHatch crossHatch;
    private final ParallelHatch parallelHatch;
    private final ConcentricHatch concentricHatch;
    private final SpiralHatch spiralHatch;
    private final PerlinHatch perlinHatch;

    public ColorType colorMode = ColorType.MultiColor;
    public HatchModeType hatchMode = HatchModeType.Cross;
    public int width = 200;
    public int height = 200;
    public int hatchSpacing = 50;
    public int strokeWeight = 20;
    public int maxColors = 10;
    public int strokeSpacing = 5;
    public boolean fillEnabled = true;
    // Données de broderie
    public Array<Array<Vector2>> polylines = new Array<>();
    public Array<Color> colors = new Array<>();
    public Color currentColor = Color.BLACK;

    // État du dessin
    private boolean drawing = false;
    public Array<Vector2> currentPolyline;

    public PEmbroiderGraphicsLibgdx() {
        this.crossHatch = new CrossHatch(this);
        this.parallelHatch = new ParallelHatch(this);
        this.concentricHatch = new ConcentricHatch(this);
        this.spiralHatch = new SpiralHatch(this);
        this.perlinHatch = new PerlinHatch(this);
    }

    public void beginDraw() {
        polylines.clear();
        colors.clear();
        drawing = true;
    }

    public void endDraw() {
        drawing = false;
        optimizeStitchPaths();
    }

    public void beginShape() {
        currentPolyline = new Array<>();
    }

    public void endShape() {
        if (currentPolyline != null && currentPolyline.size > 0) {
            polylines.add(currentPolyline);
            colors.add(currentColor.cpy());
        }
        currentPolyline = null;
    }

    public void image(Pixmap pixmap, float x, float y, int width, int height) {
        this.width = width;
        this.height = height;
        beginShape();
        applyHatchMode(pixmap, x, y);
        endShape();
    }

    /**
     * Applique le mode de hachure sélectionné.
     */
    private void applyHatchMode(Pixmap pixmap, float x, float y) {
        ArrayList<ArrayList<Vector2>> contours = generateContours(pixmap);
        // TODO
        /*switch (hatchMode) {
            case Cross:
                crossHatch.apply(this, pixmap, x, y,contours);
                break;
            case Parallel:
                parallelHatch.apply(this, pixmap, x, y,contours);
                break;
            case Concentric:
                concentricHatch.apply(this, pixmap, x, y,contours);
                break;
            case Spiral:
                spiralHatch.apply(this, pixmap, x, y,contours);
                break;
            case PerlinNoise:
                perlinHatch.apply(this, pixmap, x, y,contours);
                break;
        }*/
    }

    private ArrayList<ArrayList<Vector2>> generateContours(Pixmap pixmap) {
        ArrayList<ArrayList<Vector2>> contours = PEmbroiderTrace.findContours(pixmap);
        float epsilon = Math.min(1.0f, pixmap.getWidth() / 100f);
        contours.replaceAll(polyline -> PEmbroiderTrace.approxPolyDP(polyline, epsilon));
        for (ArrayList<Vector2> contour : contours) {
            for (int j = 0; j < contour.size(); j++) {
                Vector2 p1 = contour.get(j);
                Vector2 p2 = contour.get((j + 1) % contour.size());
                float adjustedX1 = (p1.x * width / pixmap.getWidth());
                float adjustedY1 = height - (p1.y * height / pixmap.getHeight());
                addStitchIfVisible(pixmap, adjustedX1, adjustedY1, this);
                float steps = p1.dst(p2) / hatchSpacing;
                for (int k = 0; k <= steps; k++) {
                    Vector2 interp = p1.cpy().lerp(p2, k / steps);
                    float interpX = (interp.x * width / pixmap.getWidth());
                    float interpY = height - (interp.y * height / pixmap.getHeight());
                    addStitchIfVisible(pixmap, interpX, interpY, this);
                }
            }
        }
        return contours;
    }

    public void visualize(boolean color, boolean stitches, boolean route, int nStitches, float targetWidth, float targetHeight, float offsetX, float offsetY) {
        float scaleX = targetWidth / width;
        float scaleY = targetHeight / height;
        float scale = Math.max(scaleX, scaleY);
        int n = 0;

        ShapeRenderer shapeRenderer = new ShapeRenderer();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Use Array<Vector2> instead of List<Vector2>
        for (int i = 0; i < polylines.size; i++) {
            Array<Vector2> polyline = polylines.get(i);
            if (polyline != null && polyline.size > 1) {
                if (color) {
                    int colorInt = colors.get(i).toIntBits();
                    float r = ((colorInt >> 16) & 0xFF) / 255f;
                    float g = ((colorInt >> 8) & 0xFF) / 255f;
                    float b = (colorInt & 0xFF) / 255f;
                    shapeRenderer.setColor(r, g, b, 1);
                } else if (stitches) {
                    shapeRenderer.setColor(0, 0, 0, 1);
                } else {
                    shapeRenderer.setColor(MathUtils.random(200) / 255f, MathUtils.random(200) / 255f, MathUtils.random(200) / 255f, 1);
                }

                for (int j = 0; j < polyline.size - 1; j++) {
                    Vector2 p0 = polyline.get(j);
                    Vector2 p1 = polyline.get(j + 1);
                    float scaledP0X = p0.x * scale + offsetX;
                    float scaledP0Y = p0.y * scale + offsetY;
                    float scaledP1X = p1.x * scale + offsetX;
                    float scaledP1Y = p1.y * scale + offsetY;

                    shapeRenderer.line(scaledP0X, scaledP0Y, scaledP1X, scaledP1Y);

                    n++;
                    if (n >= nStitches) {
                        break;
                    }
                }
            }
            if (n >= nStitches) {
                break;
            }
        }

        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        n = 0;
        for (int i = 0; i < polylines.size; i++) {
            if (route) {
                if (i != 0 && !polylines.get(i - 1).isEmpty() && !polylines.get(i).isEmpty()) {
                    shapeRenderer.setColor(1, 0, 0, 1); // Red
                    Vector2 p0 = polylines.get(i - 1).get(polylines.get(i - 1).size - 1);
                    Vector2 p1 = polylines.get(i).get(0);

                    float scaledP0X = p0.x * scale + offsetX;
                    float scaledP0Y = p0.y * scale + offsetY;
                    float scaledP1X = p1.x * scale + offsetX;
                    float scaledP1Y = p1.y * scale + offsetY;

                    shapeRenderer.line(scaledP0X, scaledP0Y, scaledP1X, scaledP1Y);
                }
            }
            if (stitches) {
                if (polylines.get(i) != null && polylines.get(i).size > 1) {
                    for (int j = 0; j < polylines.get(i).size - 1; j++) {
                        Vector2 p0 = polylines.get(i).get(j);
                        Vector2 p1 = polylines.get(i).get(j + 1);

                        float scaledP0X = p0.x * scale + offsetX;
                        float scaledP0Y = p0.y * scale + offsetY;
                        float scaledP1X = p1.x * scale + offsetX;
                        float scaledP1Y = p1.y * scale + offsetY;

                        if (j == 0) {
                            shapeRenderer.setColor(0, 1, 0, 1); // Green for start
                            shapeRenderer.rect(scaledP0X - 1, scaledP0Y - 1, 2, 2);
                        }

                        shapeRenderer.setColor(1, 0, 1, 1); // Magenta for stitches
                        shapeRenderer.rect(scaledP1X - 1, scaledP1Y - 1, 2, 2);

                        n++;
                        if (n >= nStitches) {
                            break;
                        }
                    }
                }
                if (n >= nStitches) {
                    break;
                }
            }
        }

        shapeRenderer.end();
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
}