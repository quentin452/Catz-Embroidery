package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.utils.enums.ColorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static fr.iamacat.utils.enums.ColorType.MultiColor;
import static fr.iamacat.utils.enums.ColorType.Realistic;

public class ColorUtil {
    public static Color[] precalculateColors(Pixmap pixmap, int maxColors, ColorType colorType) {
        if (maxColors < 1) {
            maxColors = 1;
        }
        int pixWidth = pixmap.getWidth();
        int pixHeight = pixmap.getHeight();
        Color[] colorsCache = new Color[pixWidth * pixHeight];

        if (colorType == ColorType.Bitmap) {
            // In TraceBitmap mode, just use the exact pixel colors
            for (int y = 0; y < pixHeight; y++) {
                for (int x = 0; x < pixWidth; x++) {
                    int pixel = pixmap.getPixel(x, y);
                    Color color = new Color();
                    Color.rgba8888ToColor(color, pixel);
                    colorsCache[y * pixWidth + x] = color;
                }
            }
        } else {
            // Existing logic for Realistic and MultiColor modes
            // Collect dominant or random colors as needed (existing behavior)
            Map<Color, Integer> colorFrequency = new HashMap<>();
            for (int y = 0; y < pixHeight; y++) {
                for (int x = 0; x < pixWidth; x++) {
                    int pixel = pixmap.getPixel(x, y);
                    Color color = new Color();
                    Color.rgba8888ToColor(color, pixel);
                    colorFrequency.put(color, colorFrequency.getOrDefault(color, 0) + 1);
                }
            }

            List<Color> dominantColors = colorFrequency.entrySet().stream()
                    .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                    .map(Map.Entry::getKey)
                    .limit(maxColors)
                    .collect(Collectors.toList());

            List<Color> randomColors = new ArrayList<>();
            for (int i = 0; i < maxColors; i++) {
                randomColors.add(new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1));
            }

            int dominantIndex = 0;
            int randomIndex = 0;
            for (int y = 0; y < pixHeight; y++) {
                for (int x = 0; x < pixWidth; x++) {
                    int index = y * pixWidth + x;
                    if (colorType == ColorType.Realistic) {
                        colorsCache[index] = dominantColors.get(dominantIndex);
                        dominantIndex = (dominantIndex + 1) % dominantColors.size();
                    } else if (colorType == ColorType.MultiColor) {
                        colorsCache[index] = randomColors.get(randomIndex);
                        randomIndex = (randomIndex + 1) % randomColors.size();
                    } else {
                        colorsCache[index] = Color.BLACK;
                    }
                }
            }
        }

        return colorsCache;
    }


    public static Color getCachedColor(Pixmap pixmap, float x, float y, PEmbroiderGraphicsLibgdx brodery) {
        int pixX = (int) x;
        int pixY = (int) y;
        if (pixX < 0 || pixX >= pixmap.getWidth() || pixY < 0 || pixY >= pixmap.getHeight()) {
            return Color.BLACK;
        }
        int index = pixY * pixmap.getWidth() + pixX;

        // Si la couleur a été précalculée et est dans le cache, on la retourne
        if (brodery.colorsCache != null && index < brodery.colorsCache.length) {
            return brodery.colorsCache[index];
        }

        // Par défaut, renvoyer la couleur noire si aucune correspondance
        return Color.BLACK;
    }
}
