package fr.iamacat;

import fr.iamacat.utils.Logger;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PShape;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.embroider.PEmbroiderWriter;
import processing.event.MouseEvent;

import javax.swing.*;
import java.io.File;

public class PEmbroiderViewer extends PApplet {

    private PShape embroideryShape;
    private PGraphics pg;
    private PEmbroiderGraphics embroidery;

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.PEmbroiderViewer");
    }

    public void settings() {
        size(1280, 720);
    }

    public void setup() {
        pg = createGraphics(width, height);
        embroidery = new PEmbroiderGraphics(this, width, height);
        //loadEmbroideryFile();    // TODO
    }

    public void draw() {
        background(255);
        if (embroideryShape != null) {
            shape(embroideryShape, 10, 10);
        }
    }

    public void mouseClicked(MouseEvent event) {
        if (event.getButton() == LEFT) {
           // loadEmbroideryFile();    // TODO
        }
    }
    // TODO
    /*private void loadEmbroideryFile() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Open embroidery file");
        int userSelection = fileChooser.showOpenDialog(null);
        if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
            File fileToLoad = fileChooser.getSelectedFile();
            String filePath = fileToLoad.getAbsolutePath();
            String fileExtension = getFileExtension(filePath);

            switch (fileExtension) {
                case "svg":
                    embroideryShape = loadShape(filePath);
                    break;
                case "pes":
                    embroideryShape = loadPesFile(filePath);
                    break;
                default:
                    Logger.getInstance().log(Logger.Project.Viewer,"Unsupported file format: " + fileExtension);
                    break;
            }
        }
    }

    private PShape loadPesFile(String filePath) {
        PEmbroiderWriter.readPesFile(filePath, embroidery.polylines, embroidery.colors);
        PShape shape = createShape();
        shape.beginShape();
        for (int i = 0; i < embroidery.polylines.size(); i++) {
            shape.fill(embroidery.colors.get(i).getRGB());
            for (PVector point : embroidery.polylines.get(i)) {
                shape.vertex(point.x, point.y);
            }
        }
        shape.endShape();
        return shape;
    }*/

    private String getFileExtension(String filePath) {
        int lastIndex = filePath.lastIndexOf('.');
        if (lastIndex > 0) {
            return filePath.substring(lastIndex + 1).toLowerCase();
        }
        return "";
    }
}
