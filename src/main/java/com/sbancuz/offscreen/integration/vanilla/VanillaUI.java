package com.sbancuz.offscreen.integration.vanilla;

import com.sbancuz.offscreen.mixins.GuiContainerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.api.HostUI;
import net.minecraft.client.gui.inventory.GuiContainer;

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
    public void onResize(final int width, final int height) {
        if (screen instanceof GuiContainer container) {
            final GuiContainerAccessor accessor = (GuiContainerAccessor) container;
            accessor.setGuiTop((height - accessor.getYSize()) / 2);
            accessor.setGuiLeft((width - accessor.getXSize()) / 2);
        }
    }

    @Override
    public void draw(Minecraft mc, float partialTicks, long now) {
        screen.drawScreen(0, 0, partialTicks);
    }
}
