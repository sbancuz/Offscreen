package com.sbancuz.offscreen.window;

import java.util.Map;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.api.HostUI;

public final class HostedScreen<T extends HostUI> {

    // We are not multithreaded, and I don't expect it to ever be, so one static instance should suffice
    private static GuiScreen bgSuppressed;

    public static boolean isSuppressingBgFor(final GuiScreen screen) {
        return screen == bgSuppressed;
    }

    private final T screen;
    private final Minecraft mc;

    private int width = -1;
    private int height = -1;
    // private int dragButton = -1;
    // private long dragStartMs;

    public HostedScreen(T screen) {
        this.screen = screen;
        this.mc = Minecraft.getMinecraft();
    }

    public void resize(final int windowWidth, final int windowHeight, final int guiWidth, final int guiHeight) {
        this.width = guiWidth;
        this.height = guiHeight;

        runWith(
            s -> s.getGuiScreen()
                .setWorldAndResolution(mc, windowWidth, windowHeight));
        screen.onResize(width, height);
    }

    public void dispose() {
        screen.dispose();
    }

    public boolean needsResize(final int width, final int height) {
        return this.width != width || this.height != height;
    }

    public void runWith(final Consumer<T> r) {
        final GuiScreen prev = mc.currentScreen;
        mc.currentScreen = screen.getGuiScreen();
        try {
            r.accept(screen);
        } finally {
            mc.currentScreen = prev;
        }
    }

    public void draw(Minecraft mc, float partialTicks, long now) {
        bgSuppressed = screen.getGuiScreen();
        screen.draw(mc, partialTicks, now);
        bgSuppressed = null;
    }
}
