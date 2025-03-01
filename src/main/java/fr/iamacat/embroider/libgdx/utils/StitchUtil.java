package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import static fr.iamacat.embroider.libgdx.utils.ColorUtil.getCachedColor;

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
        float invertedY = brodery.height - py;
        float scaledX = px * brodery.width / pixmap.getWidth();
        float scaledY = invertedY * brodery.height / pixmap.getHeight();
        Vector2 pos = new Vector2(scaledX, scaledY);
        Color col = getCachedColor(pixmap, px, py, brodery);
        brodery.vertex(pos, col);
    }
}
