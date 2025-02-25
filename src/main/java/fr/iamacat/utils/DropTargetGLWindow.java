package fr.iamacat.utils;

import com.jogamp.newt.opengl.GLWindow;

import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.dnd.*;
import java.io.*;
import java.util.TooManyListenersException;

import static processing.controlP5.ControlP5Legacy.println;

public class DropTargetGLWindow implements DropTargetListener, Serializable {
    @Serial
    private static final long serialVersionUID = -6283860791671019047L;

    private GLWindow glWindow;
    private DropTargetListener dtListener;
    private boolean active;
    private FlavorMap flavorMap;
    private int actions;

    public DropTargetGLWindow(GLWindow glWindow, int ops, DropTargetListener dtl, boolean act, FlavorMap fm) {
        if (glWindow == null) {
            throw new IllegalArgumentException("GLWindow cannot be null");
        }

        this.glWindow = glWindow;
        setDefaultActions(ops);

        if (dtl != null) {
            this.dtListener = dtl;
        }

        if (fm != null) {
            this.flavorMap = fm;
        } else {
            this.flavorMap = SystemFlavorMap.getDefaultFlavorMap();
        }

        this.active = act;
        println("DropTargetGLWindow initialized with GLWindow, actions: " + ops + ", active: " + act);
    }

    public DropTargetGLWindow(GLWindow glWindow, int ops, DropTargetListener dtl, boolean act) {
        this(glWindow, ops, dtl, act, null);
    }

    public DropTargetGLWindow(GLWindow glWindow) {
        this(glWindow, DnDConstants.ACTION_COPY_OR_MOVE, null, true, null);
    }

    public synchronized void setGLWindow(GLWindow glWindow) {
        if (this.glWindow == glWindow) return;

        this.glWindow = glWindow;
        if (glWindow != null) {
            // Associate any GLWindow-specific drop handling logic here
        }
        println("GLWindow set: " + glWindow);
    }

    public synchronized GLWindow getGLWindow() {
        return glWindow;
    }

    public void setDefaultActions(int ops) {
        this.actions = ops & (DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_REFERENCE);
        println("Default actions set: " + actions);
    }

    public int getDefaultActions() {
        return actions;
    }

    public synchronized void setActive(boolean isActive) {
        if (isActive != active) {
            active = isActive;
            println("Active status set to: " + active);
        }
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void addDropTargetListener(DropTargetListener dtl) throws TooManyListenersException {
        if (dtl == null) return;
        if (dtListener != null) {
            throw new TooManyListenersException();
        }
        dtListener = dtl;
        println("DropTargetListener added: " + dtl);
    }

    public synchronized void removeDropTargetListener(DropTargetListener dtl) {
        if (dtListener != null && dtListener.equals(dtl)) {
            dtListener = null;
            println("DropTargetListener removed: " + dtl);
        }
    }

    @Override
    public synchronized void dragEnter(DropTargetDragEvent dtde) {
        println("dragEnter triggered: " + dtde);
        if (active && dtListener != null) {
            dtListener.dragEnter(dtde);
        }
    }

    @Override
    public synchronized void dragOver(DropTargetDragEvent dtde) {
        println("dragOver triggered: " + dtde);
        if (active && dtListener != null) {
            dtListener.dragOver(dtde);
        }
    }

    @Override
    public synchronized void dropActionChanged(DropTargetDragEvent dtde) {
        println("dropActionChanged triggered: " + dtde);
        if (active && dtListener != null) {
            dtListener.dropActionChanged(dtde);
        }
    }

    @Override
    public synchronized void dragExit(DropTargetEvent dte) {
        println("dragExit triggered: " + dte);
        if (active && dtListener != null) {
            dtListener.dragExit(dte);
        }
    }

    @Override
    public synchronized void drop(DropTargetDropEvent dtde) {
        println("drop triggered: " + dtde);
        if (active && dtListener != null) {
            dtListener.drop(dtde);
        }
    }

    public FlavorMap getFlavorMap() {
        return flavorMap;
    }

    public void setFlavorMap(FlavorMap fm) {
        this.flavorMap = fm != null ? fm : SystemFlavorMap.getDefaultFlavorMap();
        println("FlavorMap set: " + flavorMap);
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        s.writeObject(dtListener);
    }

    private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
        s.defaultReadObject();
        dtListener = (DropTargetListener) s.readObject();
    }
}
