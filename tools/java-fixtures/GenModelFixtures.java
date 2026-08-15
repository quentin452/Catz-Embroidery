// Regenerates the emb-model algorithm fixtures under fixtures/model/.
//
// Headless: the PEmbroiderGraphics algorithms are pure geometry (the only use of
// the PApplet is `app.random`, harmless on a bare instance).
//
// Usage (from this repo root), after compiling GenFixtures:
//   javac -encoding UTF-8 -cp "src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" ^
//         -d tools/java-fixtures/out tools/java-fixtures/GenModelFixtures.java
//   java -cp "tools/java-fixtures/out;src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" ^
//        GenModelFixtures
//
// Determinism notes:
//  - hatch/resample outputs are deterministic (randomize = 0).
//  - TSP is run with trials=3: trials 0,1,2 use fixed starts (0, y-min, x-min);
//    trials >= 3 use Math.random and are NOT comparable — the Rust port uses a
//    fixed-seed PRNG there, so fixture tests stay on trials=3.

import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderTrace;
import processing.embroider.PEmbroiderTSP;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

public class GenModelFixtures {
    static void dumpPolyline(PrintWriter out, ArrayList<PVector> poly) {
        out.println("P " + poly.size());
        for (PVector p : poly) {
            out.println(p.x + " " + p.y);
        }
    }

    static void dumpPolylines(PrintWriter out, ArrayList<ArrayList<PVector>> polys) {
        out.println("L " + polys.size());
        for (ArrayList<PVector> poly : polys) {
            dumpPolyline(out, poly);
        }
    }

    public static void main(String[] args) throws Exception {
        PApplet app = new PApplet();
        PEmbroiderGraphics E = new PEmbroiderGraphics(app, 100, 100);

        // Concave L-shape: exercises non-convex hatch crossings.
        ArrayList<PVector> L = new ArrayList<>();
        float[][] pts = {{10f, 10f}, {90f, 10f}, {90f, 60f}, {50f, 60f}, {50f, 90f}, {10f, 90f}};
        for (float[] p : pts) {
            L.add(new PVector(p[0], p[1]));
        }

        // Zigzag polyline for resample: segments both under and over maxLen,
        // with a sharp corner (tests the maxTurn skip).
        ArrayList<PVector> zig = new ArrayList<>();
        float[][] zpts = {{0f, 0f}, {3f, 0f}, {3.5f, 1f}, {15f, 1f}, {15f, 5f}, {60f, 5f},
                          {60f, 6f}, {120f, 6f}};
        for (float[] p : zpts) {
            zig.add(new PVector(p[0], p[1]));
        }

        // Six scattered segments for TSP: mixed orientations.
        ArrayList<ArrayList<PVector>> tsp = new ArrayList<>();
        float[][][] segs = {{{10f, 10f}, {30f, 10f}}, {{25f, 40f}, {45f, 35f}},
                            {{5f, 60f}, {8f, 70f}}, {{70f, 20f}, {60f, 30f}},
                            {{80f, 80f}, {90f, 75f}}, {{50f, 55f}, {55f, 60f}}};
        for (float[][] s : segs) {
            ArrayList<PVector> seg = new ArrayList<>();
            seg.add(new PVector(s[0][0], s[0][1]));
            seg.add(new PVector(s[1][0], s[1][1]));
            tsp.add(seg);
        }

        File dir = new File("fixtures/model");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        PrintWriter h45 = new PrintWriter("fixtures/model/hatch45.txt", "UTF-8");
        dumpPolylines(h45, E.hatchParallel(L, PApplet.QUARTER_PI, 4f));
        h45.close();

        PrintWriter h30 = new PrintWriter("fixtures/model/hatch03.txt", "UTF-8");
        dumpPolylines(h30, E.hatchParallel(L, 0.3f, 5f));
        h30.close();

        PrintWriter rs = new PrintWriter("fixtures/model/resample.txt", "UTF-8");
        dumpPolyline(rs, E.resample(zig, 4f, 10f, 0f, 0f));
        rs.close();

        PrintWriter ts = new PrintWriter("fixtures/model/tsp.txt", "UTF-8");
        dumpPolylines(ts, PEmbroiderTSP.solve(tsp, 3, 999));
        ts.close();

        // Binary mask: a filled circle, 40x40, white inside (the thresholded
        // PImage the converter's image() path would produce).
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

        PrintWriter fc = new PrintWriter("fixtures/model/findcontours.txt", "UTF-8");
        dumpPolylines(fc, PEmbroiderTrace.findContours(circle));
        fc.close();

        PrintWriter ap = new PrintWriter("fixtures/model/approxpolydp.txt", "UTF-8");
        ArrayList<ArrayList<PVector>> conts = PEmbroiderTrace.findContours(circle);
        if (!conts.isEmpty()) {
            dumpPolyline(ap, PEmbroiderTrace.approxPolyDP(conts.get(0), 1f));
        }
        ap.close();

        // The converter's raster path: HATCH_SPACING 4, STITCH_LENGTH 10,
        // PARALLEL_RESAMPLING_OFFSET_FACTOR 0.5, effective angle QUARTER_PI
        // (HALF_PI - HATCH_ANGLE with the default HATCH_ANGLE = QUARTER_PI).
        PrintWriter hr = new PrintWriter("fixtures/model/hatchraster.txt", "UTF-8");
        dumpPolylines(hr, E.hatchParallelRaster(circle, PApplet.QUARTER_PI, 4f, 1f));
        hr.close();

        ArrayList<ArrayList<PVector>> cross = E.hatchParallelRaster(circle, PApplet.QUARTER_PI, 4f, 1f);
        cross.addAll(E.hatchParallelRaster(circle, PApplet.QUARTER_PI + PApplet.HALF_PI, 4f, 1f));
        PrintWriter cr = new PrintWriter("fixtures/model/cross.txt", "UTF-8");
        dumpPolylines(cr, E.resampleCrossIntersection(cross, PApplet.QUARTER_PI, 4f, 10f, 0.5f, 0f));
        cr.close();

        System.out.println("model fixtures written");
    }
}
