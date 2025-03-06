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

import java.util.List;
// TODO OPTIMIZE THIS
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
            return quantizeToBlackAndWhite(input);
        } else {
            return quantizeToMultiColor(input, maxColors);
        }
    }

    private Pixmap quantizeToMultiColor(Pixmap input, int maxColors) {
        int width = input.getWidth();
        int height = input.getHeight();
        int[][] pixels = new int[width][height];

        // 1. Extract RGB components (without alpha)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgba = input.getPixel(x, y);
                // Convert RGBA8888 to RGB888 (mask alpha)
                int rgb = (rgba >> 8) & 0x00FFFFFF;
                pixels[x][y] = rgb;
            }
        }

        // 2. Perform color quantization to reduce the number of colors
        int[] colormap = Quantize.quantizeImage(pixels, maxColors);

        // 3. Rebuild the output Pixmap
        Pixmap output = new Pixmap(width, height, Pixmap.Format.RGBA8888);

        // 4. Re-inject the alpha while writing the quantized colors
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int quantizedRGB = colormap[pixels[x][y]];
                int alpha = 0xFF; // Hardcoded alpha as full opacity
                int quantizedRGBA = (quantizedRGB << 8) | alpha; // Combine RGB and alpha
                output.drawPixel(x, y, quantizedRGBA);
            }
        }

        return output;
    }

    private Pixmap quantizeToBlackAndWhite(Pixmap input) {
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