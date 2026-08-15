// Diagnostic probe: prints the Java's resampleCrossIntersection crossline grid
// and the first polyline's intersections. Compare with emb-model's crossdebug
// example output.

import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;

import java.util.ArrayList;

public class CrossProbe {
    public static void main(String[] args) throws Exception {
        PApplet app = new PApplet();
        PEmbroiderGraphics E = new PEmbroiderGraphics(app, 100, 100);

        PImage circle = new PImage(40, 40, PApplet.ARGB);
        circle.loadPixels();
        for (int i = 0; i < 40 * 40; i++) {
            int x = i % 40;
            int y = i / 40;
            float dx = x - 19.5f;
            float dy = y - 19.5f;
            circle.pixels[i] = (dx * dx + dy * dy <= 15f * 15f) ? 0xFFFFFFFF : 0xFF000000;
        }
        circle.updatePixels();

        ArrayList<ArrayList<PVector>> polys = E.hatchParallelRaster(circle, PApplet.QUARTER_PI, 4f, 1f);
        System.out.println("hatch polylines: " + polys.size());

        float angle = PApplet.QUARTER_PI;
        float spacing = 4f;
        float len = 10f;
        float offsetFactor = 0.5f;
        float randomize = 0f;
        float base = len * offsetFactor;
        float relang = PApplet.atan2(spacing, base);
        float d = len * PApplet.cos(PApplet.HALF_PI - relang);
        float ang = angle - relang;
        System.out.println("relang=" + relang + " d=" + d + " ang=" + ang);

        PEmbroiderGraphics.BCircle bcirc = E.new BCircle(polys, 0);
        bcirc.r *= 1.05;
        System.out.println("bcirc: (" + bcirc.x + ", " + bcirc.y + ") r=" + bcirc.r);

        float x0 = bcirc.x - bcirc.r * PApplet.cos(ang);
        float y0 = bcirc.y - bcirc.r * PApplet.sin(ang);
        float x1 = bcirc.x + bcirc.r * PApplet.cos(ang);
        float y1 = bcirc.y + bcirc.r * PApplet.sin(ang);
        float l = new PVector(x0, y0).dist(new PVector(x1, y1));
        int n = (int) Math.ceil(l / d);
        System.out.println("crosslines n=" + n + " l=" + l);
        for (int i = 0; i < n; i++) {
            float t = (float) i / (float) (n - 1);
            float x = x0 * (1 - t) + x1 * t;
            float y = y0 * (1 - t) + y1 * t;
            float px = x + bcirc.r * PApplet.cos(ang - PApplet.HALF_PI);
            float py = y + bcirc.r * PApplet.sin(ang - PApplet.HALF_PI);
            float qx = x + bcirc.r * PApplet.cos(ang + PApplet.HALF_PI);
            float qy = y + bcirc.r * PApplet.sin(ang + PApplet.HALF_PI);
            System.out.println("crossline " + i + ": (" + px + ", " + py + ") -> (" + qx + ", " + qy + ")");
        }

        PVector a = polys.get(0).get(0);
        PVector b = polys.get(0).get(1);
        System.out.println("first polyline: " + a + " -> " + b);
        for (int i = 0; i < n; i++) {
            float t = (float) i / (float) (n - 1);
            float x = x0 * (1 - t) + x1 * t;
            float y = y0 * (1 - t) + y1 * t;
            PVector p = new PVector(x + bcirc.r * PApplet.cos(ang - PApplet.HALF_PI), y + bcirc.r * PApplet.sin(ang - PApplet.HALF_PI));
            PVector q = new PVector(x + bcirc.r * PApplet.cos(ang + PApplet.HALF_PI), y + bcirc.r * PApplet.sin(ang + PApplet.HALF_PI));
            PVector o = PEmbroiderGraphics.segmentIntersect3D(a, b, p, q);
            if (o != null) {
                float t0 = o.x;
                System.out.println("  intersection t=" + t0 + " -> (" + (a.x * (1 - t0) + b.x * t0) + ", " + (a.y * (1 - t0) + b.y * t0) + ")");
            }
        }
    }
}
