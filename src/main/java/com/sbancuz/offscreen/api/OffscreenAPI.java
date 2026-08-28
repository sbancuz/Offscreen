package com.sbancuz.offscreen.api;

import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.integration.vanilla.VanillaUI;
import com.sbancuz.offscreen.window.Window;

public final class OffscreenAPI {

    // TODO: Maybe use windowHandle or something
    public static Window open(GuiScreen screen) {
        final Window w = new Window(screen.toString());

        w.setUI(new VanillaUI(screen));
        return w;
    }
}
