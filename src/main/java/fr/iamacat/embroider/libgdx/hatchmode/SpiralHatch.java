package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;

public class SpiralHatch extends BaseHatch{

    public SpiralHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }
    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        float theta = 0;
        float radius = 0;
        float radiusMax = Math.max(width, height) / 2.0f;

        while (radius < radiusMax) {
            float px = centerX + radius * (float) Math.cos(theta);
            float py = centerY + radius * (float) Math.sin(theta);
            addStitchIfVisible(pixmap, px, py,brodery);

            theta += 0.1f;
            radius += 0.1f;
        }
    }
}
