package fr.iamacat.embroider;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import fr.iamacat.utils.PConstants;
import javafx.scene.Scene;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class PEmbroiderReader {
    public static class PES {

        public static void read(String filename, ArrayList<ArrayList<Vector2>> polylines, ArrayList<Integer> colors, int canvasWidth, int canvasHeight) throws IOException {
            try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
                byte[] header = new byte[8];
                dis.readFully(header);
                System.out.println("En-tête PES détecté : " + new String(header));

                dis.skipBytes(8); // Skip to data section offset
                int dataOffset = readIntLittleEndian(dis);
                dis.skipBytes(dataOffset - 16); // Skip to the data section

                ArrayList<Vector2> currentPolyline = new ArrayList<>();
                int currentColor = -1;

                while (dis.available() >= 3) {
                    int b1 = dis.readUnsignedByte();
                    int b2 = dis.readUnsignedByte();
                    int command = dis.readUnsignedByte();

                    if (command == 0x80) {
                        currentColor = b1;
                        colors.add(currentColor);

                        if (!currentPolyline.isEmpty()) {
                            polylines.add(new ArrayList<>(currentPolyline));
                            currentPolyline.clear();
                        }
                    } else {
                        short x = (short) ((b1 & 0xFF) | ((b2 & 0x03) << 8));
                        short y = (short) (((b2 >> 2) & 0x3F) | ((command & 0x0F) << 6));
                        if ((b2 & 0x80) != 0) x = (short) -x;
                        if ((command & 0x10) != 0) y = (short) -y;

                        currentPolyline.add(new Vector2(x, y));
                    }
                }

                if (!currentPolyline.isEmpty()) {
                    polylines.add(currentPolyline);
                    colors.add(currentColor);
                }

            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException("Erreur de lecture du fichier PES : " + e.getMessage(), e);
            }
        }

        private static int readIntLittleEndian(DataInputStream dis) throws IOException {
            byte[] bytes = new byte[4];
            dis.readFully(bytes);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
    }
    public static void normalizePolylines(ArrayList<ArrayList<Vector2>> polylines, int canvasWidth, int canvasHeight) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        // Trouver les coordonnées minimales et maximales
        for (ArrayList<Vector2> polyline : polylines) {
            for (Vector2 point : polyline) {
                if (point.x < minX) minX = point.x;
                if (point.y < minY) minY = point.y;
                if (point.x > maxX) maxX = point.x;
                if (point.y > maxY) maxY = point.y;
            }
        }

        // Calculer les facteurs de mise à l'échelle
        float scaleX = canvasWidth / (maxX - minX);
        float scaleY = canvasHeight / (maxY - minY);
        float scale = Math.min(scaleX, scaleY);

        // Normaliser les coordonnées
        for (ArrayList<Vector2> polyline : polylines) {
            for (Vector2 point : polyline) {
                point.x = (point.x - minX) * scale;
                point.y = (point.y - minY) * scale;
            }
        }
    }
    // Méthode pour lire un fichier à partir du nom de fichier et retourner les polylines et couleurs
    public static EmbroideryData read(String filename, int canvasWidth, int canvasHeight) {
        // Séparer le nom du fichier et son extension
        String[] tokens = filename.split("\\.(?=[^\\.]+$)");
        System.out.println("Reading file: " + filename);
        System.out.println("Base name: " + tokens[0]);
        System.out.println("Extension: " + tokens[1]);

        // Créer des listes pour stocker les données lues (polylines et couleurs)
        ArrayList<ArrayList<Vector2>> polylines = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        try {
            // Lire le fichier en fonction de son extension
            if (tokens[1].equalsIgnoreCase("DST")) {
                //DST.read(filename, polylines, colors);
            } else if (tokens[1].equalsIgnoreCase("EXP")) {
                //EXP.read(filename, polylines, colors);
            } else if (tokens[1].equalsIgnoreCase("VP3")) {
                //VP3.read(filename, polylines, colors);
            } else if (tokens[1].equalsIgnoreCase("PES")) {
                PES.read(filename, polylines, colors,canvasWidth,canvasHeight);
            } else {
                System.out.println("Unsupported format: " + tokens[1]);
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }

        normalizePolylines(polylines, canvasWidth, canvasHeight);
        return new EmbroideryData(polylines, colors);
    }

    public static Pixmap createImageFromPolylines(ArrayList<ArrayList<Vector2>> polylines, ArrayList<Integer> colors, int width, int height) {
        // Create a Pixmap with the given width and height
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGB888);

        // Clear the image to make the background white
        pixmap.setColor(Color.WHITE);
        pixmap.fill();  // Fill the pixmap with the background color (white)

        // Draw the polylines on the pixmap
        for (int i = 0; i < polylines.size(); i++) {
            ArrayList<Vector2> polyline = polylines.get(i);
            int colorInt = colors.get(i); // Get the color for the polyline

            // Set the drawing color for the current polyline
            pixmap.setColor(new Color((colorInt >> 16) & 0xFF, (colorInt >> 8) & 0xFF, colorInt & 0xFF, 1));

            // Draw the polyline on the pixmap
            for (int j = 0; j < polyline.size() - 1; j++) {
                Vector2 p1 = polyline.get(j);
                Vector2 p2 = polyline.get(j + 1);
                drawLineOnPixmap(pixmap, p1, p2);
            }
        }

        return pixmap;
    }

    private static void drawLineOnPixmap(Pixmap pixmap, Vector2 p1, Vector2 p2) {
        int x0 = (int) p1.x;
        int y0 = (int) p1.y;
        int x1 = (int) p2.x;
        int y1 = (int) p2.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = (x0 < x1) ? 1 : -1;
        int sy = (y0 < y1) ? 1 : -1;
        int err = dx - dy;

        while (true) {
            pixmap.drawPixel(x0, y0);

            if (x0 == x1 && y0 == y1) break;

            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    // Classe pour encapsuler les données d'embroidery
    public static class EmbroideryData {
        private ArrayList<ArrayList<Vector2>> polylines;
        private ArrayList<Integer> colors;

        public EmbroideryData(ArrayList<ArrayList<Vector2>> polylines, ArrayList<Integer> colors) {
            this.polylines = polylines;
            this.colors = colors;
        }

        public ArrayList<ArrayList<Vector2>> getPolylines() {
            return polylines;
        }

        public ArrayList<Integer> getColors() {
            return colors;
        }
    }
}
