package fr.iamacat.utils;

public class PerlinNoise {

    private int[] p;

    public PerlinNoise() {
        p = new int[512];
        int[] permutation = new int[256];

        // Initialize permutation array with random values
        for (int i = 0; i < 256; i++) {
            permutation[i] = i;
        }

        // Shuffle the permutation array
        for (int i = 0; i < 256; i++) {
            int j = (int) (Math.random() * 256);
            int temp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = temp;
        }

        // Double the permutation array to avoid wrapping
        for (int i = 0; i < 256; i++) {
            p[256 + i] = p[i] = permutation[i];
        }
    }

    // Fade function to smooth the noise
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    // Linear interpolation
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    // Grad function to calculate gradient
    private double grad(int hash, double x, double y) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : 0);
        return (h & 1) == 0 ? u + v : u - v;
    }

    // Generate Perlin noise value at (x, y)
    public double getNoise(double x, double y) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        double u = fade(x);
        double v = fade(y);

        int a = p[X] + Y;
        int b = p[X + 1] + Y;

        return lerp(v, lerp(u, grad(p[a], x, y), grad(p[b], x - 1, y)),
                lerp(u, grad(p[a + 1], x, y - 1), grad(p[b + 1], x - 1, y - 1)));
    }
}
