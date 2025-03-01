package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.StitchUtil;

import java.util.ArrayList;

public class ConcentricHatch extends BaseHatch{
    public ConcentricHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y,ArrayList<ArrayList<Vector2>> contours) {
        throw new UnsupportedOperationException("Feature incomplete. Contact assistance.");
    }
}
