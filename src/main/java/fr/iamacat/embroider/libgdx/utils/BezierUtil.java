package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;

public class BezierUtil {
    public static void renderBezierCurve(ShapeRenderer renderer, BezierCurve curve, Color color,
                                         float offsetX, float offsetY, int height) {
        renderer.setColor(color);
        Vec2 prev = curve.f(0);
        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0;
            Vec2 next = curve.f(t);
            float x1 = (float) prev.x + offsetX;
            float y1 = (float) (height - prev.y + offsetY);
            float x2 = (float) next.x + offsetX;
            float y2 = (float) (height - next.y + offsetY);
            renderer.line(x1, y1, x2, y2);
            prev = next;
        }
    }

    public static void addBezierShape(PEmbroiderGraphicsLibgdx brodery, BezierShape shape) {
        brodery.bezierShapes.add(shape);
        for(BezierCurve curve : shape) {
            brodery.currentPath.add(curve);
        }
    }
    public static void addBezierStitches(PEmbroiderGraphicsLibgdx brodery, BezierCurve curve,
                                         Color color) {
        Vec2 prev = curve.f(0);
        for (int i = 1; i <= brodery.hatchSpacing; i++) {
            double t = i / (double)brodery.hatchSpacing;
            Vec2 next = curve.f(t);
            brodery.addCurve(new BezierCurve(
                    prev,
                    prev, // Linear stitch points
                    next,
                    next
            ), color);
            prev = next;
        }
    }
}