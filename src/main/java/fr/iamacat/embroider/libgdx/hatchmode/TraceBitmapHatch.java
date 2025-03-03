package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.BezierUtil;
import fr.iamacat.utils.enums.ColorType;
import imagemagick.Quantize;
import net.plantabyte.drptrace.*;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;
import net.plantabyte.drptrace.intmaps.ZOrderIntMap;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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
        Pixmap quantizedPixmap = quantize(pixmap, brodery.colorMode, brodery.maxColors);
        ZOrderIntMap tracedImage = convertToDrPTraceMap(quantizedPixmap);
        Tracer tracer = new IntervalTracer(TRACE_PRECISION);
        List<BezierShape> shapes = tracer.traceAllShapes(tracedImage);

        // Calculate scaling factors
        float scaleX = (brodery.width * 3.67f) / quantizedPixmap.getWidth();
        float scaleY = (brodery.height * 3.67f) / quantizedPixmap.getHeight();

        for (BezierShape shape : shapes) {
            BezierUtil.scaleShape(shape,scaleX,scaleY);
            BezierUtil.addBezierShape(brodery, shape);
            processBezierShape(brodery, shape);
        }
    }

    private Pixmap quantize(Pixmap input, ColorType colorType, int maxColors) {
        if (colorType == ColorType.BlackAndWhite) {
            return quantizeToBlackAndWhite(input);
        } else {
            return quantizeToMultiColor(input, maxColors);
        }
    }

    private Pixmap quantizeToMultiColor(Pixmap input, int maxColors) {
        // Convertir Pixmap en tableau 2D int
        int width = input.getWidth();
        int height = input.getHeight();
        int[][] pixels = new int[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pixels[x][y] = input.getPixel(x, y);
            }
        }

        // Appliquer la quantification
        int[] colormap = Quantize.quantizeImage(pixels, maxColors);

        // Créer le Pixmap de sortie
        Pixmap output = new Pixmap(width, height, Pixmap.Format.RGBA8888);

        // Remplacer les indices par les couleurs de la palette
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int color = colormap[pixels[x][y]];
                output.drawPixel(x, y, color);
            }
        }

        return output;
    }

    private Pixmap quantizeToBlackAndWhite(Pixmap input) {
        // Même implémentation que précédemment
        Pixmap output = new Pixmap(input.getWidth(), input.getHeight(), Pixmap.Format.RGBA8888);
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                Color color = new Color(input.getPixel(x, y));
                float luminance = (color.r + color.g + color.b) / 3.0f;
                int quantizedColor = luminance > 0.5f ?
                        Color.WHITE.toIntBits() :
                        Color.BLACK.toIntBits();
                output.drawPixel(x, y, quantizedColor);
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