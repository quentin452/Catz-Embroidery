package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.embroider.libgdx.hatchmode.BaseHatch;
import fr.iamacat.embroider.libgdx.utils.StitchUtil;
import java.util.ArrayList;

public class RealisticHatch extends BaseHatch {
    public RealisticHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery, Pixmap pixmap, float x, float y, ArrayList<ArrayList<Vector2>> contours) {
        System.out.println("Called realistic hatch with bitmap filling");

        // Create a bitmap representing the filled area
        Pixmap bitmap = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), Pixmap.Format.RGBA8888);
        bitmap.setColor(0, 0, 0, 1); // Set color to black for filled areas
        bitmap.fill(); // Initially fill the bitmap (you can modify it based on your needs)

        // Iterate over the contours and convert them to a filled bitmap
        for (ArrayList<Vector2> contour : contours) {
            fillContourInBitmap(bitmap, contour);
        }

        // Now iterate over the pixels and apply stitches to the filled areas
        for (int i = 0; i < bitmap.getWidth(); i++) {
            for (int j = 0; j < bitmap.getHeight(); j++) {
                if (bitmap.getPixel(i, j) != 0) { // If the pixel is filled (non-transparent)
                    float stitchX = i + x;
                    float stitchY = j + y;
                    StitchUtil.addStitch(pixmap, stitchX, stitchY, brodery);
                }
            }
        }
    }

    /**
     * Fills the contour into a bitmap by setting the corresponding pixels to a filled color.
     * @param bitmap The bitmap where the contour will be filled.
     * @param contour The contour to fill.
     */
    private void fillContourInBitmap(Pixmap bitmap, ArrayList<Vector2> contour) {
        // Simple approach to fill the contour area using an algorithm like flood fill or scanline fill.
        // You can use scanline fill or another bitmap-based technique to fill the area inside the contour.

        // For simplicity, here we're just marking the points inside the contour
        // (In a more advanced solution, you'd need a fill algorithm)
        for (Vector2 point : contour) {
            bitmap.drawPixel((int) point.x, (int) point.y);
        }
    }
}
