package com.sbancuz.offscreen.integration.vanilla;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjglx.input.KeyCodes;
import org.lwjglx.input.Keyboard;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.api.MCHostUI;
import com.sbancuz.offscreen.mixins.GuiContainerAccessor;
import com.sbancuz.offscreen.mixins.GuiScreenAccessor;
import com.sbancuz.offscreen.scope.FocusScope;
import com.sbancuz.offscreen.scope.Scope;
import com.sbancuz.offscreen.scope.ScopePipeline;
import com.sbancuz.offscreen.scope.ScreenScope;

import codechicken.nei.guihook.GuiContainerManager;
import me.eigenraven.lwjgl3ify.api.InputEvents;

public class VanillaUI implements MCHostUI {

    private static final Keyboard.KeyEvent[] PRESERVED_KEYS = new Keyboard.KeyEvent[64];

    private final GuiScreen screen;
    private final Scope scope;

    public VanillaUI(GuiScreen screen) {
        this.screen = screen;
        this.scope = ScopePipeline.builder()
            .always(new FocusScope())
            .always(new ScreenScope(this::getGuiScreen))
            .build();
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public void dispose() {

    }

    public void update() {
        if (screen.width <= 0) return;
        screen.updateScreen();
    }

    @Override
    public GuiScreen getGuiScreen() {
        return screen;
    }

    @Override
    public void onResize(final int width, final int height) {
        if (screen instanceof GuiContainer container) {
            final GuiContainerAccessor accessor = (GuiContainerAccessor) container;
            accessor.setGuiTop((height - accessor.getYSize()) / 2);
            accessor.setGuiLeft((width - accessor.getXSize()) / 2);
        }
    }

    @Override
    public void draw(Minecraft mc, final int mouseX, final int mouseY, float partialTicks, long now) {
        screen.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onMouseClicked(int mouseX, int mouseY, int button) {
        if (!(screen instanceof GuiScreenAccessor acc)) return;
        try {
            acc.invokeMouseClicked(mouseX, mouseY, button);
        } catch (final Throwable t) {
            Offscreen.LOG.trace("[secondscreen] mouseClicked failed", t);
        }
    }

    @Override
    public void onMouseReleased(int mouseX, int mouseY, int button) {
        if (!(screen instanceof GuiScreenAccessor acc)) return;
        acc.invokeMouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    public void onTextInput(String text) {
        InputEvents.injectTextEvent(new InputEvents.TextEvent(text));
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (screen instanceof GuiContainer gc) {
            final GuiContainerManager neiManager = GuiContainerManager.getManager(gc);
            if (neiManager != null) {
                try {
                    final var queue = Keyboard.eventQueue;
                    int preservedCount = 0;
                    Keyboard.KeyEvent pending;
                    while ((pending = queue.poll()) != null && preservedCount < PRESERVED_KEYS.length) {
                        PRESERVED_KEYS[preservedCount++] = pending;
                    }
                    try {
                        Keyboard.addRawKeyEvent(
                            new Keyboard.KeyEvent(
                                keyCode,
                                keyCode,
                                typedChar,
                                Keyboard.KeyState.PRESS,
                                System.nanoTime()));

                        final ByteBuffer keyArray = Keyboard.sdlKeyPressedArray;
                        final int sdlScancode = KeyCodes.lwjglToSdlScancode(keyCode);
                        final byte prevKeyState = (keyArray != null && sdlScancode > 0
                            && sdlScancode < keyArray.limit()) ? keyArray.get(sdlScancode) : 0;
                        if (keyArray != null && sdlScancode > 0 && sdlScancode < keyArray.limit()) {
                            keyArray.put(sdlScancode, (byte) 1);
                        }
                        try {
                            neiManager.handleKeyboardInput();
                        } finally {
                            if (keyArray != null && sdlScancode > 0 && sdlScancode < keyArray.limit()) {
                                keyArray.put(sdlScancode, prevKeyState);
                            }
                        }
                    } finally {
                        for (int i = 0; i < preservedCount; i++) {
                            queue.add(PRESERVED_KEYS[i]);
                            PRESERVED_KEYS[i] = null;
                        }
                    }
                } catch (final Throwable t) {
                    Offscreen.LOG.trace("[secondscreen] NEI key handling failed", t);
                }
                return;
            }
        }
        if (!(screen instanceof GuiScreenAccessor acc)) return;
        try {
            acc.invokeKeyTyped(typedChar, keyCode);
        } catch (final Throwable t) {
            Offscreen.LOG.trace("[secondscreen] keyTyped failed", t);
        }
    }
}
