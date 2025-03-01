package fr.iamacat.embroider.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.hatchmode.*;
import fr.iamacat.embroider.libgdx.utils.StitchUtil;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fr.iamacat.embroider.libgdx.utils.ColorUtil.precalculateColors;
import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitch;
import static fr.iamacat.utils.enums.ColorType.MultiColor;
import static fr.iamacat.utils.enums.ColorType.Realistic;

// TODO FIX SAVING CAUSING BUGS
// TODO FIX WHEN I EXTRACT BRODERY TO PNG , IT DONT SAVE SAME COLORS
// TODO FIX BLACK AND WHITE MOD AND THE MULTICOLOR MOD CAN SAVE NOTHING (PROBABLY A COLOR PROBLEM)
// TODO FIX visualize don't draw the good colors
// TODO SUPPORT MULTPLE COLORS FOR REALISTIC MODE(collect dominant colors using maxColors field) AND MULTI COLOR MODE (use random color using maxColors field)
public class PEmbroiderGraphicsLibgdx {
    private final CrossHatch crossHatch;
    private final ParallelHatch parallelHatch;
    private final ConcentricHatch concentricHatch;
    private final SpiralHatch spiralHatch;
    private final PerlinHatch perlinHatch;
    public Color[] colorsCache;

    public ColorType colorMode = MultiColor;
    public HatchModeType hatchMode = HatchModeType.Cross;
    public int width = 200;
    public int height = 200;
    public int hatchSpacing = 50;
    public int strokeWeight = 20;
    public int maxColors = 10;
    public int strokeSpacing = 5;
    public boolean fillEnabled = true;
    public Array<Array<StitchUtil.StitchPoint>> stitchPaths = new Array<>();
    private Array<StitchUtil.StitchPoint> currentPath;

    public PEmbroiderGraphicsLibgdx() {
        this.crossHatch = new CrossHatch(this);
        this.parallelHatch = new ParallelHatch(this);
        this.concentricHatch = new ConcentricHatch(this);
        this.spiralHatch = new SpiralHatch(this);
        this.perlinHatch = new PerlinHatch(this);
    }

    public void beginDraw() {
        stitchPaths.clear();
        colorsCache = new Color[0];
    }

    public void endDraw() {
        optimizeStitchPaths();
    }

    public void beginShape() {
        currentPath = new Array<>();
    }

    public void endShape() {
        if (currentPath != null && currentPath.size > 0) {
            stitchPaths.add(currentPath);
        }
        currentPath = null;
    }

    public void vertex(Vector2 pos, Color col) {
        if (currentPath != null) {
            currentPath.add(new StitchUtil.StitchPoint(pos, col));
        }
    }

    public void image(Pixmap pixmap, float x, float y, int width, int height) {
        this.width = width;
        this.height = height;
        beginShape();
        colorsCache = precalculateColors(pixmap, maxColors,colorMode);
        applyHatchMode(pixmap, x, y);
        endShape();
    }

    /**
     * Applique le mode de hachure sélectionné.
     */
    private void applyHatchMode(Pixmap pixmap, float x, float y) {
        ArrayList<ArrayList<Vector2>> contours = generateContours(pixmap);
        switch (hatchMode) {
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
        }
    }

    private ArrayList<ArrayList<Vector2>> generateContours(Pixmap pixmap) {
        ArrayList<ArrayList<Vector2>> contours = PEmbroiderTrace.findContours(pixmap);
        float epsilon = Math.min(1.0f, pixmap.getWidth() / 100f);
        contours.replaceAll(polyline -> PEmbroiderTrace.approxPolyDP(polyline, epsilon));

        for (ArrayList<Vector2> contour : contours) {
            for (int j = 0; j < contour.size(); j++) {
                Vector2 p1 = contour.get(j);
                Vector2 p2 = contour.get((j + 1) % contour.size());
                addStitch(pixmap, p1.x, p1.y, this);

                float steps = p1.dst(p2) / hatchSpacing;
                for (int k = 0; k <= steps; k++) {
                    Vector2 interp = p1.cpy().lerp(p2, k / steps);
                    addStitch(pixmap, interp.x, interp.y, this);
                }
            }
        }
        return contours;
    }



    public void visualize(ShapeRenderer renderer, float offsetX, float offsetY) {
        renderer.begin(ShapeRenderer.ShapeType.Line);

        for (Array<StitchUtil.StitchPoint> path : stitchPaths) {
            for (int i = 1; i < path.size; i++) {
                StitchUtil.StitchPoint p1 = path.get(i-1);
                StitchUtil.StitchPoint p2 = path.get(i);

                renderer.setColor(p1.color);
                renderer.line(
                        p1.position.x+ offsetX, p1.position.y+ offsetY,
                        p2.position.x+ offsetX, p2.position.y+ offsetY
                );
            }
        }

        renderer.end();
    }
    private void optimizeStitchPaths() {
        // Optimisation des chemins pour minimiser les sauts
        // (Implémentation simplifiée)
        Array<Array<StitchUtil.StitchPoint>> optimized = new Array<>();
        for (Array<StitchUtil.StitchPoint> path : stitchPaths) {
            if (path.size > 1) {
                optimized.add(path);
            }
        }
        stitchPaths = optimized;
    }
}