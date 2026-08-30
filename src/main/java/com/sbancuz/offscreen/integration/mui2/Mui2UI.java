package com.sbancuz.offscreen.integration.mui2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.sbancuz.offscreen.api.HostUI;
import com.sbancuz.offscreen.window.input.FrameEvent;

import me.eigenraven.lwjgl3ify.api.InputEvents;

public class Mui2UI implements HostUI {

    private final ModularScreen screen;
    private int dragButton = -1;
    private long dragStartMs;

    public Mui2UI(ModularScreen screen) {
        this.screen = screen;
    }

    @Override
    public void dispose() {

    }

    @Override
    public GuiScreen getGuiScreen() {
        return screen.getScreenWrapper()
            .getGuiScreen();
    }

    @Override
    public void draw(Minecraft mc, final int mouseX, final int mouseY, float partialTicks, long now) {}

    public void onResize(final int width, final int height) {
        screen.onResize(width, height);
    }

    @Override
    public void onInput(FrameEvent event, float partialTicks) {
        final Minecraft mc = Minecraft.getMinecraft();
        final int mouseX = Math.max(event.mouseX, 0);
        final int mouseY = Math.max(event.mouseY, 0);

        screen.getContext()
            .updateState(mouseX, mouseY, partialTicks);
        screen.getContext()
            .reset();

        if (event.pressButton != -1) {
            final int button = event.pressButton;
            event.pressButton = -1;
            screen.onMousePressed(button);
            dragButton = button;
            dragStartMs = System.currentTimeMillis();
        }
        if (event.releaseButton != -1) {
            final int button = event.releaseButton;
            event.releaseButton = -1;
            screen.onMouseRelease(button);
            if (dragButton == button) dragButton = -1;
        }
        if (dragButton != -1) {
            final long heldMs = System.currentTimeMillis() - dragStartMs;
            screen.onMouseDrag(dragButton, heldMs);
        }
        if (event.wheelDelta != 0f) {
            final UpOrDown dir = event.wheelDelta > 0 ? UpOrDown.UP : UpOrDown.DOWN;
            event.wheelDelta = 0f;
            screen.onMouseScroll(dir, 1);
        }

        for (int i = 0; i < event.keyCount; i++) {
            final var key = event.keys[i];
            if (key.pressed()) {
                screen.onKeyPressed(key.character(), key.keyCode());
            } else {
                screen.onKeyRelease(key.character(), key.keyCode());
            }
            InputEvents.injectKeyEvent(
                new InputEvents.KeyEvent(
                    key.keyCode(),
                    0,
                    key.sdlKeyCode(),
                    key.sdlScanCode(),
                    0,
                    key.pressed() ? InputEvents.KeyAction.PRESSED : InputEvents.KeyAction.RELEASED,
                    (short) key.sdlMod(),
                    0L));
        }
        if (!event.text.isEmpty()) {
            final String text = event.text.toString();
            event.text.setLength(0);
            InputEvents.injectTextEvent(new InputEvents.TextEvent(text));
        }
    }
}
