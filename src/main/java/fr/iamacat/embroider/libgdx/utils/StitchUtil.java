package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import java.util.List;

public class StitchUtil {
    public static class StitchPoint {
        public Vector2 position;
        public Color color;

        public StitchPoint(Vector2 pos, Color col) {
            position = pos.cpy();
            color = col.cpy();
        }
    }

    public static void addStitchesWithoutColorChanges(PEmbroiderGraphicsLibgdx brodery, List<Vector2> points, Color color, float offsetX, float offsetY, Pixmap pixmap) {
        for (Vector2 p : points) {
            float scaledX = p.x * brodery.width / pixmap.getWidth();
            float invertedY = brodery.height - p.y;
            float scaledY = invertedY * brodery.height / pixmap.getHeight();
            Vector2 stitchPos = new Vector2(scaledX + offsetX, scaledY - offsetY);
            brodery.vertex(stitchPos, color);
        }
    }
}
