package com.sbancuz.offscreen.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public interface HostUI {

    default void update() {}

    void dispose();

    default void onResize(final int width, final int height) {}

    GuiScreen getGuiScreen();

    void draw(Minecraft mc, float partialTicks, long now);
}
