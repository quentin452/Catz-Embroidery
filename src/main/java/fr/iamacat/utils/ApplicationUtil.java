package fr.iamacat.utils;

import processing.core.PApplet;

public class ApplicationUtil {
    public static void exitApplication(PApplet p) {
        Logger.getInstance().log(Logger.Project.Converter, Translator.getInstance().translate("closing_the_app"));
        Logger.getInstance().archiveLogs();
        if (p.getSurface().isStopped()) {
            p.exitActual();
        } else {
            p.dispose();
            p.exitActual();
        }
    }
}
