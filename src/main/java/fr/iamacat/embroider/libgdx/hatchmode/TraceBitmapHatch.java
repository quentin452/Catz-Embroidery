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
import net.plantabyte.drptrace.intmaps.ZOrderIntMap;

import java.util.Arrays;
import java.util.List;
public class TraceBitmapHatch extends BaseHatch {

    public TraceBitmapHatch(PEmbroiderGraphicsLibgdx graphicsLibgdx) {
        super(graphicsLibgdx);
    }
    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        Pixmap quantizedPixmap = quantize(pixmap, brodery.colorMode, brodery.maxColors);
        ZOrderIntMap tracedImage = convertToDrPTraceMap(quantizedPixmap);
        if (brodery.hatchSpacing <= 0) {
            brodery.hatchSpacing = 1;
        }
        Tracer tracer = new PolylineTracer(brodery.hatchSpacing);
        List<BezierShape> shapes = tracer.traceAllShapes(tracedImage);
        for (BezierShape shape : shapes) {
            BezierUtil.addBezierShape(brodery, shape);
            processBezierShape(brodery, shape);
        }
    }

    private Pixmap quantize(Pixmap input, ColorType colorType, int maxColors) {
        if (colorType == ColorType.BlackAndWhite) {
            return quantizeToGrayscale(input, maxColors);
        } else {
            return quantizeToMultiColor(input, maxColors);
        }
    }

    private Pixmap quantizeToGrayscale(Pixmap input, int maxColors) {
        Pixmap output = new Pixmap(input.getWidth(), input.getHeight(), Pixmap.Format.RGBA8888);
        float[] levels = generateGrayLevels(Math.min(4,maxColors));

        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int rgba = input.getPixel(x, y);
                int alpha = rgba & 0xFF;

                if (alpha == 0) {
                    output.drawPixel(x, y, 0);
                    continue;
                }

                Color color = new Color(rgba);
                float luminance = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b;
                float gray = findClosestLevel(luminance, levels);

                int quantized = Color.rgba8888(gray, gray, gray, alpha / 255f);
                output.drawPixel(x, y, quantized);
            }
        }
        return output;
    }

    private float[] generateGrayLevels(int maxColors) {
        float[] levels = new float[maxColors];
        for (int i = 0; i < maxColors; i++) {
            levels[i] = i / (float) (maxColors - 1);
        }
        return levels;
    }

    private float findClosestLevel(float luminance, float[] levels) {
        int index = Arrays.binarySearch(levels, luminance);
        if (index < 0) {
            index = -index - 1;
        }
        return levels[Math.min(index, levels.length - 1)];
    }


    private Pixmap quantizeToMultiColor(Pixmap input, int maxColors) {
        int width = input.getWidth();
        int height = input.getHeight();
        int[][] pixels = new int[width][height];
        int[][] alphas = new int[width][height]; // Store alpha values

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgba = input.getPixel(x, y);
                pixels[x][y] = (rgba >> 8) & 0x00FFFFFF; // RGB components
                alphas[x][y] = rgba & 0xFF; // Alpha component
            }
        }

        int[] colormap = Quantize.quantizeImage(pixels, maxColors);
        Pixmap output = new Pixmap(width, height, Pixmap.Format.RGBA8888);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int quantizedRGB = colormap[pixels[x][y]];
                int alpha = alphas[x][y]; // Preserve original alpha
                output.drawPixel(x, y, (quantizedRGB << 8) | alpha);
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
        for (int y = 0; y < gdxPixmap.getHeight(); y++) {
            for (int x = 0; x < gdxPixmap.getWidth(); x++) {
                int rgba = gdxPixmap.getPixel(x, y);
                int argb = ( (rgba & 0xFF) << 24) | // A
                        ((rgba >> 8) & 0xFF0000) | // R
                        ((rgba >> 8) & 0x00FF00) | // G
                        ((rgba >> 8) & 0x0000FF);  // B
                map.set(x, y, argb);
            }
        }
        return map;
    }
}