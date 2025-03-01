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

        // Stocker les couleurs pour Realistic
        Map<Color, Integer> colorFrequency = new HashMap<>();

        // Première étape : collecter les couleurs et leur fréquence
        for (int y = 0; y < pixHeight; y++) {
            for (int x = 0; x < pixWidth; x++) {
                int pixel = pixmap.getPixel(x, y);
                Color color = new Color();
                Color.rgba8888ToColor(color, pixel);

                // Compter la fréquence des couleurs
                colorFrequency.put(color, colorFrequency.getOrDefault(color, 0) + 1);
            }
        }

        // Liste des couleurs dominantes dans le mode Realistic
        List<Color> dominantColors = colorFrequency.entrySet().stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue())) // Trier par fréquence
                .map(Map.Entry::getKey) // Extraire les couleurs
                .limit(maxColors) // Limiter à maxColors
                .collect(Collectors.toList());

        // Liste des couleurs aléatoires dans le mode MultiColor
        List<Color> randomColors = new ArrayList<>();
        for (int i = 0; i < maxColors; i++) {
            randomColors.add(new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1));
        }

        // Deuxième étape : Assigner les couleurs dominantes ou aléatoires à colorsCache
        int dominantIndex = 0;
        int randomIndex = 0;

        for (int y = 0; y < pixHeight; y++) {
            for (int x = 0; x < pixWidth; x++) {
                int index = y * pixWidth + x;

                // Si le mode est Realistic, attribuer une couleur dominante à ce pixel
                if (colorType == Realistic) {
                    colorsCache[index] = dominantColors.get(dominantIndex);
                    dominantIndex = (dominantIndex + 1) % dominantColors.size(); // Passer à la couleur suivante
                }
                // Si le mode est MultiColor, attribuer une couleur aléatoire
                else if (colorType == MultiColor) {
                    colorsCache[index] = randomColors.get(randomIndex);
                    randomIndex = (randomIndex + 1) % randomColors.size(); // Passer à la couleur suivante
                }
                // Par défaut, la couleur est noire
                else {
                    colorsCache[index] = Color.BLACK;
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
