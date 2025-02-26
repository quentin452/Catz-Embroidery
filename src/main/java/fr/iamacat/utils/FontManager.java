package fr.iamacat.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

public class FontManager {
    public static BitmapFont generateFont(String fontFilePath, int size) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(fontFilePath));
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = size;  // Increase size for better quality
        parameter.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        parameter.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        parameter.genMipMaps = true; // Helps with smooth scaling
        BitmapFont font = generator.generateFont(parameter);
        generator.dispose(); // Dispose to avoid memory leaks
        return font;
    }
    public static Skin createCustomSkin(String fontPath, int size) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(fontPath));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = size;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.minFilter = Texture.TextureFilter.Linear;
        BitmapFont font = generator.generateFont(parameter);
        generator.dispose();

        Skin skin = new Skin(Gdx.files.internal("uiskins/cloud-form/skin/cloud-form-ui.json"));

        // Apply the font to various UI components
        skin.get(TextButton.TextButtonStyle.class).font = font;
        skin.get(Label.LabelStyle.class).font = font;
        skin.get(SelectBox.SelectBoxStyle.class).font = font;
        skin.get(SelectBox.SelectBoxStyle.class).listStyle.font = font; // For dropdown list items

        return skin;
    }
}
