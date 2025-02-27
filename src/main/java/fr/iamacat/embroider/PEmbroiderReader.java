package fr.iamacat.embroider;

import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import static processing.controlP5.ControlP5Legacy.println;

public class PEmbroiderReader {
    public static class PES {

        public static void read(String filename, ArrayList<ArrayList<PVector>> polylines, ArrayList<Integer> colors, int canvasWidth, int canvasHeight) throws IOException {
            try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
                byte[] header = new byte[8];
                dis.readFully(header);
                System.out.println("En-tête PES détecté : " + new String(header));

                dis.skipBytes(8); // Skip to data section offset
                int dataOffset = readIntLittleEndian(dis);
                dis.skipBytes(dataOffset - 16); // Skip to the data section

                ArrayList<PVector> currentPolyline = new ArrayList<>();
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

                        currentPolyline.add(new PVector(x, y));
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
    public static void normalizePolylines(ArrayList<ArrayList<PVector>> polylines, int canvasWidth, int canvasHeight) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        // Trouver les coordonnées minimales et maximales
        for (ArrayList<PVector> polyline : polylines) {
            for (PVector point : polyline) {
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
        for (ArrayList<PVector> polyline : polylines) {
            for (PVector point : polyline) {
                point.x = (point.x - minX) * scale;
                point.y = (point.y - minY) * scale;
            }
        }
    }
    // Méthode pour lire un fichier à partir du nom de fichier et retourner les polylines et couleurs
    public static EmbroideryData read(String filename, int canvasWidth, int canvasHeight) {
        // Séparer le nom du fichier et son extension
        String[] tokens = filename.split("\\.(?=[^\\.]+$)");
        println("Reading file: " + filename);
        println("Base name: " + tokens[0]);
        println("Extension: " + tokens[1]);

        // Créer des listes pour stocker les données lues (polylines et couleurs)
        ArrayList<ArrayList<PVector>> polylines = new ArrayList<>();
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
                println("Unsupported format: " + tokens[1]);
            }
        } catch (IOException e) {
            println("Error reading file: " + e.getMessage());
        }

        normalizePolylines(polylines, canvasWidth, canvasHeight);
        return new EmbroideryData(polylines, colors);
    }

    public static PImage createImageFromPolylines(ArrayList<ArrayList<PVector>> polylines, ArrayList<Integer> colors, int width, int height, PApplet p) {
        PImage image = new PImage(width, height, PApplet.RGB);
        image.loadPixels();

        // Effacer l'image pour obtenir un fond blanc
        for (int i = 0; i < image.pixels.length; i++) {
            image.pixels[i] = p.color(255);
        }

        // Dessiner les polylines sur l'image
        for (int i = 0; i < polylines.size(); i++) {
            ArrayList<PVector> polyline = polylines.get(i);
            int color = colors.get(i);

            for (int j = 0; j < polyline.size() - 1; j++) {
                PVector p1 = polyline.get(j);
                PVector p2 = polyline.get(j + 1);
                drawLineOnImage(image, p1, p2, color, width, height);
            }
        }

        image.updatePixels();
        return image;
    }

    private static void drawLineOnImage(PImage image, PVector p1, PVector p2, int color, int width, int height) {
        int x1 = (int) PApplet.map(p1.x, 0, width, 0, image.width);
        int y1 = (int) PApplet.map(p1.y, 0, height, 0, image.height);
        int x2 = (int) PApplet.map(p2.x, 0, width, 0, image.width);
        int y2 = (int) PApplet.map(p2.y, 0, height, 0, image.height);

        // Algorithme de tracé de ligne de Bresenham
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x1 >= 0 && x1 < image.width && y1 >= 0 && y1 < image.height) {
                image.set(x1, y1, color);
            }

            if (x1 == x2 && y1 == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }


    // Classe pour encapsuler les données d'embroidery
    public static class EmbroideryData {
        private ArrayList<ArrayList<PVector>> polylines;
        private ArrayList<Integer> colors;

        public EmbroideryData(ArrayList<ArrayList<PVector>> polylines, ArrayList<Integer> colors) {
            this.polylines = polylines;
            this.colors = colors;
        }

        public ArrayList<ArrayList<PVector>> getPolylines() {
            return polylines;
        }

        public ArrayList<Integer> getColors() {
            return colors;
        }
    }
}
