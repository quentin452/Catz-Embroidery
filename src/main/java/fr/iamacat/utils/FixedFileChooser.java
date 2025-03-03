package fr.iamacat.utils;

import com.kotcrab.vis.ui.widget.file.*;
import fr.iamacat.manager.DialogManager;

public class FixedFileChooser extends FileChooser {

    public FixedFileChooser(Mode mode) {
        super(mode);
    }

    @Override
    public void fadeOut(float time) {
        super.fadeOut(time);
        DialogManager.dialogEnabled = false;
    }
}
