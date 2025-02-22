package fr.iamacat.pembroider_editor;

import fr.iamacat.utils.Logger;
import fr.iamacat.utils.Translatable;
import fr.iamacat.utils.Translator;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PVector;
import processing.embroider.PEmbroiderGraphics;
import processing.event.MouseEvent;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
// TODO FIX , USING THE TOOL_FINELINE set a color and if you use an another tool it set an another color
public class Main extends PApplet implements Translatable {
    PEmbroiderGraphics E;
    private final int currentWidth = 1280;
    private final int currentHeight = 720;
    private boolean enableEscapeMenu = false , isDialogOpen = false;
    ArrayList<ArrayList<Layer>> undoStack = new ArrayList<>();
    ArrayList<ArrayList<Layer>> redoStack = new ArrayList<>();

    public void settings() {
        size(currentWidth, currentHeight);
    }

    public static void main(String[] args) {
        PApplet.main("fr.iamacat.pembroider_editor.Main");
    }
    int W = (int) (currentWidth/ 1.25);
    int H = currentHeight;

    int PX = 40;

    static final int LIN = 1;
    static final int PLY = 2;
    static final int TXT = 3;

    static final int TOOL_FREEHAND = 1;
    static final int TOOL_VERTEX = 2;
    static final int TOOL_PAINT = 3;
    static final int TOOL_FATPAINT = 4;
    static final int TOOL_FINELINE = 5;
    static final int TOOL_TEXT = 6;
    static final int TOOL_EDIT = 7;

    String[] tooltip = {
            "",
            Translator.getInstance().translate("tooltip_editor_FREEHAND"),
            Translator.getInstance().translate("tooltip_editor_VERTEX"),
            Translator.getInstance().translate("tooltip_editor_PAINT"),
            Translator.getInstance().translate("tooltip_editor_FATPAINT"),
            Translator.getInstance().translate("tooltip_editor_FINELINE"),
            Translator.getInstance().translate("tooltip_editor_TEXT"),
            Translator.getInstance().translate("tooltip_editor_EDIT"),
    };

    int tool = TOOL_FREEHAND;
    int currentLayer = 0;
    boolean needsUpdate = true;

    int editState = 0;
    int editI = 0;
    int editJ = 0;

    PApplet app = this;

    ArrayList<Layer> layers;

    ArrayList<PVector> polyBuff;

    PGraphics render;

    @Override
    public void updateTranslations() {

    }

    static class Element{
        int type;
        ArrayList<PVector> data;
        float paramF0;
        String paramS0;
        Element(int _type){
            type = _type;
            data = new ArrayList<>();
        }
    }
    class Layer implements Cloneable{
        int hatchMode = PEmbroiderGraphics.CONCENTRIC;
        int strokeMode = PEmbroiderGraphics.TANGENT;
        int hatchColor = color(0,0,255);
        int strokeColor = color(255,0,0);
        float hatchSpacing = 4;
        float strokeWeight = 10;
        boolean visible = true;
        boolean cull = true;
        ArrayList<Element> elements;
        //PGraphics mask;
        PGraphics render;

        PEmbroiderGraphics E;

        Layer(){
            E = new PEmbroiderGraphics(app,W,H);

            render = createGraphics(W,H);
            render.beginDraw();
            render.background(0);
            render.endDraw();

            elements = new ArrayList<>();
        }
        @Override
        protected Layer clone() throws CloneNotSupportedException {
            return (Layer) super.clone(); // Cast explicite
        }

    }

    void rasterizeLayer(Layer lay){
        PGraphics pg = lay.render;
        pg.beginDraw();
        pg.pushMatrix();
        pg.background(0);
        for (int i = 0; i < lay.elements.size(); i++){
            Element elt = lay.elements.get(i);
            if (elt.type == TXT){
                pg.fill(255);
                pg.noStroke();
                pg.textSize(elt.paramF0);
                pg.text(elt.paramS0,elt.data.get(0).x,elt.data.get(0).y);
            }else{
                if (elt.type == LIN){
                    pg.noFill();
                    pg.strokeWeight(elt.paramF0);
                    pg.stroke(255);
                }else if (elt.type == PLY){
                    pg.noStroke();
                    pg.fill(255);
                }
                pg.beginShape();
                for (int j = 0; j < elt.data.size(); j++){
                    PVector p = elt.data.get(j);
                    pg.vertex(p.x,p.y);
                }
                pg.endShape();
            }
        }
        pg.popMatrix();
        pg.endDraw();
    }

