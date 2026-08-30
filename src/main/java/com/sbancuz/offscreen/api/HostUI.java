package com.sbancuz.offscreen.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.scope.Scope;
import com.sbancuz.offscreen.window.input.FrameEvent;

public interface HostUI {

    default Scope scope() {
        return Scope.NOP;
    }

    default void update() {}

    void dispose();

    default void onResize(final int width, final int height) {}

    GuiScreen getGuiScreen();

    void draw(Minecraft mc, int mouseX, int mouseY, float partialTicks, long now);

    default void onInput(FrameEvent event, float partialTicks) {}
}
