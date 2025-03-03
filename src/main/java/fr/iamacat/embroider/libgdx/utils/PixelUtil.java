package fr.iamacat.embroider.libgdx.utils;

public class PixelUtil {

    public static float pixelToMm(float width,float height) {
        width *= 3.67f;
        height *= 3.67f;
        return Math.min(width, height);
    }
}