    void stitchLayer(int idx) {
        Layer lay = layers.get(idx);
        if (lay.elements.isEmpty()) {
            if (E != null) {
                E.clear();
            }
            return;
        }

        rasterizeLayer(lay);

        if (lay.cull) {
            if (lay.render.width > 0 && lay.render.height > 0) {
                lay.render.beginDraw();
                lay.render.blendMode(SUBTRACT);
                for (int i = idx + 1; i < layers.size(); i++) {
                    rasterizeLayer(layers.get(i));
                    if (layers.get(i).render.width > 0 && layers.get(i).render.height > 0) {
                        lay.render.image(layers.get(i).render, 0, 0);
                    }
                }
                lay.render.blendMode(BLEND);
                lay.render.endDraw();
            } else {
                Logger.getInstance().log(Logger.Project.Converter, "Invalid render dimensions for layer: " + idx);
            }
        }

        PEmbroiderGraphics E = getPEmbroiderGraphics(lay);
        if (E.width > 0 && E.height > 0 && lay.render.width > 0 && lay.render.height > 0) {
            E.image(lay.render, 0, 0);
        } else {
            Logger.getInstance().log(Logger.Project.Converter, "Invalid dimensions for PEmbroiderGraphics or lay.render in stitchLayer.");
        }
    }

    private static PEmbroiderGraphics getPEmbroiderGraphics(Layer lay) {
        PEmbroiderGraphics E = lay.E;
        E.clear();
        E.strokeWeight(lay.strokeWeight);
        E.hatchSpacing(lay.hatchSpacing);
        if (lay.strokeColor == 0){
            E.noStroke();
        }else{
            E.stroke((lay.strokeColor>>16)&255, (lay.strokeColor>>8)&255, (lay.strokeColor)&255);
        }
        if (lay.hatchColor == 0){
            E.noFill();
        }else{
            E.fill((lay.hatchColor>>16)&255, (lay.hatchColor>>8)&255, (lay.hatchColor)&255);
        }
        E.hatchMode(lay.hatchMode);
        E.strokeMode(lay.strokeMode);
        return E;
    }

    void visualize(PEmbroiderGraphics E){
        render.beginDraw();
        for (int i = 0; i < E.polylines.size(); i++) {
            render.stroke(app.red(E.colors.get(i)),app.green(E.colors.get(i)),app.blue(E.colors.get(i)));
            render.strokeWeight(1);
            render.beginShape();
            render.noFill();
            for (int j = 0; j < E.polylines.get(i).size(); j++) {
                PVector p0 = E.polylines.get(i).get(j);
                render.vertex(p0.x,p0.y);
            }
            render.endShape();
        }
        render.endDraw();
    }

