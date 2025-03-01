package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.PEmbroiderTrace;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.utils.BBox;
import fr.iamacat.embroider.libgdx.utils.StitchUtil;

import java.util.ArrayList;

public class ParallelHatch extends BaseHatch {
    public ParallelHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }
    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y, ArrayList<ArrayList<Vector2>> contours) {
        // Create a bounding box
        BBox boundingBox = new BBox();

        // Iterate over all contours and update the bounding box
        for (ArrayList<Vector2> contour : contours) {
            for (Vector2 point : contour) {
                boundingBox.update(point);
            }
        }

        // Access minX, minY, maxX, and maxY from the bounding box
        float startX = boundingBox.getMaxX();
        float startY = boundingBox.getMinY();
        float endX = boundingBox.getMinX();
        float endY = boundingBox.getMaxY();

        // Generate parallel lines inside the bounding box of the contours
        for (float yLine = startY; yLine <= endY; yLine += brodery.hatchSpacing) {
            for (float xLine = startX; xLine >= endX; xLine -= brodery.hatchSpacing) {
                // Add a stitch to the current position
                StitchUtil.addStitch(pixmap, xLine, yLine, brodery);
            }
        }
    }
}
