package fr.iamacat.utils;

import com.google.gson.Gson;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Saving {
    private static final String FILE_PATH = "current_language.json";
    private static Gson gson = new Gson();

    public static void saveLanguage(String language) {
        Map<String, String> data = new HashMap<>();
        data.put("currentLanguage", language);

        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String loadLanguage() {
        Path path = Paths.get(FILE_PATH);
        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path));
                Map<String, String> data = gson.fromJson(json, Map.class);
                return data.get("currentLanguage");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("File does not exist: " + FILE_PATH);
        }
        return null;
    }
}
