package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

public class StitchUtil {
    /**
     * Ajoute un point de broderie si le pixel est visible.
     */
    // TODO FIX THIS METHOD
    public static void addStitchIfVisible(Pixmap pixmap, float px, float py, PEmbroiderGraphicsLibgdx brodery) {
        int originalX = (int) (px - (float) brodery.width / 2);
        int originalY = (int) (py - (float) brodery.height / 2);
        int pixel = pixmap.getPixel(originalX, originalY);
        Color pixelColor = getColorForPixel(pixel,brodery);
        brodery.currentColor = pixelColor;
        vertex(px, py,brodery);
    }

    /**
     * Détermine la couleur du pixel en fonction du mode sélectionné.
     */
    public static Color getColorForPixel(int pixel,PEmbroiderGraphicsLibgdx brodery) {
        Color color = new Color();
        Color.rgba8888ToColor(color, pixel);

        switch (brodery.colorMode) {
            case Realistic:
                return color; // Utilise la couleur exacte de l'image

            case BlackAndWhite:
                return Color.BLACK; // Tout en noir

            case MultiColor:
                return new Color(
                        (float) Math.random(),
                        (float) Math.random(),
                        (float) Math.random(),
                        1
                ); // Couleur aléatoire

            default:
                return Color.BLACK;
        }
    }

    public static void vertex(float x, float y,PEmbroiderGraphicsLibgdx brodery) {
        if (brodery.currentPolyline != null) {
            brodery.currentPolyline.add(new Vector2(x, y));
        }
    }

}
