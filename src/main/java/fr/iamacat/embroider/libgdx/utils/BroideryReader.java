package fr.iamacat.embroider.libgdx.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import org.embroideryio.embroideryio.EmbConstant;
import org.embroideryio.embroideryio.EmbPattern;
import org.embroideryio.embroideryio.EmbThread;
import org.embroideryio.embroideryio.PesReader;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
// TODO ADD PES READER
public class BroideryReader {

    private static Texture readSVGAsTexture(String filename, float desiredWidth, float desiredHeight) {
        List<BezierShape> shapes = readSVG(filename);
        // Create a Pixmap to draw the shapes
        Pixmap pixmap = new Pixmap((int) desiredWidth, (int) desiredHeight, Pixmap.Format.RGBA8888);
        // Draw the BezierShapes directly to the Pixmap with scaling
        for (BezierShape shape : shapes) {
            int color = shape.getColor();
            // Normalize RGB values to [0, 1] range
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            Color gdxColor = new Color(r, g, b, (color >> 24 & 0xFF) / 255f); // Fully opaque color (alpha = 1)
            // Set the color for drawing
            pixmap.setColor(gdxColor);
            // Render each Bezier curve
            for (BezierCurve curve : shape) {
                BezierUtil.renderBezierCurveToPixmap(pixmap, curve, gdxColor, 1);
            }
        }
        // Create the texture from the pixmap
        Texture texture = new Texture(pixmap);
        pixmap.dispose(); // Dispose the Pixmap to free resources
        return texture;
    }
    // THIS IS BUGGED
    private static Texture readPESAsTexture(String filename, float desiredWidth, float desiredHeight) {
        try {
            PesReader pesReader = new PesReader();
            EmbPattern pattern = new EmbPattern();

            // Charger le fichier
            try (FileInputStream inputStream = new FileInputStream(filename)) {
                pesReader.read(pattern, inputStream);
            }

            // Récupérer les dimensions réelles du motif
            float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < pattern.size(); i++) {
                float x = pattern.getX(i);
                float y = pattern.getY(i);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            float patternWidth = maxX - minX;
            float patternHeight = maxY - minY;

            // Éviter les divisions par zéro
            if (patternWidth <= 0 || patternHeight <= 0) {
                Gdx.app.error("BroideryReader", "Dimensions invalides.");
                return null;
            }

            // Conserver le ratio d'aspect
            float aspectRatio = patternWidth / patternHeight;
            float originalDesiredWidth = desiredWidth;
            float originalDesiredHeight = desiredHeight;
            if (desiredWidth / desiredHeight > aspectRatio) {
                desiredWidth = desiredHeight * aspectRatio;
            } else {
                desiredHeight = desiredWidth / aspectRatio;
            }

            // Créer le Pixmap avec la taille originale demandée
            Pixmap pixmap = new Pixmap((int) originalDesiredWidth, (int) originalDesiredHeight, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.CLEAR);
            pixmap.fill();

            int currentThreadIndex = 0;
            int prevX = -1, prevY = -1;

            for (int i = 0; i < pattern.size(); i++) {
                int command = pattern.getData(i);
                float x = pattern.getX(i);
                float y = pattern.getY(i);

                // Gestion des sauts/changements de couleur
                if (command == EmbConstant.JUMP || command == EmbConstant.TRIM) {
                    prevX = -1;
                    prevY = -1;
                    continue;
                }
                if (command == EmbConstant.COLOR_CHANGE || command == EmbConstant.STOP) {
                    currentThreadIndex = (currentThreadIndex + 1) % pattern.getThreadCount();
                    prevX = -1;
                    prevY = -1;
                    continue;
                }

                // Calcul des coordonnées centrées
                float offsetX = (originalDesiredWidth - desiredWidth) / 2;
                float offsetY = (originalDesiredHeight - desiredHeight) / 2;
                int screenX = (int) (offsetX + ((x - minX) / patternWidth) * desiredWidth);
                int screenY = (int) (offsetY + desiredHeight - ((y - minY) / patternHeight) * desiredHeight);

                // Clamping
                screenX = Math.max(0, Math.min(screenX, (int) originalDesiredWidth - 1));
                screenY = Math.max(0, Math.min(screenY, (int) originalDesiredHeight - 1));

                // Dessin
                EmbThread thread = pattern.getThread(currentThreadIndex);
                int color = thread.getColor();
                pixmap.setColor(
                        ((color >> 16) & 0xFF) / 255f,
                        ((color >> 8) & 0xFF) / 255f,
                        (color & 0xFF) / 255f,
                        (color >> 24 & 0xFF) / 255f
                );

                if (prevX != -1 && prevY != -1) {
                    pixmap.drawLine(prevX, prevY, screenX, screenY);
                } else {
                    pixmap.drawPixel(screenX, screenY);
                }
                prevX = screenX;
                prevY = screenY;
            }

            Texture texture = new Texture(pixmap);
            pixmap.dispose();
            return texture;

        } catch (Exception e) {
            Gdx.app.error("BroideryReader", "Erreur", e);
            return null;
        }
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
            case "PES":
                texture = readPESAsTexture(filename,desiredWidth,desiredHeight);
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
