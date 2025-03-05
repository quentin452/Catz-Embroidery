package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class BezierUtil {
    public static void fillPolygon(Pixmap pixmap, List<Vec2> points, Color color) {
        if (points.size() < 3) return;

        int[] x = points.stream().mapToInt(p -> (int) Math.round(p.x)).toArray();
        int[] y = points.stream().mapToInt(p -> (int) Math.round(p.y)).toArray();
        int minY = Arrays.stream(y).min().orElse(0);
        int maxY = Arrays.stream(y).max().orElse(0);
        if (minY >= maxY) return;

        List<Edge>[] edgeTable = new ArrayList[maxY - minY + 1];
        Arrays.setAll(edgeTable, i -> new ArrayList<>());

        for (int i = 0; i < x.length; i++) {
            int j = (i + 1) % x.length;
            if (y[i] == y[j]) continue;

            boolean downward = y[j] > y[i];
            int yStart = downward ? y[i] : y[j];
            int yEnd = downward ? y[j] : y[i];
            float slope = (x[j] - x[i]) / (float) (y[j] - y[i]);

            edgeTable[yStart - minY].add(new Edge(yEnd, downward ? x[i] : x[j], slope));
        }

        List<Edge> activeEdges = new ArrayList<>();
        for (int yCurrent = minY; yCurrent <= maxY; yCurrent++) {
            int idx = yCurrent - minY;
            if (idx >= 0 && idx < edgeTable.length) activeEdges.addAll(edgeTable[idx]);

            int finalYCurrent = yCurrent;
            activeEdges.removeIf(e -> e.yMax <= finalYCurrent);
            activeEdges.sort(Comparator.comparing((Edge e) -> e.currentX).thenComparing(e -> e.slope));

            for (int i = 0; i < activeEdges.size(); i += 2) {
                Edge left = activeEdges.get(i);
                Edge right = (i + 1 < activeEdges.size()) ? activeEdges.get(i + 1) : left;

                int startX = (int) Math.ceil(left.currentX);
                int endX = (int) Math.floor(right.currentX);
                if (startX < endX) pixmap.drawLine(startX, yCurrent, endX, yCurrent);

                left.currentX += left.slope;
                if (i + 1 < activeEdges.size()) right.currentX += right.slope;
            }
        }
    }

    private static class Edge {
        final int yMax;
        float currentX;
        final float slope;

        Edge(int yMax, float xStart, float slope) {
            this.yMax = yMax;
            this.currentX = xStart;
            this.slope = slope;
        }
    }

    public static void renderBezierCurveToPixmap(Pixmap pixmap, BezierCurve curve, Color color, float scale) {
        pixmap.setColor(color);
        Vec2 prev = curve.getP1();
        int samples = 50;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Vec2 next = curve.f(t);

            int x1 = Math.round((float) prev.x * scale);
            int y1 = Math.round((float) prev.y * scale);
            int x2 = Math.round((float) next.x * scale);
            int y2 = Math.round((float) next.y * scale);

            pixmap.drawLine(x1, y1, x2, y2);
            prev = next;
        }
    }

    public static void addBezierShape(PEmbroiderGraphicsLibgdx brodery, BezierShape shape) {
        brodery.bezierShapes.add(shape);
        brodery.currentPath.addAll(shape);
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

        // Find bounding box
        for (BezierShape shape : shapes) {
            for (BezierCurve curve : shape) {
                for (Vec2 point : new Vec2[]{curve.getP1(), curve.getP2(), curve.getP3(), curve.getP4()}) {
                    minX = Math.min(minX, (float) point.x);
                    minY = Math.min(minY, (float) point.y);
                    maxX = Math.max(maxX, (float) point.x);
                    maxY = Math.max(maxY, (float) point.y);
                }
            }
        }

        float originalWidth = maxX - minX;
        float originalHeight = maxY - minY;
        if (originalWidth <= 0 || originalHeight <= 0) return;

        // Compute uniform scale factor
        float scaleFactor = Math.min(scaleX / originalWidth, scaleY / originalHeight);

        // Translate to origin & scale
        for (BezierShape shape : shapes) {
            List<BezierCurve> newCurves = new ArrayList<>();
            for (BezierCurve curve : shape) {
                BezierCurve translated = curve.translate(-minX, -minY);
                BezierCurve scaled = translated.scale(scaleFactor, new Vec2(0, 0));
                newCurves.add(scaled);
            }
            shape.clear();
            shape.addAll(newCurves);
        }
    }

    public static Texture generateScaledTextureFromBezierShapes(List<BezierShape> shapes, float targetWidth, float targetHeight) {
        // Scale the original shapes directly
        scaleShapes(shapes, targetWidth, targetHeight);

        // Create Pixmap
        Pixmap renderPixmap = new Pixmap((int) targetWidth, (int) targetHeight, Pixmap.Format.RGBA8888);
        renderPixmap.setColor(Color.CLEAR);
        renderPixmap.fill();

        // Render shapes
        for (BezierShape shape : shapes) {
            int color = shape.getColor();
            Color gdxColor = new Color(
                    ((color >> 16) & 0xFF) / 255f,
                    ((color >> 8) & 0xFF) / 255f,
                    (color & 0xFF) / 255f,
                    1f
            );
            renderPixmap.setColor(gdxColor);
            for (BezierCurve curve : shape) {
                renderBezierCurveToPixmap(renderPixmap, curve, gdxColor, 1);
            }
        }

        // Create Texture
        Texture texture = new Texture(renderPixmap);
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