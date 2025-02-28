package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

public class BaseHatch {
    int width, height, hatchSpacing,strokeSpacing,strokeWeight;
    BaseHatch(PEmbroiderGraphicsLibgdx brodery) {
        this.width = brodery.width;
        this.height = brodery.height;
        this.hatchSpacing = brodery.hatchSpacing;
    }
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y) {
        throw new UnsupportedOperationException("Feature incomplete. Contact assistance.");
    }
}
