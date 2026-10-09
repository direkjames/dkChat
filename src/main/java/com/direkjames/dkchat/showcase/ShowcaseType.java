package com.direkjames.dkchat.showcase;

/** The three things a player can show in chat. */
public enum ShowcaseType {
    ITEM("item", 0),
    INVENTORY("inventory", 45),
    ENDERCHEST("enderchest", 27);

    private final String key;
    private final int guiSize;

    ShowcaseType(String key, int guiSize) {
        this.key = key;
        this.guiSize = guiSize;
    }

    /** The section name in config.yml (showcase.&lt;key&gt;). */
    public String key() {
        return key;
    }

    public String permission() {
        return "dkchat.showcase." + key;
    }

    /** Size of the preview GUI, or 0 for types without one. */
    public int guiSize() {
        return guiSize;
    }
}
