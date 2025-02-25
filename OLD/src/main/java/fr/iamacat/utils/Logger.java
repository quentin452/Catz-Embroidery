package fr.iamacat.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.*;

import static processing.controlP5.ControlP5Legacy.println;

public class Logger {

    private static Logger instance;
    private File logFile;
    private BufferedWriter writer;
    private boolean isClosed = false;

    public enum Project {
        Launcher,
        Converter,
        Editor,
        Viewer,
        Embroidery,
        NONE,
    }

    private Logger() {
        File logsDir = new File("Logs");
        if (!logsDir.exists()) {
            logsDir.mkdir();
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        logFile = new File(logsDir, "log_" + timestamp + ".txt");

        try {
            writer = new BufferedWriter(new FileWriter(logFile));
            writer.write("### Lancement du jeu - " + timestamp + " ###\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Logger getInstance() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }

    public synchronized void log(Project project, String message) {
        if (isClosed) {
            return;
        }
        String logMessage = "[" + project.name() + "] " + message;
        println(logMessage);
        try {
            writer.write(logMessage + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void closeWriter() {
        if (isClosed) {
            return;
        }
        try {
            if (writer != null) {
                writer.close();
                isClosed = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void archiveLogs() {
        closeWriter();

        try {
            String zipFileName = "Logs/logs_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".zip";
            File zipFile = new File(zipFileName);
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                try (FileInputStream fis = new FileInputStream(logFile)) {
                    ZipEntry zipEntry = new ZipEntry(logFile.getName());
                    zos.putNextEntry(zipEntry);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) >= 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
            }
            logFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
