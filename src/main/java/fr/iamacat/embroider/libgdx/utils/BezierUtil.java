package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.Vec2;

public class BezierUtil {
    public static void renderBezierCurve(ShapeRenderer renderer, BezierCurve curve, Color color,
                                         float offsetX, float offsetY) {
        renderer.setColor(color);
        Vec2 prev = curve.f(0);
        for (int i = 1; i <= 20; i++) { // Sample 20 points per curve
            double t = i / 20.0;
            Vec2 next = curve.f(t);
            renderer.line(
                    (float)prev.x + offsetX, (float)prev.y + offsetY,
                    (float)next.x + offsetX, (float)next.y + offsetY
            );
            prev = next;
        }
    }

    public static void addBezierStitches(PEmbroiderGraphicsLibgdx brodery, BezierCurve curve,
                                         Color color, float offsetX, float offsetY) {
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