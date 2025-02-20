package fr.iamacat;

import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderHatchSatin;
import processing.embroider.PEmbroiderWriter;

import java.io.File;
import java.util.ArrayList;

public class PEmbroiderApplication extends PApplet {
    PImage img;
    PEmbroiderGraphics embroidery;

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderApplication");
    }

    @Override
    public void settings() {
        size(800, 600);
    }

    @Override
    public void setup() {
        selectInput("Choose a PNG file to convert to PES:", "fileSelected");
    }

    public void fileSelected(File selection) {
        if (selection == null) {
            println("No file selected.");
        } else {
            String path = selection.getAbsolutePath();
            img = loadImage(path);
            if (img != null) {
                // Initialize embroidery graphics
                embroidery = new PEmbroiderGraphics(this, img.width, img.height);
                embroidery.setStitch(10, 5, 0);

                // Generate hatch satin paths
                ArrayList<ArrayList<PVector>> stitches = PEmbroiderHatchSatin.hatchSatinRaster(img, 10, 5);

                // Add paths to embroidery object
                embroidery.beginDraw();
                for (ArrayList<PVector> stitch : stitches) {
                    if (stitch.size() > 1) {
                        embroidery.beginShape();
                        for (PVector point : stitch) {
                            embroidery.vertex(point.x, point.y);
                        }
                        embroidery.endShape();
                    }
                }
                embroidery.optimize();
                embroidery.endDraw();

                // Prompt the user to choose the file name and format to save
                selectOutput("Choose where to save your embroidery file", "fileSaved");
            } else {
                println("Failed to load the image.");
            }
        }
    }

    // This function will be called when the user selects a save location
    public void fileSaved(File selection) {
        if (selection != null) {
            // Get the output path
            String outputPath = selection.getAbsolutePath();

            // Split the filename by period (.)
            String[] tokens = outputPath.split("\\.(?=[^\\.]+$)");

            // Ensure the file has an extension
            if (tokens.length > 1) {
                String extension = tokens[1].toUpperCase();  // Get the extension, e.g., "PES", "DST"

                // Choose which type to save based on the extension
                try {
                    if (extension.equals("DST")) {
                        PEmbroiderWriter.write(outputPath, embroidery.polylines, embroidery.colors, embroidery.width, embroidery.height, false);
                    } else if (extension.equals("PES")) {
                        PEmbroiderWriter.write(outputPath, embroidery.polylines, embroidery.colors, embroidery.width, embroidery.height, false);
                    } else if (extension.equals("SVG")) {
                        PEmbroiderWriter.write(outputPath, embroidery.polylines, embroidery.colors, embroidery.width, embroidery.height, true);
                    } else {
                        println("Unsupported file format: " + extension);
                    }
                    println("File saved as " + extension);
                } catch (Exception e) {
                    println("Error saving file: " + e.getMessage());
                }
            } else {
                // Handle case when the file has no extension
                println("Error: No file extension found. Please add an extension (e.g., .PES, .DST, .SVG).");
            }
        }
    }


    @Override
    public void draw() {
        background(255);
        if (img != null) {
            image(img, 0, 0, width, height);
        }
    }
}
