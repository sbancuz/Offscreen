package com.sbancuz.offscreen.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.integration.vanilla.VanillaUI;
import com.sbancuz.offscreen.window.Window;
import net.minecraft.client.gui.inventory.GuiInventory;

import java.util.function.Supplier;

public final class OffscreenAPI {

    // TODO: Maybe use windowHandle or something
    public static Window open(Supplier<GuiScreen> screen) {
        final Window w = new Window(screen.toString());

        w.setUI(new VanillaUI(new GuiInventory(Minecraft.getMinecraft().thePlayer)));
        return w;
    }
}
