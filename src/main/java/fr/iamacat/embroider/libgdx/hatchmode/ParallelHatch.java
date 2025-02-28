package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;

public class ParallelHatch  extends BaseHatch{

    public ParallelHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        for (int j = 0; j < height; j += hatchSpacing) {
            for (int i = 0; i < width; i += hatchSpacing) {
                addStitchIfVisible(pixmap, x + i, y + j,brodery);
            }
        }
    }
}
