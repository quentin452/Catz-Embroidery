package fr.iamacat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Translator {
    private static Translator instance;
    private Map<String, Map<String, String>> translations;
    private String currentLanguage = "en";
    private final List<Translatable> translatables = new ArrayList<>();
    public static boolean isReloadingLanguage;
    private Translator() {
        loadTranslations();
        currentLanguage = Saving.loadLanguage();
        if (currentLanguage == null) {
            currentLanguage = "en";
        }
    }

    public static Translator getInstance() {
        if (instance == null) {
            instance = new Translator();
        }
        return instance;
    }

    private void loadTranslations() {
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/translations.json"), "UTF-8")) {
            Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
            translations = new Gson().fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void setLanguage(String language) {
        isReloadingLanguage = true;
        boolean needLanguageUpdate = !Objects.equals(currentLanguage, language);
        currentLanguage = language;
        Saving.saveLanguage(language);
        if (needLanguageUpdate) {
            List<Translatable> copy = new ArrayList<>(translatables);
            for (Translatable translatable : copy) {
                translatable.updateTranslations();
            }
        }
        isReloadingLanguage = false;
    }

    public void registerTranslatable(Translatable translatable) {
        translatables.add(translatable);
    }
    public String translate(String key) {
        Map<String, String> langTranslations = translations.getOrDefault(currentLanguage, translations.get("en"));
        return langTranslations.getOrDefault(key, key);
    }

}
