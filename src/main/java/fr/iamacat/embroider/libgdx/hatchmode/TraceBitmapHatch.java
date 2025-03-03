package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.BezierUtil;
import net.plantabyte.drptrace.*;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;
import net.plantabyte.drptrace.intmaps.ZOrderIntMap;

import java.util.List;
// TODO OPTIMIZE THIS
// TODO ADD COLOR TRACING
public class TraceBitmapHatch extends BaseHatch {
    private static final int TRACE_PRECISION = 20;

    public TraceBitmapHatch(PEmbroiderGraphicsLibgdx graphicsLibgdx) {
        super(graphicsLibgdx);
    }
    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        Pixmap quantizedPixmap = quantizeToBinary(pixmap);
        ZOrderIntMap tracedImage = convertToDrPTraceMap(quantizedPixmap);
        Tracer tracer = new IntervalTracer(TRACE_PRECISION);
        List<BezierShape> shapes = tracer.traceAllShapes(tracedImage);

        // Calculate scaling factors
        float scaleX = (brodery.width * 3.67f) / quantizedPixmap.getWidth();
        float scaleY = (brodery.height * 3.67f) / quantizedPixmap.getHeight();

        for (BezierShape shape : shapes) {
            // Scale the shape before adding
            BezierShape scaledShape = scaleShape(shape, scaleX, scaleY);
            BezierUtil.addBezierShape(brodery, scaledShape);
            processBezierShape(brodery, scaledShape);
        }
    }

    private BezierShape scaleShape(BezierShape original, float scaleX, float scaleY) {
        BezierShape scaled = new BezierShape();
        for (BezierCurve curve : original) {
            Vec2 p1 = new Vec2(curve.getP1().x * scaleX, curve.getP1().y * scaleY);
            Vec2 p2 = new Vec2(curve.getP2().x * scaleX, curve.getP2().y * scaleY);
            Vec2 p3 = new Vec2(curve.getP3().x * scaleX, curve.getP3().y * scaleY);
            Vec2 p4 = new Vec2(curve.getP4().x * scaleX, curve.getP4().y * scaleY);

            scaled.add(new BezierCurve(p1, p2, p3, p4));
        }
        scaled.setColor(original.getColor());
        return scaled;
    }

    private Pixmap quantizeToBinary(Pixmap input) {
        Pixmap output = new Pixmap(input.getWidth(), input.getHeight(), Pixmap.Format.RGBA8888);
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                Color color = new Color(input.getPixel(x, y));
                float luminance = (color.r + color.g + color.b) / 3.0f;
                int quantizedColor = luminance > 0.5f ? Color.WHITE.toIntBits() : Color.BLACK.toIntBits();
                output.setColor(quantizedColor);
                output.drawPixel(x, y);
            }
        }
        return output;
    }

    private void processBezierShape(PEmbroiderGraphicsLibgdx brodery, BezierShape shape) {
        for (BezierCurve curve : shape) {
            BezierUtil.addBezierStitches(brodery, curve);
        }
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