package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

public class StitchUtil {
    public static class StitchPoint {
        public Vector2 position;
        public Color color;

        public StitchPoint(Vector2 pos, Color col) {
            position = pos.cpy();
            color = col.cpy();
        }
    }
    public static void addStitch(Pixmap pixmap, float px, float py, PEmbroiderGraphicsLibgdx brodery) {
        // Inverser l'axe Y ici (flip verticalement)
        float invertedY = brodery.height - py; // Inverser l'axe Y pour correspondre au système de coordonnées de la broderie

        // Appliquer la mise à l'échelle des coordonnées (diviser par width et height)
        float scaledX = px * brodery.width / pixmap.getWidth();
        float scaledY = invertedY * brodery.height / pixmap.getHeight();

        // Utiliser les coordonnées mises à l'échelle
        Vector2 pos = new Vector2(scaledX, scaledY);

        // Calculer la couleur (cela doit également être basé sur les coordonnées px, py d'origine)
        Color col = calculateColor(pixmap, px, py, brodery);

        // Ajouter le stitch dans la broderie
        brodery.vertex(pos, col);
    }



    private static Color calculateColor(Pixmap pixmap, float x, float y,
                                        PEmbroiderGraphicsLibgdx brodery) {
        // Logique de couleur unifiée
        return switch (brodery.colorMode) {
            case Realistic -> getPixelColor(pixmap, x, y);
            case MultiColor -> generateRandomColor();
            default -> Color.BLACK;
        };
    }

    private static Color getPixelColor(Pixmap pixmap, float x, float y) {
        int pixel = pixmap.getPixel((int)x, (int)y);
        Color color = new Color();
        Color.rgba8888ToColor(color, pixel);
        return color;
    }

    private static Color generateRandomColor() {
        return new Color(
                MathUtils.random(),
                MathUtils.random(),
                MathUtils.random(),
                1
        );
    }
}
