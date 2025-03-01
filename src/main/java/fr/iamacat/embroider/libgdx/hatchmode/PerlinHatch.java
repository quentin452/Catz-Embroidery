package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.StitchUtil;
import fr.iamacat.utils.PerlinNoise;

import java.util.ArrayList;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;

public class PerlinHatch  extends BaseHatch{

    public PerlinHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y,ArrayList<ArrayList<Vector2>> contours) {
        throw new UnsupportedOperationException("Feature incomplete. Contact assistance.");
    }
}
