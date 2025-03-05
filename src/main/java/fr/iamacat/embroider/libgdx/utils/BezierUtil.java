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
    public static void fillPolygon(Pixmap pixmap, List<Vec2> points, Color color) {
        if (points.size() < 3) return; // Not a polygon

        int[] xPoints = new int[points.size()];
        int[] yPoints = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xPoints[i] = (int) Math.round(points.get(i).x);
            yPoints[i] = (int) Math.round(points.get(i).y);
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y : yPoints) {
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        if (minY >= maxY) return;

        List<Edge>[] edgeTable = new ArrayList[maxY - minY + 1];
        for (int i = 0; i < edgeTable.length; i++) {
            edgeTable[i] = new ArrayList<>();
        }

        int numVertices = points.size();
        for (int i = 0; i < numVertices; i++) {
            int next = (i + 1) % numVertices;
            int x0 = xPoints[i], y0 = yPoints[i];
            int x1 = xPoints[next], y1 = yPoints[next];
            if (y0 == y1) continue; // Skip horizontal edges

            boolean downwards = y1 > y0;
            int yStart = downwards ? y0 : y1;
            int yEnd = downwards ? y1 : y0;
            int xStart = downwards ? x0 : x1;
            float slope = (y1 != y0) ? (float) (x1 - x0) / (y1 - y0) : 0;

            Edge edge = new Edge(yStart, yEnd, xStart, slope);
            edgeTable[yStart - minY].add(edge);
        }

        List<Edge> activeEdges = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            int index = y - minY;
            if (index >= 0 && index < edgeTable.length) {
                activeEdges.addAll(edgeTable[index]);
            }

            int finalY = y;
            activeEdges.removeIf(edge -> edge.yMax <= finalY);

            activeEdges.sort((a, b) -> {
                if (a.currentX != b.currentX) return (int) (a.currentX - b.currentX);
                return Float.compare(a.slope, b.slope);
            });

            for (int i = 0; i < activeEdges.size(); i += 2) {
                Edge left = activeEdges.get(i);
                Edge right = (i + 1 < activeEdges.size()) ? activeEdges.get(i + 1) : left;

                int startX = (int) Math.ceil(left.currentX);
                int endX = (int) Math.floor(right.currentX);

                if (startX < endX) {
                    for (int x = startX; x <= endX; x++) {
                        if (x >= 0 && x < pixmap.getWidth() && y >= 0 && y < pixmap.getHeight()) {
                            pixmap.drawPixel(x, y);
                        }
                    }
                }

                left.currentX += left.slope;
                if (i + 1 < activeEdges.size()) {
                    right.currentX += right.slope;
                }
            }
        }
    }

    private static class Edge {
        int yMax;
        float currentX;
        float slope;

        Edge(int yStart, int yEnd, int xStart, float slope) {
            this.yMax = yEnd;
            this.currentX = xStart;
            this.slope = slope;
        }
    }
    public static void renderBezierCurveToShapeRenderer(ShapeRenderer renderer, BezierCurve curve, BezierShape shape, float offsetX, float offsetY, int visualizeWidth, int visualizeHeight) {
        int color = shape.getColor();

        // Extraction des composantes RGB (sans alpha)
        int r = (color >> 16) & 0xFF; // Bits 16-23
        int g = (color >> 8) & 0xFF;  // Bits 8-15
        int b = color & 0xFF;         // Bits 0-7

        // Forçage de l'alpha à 1.0 (opaque)
        renderer.setColor(r, g, b, 1.0f);

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
        Texture texture;
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
                    (color >> 16 & 0xFF),
                    (color >> 8 & 0xFF),
                    (color & 0xFF),
                    1f
            );
            renderPixmap.setColor(gdxColor);
            for (BezierCurve curve : shape) {
                BezierUtil.renderBezierCurveToPixmap(renderPixmap, curve, gdxColor);
            }
        }

        texture = new Texture(renderPixmap);
        renderPixmap.dispose();
        return texture;
    }


    static List<Vec2> sampleBezierCurve(BezierCurve curve) {
        List<Vec2> points = new ArrayList<>();
        int samples = 50;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            points.add(curve.f(t));
        }
        return points;
    }
}