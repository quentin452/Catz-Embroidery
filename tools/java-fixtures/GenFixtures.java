// Regenerates the emb-data fixtures the Rust suite byte-compares against.
//
// Usage (from this repo root):
//   javac -encoding UTF-8 -cp "src/main/java;library/*" -d tools/java-fixtures/out ^
//         tools/java-fixtures/GenFixtures.java
//   java -cp "tools/java-fixtures/out;src/main/java;library/*" GenFixtures
//
// Output lands in fixtures/ as simple.dst, simple.pes, simple.svg.
// The design is deliberately small and deterministic: one square + one triangle,
// two colours, a few jumps — every value hand-checkable in the fixtures.

import processing.core.PVector;
import processing.embroider.PEmbroiderReader;
import processing.embroider.PEmbroiderWriter;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

public class GenFixtures {
    public static void main(String[] args) throws Exception {
        float[] bounds = {0f, 0f, 100f, 100f};
        ArrayList<PVector> stitches = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Boolean> jumps = new ArrayList<>();

        int red = 0xFF000000 | 0xFF0000;
        int blue = 0xFF000000 | 0x0000FF;

        // Square outline in red: (10,10) -> (90,10) -> (90,90) -> (10,90) -> (10,10)
        float[][] square = {{10f, 10f}, {90f, 10f}, {90f, 90f}, {10f, 90f}, {10f, 10f}};
        for (float[] p : square) {
            stitches.add(new PVector(p[0], p[1]));
            colors.add(red);
            jumps.add(false);
        }

        // A long jump (over 100 in one axis -> DST jump-splitting kicks in)
        stitches.add(new PVector(10f, 10f));
        colors.add(red);
        jumps.add(true);
        stitches.add(new PVector(30f, 10f));
        colors.add(red);
        jumps.add(false);

        // Triangle in blue: (30,10) -> (60,50) -> (20,50) -> (30,10)
        float[][] tri = {{30f, 10f}, {60f, 50f}, {20f, 50f}, {30f, 10f}};
        for (float[] p : tri) {
            stitches.add(new PVector(p[0], p[1]));
            colors.add(blue);
            jumps.add(false);
        }

        File out = new File("fixtures");
        if (!out.exists()) {
            out.mkdirs();
        }

        PEmbroiderWriter.DST.write("fixtures/simple", bounds, stitches, colors, "Simple Test", jumps);
        PEmbroiderWriter.PES.write("fixtures/simple", bounds, stitches, colors, "Simple Test", jumps);
        PEmbroiderWriter.SVG.write("fixtures/simple", bounds, stitches, colors, "Simple Test", jumps);
        System.out.println("fixtures written");
    }
}
