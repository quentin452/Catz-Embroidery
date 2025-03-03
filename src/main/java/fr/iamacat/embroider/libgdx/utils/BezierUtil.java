package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;

public class BezierUtil {
    public static void renderBezierCurveToShapeRenderer(ShapeRenderer renderer, BezierCurve curve, BezierShape shape, float offsetX, float offsetY, int visualizeWidth, int visualizeHeight) {
        int color = shape.getColor();

        // Extraction des composantes RGB (sans alpha)
        int r = (color >> 16) & 0xFF; // Bits 16-23
        int g = (color >> 8) & 0xFF;  // Bits 8-15
        int b = color & 0xFF;         // Bits 0-7

        // Forçage de l'alpha à 1.0 (opaque)
        renderer.setColor(r / 255f, g / 255f, b / 255f, 1.0f);

        Vec2 prev = curve.f(0);
        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0;
            Vec2 next = curve.f(t);
            float aspectRatio = (float) visualizeWidth / visualizeHeight;
            float x1 = ((float) prev.x + offsetX) * aspectRatio;
            float y1 = (float) prev.y + offsetY;
            float x2 = ((float) next.x + offsetX) * aspectRatio;
            float y2 = (float) next.y + offsetY;
            renderer.line(x1, y1, x2, y2);
            prev = next;
        }
    }

    public static void renderBezierCurveToPixmap(Pixmap pixmap, BezierCurve curve, Color color) {
        pixmap.setColor(color);
        Vec2 prev = curve.getP1();
        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0;
            Vec2 next = curve.f(t);
            float x1 = (float) prev.x;
            float y1 = (float) prev.y;
            float x2 = (float) next.x;
            float y2 = (float) next.y;
            pixmap.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            prev = next;
        }
    }
/*
    public static void renderBezierCurveToPixmap(Pixmap pixmap, BezierCurve curve, Color color, float scale) {
        pixmap.setColor(color);
        Vec2 prev = curve.getP1().mul(scale);
        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0;
            Vec2 next = curve.f(t).mul(scale);
            float x1 = (float) prev.x;
            float y1 = (float) prev.y;
            float x2 = (float) next.x;
            float y2 = (float) next.y;
            pixmap.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            prev = next;
        }
    }
 */
    public static void addBezierShape(PEmbroiderGraphicsLibgdx brodery, BezierShape shape) {
        brodery.bezierShapes.add(shape);
        for(BezierCurve curve : shape) {
            brodery.currentPath.add(curve);
        }
    }
    public static void addBezierStitches(PEmbroiderGraphicsLibgdx brodery, BezierCurve curve) {
        Vec2 prev = curve.f(0);
        for (int i = 1; i <= brodery.hatchSpacing; i++) {
            double t = i / (double)brodery.hatchSpacing;
            Vec2 next = curve.f(t);
            brodery.addCurve(new BezierCurve(
                    prev,
                    prev, // Linear stitch points
                    next,
                    next));
            prev = next;
        }
    }
}