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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchesWithoutColorChanges;
// TODO FIX BITMAP ISNT POSITIONNED CORRECTLY DURING VISUALIZATION
public class TraceBitmapHach extends BaseHatch {
    private static final int TRACE_PRECISION = 20;

    public TraceBitmapHach(PEmbroiderGraphicsLibgdx graphicsLibgdx) {
        super(graphicsLibgdx);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y, ArrayList<ArrayList<Vector2>> contours) {
        // Quantize the Pixmap to reduce colors
        Pixmap quantizedPixmap = quantizeToBinary(pixmap);
        ZOrderIntMap tracedImage = convertToDrPTraceMap(quantizedPixmap);
        Tracer tracer = new IntervalTracer(TRACE_PRECISION);
        List<BezierShape> shapes = tracer.traceAllShapes(tracedImage);

        for (BezierShape shape : shapes) {
            processBezierShape(brodery, shape, x, y, quantizedPixmap);
        }

        saveBezierShapesAsSVG(shapes, quantizedPixmap.getWidth(), quantizedPixmap.getHeight());
    }

    // Quantizes the image to black and white based on a luminance threshold
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

    private void processBezierShape(PEmbroiderGraphicsLibgdx brodery, BezierShape shape, float offsetX, float offsetY, Pixmap source) {
        Color shapeColor = getColorForShape(shape, source);

        for (BezierCurve curve : shape) {
            List<Vector2> stitches = sampleBezierCurve(curve, brodery.hatchSpacing);
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
    private void saveBezierShapesAsSVG(List<BezierShape> shapes, int width, int height) {
        try (BufferedWriter out = Files.newBufferedWriter(Paths.get("out.svg"))) {
            // Write the SVG header
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
            out.write(String.format("<svg width=\"%d\" height=\"%d\" id=\"svgroot\" version=\"1.1\" viewBox=\"0 0 %d %d\" xmlns=\"http://www.w3.org/2000/svg\">\n", width, height, width, height));

            // Write each Bezier shape as a path
            for (BezierShape shape : shapes) {
                int color = shape.getColor();  // ARGB format
                int alpha = (color >> 24) & 0xFF;
                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = color & 0xFF;
                String hexColor = String.format("#%02x%02x%02x", red, green, blue);
                out.write(String.format("<path style=\"fill:%s\" d=\"%s\" />\n", hexColor, shape.toSVGPathString()));
            }

            // Close the SVG tag
            out.write("</svg>\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}