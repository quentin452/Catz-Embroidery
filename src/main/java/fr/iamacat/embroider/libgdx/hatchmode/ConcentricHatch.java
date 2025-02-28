package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;

public class ConcentricHatch extends BaseHatch{
    public ConcentricHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        for (float r = hatchSpacing; r < Math.max(width, height) / 2.0f; r += hatchSpacing) {
            for (float angle = 0; angle < 360; angle += 10) {
                float rad = (float) Math.toRadians(angle);
                float px = centerX + r * (float) Math.cos(rad);
                float py = centerY + r * (float) Math.sin(rad);
                addStitchIfVisible(pixmap, px, py,brodery);
            }
        }
    }
}
