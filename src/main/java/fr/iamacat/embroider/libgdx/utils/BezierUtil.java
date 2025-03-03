package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;

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


    public static BezierShape scaleShape(BezierShape original, float scaleX, float scaleY) {
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

    public static void scaleShapes(List<BezierShape> shapes, float scaleX, float scaleY) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        // Calculer la boîte englobante originale
        for (BezierShape shape : shapes) {
            for (BezierCurve curve : shape) {
                for (Vec2 point : new Vec2[]{curve.getP1(), curve.getP2(), curve.getP3(), curve.getP4()}) {
                    minX = (float) Math.min(minX, point.x);
                    minY = (float) Math.min(minY, point.y);
                    maxX = (float) Math.max(maxX, point.x);
                    maxY = (float) Math.max(maxY, point.y);
                }
            }
        }

        float originalWidth = maxX - minX;
        float originalHeight = maxY - minY;
        if (originalWidth <= 0 || originalHeight <= 0) return;

        // Calculer le facteur d'échelle
        float scaleFactor = Math.min(scaleX / originalWidth, scaleY / originalHeight);

        // Translater toutes les courbes vers l'origine (0,0)
        for (BezierShape shape : shapes) {
            List<BezierCurve> translatedCurves = new ArrayList<>();
            for (BezierCurve curve : shape) {
                translatedCurves.add(curve.translate(-minX, -minY));
            }
            shape.clear();
            shape.addAll(translatedCurves);
        }

        // Mettre à l'échelle toutes les courbes autour de (0,0)
        for (BezierShape shape : shapes) {
            List<BezierCurve> scaledCurves = new ArrayList<>();
            for (BezierCurve curve : shape) {
                scaledCurves.add(curve.scale(scaleFactor, new Vec2(0, 0)));
            }
            shape.clear();
            shape.addAll(scaledCurves);
        }
    }
    
    public static Texture generateScaledTextureFromBezierShapes(List<BezierShape> shapes, float targetWidth, float targetHeight) {
        Texture texture = null;
        List<BezierShape> scaledShapes = new ArrayList<>();
        for (BezierShape original : shapes) {
            BezierShape copy = new BezierShape();
            for (BezierCurve curve : original) {
                copy.add(new BezierCurve(
                        curve.getP1(),
                        curve.getP2(),
                        curve.getP3(),
                        curve.getP4()
                ));
            }
            copy.setColor(original.getColor());
            scaledShapes.add(copy);
        }

        BezierUtil.scaleShapes(scaledShapes, targetWidth, targetHeight);

        Pixmap renderPixmap = new Pixmap((int) targetWidth, (int) targetHeight, Pixmap.Format.RGBA8888);
        renderPixmap.setColor(Color.CLEAR);
        renderPixmap.fill();

        for (BezierShape shape : scaledShapes) {
            int color = shape.getColor();
            Color gdxColor = new Color(
                    (color >> 16 & 0xFF) / 255f,
                    (color >> 8 & 0xFF) / 255f,
                    (color & 0xFF) / 255f,
                    1f
            );
            renderPixmap.setColor(gdxColor);
            for (BezierCurve curve : shape) {
                BezierUtil.renderBezierCurveToPixmap(renderPixmap, curve, gdxColor);
            }
        }

        if (texture != null) {
            texture.dispose();
        }
        texture = new Texture(renderPixmap);
        renderPixmap.dispose();
        return texture;
    }
}