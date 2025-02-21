package fr.iamacat.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.*;

public class Logger {

    private static Logger instance;  // Instance unique du logger
    private File logFile;
    private BufferedWriter writer;

    public enum Project {
        Launcher,
        Converter,
        Editor,
    }

    // Constructeur privé pour empêcher la création d'instances multiples
    private Logger() {
        // Créer un dossier Logs s'il n'existe pas
        File logsDir = new File("Logs");
        if (!logsDir.exists()) {
            logsDir.mkdir();
        }

        // Créer un fichier log avec un timestamp
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        logFile = new File(logsDir, "log_" + timestamp + ".txt");

        try {
            writer = new BufferedWriter(new FileWriter(logFile));
            writer.write("### Lancement du jeu - " + timestamp + " ###\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Méthode statique pour récupérer l'instance unique du logger
    public static Logger getInstance() {
        if (instance == null) {
            instance = new Logger();  // Si l'instance n'existe pas, on la crée
        }
        return instance;
    }

    // Méthode pour ajouter un message dans le log
    public void log(Project project, String message) {
        String logMessage = "[" + project.name() + "] " + message;
        try {
            writer.write(logMessage + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Méthode pour fermer le writer
    public void closeWriter() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Méthode pour archiver les logs dans un fichier zip
    public void archiveLogs() {
        try {
            // Fermer le writer avant d'archiver
            closeWriter();

            // Créer un fichier zip dans le dossier Logs
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

            // Supprimer le fichier log original après l'archivage
            if (!logFile.delete()) {
                System.err.println("Failed to delete log file: " + logFile.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
