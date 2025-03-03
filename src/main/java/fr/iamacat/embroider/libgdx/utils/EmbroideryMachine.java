package fr.iamacat.embroider.libgdx.utils;

// TODO THIS FEATURE IS UNTESTED
public enum EmbroideryMachine {
    TEST_MACHINE("Test Machine" , 100,110),
    BROTHER_SKITCH_PP1("Brother Skitch PP1", 250, 400),
    ;

    public final String displayName;
    public final int minStitchesPerMinute;
    public final int maxStitchesPerMinute;

    EmbroideryMachine(String displayName, int minSpeed, int maxSpeed) {
        this.displayName = displayName;
        this.minStitchesPerMinute = minSpeed;
        this.maxStitchesPerMinute = maxSpeed;
    }

    @Override
    public String toString() {
        return displayName;
    }
}