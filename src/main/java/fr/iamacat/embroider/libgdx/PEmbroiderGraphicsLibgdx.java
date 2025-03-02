package fr.iamacat.embroider.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import fr.iamacat.embroider.libgdx.hatchmode.*;
import fr.iamacat.embroider.libgdx.utils.StitchUtil;
import fr.iamacat.utils.enums.ColorType;
import fr.iamacat.utils.enums.HatchModeType;

import java.util.Objects;

// TODO ADD BRODERING TIME ESTIMATION
// TODO FIX SAVING CAUSING BUGS
// TODO FIX WHEN I EXTRACT BRODERY TO PNG , IT DONT SAVE SAME COLORS
// TODO FIX SAVING SGV/PES OR ANOTHER BRODERY FILES CAUSING LARGE FILES
// TODO ADD A WORKING VISUALIZE CACHING
public class PEmbroiderGraphicsLibgdx {
    private final TraceBitmapHatch traceBitmapHach;
    public Color[] colorsCache;

    public ColorType colorMode = ColorType.Bitmap;
    public HatchModeType hatchMode = HatchModeType.TraceBitmap;
    public int width = 200;
    public int height = 200;
    public int hatchSpacing = 50;
    public int maxColors = 10;
    public boolean fillEnabled = true;
    public Array<Array<StitchUtil.StitchPoint>> stitchPaths = new Array<>();
    private Array<StitchUtil.StitchPoint> currentPath;


    public PEmbroiderGraphicsLibgdx() {
        this.traceBitmapHach = new TraceBitmapHatch(this);
    }

    public void beginDraw() {
        stitchPaths.clear();
        colorsCache = new Color[0];
    }

    public void endDraw() { }

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
        applyHatchMode(pixmap, x, y);
        endShape();
    }

    /**
     * Applique le mode de hachure sélectionné.
     */
    private void applyHatchMode(Pixmap pixmap, float x, float y) {
        if (!fillEnabled) {
            return;
        }
        if (Objects.requireNonNull(hatchMode) == HatchModeType.TraceBitmap) {
            traceBitmapHach.apply(this, pixmap, x, y);
        }
    }

    public void visualizeNoCaching(ShapeRenderer renderer, float offsetX, float offsetY) {
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
}