package fr.iamacat.embroider.libgdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import fr.iamacat.embroider.libgdx.hatchmode.*;
import fr.iamacat.embroider.libgdx.utils.BezierUtil;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO ADD BRODERING TIME ESTIMATION + STITCH STATS
// TODO FIX SAVING SGV/PES OR ANOTHER BRODERY FILES CAUSING LARGE FILES
// TODO ADD A WORKING VISUALIZE CACHING
// TODO ADD DRAW ON SCREEN AVERAGE BRODERYING AND STATS
// TODO FIX WRONG COLORS
// TODO FIX DRAWED STITCH RENDER AT Z INDEX HIGHER THAN DIALOGS UTILS THINGS
public class PEmbroiderGraphicsLibgdx {
    private final TraceBitmapHatch traceBitmapHach;

    public ColorType colorMode = ColorType.Bitmap;
    public HatchModeType hatchMode = HatchModeType.TraceBitmap;
    public int width = 200; // ATTENTION IF YOU USE TOO HIGH WIDTH AND HEIGHT IT CAN MOVE STITCH POSITIONS OUTSIDE OF WINDOW , so visualize not render correctly
    public int height = 200; // ATTENTION IF YOU USE TOO HIGH WIDTH AND HEIGHT IT CAN MOVE STITCH POSITIONS OUTSIDE OF WINDOW , so visualize not render correctly
    public int hatchSpacing = 50;
    public int maxColors = 10;
    public boolean fillEnabled = true;
    public List<List<BezierCurve>> stitchPaths = new ArrayList<>();
    public List<BezierShape> bezierShapes = new ArrayList<>();

    public List<BezierCurve> currentPath;

    // Required renderers
    private final ShapeRenderer shapeRenderer;
    public PEmbroiderGraphicsLibgdx(ShapeRenderer shapeRenderer) {
        this.traceBitmapHach = new TraceBitmapHatch(this);
        this.shapeRenderer = shapeRenderer;
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
    public void visualizeNoCaching(float offsetX, float offsetY, int visualizeWidth,int visualizeHeight) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (BezierShape shape : bezierShapes) {
            for (BezierCurve curve : shape) {
                BezierUtil.renderBezierCurveToShapeRenderer(shapeRenderer, curve, shape, offsetX, offsetY,visualizeWidth,visualizeHeight);
            }
        }
        shapeRenderer.end();
    }
}