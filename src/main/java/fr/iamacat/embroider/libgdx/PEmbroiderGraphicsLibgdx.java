package fr.iamacat.embroider.libgdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import fr.iamacat.embroider.libgdx.hatchmode.*;
import fr.iamacat.embroider.libgdx.utils.BezierUtil;
import fr.iamacat.embroider.libgdx.utils.EmbroideryMachine;
import fr.iamacat.embroider.libgdx.utils.PixelUtil;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static fr.iamacat.embroider.libgdx.utils.PixelUtil.pixelToMm;

// TODO FIX STITCH STATS + BRODERY TIME PROBLEM
// TODO FIX PNG/SVG WRITING AND READING COLORS
// TODO REMOVE STITCH BORDERS
// TODO FIX DRAWED STITCH RENDER AT Z INDEX HIGHER THAN DIALOGS UTILS THINGS
// TODO ADD a variable in TraceBitmapHatch to increase/reduce quality of the shape by reducing triangle
public class PEmbroiderGraphicsLibgdx {
    private final TraceBitmapHatch traceBitmapHach;

    public ColorType colorMode = ColorType.MultiColor;
    public HatchModeType hatchMode = HatchModeType.TraceBitmap;
    public int width = 200; // ATTENTION IF YOU USE TOO HIGH WIDTH AND HEIGHT IT CAN MOVE STITCH POSITIONS OUTSIDE OF WINDOW , so visualize not render correctly
    public int height = 200; // ATTENTION IF YOU USE TOO HIGH WIDTH AND HEIGHT IT CAN MOVE STITCH POSITIONS OUTSIDE OF WINDOW , so visualize not render correctly
    public int hatchSpacing = 50;
    public int maxColors = 10;
    public boolean fillEnabled = true;
    public List<List<BezierCurve>> stitchPaths = new ArrayList<>();
    public List<BezierShape> bezierShapes = new ArrayList<>();

    public EmbroideryMachine selectedMachine = EmbroideryMachine.BROTHER_SKITCH_PP1;
    private String statsText = "";

    public List<BezierCurve> currentPath;
    private Texture cachedTexture;
    private SpriteBatch spriteBatch;

    // Required renderers
    private final ShapeRenderer shapeRenderer;
    public PEmbroiderGraphicsLibgdx(ShapeRenderer shapeRenderer) {
        this.traceBitmapHach = new TraceBitmapHatch(this);
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch = new SpriteBatch();
    }

    public void beginDraw() {
        stitchPaths.clear();
        bezierShapes.clear();
    }

    public void endDraw() { }

    public void beginShape() {
        currentPath = new ArrayList<>();
    }

    public void endShape() {
        if (currentPath != null && !currentPath.isEmpty()) {
            stitchPaths.add(currentPath);
        }
        currentPath = null;
    }

    public void addCurve(BezierCurve curve) {
        if (currentPath != null) {
            currentPath.add(curve);
        }
    }

    public void image(Pixmap pixmap, float x, float y, int width, int height) {
        this.width = width;
        this.height = height;
        beginShape();
        applyHatchMode(pixmap, x, y);
        endShape();
        float scale = PixelUtil.pixelToMm(width,height);
        cachedTexture = BezierUtil.generateScaledTextureFromBezierShapes(bezierShapes,scale,scale);
    }

    /**
     * Applique le mode de hachure sélectionné.
     */
    private void applyHatchMode(Pixmap pixmap, float x, float y) {
        if (!fillEnabled) return;
        if (Objects.requireNonNull(hatchMode) == HatchModeType.TraceBitmap) {
            traceBitmapHach.apply(this, pixmap, x, y);
        }
    }
    public void visualizeCacheOrNo(float offsetX, float offsetY, int visualizeWidth, int visualizeHeight) {
        if (cachedTexture != null) {
            spriteBatch.begin();
            spriteBatch.draw(cachedTexture, offsetX, offsetY, visualizeWidth, visualizeHeight);
            spriteBatch.end();
        } else {
            // Fallback to original rendering if no cached texture
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            for (BezierShape shape : bezierShapes) {
                for (BezierCurve curve : shape) {
                    BezierUtil.renderBezierCurveToShapeRenderer(shapeRenderer, curve, shape, offsetX, offsetY, visualizeWidth, visualizeHeight);
                }
            }
            shapeRenderer.end();
        }
    }
    public void updateStats() {
        int totalStitches = calculateTotalStitches();

        float minTime = totalStitches / (float)selectedMachine.maxStitchesPerMinute;
        float maxTime = totalStitches / (float)selectedMachine.minStitchesPerMinute;

        int minTimeMinutes = (int) minTime;
        int minTimeSeconds = (int) ((minTime - minTimeMinutes) * 60);
        int maxTimeMinutes = (int) maxTime;
        int maxTimeSeconds = (int) ((maxTime - maxTimeMinutes) * 60);

        statsText = String.format(
                "Points: %d\nTemps estimé: %d min %d sec - %d min %d sec\nMachine: %s\n\n*Fonctionnalité en Bêta*",
                totalStitches, minTimeMinutes, minTimeSeconds, maxTimeMinutes, maxTimeSeconds, selectedMachine.displayName
        );
    }

    private int calculateTotalStitches() {
        int count = 0;
        for (BezierShape shape : bezierShapes) {
            for (BezierCurve curve : shape) {
                count += hatchSpacing; // Chaque courbe génère 'hatchSpacing' points
            }
        }
        return count;
    }

    public String getStatsText() {
        return statsText;
    }
}