    void newElementFromPolyBuff() {
        Layer lay = layers.get(currentLayer);
        Element elt = new Element(((tool == TOOL_PAINT) || (tool == TOOL_FATPAINT) || (tool == TOOL_FINELINE)) ? LIN : PLY);
        elt.data = new ArrayList<>(polyBuff);
        elt.paramF0 = (tool == TOOL_FATPAINT) ? 60 : (tool == TOOL_FINELINE ? 1 : 20);
        lay.elements.add(elt);
        polyBuff.clear();
        try {
            saveState();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        needsUpdate = true;
    }
    void removeElementFromPolyBuff() throws CloneNotSupportedException {
        Layer lay = layers.get(currentLayer);
        if (!lay.elements.isEmpty()) {
            saveState();
            lay.elements.remove(lay.elements.size() - 1);
            needsUpdate = true;
        } else {
            Logger.getInstance().log(Logger.Project.Editor,"No elements to remove from polyBuff.");
        }
    }

    void switchTool(int what){
        tool = what;
        polyBuff.clear();
        needsUpdate = true;
        mousePressed = false;
    }

    void writeOut(String path){
        PEmbroiderGraphics E = new PEmbroiderGraphics(app,W,H);
        E.setPath(path);
        for (Layer layer : layers) {
            E.polylines.addAll(layer.E.polylines);
            E.colors.addAll(layer.E.colors);
            E.cullGroups.addAll(layer.E.cullGroups);
        }
        E.optimize();
        E.endDraw();
    }

    void drawToolButton(int index, int toolType, String label) {
        float x = 0;
        float y = PX * index;

        fill(tool == toolType ? 180 : 255);
        stroke(0);
        strokeWeight(1);
        rect(x, y, PX, PX);
        fill(0);
        textSize(30);
        text(label, (float) PX / 2, y + (float) PX / 2 - 5);

        if (!mouseOnCanvas() && mousePressed && x <= mouseX && mouseX <= x + PX && y <= mouseY && mouseY <= y + PX) {
            switchTool(toolType);
        }
    }

    void drawToolsGui() {
        pushMatrix();
        textAlign(CENTER, CENTER);

        drawToolButton(0, TOOL_FREEHAND, "S");
        drawToolButton(1, TOOL_VERTEX, "Z");
        drawToolButton(2, TOOL_PAINT, "o");
        drawToolButton(3, TOOL_FATPAINT, "O");
        drawToolButton(4, TOOL_FINELINE, "FL");
        drawToolButton(5, TOOL_TEXT, "T");
        drawToolButton(6, TOOL_EDIT, "E");

        // Bouton Save
        float x = 0;
        float y = H - PX;
        fill(255);
        stroke(0);
        strokeWeight(1);
        rect(x, y, PX, PX);
        fill(0);
        textSize(14);
        text("Save", (float) PX / 2, y + (float) PX / 2 - 2);

        if (!mouseOnCanvas() && mousePressed && x <= mouseX && mouseX <= x + PX && y <= mouseY && mouseY <= y + PX) {
            saveFile();
            mousePressed = false;
        }

        popMatrix();
    }

    private void saveFile() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Enregistrer le fichier de broderie");
        javax.swing.filechooser.FileNameExtensionFilter pesFilter = new javax.swing.filechooser.FileNameExtensionFilter("Fichiers PES (*.pes)", "pes");
        javax.swing.filechooser.FileNameExtensionFilter svgFilter = new javax.swing.filechooser.FileNameExtensionFilter("Fichiers SVG (*.svg)", "svg");
        javax.swing.filechooser.FileNameExtensionFilter dstFilter = new javax.swing.filechooser.FileNameExtensionFilter("Fichiers DST (*.dst)", "dst");
        fileChooser.addChoosableFileFilter(pesFilter);
        fileChooser.addChoosableFileFilter(svgFilter);
        fileChooser.addChoosableFileFilter(dstFilter);
        fileChooser.setFileFilter(pesFilter);
        int userSelection = fileChooser.showSaveDialog(null);
        if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.contains(".")) {
                if (fileChooser.getFileFilter() == pesFilter) {
                    filePath += ".pes";
                } else if (fileChooser.getFileFilter() == svgFilter) {
                    filePath += ".svg";
                } else if (fileChooser.getFileFilter() == dstFilter) {
                    filePath += ".dst";
                }
                fileToSave = new File(filePath);
            }
            Logger.getInstance().log(Logger.Project.Editor, "Enregistrer le fichier sous : " + fileToSave.getAbsolutePath());
            javax.swing.JOptionPane.showMessageDialog(null, "Optimisation de l'ordre des points et enregistrement du fichier, cela peut prendre un certain temps...");
            writeOut(fileToSave.getAbsolutePath());
            javax.swing.JOptionPane.showMessageDialog(null, "Fichier de broderie enregistré avec succès !");
        } else {
            javax.swing.JOptionPane.showMessageDialog(null, "Enregistrement annulé par l'utilisateur.");
        }
    }

    void drawLayersGui(){
        int ww = width-PX-W;
        pushStyle();
        pushMatrix();
        translate(PX+W,0);

        float oy = 0;
        for (int i = 0; i < layers.size(); i++){
            boolean clicked = false;
            Layer lay = layers.get(i);
            fill(i == currentLayer ? 180 : 255);
            stroke(0);
            strokeWeight(1);
            rect(0,oy,ww,50);
            rasterizeLayer(layers.get(i));
            image(layers.get(i).render,4,oy+4,42,42);


            fill((lay.hatchColor>>16)&255, (lay.hatchColor>>8)&255, (lay.hatchColor)&255);
            rect(50,oy+24,20,20);

            if (!mouseOnCanvas() && mousePressed && PX+W+50 <= mouseX && mouseX <= PX+W+70 && oy+24 <= mouseY && mouseY <= oy+44){
                java.awt.Color col = javax.swing.JColorChooser.showDialog(
                        null,
                        "Select hatch color",
                        new java.awt.Color((lay.hatchColor>>16)&255, (lay.hatchColor>>8)&255, (lay.hatchColor)&255)
                );
                if (col != null){
                    lay.hatchColor = color(col.getRed(),col.getGreen(),col.getBlue());
                }
                String rawInp = javax.swing.JOptionPane.showInputDialog("Enter hatch spacing",lay.hatchSpacing);
                if (rawInp != null){
                    lay.hatchSpacing= Float.parseFloat(rawInp);

                    Object[] options = {"Parallel","Concentric"};
                    int op = javax.swing.JOptionPane.showOptionDialog(null, "Select hatch mode", "Hatch Mode",
                            javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE,
                            null, options, lay.hatchMode==PEmbroiderGraphics.PARALLEL?"Parallel":"Concentric");
                    if (op == 0) {
                        lay.hatchMode = PEmbroiderGraphics.PARALLEL;
                    }else if (op == 1) {
                        lay.hatchMode = PEmbroiderGraphics.CONCENTRIC;
                    }
                }
                needsUpdate = true;
                mousePressed = false;
                clicked = true;
            }

            fill(255);
            rect(70,oy+24,20,20);
            strokeWeight(4);
            stroke((lay.strokeColor>>16)&255, (lay.strokeColor>>8)&255, (lay.strokeColor)&255);
            line(74,oy+28,86,oy+40);

            if (!mouseOnCanvas() && mousePressed && PX+W+70 <= mouseX && mouseX <= PX+W+90 && oy+24 <= mouseY && mouseY <= oy+44){
                java.awt.Color col = javax.swing.JColorChooser.showDialog(
                        null,
                        "Select stroke color",
                        new java.awt.Color((lay.strokeColor>>16)&255, (lay.strokeColor>>8)&255, (lay.strokeColor)&255)
                );
                if (col != null){
                    lay.strokeColor = color(col.getRed(),col.getGreen(),col.getBlue());

                }
                String rawInp = javax.swing.JOptionPane.showInputDialog("Enter stroke weight",""+lay.strokeWeight);
                if (rawInp != null && !rawInp.isEmpty()){

                    lay.strokeWeight= Float.parseFloat(rawInp);

                    Object[] options = {"Tangent","Perpendicular"};
                    int op = javax.swing.JOptionPane.showOptionDialog(null, "Select stroke mode", "Stroke Mode",
                            javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE,
                            null, options, lay.strokeMode==PEmbroiderGraphics.TANGENT?"Tangent":"Perpendicular");
                    if (op == 0) {
                        lay.strokeMode = PEmbroiderGraphics.TANGENT;
                    }else if (op == 1) {
                        lay.strokeMode = PEmbroiderGraphics.PERPENDICULAR;
                    }
                }
                needsUpdate = true;
                mousePressed = false;
                clicked = true;
            }

            fill(255);
            stroke(0);
            strokeWeight(1);
            rect(90,oy+24,20,20);
            E.circle(98,oy+34,8);
            if (!lay.cull){
                noFill();
            }
            E.circle(103,oy+34,8);
            if (!mouseOnCanvas() && mousePressed && PX+W+90 <= mouseX && mouseX <= PX+W+110 && oy+24 <= mouseY && mouseY <= oy+44){
                lay.cull = !lay.cull;
                needsUpdate = true;
                mousePressed = false;
            }


            fill(255);
            stroke(0);
            strokeWeight(1);
            rect(110,oy+24,20,20);
            fill(0);
            textSize(12);
            textAlign(CENTER,CENTER);
            if (lay.visible){
                text("o",121,oy+32);
                text("<",116,oy+32);
                text(">",126,oy+32);
            }else{
                text("=",121,oy+32);
                text("-",116,oy+32);
                text("-",126,oy+32);
            }
            if (!mouseOnCanvas() && mousePressed && PX+W+110 <= mouseX && mouseX <= PX+W+130 && oy+24 <= mouseY && mouseY <= oy+44){
                lay.visible = !lay.visible;
                needsUpdate = true;
                mousePressed = false;
            }

            fill(255);
            stroke(0);
            strokeWeight(1);
            rect(130,oy+24,20,20);
            fill(0);
            textSize(12);
            textAlign(CENTER,CENTER);
            text("X",141,oy+33);

            if (!mouseOnCanvas() && mousePressed && PX+W+130 <= mouseX && mouseX <= PX+W+150 && oy+24 <= mouseY && mouseY <= oy+44){

                if (layers.size() <= 1){
                    javax.swing.JOptionPane.showMessageDialog(null, "Cannot delete the only layer!");
                    needsUpdate = true;
                    mousePressed = false;
                }else{
                    if (currentLayer == i){
                        if (currentLayer > 0){
                            currentLayer --;
                        }else{
                            currentLayer ++;
                        }
                    }
                    layers.remove(i);
                    while (currentLayer >= layers.size()){
                        currentLayer--;
                    }
                    needsUpdate = true;
                    mousePressed = false;
                    popMatrix();
                    popStyle();
                    return;
                }
            }

            fill(0);
            noStroke();
            textSize(12);
            textAlign(LEFT,BASELINE);
            text(Translator.getInstance().translate("layer") + " " +i,50,oy+14);

            if (!clicked && !mouseOnCanvas() && mousePressed && PX+W <= mouseX && mouseX <= width && oy <= mouseY && mouseY <= oy+50){
                currentLayer = i;
            }

            oy +=50;
        }

        fill(255);
        stroke(0);
        strokeWeight(1);
        rect(0,oy,ww,30);
        fill(0);
        noStroke();
        textAlign(CENTER,CENTER);
        textSize(18);
        text("+", (float) ww /2,oy+13);
        if (!mouseOnCanvas() && mousePressed && PX+W <= mouseX && mouseX <= width && oy <= mouseY && mouseY <= oy+30){
            Layer lay = new Layer();
            lay.strokeColor = color(random(255),random(255),random(255));
            lay.hatchColor = color(random(255),random(255),random(255));
            layers.add(lay);
            currentLayer = layers.size()-1;
            mousePressed = false;
        }

        popMatrix();
        popStyle();
    }
    void drawGui(){
        drawToolsGui();
        drawLayersGui();
        pushStyle();
        fill(200);
        stroke(0);
        strokeWeight(1);
        rect(0,H,width,height-H);
        fill(0);
        noStroke();
        textAlign(LEFT,BOTTOM);
        textSize(12);
        text(tooltip[tool%tooltip.length],45,height-2);
        popStyle();
    }
    void drawEditMode(){
        render.beginDraw();
        render.clear();
        render.background(255);

        Layer lay = layers.get(currentLayer);
        for (int i = 0; i < lay.elements.size(); i++){
            Element elt = lay.elements.get(i);

            render.noFill();
            render.stroke(0);
            render.beginShape();
            for (int j = 0; j < elt.data.size(); j++){
                render.vertex(elt.data.get(j).x,elt.data.get(j).y);
            }

            render.endShape();
            for (int j = 0; j < elt.data.size(); j++){
                boolean isSel;
                if (editState == 0){
                    isSel = new PVector(mouseX-PX,mouseY).dist(elt.data.get(j))<10;
                }else{
                    isSel = (i == editI) && (j == editJ);
                }
                if (isSel){
                    render.fill(0,0,255);
                    render.rect(elt.data.get(j).x-4,elt.data.get(j).y-4,8,8);
                    if (editState == 0 && mousePressed){
                        editState = 1;
                        editI = i;
                        editJ = j;
                    }
                }else{
                    render.noFill();
                    render.rect(elt.data.get(j).x-2,elt.data.get(j).y-2,4,4);
                }

            }
        }
        if (editState == 1){
            lay.elements.get(editI).data.get(editJ).x = mouseX-PX;
            lay.elements.get(editI).data.get(editJ).y = mouseY;
            if (!mousePressed){
                editState = 0;
            }
        }
        render.endDraw();
    }

    public void setup() {
        Translator.getInstance().registerTranslatable(this);
        surface.setResizable(true);
        render = createGraphics(W, H);
        layers = new ArrayList<>();
        layers.add(new Layer());
        polyBuff = new ArrayList<>();
        E = new PEmbroiderGraphics(this, width, height);
    }


    boolean mouseOnCanvas(){
        return PX < mouseX && mouseX < PX+W && 0 < mouseY && mouseY < H;
    }

    private boolean hasEmbroideryRendered() {
        for (Layer layer : layers) {
            if (!layer.elements.isEmpty()) {
                return true;
            }
        }
        return false;
    }
    public void draw(){
        background(100);
        enableEscapeMenu = hasEmbroideryRendered();
        if (tool == TOOL_EDIT){
            drawEditMode();
        }else if (needsUpdate){
            render.beginDraw();
            render.clear();
            render.background(255);
            render.endDraw();
            for (int i = layers.size()-1; i >= 0; i--){
                if (layers.get(i).visible){
                    stitchLayer(i);
                    visualize(layers.get(i).E);
                }
            }
            needsUpdate = false;

        }

        image(render,PX,0);

        stroke(0);
        noFill();
        strokeWeight(1);
        rect(PX,0,W,H);
        beginShape();
        for (PVector pVector : polyBuff) {
            vertex(pVector.x + PX, pVector.y);
            rect(pVector.x - 2 + PX, pVector.y - 2, 4, 4);
        }
        vertex(mouseX,mouseY);
        endShape();

        drawGui();

        if (mouseOnCanvas()){
            if (tool == TOOL_FREEHAND || tool == TOOL_PAINT || tool == TOOL_FATPAINT || tool == TOOL_FINELINE){
                if (mousePressed){
                    PVector p = new PVector(mouseX-PX,mouseY);
                    if (polyBuff.isEmpty() || polyBuff.get(polyBuff.size()-1).dist(p) > 10){
                        polyBuff.add(p);
                    }
                }else if (polyBuff.size() > 2){
                    newElementFromPolyBuff();
                }
            }
        }

    }
    public void mousePressed(MouseEvent evt){
        if (mouseOnCanvas()){
            if (tool == TOOL_VERTEX){
                if (evt.getCount() == 1){
                    polyBuff.add(new PVector(mouseX-PX,mouseY));
                }else{
                    newElementFromPolyBuff();
                }
            }else if (tool == TOOL_TEXT){
                String txt = javax.swing.JOptionPane.showInputDialog("Enter Text");
                String rawInp = javax.swing.JOptionPane.showInputDialog("Enter Text Size",128);
                if (txt != null && rawInp != null){
                    float siz = Float.parseFloat(rawInp);
                    Layer lay = layers.get(currentLayer);
                    Element elt = new Element(TXT);
                    elt.paramS0 = txt;
                    elt.paramF0 = siz;
                    polyBuff.add(new PVector(mouseX-PX,mouseY));
                    elt.data = new ArrayList<>(polyBuff);
                    lay.elements.add(elt);
                }
                polyBuff.clear();
                needsUpdate = true;
            }
        }
    }
    @Override
    public void keyPressed() {
        if (tool == TOOL_EDIT && key == DELETE) {
            try {
                deleteSelectedPoint();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        } else if (keyCode == BACKSPACE && keyEvent.isControlDown()) {
            try {
                removeElementFromPolyBuff();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void deleteSelectedPoint() throws CloneNotSupportedException {
        if (editState == 1) {
            Layer lay = layers.get(currentLayer);
            Element elt = lay.elements.get(editI);
            if (elt.data.size() > 1) {  // Avoid To Remove the last point
                saveState();
                elt.data.remove(editJ);
                editState = 0;
                needsUpdate = true;
            } else {
                Logger.getInstance().log(Logger.Project.Editor,"Cannot delete the last point in the element.");
            }
        } else {
            Logger.getInstance().log(Logger.Project.Editor,"Edit state is not 1. No point to delete.");
        }
    }

    void saveState() throws CloneNotSupportedException {
        redoStack.clear();
        undoStack.add(copyLayers());
    }

    ArrayList<Layer> copyLayers() throws CloneNotSupportedException {
        ArrayList<Layer> copy = new ArrayList<>();
        for (Layer l : layers) {
            copy.add(l.clone());
        }
        return copy;
    }

    private void showExitDialog() {
        if (!enableEscapeMenu) {
            exitApplication();
            return;
        }
        String[] options = {Translator.getInstance().translate("save_and_quit"), Translator.getInstance().translate("exit_without_save"),Translator.getInstance().translate("cancel")};
        int option = JOptionPane.showOptionDialog(
                (java.awt.Component) this.getSurface().getNative(),
                Translator.getInstance().translate("save_sentence_1"),
                Translator.getInstance().translate("confirm_closing"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (option == 0) {
            saveFileAndExit();
        } else if (option == 1) {
            exitApplication();
        } else {
            isDialogOpen = false;
        }
    }

    private void saveFileAndExit() {
        if (!isDialogOpen) {
            isDialogOpen = true;
            saveFile();
            exitApplication();
        }
    }

    private void exitApplication() {
        Logger.getInstance().log(Logger.Project.Converter, Translator.getInstance().translate("closing_the_app"));
        Logger.getInstance().archiveLogs();
        if (this.surface.isStopped()) {
            this.exitActual();
        } else if (this.looping) {
            this.finished = true;
            this.exitCalled = true;
        } else if (!this.looping) {
            this.dispose();
            this.exitActual();
        }
    }

    @Override
    public void exit() {
        showExitDialog();
    }
}
