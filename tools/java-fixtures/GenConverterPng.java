// Headless converter acceptance harness: runs the converter's pipeline
// (fr.iamacat.pembroider_converter.Main's processImageWithProgress +
// fileSaved, minus the GUI) on an image and writes the stitched design as
// PNGs, for visual comparison against the Rust converter (M5 acceptance).
//
// Usage (from this repo root):
//   javac -encoding UTF-8 -cp "src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" ^
//         -d tools/java-fixtures/out tools/java-fixtures/GenConverterPng.java
//   java "-Duser.language=en" "-Duser.country=US" ^
//        -cp "tools/java-fixtures/out;src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" ^
//        GenConverterPng <image> <outdir-no-extension-prefix>
//
// The PNG writer renders on a transparent 1000x1000 canvas, centred on the
// design's bounds — the (860, 70) canvas offset cancels, so the Rust side
// renders the same centred space and the two PNGs are directly comparable.
//
// Determinism: E.optimize() uses Math.random for the TSP trials — it only
// reorders the stitch runs, never the geometry, so the PNG lines are stable.

import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderWriter;

import java.util.ArrayList;

public class GenConverterPng {
    public static void main(String[] args) throws Exception {
        PApplet app = new PApplet();
        // A bare PApplet has no primary graphics context; createGraphics()
        // needs one (the raster paths in image() use it). Initialise a
        // headless Java2D context — no window. colorMode() must be called:
        // PApplet.color() delegates to g.color() when g != null, and the
        // colorMode fields default to 0 until initialized (measured — without
        // it every color() returns 0 and the multicolor palette is all black).
        processing.awt.PGraphicsJava2D g2d = new processing.awt.PGraphicsJava2D();
        g2d.setParent(app);
        g2d.setPrimary(true);
        g2d.setSize(1280, 720);
        g2d.colorMode(PApplet.RGB, 255, 255, 255, 255);
        app.g = g2d;
        String imagePath = args[0];
        String outPrefix = args[1];

        // A bare PApplet refuses loadImage() outside setup(); load via ImageIO
        // (the PImage(BufferedImage) constructor bypasses the guard).
        PImage src = new PImage(javax.imageio.ImageIO.read(new java.io.File(imagePath)));
        if (src.width == 0) {
            throw new RuntimeException("cannot load " + imagePath);
        }

        // The Java converter's defaults: hatch CROSS, MultiColor, spacing 10,
        // stroke weight 25, stroke PERPENDICULAR at spacing 10, stroke black.
        // FillB=false is the default (outline only); the second run adds the
        // cross hatch fill; the third is BlackAndWhite + fill — the two sides
        // render the same black lines, isolating geometry from colour.
        convertAndSave(app, src, outPrefix + "_java_outline", false, true);
        convertAndSave(app, src, outPrefix + "_java_cross", true, true);
        convertAndSave(app, src, outPrefix + "_java_bw_cross", true, false);
    }

    static void convertAndSave(PApplet app, PImage src, String outPrefix, boolean fill, boolean multicolor) {
        PEmbroiderGraphics E = new PEmbroiderGraphics(app, 1280, 720);
        E.popyLineMulticolor = multicolor;
        E.beginDraw();
        E.clear();
        PImage img = src.copy();
        img.resize(1000, 1000);
        E.beginCull();
        E.hatchMode(PEmbroiderGraphics.CROSS);
        E.hatchSpacing(10);
        E.strokeWeight(25);
        E.strokeMode(PEmbroiderGraphics.PERPENDICULAR);
        E.strokeSpacing(10);
        E.stroke(0, 0, 0);
        if (fill) {
            E.fill(0, 0, 0);
        } else {
            E.noFill();
        }
        E.image(img, 860, 70);
        E.endCull();
        E.optimize();

        System.out.println(
                outPrefix + ": " + E.polylines.size() + " polylines, " + E.colors.size() + " colors");
        int totalPts = 0;
        int n2 = 0;
        int n3to5 = 0;
        int n6plus = 0;
        int maxN = 0;
        for (ArrayList<PVector> poly : E.polylines) {
            totalPts += poly.size();
            if (poly.size() == 2) n2++;
            else if (poly.size() <= 5) n3to5++;
            else n6plus++;
            maxN = Math.max(maxN, poly.size());
        }
        System.out.println(
                outPrefix + ": totalPts=" + totalPts + " avg=" + (totalPts / (float) E.polylines.size())
                        + " n2=" + n2 + " n3to5=" + n3to5 + " n6plus=" + n6plus + " maxN=" + maxN);
        System.out.println(
                outPrefix + ": design colors="
                        + E.colors.subList(0, Math.min(5, E.colors.size())));
        PEmbroiderWriter.PNG.write(app, outPrefix, "png", E.polylines, E.colors);
        System.out.println("wrote " + outPrefix + ".png");
    }
}
