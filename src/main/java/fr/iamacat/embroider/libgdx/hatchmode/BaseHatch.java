package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;

import java.util.ArrayList;

public class BaseHatch {
    int width, height, hatchSpacing,strokeSpacing,strokeWeight;
    BaseHatch(PEmbroiderGraphicsLibgdx brodery) {
        this.width = brodery.width;
        this.height = brodery.height;
        this.hatchSpacing = brodery.hatchSpacing;
    }
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y,ArrayList<ArrayList<Vector2>> contours) {
        throw new UnsupportedOperationException("Feature incomplete. Contact assistance.");
    }
}
