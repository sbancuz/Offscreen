package com.sbancuz.offscreen.integration.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.api.HostUI;

public class VanillaUI implements HostUI {

    private final GuiScreen screen;

    public VanillaUI(GuiScreen screen) {
        this.screen = screen;
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
    public void draw(Minecraft mc, float partialTicks, long now) {
        screen.drawScreen(0, 0, partialTicks);
    }
}
