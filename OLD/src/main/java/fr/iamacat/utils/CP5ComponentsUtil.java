package fr.iamacat.utils;

import processing.controlP5.*;

import java.util.function.Consumer;

public class CP5ComponentsUtil {
    public static String hoverText = "";
    public static boolean showTooltip = false;

    public static Button createButton(ControlP5 cp5, float x, float y, int width, int height, String label, boolean translated) {
        Button button = cp5.addButton(label)
                .setPosition(x, y)
                .setSize(width, height);
        if (!label.isEmpty()) {
            if (!translated) {
                button.setLabel(label);
            } else {
                button.setLabel(Translator.getInstance().translate(label));
            }
        }
        return button;
    }
    public static Button createActionButton(ControlP5 cp5, float x, float y, int width, int height,
                                            String label, Runnable action) {
        Button button = createButton(cp5, x, y, width, height, label, true);
        button.onClick(event -> action.run());
        return button;
    }

    public static DropdownList createDropdownList(
            ControlP5 cp5,
            String name,
            int x,
            int y,
            int width,
            int height,
            String[] items,
            String label,
            boolean translated,
            Consumer<Integer> onChange) {

        DropdownList ddl = cp5.addDropdownList(name)
                .setPosition(x, y)
                .setSize(width, height)
                .addItems(items);
        if (!label.isEmpty()) {
            if (!translated) {
                ddl.setLabel(label);
            } else {
                ddl.setLabel(Translator.getInstance().translate(label));
            }
        }
        ddl.onChange(e -> {
            int index = (int) e.getController().getValue();
            if (index >= 0 && index < items.length) {
                onChange.accept(index);
            }
        });

        return ddl;
    }
    public static Textfield createLabeledTextField(
            ControlP5 cp5, String name, float x, float y, int width, int height, int textColor, int labelColor,
            String defaultText, String labelKey, CallbackListener onChangeCallback) {

        Textfield textField = cp5.addTextfield(name)
                .setPosition(x, y)
                .setSize(width, height)
                .setColor(textColor)
                .setText(defaultText)
                .setAutoClear(false);
        if (onChangeCallback != null) {
            textField.onChange(onChangeCallback);
        }
        textField.getCaptionLabel()
                .setPaddingX(0)
                .setPaddingY(-40)
                .align(ControlP5.CENTER, ControlP5.BOTTOM_OUTSIDE)
                .setText(Translator.getInstance().translate(labelKey))
                .setColor(labelColor);
        textField.onEnter(event -> {
            hoverText = Translator.getInstance().translate(labelKey);
            showTooltip = true;
        });

        textField.onLeave(event -> {
            showTooltip = false;
        });

        return textField;
    }
    public static Textfield createNumericTextField(
            ControlP5 cp5, String name, float x, float y, int width, int height, int textColor, int labelColor,
            String defaultValue, String labelKey, Consumer<Float> onUpdate) {
        return createLabeledTextField(cp5, name, x, y, width, height, textColor, labelColor, defaultValue, labelKey, event -> {
            try {
                float value = Float.parseFloat(event.getController().getStringValue());
                onUpdate.accept(value);
            } catch (NumberFormatException e) {
                Logger.getInstance().log(Logger.Project.Converter, "Invalid value for " + name);
            }
        });
    }

}
