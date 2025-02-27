package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

public class FontManager {
    // Méthode modifiée dans FontManager
    public static Skin createCustomSkin(String fontPath, int size, Skin existingSkin) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(fontPath));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = size;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.minFilter = Texture.TextureFilter.Linear;
        BitmapFont font = generator.generateFont(parameter);
        generator.dispose();

        // Appliquer la police au skin existant
        existingSkin.get(TextButton.TextButtonStyle.class).font = font;
        existingSkin.get(Label.LabelStyle.class).font = font;
        existingSkin.get(SelectBox.SelectBoxStyle.class).font = font;
        existingSkin.get(SelectBox.SelectBoxStyle.class).listStyle.font = font; // Pour les éléments de la liste déroulante

        return existingSkin;
    }

}
