package fr.iamacat.embroider.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.hatchmode.*;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;

import java.util.ArrayList;

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
    public int yPosOffset = 100;
    public int zPosOffset = 340;
    public int hatchSpacing = 50;
    public int strokeWeight = 20;
    public int maxColors = 10;
    public int strokeSpacing = 5;
    public boolean fillEnabled = true;
    // Données de broderie
    private Array<Array<Vector2>> polylines = new Array<>();
    private Array<Color> colors = new Array<>();
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
                float adjustedX1 = (p1.x * width / pixmap.getWidth()) + zPosOffset;
                float adjustedY1 = height - (p1.y * height / pixmap.getHeight() + yPosOffset);
                addStitchIfVisible(pixmap, adjustedX1, adjustedY1, this);
                float steps = p1.dst(p2) / hatchSpacing;
                for (int k = 0; k <= steps; k++) {
                    Vector2 interp = p1.cpy().lerp(p2, k / steps);
                    float interpX = (interp.x * width / pixmap.getWidth()) + zPosOffset;
                    float interpY = height - (interp.y * height / pixmap.getHeight() + yPosOffset);
                    addStitchIfVisible(pixmap, interpX, interpY, this);
                }
            }
        }
        return contours;
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