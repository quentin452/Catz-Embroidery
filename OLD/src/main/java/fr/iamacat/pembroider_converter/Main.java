package fr.iamacat.pembroider_converter;

public class Main extends PApplet implements Translatable {

    private void showExitDialog() {
        DialogUtil.showExitDialog(
                (Component) this.getSurface().getNative(),
                enableEscapeMenu,
                (dropboxClient != null),
                this::saveFileAndExit,
                selectedFile -> {
                    fileSaved(selectedFile);
                    uploadToDropbox(selectedFile);
                },
                () -> ApplicationUtil.exitApplication(this)
        );
    }
}