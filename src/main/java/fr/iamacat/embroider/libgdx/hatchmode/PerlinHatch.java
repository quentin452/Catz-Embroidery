package fr.iamacat.embroider.libgdx.hatchmode;

import com.badlogic.gdx.graphics.Pixmap;
import fr.iamacat.embroider.libgdx.PEmbroiderGraphicsLibgdx;
import fr.iamacat.utils.PerlinNoise;

import static fr.iamacat.embroider.libgdx.utils.StitchUtil.addStitchIfVisible;

public class PerlinHatch  extends BaseHatch{

    public PerlinHatch(PEmbroiderGraphicsLibgdx brodery) {
        super(brodery);
    }

    @Override
    public void apply(PEmbroiderGraphicsLibgdx brodery,Pixmap pixmap, float x, float y) {
        for (int i = 0; i < width; i += hatchSpacing) {
            for (int j = 0; j < height; j += hatchSpacing) {
                float noiseValue = (float) Math.random(); // À remplacer par un vrai Perlin Noise
                if (noiseValue > 0.5) { // Seulement certains points sont tracés
                    addStitchIfVisible(pixmap, x + i, y + j,brodery);
                }
            }
        }
    }
}
