// Regenerates the emb-model font fixtures under fixtures/model/.
//
// The Rust editor's TXT stitching uses the Hershey SIMPLEX vector font
// (PEmbroiderFont.SIMPLEX) — a D010-class decision: the Java editor's text
// goes through Java2D (pg.text), which is not reproducible within D002, so
// the port uses the Java's OWN vector font API (PEmbroiderFont.putText),
// which IS pure geometry. The fixture pins the port to the Java's exact
// glyph data + putText layout (baseline-left anchor, LEFT align).
//
// Usage (from this repo root):
//   javac -encoding UTF-8 -cp "src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" ^
//         -d tools/java-fixtures/out tools/java-fixtures/GenFontFixtures.java
//   java -cp "tools/java-fixtures/out;src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" ^
//        GenFontFixtures

import processing.core.PApplet;
import processing.core.PVector;
import processing.embroider.PEmbroiderFont;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

public class GenFontFixtures {
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
        new File("fixtures/model").mkdirs();

        // "Hello!" at scale 1, baseline-left at (10, 20): the anchor the
        // Rust editor's text tool commits (the click point = baseline-left).
        PrintWriter h = new PrintWriter("fixtures/model/hershey_hello.txt", "UTF-8");
        dumpPolylines(h, PEmbroiderFont.putText(
                PEmbroiderFont.SIMPLEX, "Hello!", 10f, 20f, 1f, PApplet.LEFT));
        h.close();

        // Lowercase + digits + punctuation, the full printable range the
        // text tool can commit, scale 1.
        String s = "abcxyz 0123456789 !?.,;:()[]{}<>=+-*/_\"'#@%&$";
        PrintWriter g = new PrintWriter("fixtures/model/hershey_range.txt", "UTF-8");
        dumpPolylines(g, PEmbroiderFont.putText(
                PEmbroiderFont.SIMPLEX, s, 0f, 0f, 1f, PApplet.LEFT));
        g.close();

        // One glyph, scaled: 'W' at scale 4 (the per-element size mapping is
        // scale = textSize; the fixture pins a scaled glyph's exact points).
        PrintWriter w = new PrintWriter("fixtures/model/hershey_w_scale4.txt", "UTF-8");
        dumpPolylines(w, PEmbroiderFont.putText(
                PEmbroiderFont.SIMPLEX, "W", 30f, 40f, 4f, PApplet.LEFT));
        w.close();

        System.out.println("GenFontFixtures done");
    }
}
