package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import net.plantabyte.drptrace.*;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;
import net.plantabyte.drptrace.intmaps.ZOrderIntMap;

import java.util.ArrayList;
import java.util.List;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchesWithoutColorChanges;
// TODO FIX COLORS
// TODO FIX BITMAP ISNT POSITIONNED CORRECTLY DURING VISUALIZATION
public class TraceBitmapHach extends BaseHatch {
    private static final int TRACE_PRECISION = 2;
    private static final int STITCH_SPACING = 1;

    public TraceBitmapHach(PEmbroiderGraphicsLibgdx graphicsLibgdx) {
        super(graphicsLibgdx);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y, ArrayList<ArrayList<Vector2>> contours) {
        ZOrderIntMap tracedImage = convertToDrPTraceMap(pixmap);
        Tracer tracer = new IntervalTracer(TRACE_PRECISION);
        List<BezierShape> shapes = tracer.traceAllShapes(tracedImage);

        for(BezierShape shape : shapes) {
            processBezierShape(brodery, shape, x, y, pixmap);
        }
    }

    private void processBezierShape(PEmbroiderGraphicsLibgdx brodery, BezierShape shape, float offsetX, float offsetY, Pixmap source) {
        Color shapeColor = getColorForShape(shape, source);

        for (BezierCurve curve : shape) {
            List<Vector2> stitches = sampleBezierCurve(curve, STITCH_SPACING);
            addStitchesWithoutColorChanges(brodery, stitches, shapeColor, offsetX, offsetY, source);
        }
    }
    private List<Vector2> sampleBezierCurve(BezierCurve curve, float spacing) {
        List<Vector2> points = new ArrayList<>();
        double length = curve.length();
        int steps = (int)(length / spacing) + 1;

        for(int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            Vec2 p = curve.f(t);
            points.add(new Vector2((float)p.x, (float)p.y));
        }

        return points;
    }

    private ZOrderIntMap convertToDrPTraceMap(Pixmap gdxPixmap) {
        ZOrderIntMap map = new ZOrderIntMap(gdxPixmap.getWidth(), gdxPixmap.getHeight());
        for(int y = 0; y < gdxPixmap.getHeight(); y++) {
            for(int x = 0; x < gdxPixmap.getWidth(); x++) {
                int pixel = gdxPixmap.getPixel(x, y);
                map.set(x, y, pixel);
            }
        }
        return map;
    }

    private Color getColorForShape(BezierShape shape, Pixmap source) {
        Vec2 firstPoint = shape.get(0).f(0.0);
        int x = (int)firstPoint.x;
        int y = (int)firstPoint.y;
        return getPixelColorSafe(source, x, y);
    }

    private Color getPixelColorSafe(Pixmap pixmap, int x, int y) {
        x = Math.max(0, Math.min(pixmap.getWidth() - 1, x));
        y = Math.max(0, Math.min(pixmap.getHeight() - 1, y));
        return new Color(pixmap.getPixel(x, y));
    }
}