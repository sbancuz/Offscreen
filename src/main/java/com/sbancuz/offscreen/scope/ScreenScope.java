package com.sbancuz.offscreen.scope;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public final class ScreenScope implements Scope {

    private final Supplier<GuiScreen> wrapperScreenSupplier;
    private GuiScreen savedScreen;

    public ScreenScope(final Supplier<GuiScreen> wrapperScreenSupplier) {
        this.wrapperScreenSupplier = Objects.requireNonNull(wrapperScreenSupplier);
    }

    @Override
    public void enter() {
        final Minecraft mc = Minecraft.getMinecraft();
        savedScreen = mc.currentScreen;
        mc.currentScreen = wrapperScreenSupplier.get();
    }

    @Override
    public void restore() {
        Minecraft.getMinecraft().currentScreen = savedScreen;
        savedScreen = null;
    }
}
