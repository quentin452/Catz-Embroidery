package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.math.Vector2;

public class BBox {
    private float minX, minY, maxX, maxY;

    // Constructor to initialize with minimum and maximum values
    public BBox() {
        minX = Float.MAX_VALUE;
        minY = Float.MAX_VALUE;
        maxX = Float.MIN_VALUE;
        maxY = Float.MIN_VALUE;
    }

    // Method to update the bounding box with a new point
    public void update(Vector2 point) {
        minX = Math.min(minX, point.x);
        minY = Math.min(minY, point.y);
        maxX = Math.max(maxX, point.x);
        maxY = Math.max(maxY, point.y);
    }

    // Getters for minX, minY, maxX, maxY
    public float getMinX() {
        return minX;
    }

    public float getMinY() {
        return minY;
    }

    public float getMaxX() {
        return maxX;
    }

    public float getMaxY() {
        return maxY;
    }

    // Method to get the width of the bounding box
    public float getWidth() {
        return maxX - minX;
    }

    // Method to get the height of the bounding box
    public float getHeight() {
        return maxY - minY;
    }

    // Method to check if a point is within the bounding box
    public boolean contains(Vector2 point) {
        return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY;
    }

    // Optionally, you could add a method to expand the box if needed
    public void expand(Vector2 point) {
        update(point);
    }

    @Override
    public String toString() {
        return "BBox{" +
                "minX=" + minX +
                ", minY=" + minY +
                ", maxX=" + maxX +
                ", maxY=" + maxY +
                '}';
    }
}
