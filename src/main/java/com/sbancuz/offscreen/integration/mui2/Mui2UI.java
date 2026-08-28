package com.sbancuz.offscreen.integration.mui2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.sbancuz.offscreen.api.HostUI;

public class Mui2UI implements HostUI {

    private final ModularScreen screen;

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
    public void draw(Minecraft mc, float partialTicks, long now) {

    }

    public void onResize(final int width, final int height) {
        screen.onResize(width, height);
    }
}
