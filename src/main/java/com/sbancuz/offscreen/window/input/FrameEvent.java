package com.sbancuz.offscreen.window.input;

public class FrameEvent {

    private static final int RING_CAPACITY = 256;

    public int mouseX = -1;
    public int mouseY = -1;

    public int pressButton = -1;
    public int releaseButton = -1;

    public float wheelDelta;
    public boolean closeRequested;

    public final KeyEvent[] keys = new KeyEvent[RING_CAPACITY];
    public int keyCount;

    public final StringBuilder text = new StringBuilder();

    public FrameEvent() {
        for (int i = 0; i < keys.length; i++) keys[i] = new KeyEvent(0, (char) 0, false, 0, 0, 0);
    }

    public boolean isButtonPressed() {
        return pressButton != -1;
    }

    public void clearButtonPressed() {
        pressButton = -1;
    }

    public boolean isButtonReleased() {
        return releaseButton != -1;
    }

    public void clearButtonReleased() {
        releaseButton = -1;
    }
}
