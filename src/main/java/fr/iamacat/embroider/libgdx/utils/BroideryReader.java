package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BroideryReader {

    private static Texture readSVGAsTexture(String filename, float desiredWidth, float desiredHeight) {
        List<BezierShape> shapes = readSVG(filename);
        // Create a Pixmap to draw the shapes
        Pixmap pixmap = new Pixmap((int) desiredWidth, (int) desiredHeight, Pixmap.Format.RGBA8888);

        // Draw the BezierShapes directly to the Pixmap with scaling
        for (BezierShape shape : shapes) {
            int color = shape.getColor();
            Color gdxColor = new Color((color >> 16 & 0xFF), (color >> 8 & 0xFF), (color & 0xFF), 1f);
            pixmap.setColor(gdxColor);
            for (BezierCurve curve : shape) {
                BezierUtil.renderBezierCurveToPixmap(pixmap, curve, gdxColor,1);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose(); // Dispose the Pixmap to free resources
        return texture;
    }

    // Helper method to read SVG files
    private static List<BezierShape> readSVG(String filename) {
        List<BezierShape> shapes = new ArrayList<>();
        try {
            File svgFile = new File(filename);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(svgFile);
            doc.getDocumentElement().normalize();

            NodeList pathList = doc.getElementsByTagName("path");

            for (int i = 0; i < pathList.getLength(); i++) {
                String pathData = pathList.item(i).getAttributes().getNamedItem("d").getNodeValue();
                String color = pathList.item(i).getAttributes().getNamedItem("style").getNodeValue();
                int fillIndex = color.indexOf("fill:");
                if (fillIndex != -1) {
                    int start = fillIndex + 5; // Position après "fill:"
                    int end = color.indexOf(";", start);

                    if (end == -1) {
                        end = color.length(); // Si pas de ";", prendre jusqu'à la fin
                    }

                    color = color.substring(start, end);
                } else {
                    color = "#000000"; // Couleur par défaut si "fill:" absent
                }

                BezierShape shape = BezierShape.fromSVGPathString(pathData);
                shape.setColor(parseColor(color));
                shapes.add(shape);
            }
        } catch (Exception e) {
            Gdx.app.error("BroideryReader", "Error reading SVG file", e);
        }

        return shapes;
    }

    // Helper method to parse hex color string to integer
    private static int parseColor(String hexColor) {
        return Integer.parseInt(hexColor.substring(1), 16);
    }

    // Helper method to get the file extension
    public static String getFileExtension(String filename) {
        String[] tokens = filename.split("\\.(?=[^\\.]+$)");
        return tokens[tokens.length - 1];
    }

    // Method to read files and convert them to Texture
    public static Texture readAsTexture(String filename, float desiredWidth, float desiredHeight) throws IOException {
        Texture texture;
        String extension = getFileExtension(filename);

        switch (extension.toUpperCase()) {
            case "SVG":
                texture = readSVGAsTexture(filename,desiredWidth,desiredHeight);
                break;
            default:
                throw new IOException("Unsupported file format: " + extension);
        }

        return texture;
    }

    // Example usage
    /*public static void main(String[] args) {
        try {
            Texture texture = BroideryReader.readAsTexture("example.svg");
            // Process the texture as needed
        } catch (IOException e) {
            Gdx.app.error("BroideryReader", "Error reading file", e);
        }
    }*/
}
