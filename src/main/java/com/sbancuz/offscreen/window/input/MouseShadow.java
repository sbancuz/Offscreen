package com.sbancuz.offscreen.window.input;

public final class MouseShadow {

    private static int overrideX = -1;
    private static int overrideY = -1;

    private static int overridePixelWidth;
    private static int overridePixelHeight;

    private MouseShadow() {}

    public static void set(final int guiX, final int guiY, final int pixelWidth, final int pixelHeight) {
        overrideX = guiX;
        overrideY = guiY;
        overridePixelWidth = pixelWidth;
        overridePixelHeight = pixelHeight;
    }

    public static void clear() {
        overrideX = -1;
        overrideY = -1;
    }

    public static boolean isActive() {
        return overrideX >= 0;
    }

    public static int getX() {
        return overrideX;
    }

    public static int getY() {
        return overrideY;
    }

    public static int getPixelWidth() {
        return overridePixelWidth;
    }

    public static int getPixelHeight() {
        return overridePixelHeight;
    }
}
