package com.sbancuz.offscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.sbancuz.offscreen.api.OffscreenAPI;
import com.sbancuz.offscreen.core.Driver;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class ClientProxy extends CommonProxy {

    private static final KeyBinding debugToggleScreen = new KeyBinding(
        "key.offscreen.open",
        Keyboard.KEY_M,
        "key.offscreen");

    @Override
    public void init(final FMLInitializationEvent event) {
        super.init(event);

        ClientRegistry.registerKeyBinding(debugToggleScreen);

        FMLCommonHandler.instance()
            .bus()
            .register(this);

        FMLCommonHandler.instance()
            .bus()
            .register(Driver.INSTANCE);
    }

    @SubscribeEvent
    public void onKeyInput(final InputEvent.KeyInputEvent event) {
        if (debugToggleScreen.isPressed()) {
            // if (w == null || !w.isOpen()) {
            // Offscreen.LOG.info("[offscreen] M pressed: opening offscreen vanilla screen");
            // w = new Window("test");
            // } else {
            // Offscreen.LOG.info("[offscreen] M pressed: closing offscreen window");
            // w.destroy();
            // }

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) return; // in menu
            OffscreenAPI.open(new GuiInventory(Minecraft.getMinecraft().thePlayer));
            // if (WindowRegistry.INSTANCE.hasWindow()) {
            // WindowRegistry.INSTANCE.shutdown();
            // } else {
            // Offscreen.LOG.info("[offscreen] M pressed: opening offscreen vanilla screen");
            // OffscreenApi.open(new TestVanillaScreen());
            // }
        }
    }
}
