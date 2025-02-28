package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import java.util.ArrayList;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;

public class CrossHatch extends BaseHatch {

    public CrossHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        ArrayList<ArrayList<Vector2>> contours = PEmbroiderTrace.findContours(pixmap);

        // Ajuster le seuil en fonction de la résolution
        float epsilon = Math.min(1.0f, pixmap.getWidth() / 100f);

        // Valeur d'offset pour déplacer le dessin vers la droite
        float offsetX = 340; // TODO DO NOT HARDCODE THIS

        contours.replaceAll(polyline -> PEmbroiderTrace.approxPolyDP(polyline, epsilon));

        // Remplir les contours avec une texture en croisillons
        for (ArrayList<Vector2> contour : contours) {
            fillContourWithCrossHatch(brodery, pixmap, contour, offsetX);
        }
    }

    private void fillContourWithCrossHatch(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, ArrayList<Vector2> contour, float offsetX) {
        float hatchSpacing = brodery.hatchSpacing;

        // Dessiner le contour avec des croix régulières
        for (Vector2 point : contour) {
            float adjustedX = (point.x * brodery.width / pixmap.getWidth()) + offsetX;
            float adjustedY = brodery.height - (point.y * brodery.height / pixmap.getHeight());

            // Dessiner la grille de croix
            for (float i = -hatchSpacing; i <= hatchSpacing; i += hatchSpacing) {
                // Diagonale bas-gauche vers haut-droit (X et Y augmentent)
                float crossX1 = adjustedX + i;
                float crossY1 = adjustedY + i;
                addStitchIfVisible(pixmap, crossX1, crossY1, brodery);

                // Diagonale haut-gauche vers bas-droit (X augmente, Y diminue)
                float crossX2 = adjustedX + i;
                float crossY2 = adjustedY - i;
                addStitchIfVisible(pixmap, crossX2, crossY2, brodery);
            }
        }
    }
}
