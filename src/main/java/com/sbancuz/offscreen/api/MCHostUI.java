package com.sbancuz.offscreen.api;

import com.sbancuz.offscreen.window.input.FrameEvent;
import com.sbancuz.offscreen.window.input.KeyEvent;

public interface MCHostUI extends HostUI {

    @Override
    default void onInput(FrameEvent event, float partialTicks) {
        if (event.isButtonPressed()) {
            final int button = event.pressButton;
            event.clearButtonPressed();
            onMouseClicked(event.mouseX, event.mouseY, button);
        }
        if (event.isButtonReleased()) {
            final int button = event.releaseButton;
            event.clearButtonReleased();
            onMouseReleased(event.mouseX, event.mouseY, button);
        }
        if (event.wheelDelta != 0f) {
            final int scroll = event.wheelDelta > 0 ? 1 : -1;
            event.wheelDelta = 0f;
            onMouseScroll(event.mouseX, event.mouseY, scroll);
        }
        for (int i = 0; i < event.keyCount; i++) {
            final KeyEvent key = event.keys[i];
            onKeyTyped(key.character(), key.keyCode());
        }

        if (!event.text.isEmpty()) {
            final String text = event.text.toString();
            event.text.setLength(0);
            onTextInput(text);
        }
    }

    default void onMouseClicked(int mouseX, int mouseY, int button) {}

    default void onMouseReleased(int mouseX, int mouseY, int button) {}

    default void onMouseScroll(int mouseX, int mouseY, int scroll) {}

    default void onKeyTyped(char typedChar, int keyCode) {}

    default void onTextInput(String text) {}
}
