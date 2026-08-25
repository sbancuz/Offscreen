package com.sbancuz.offscreen;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

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
    }

    @SubscribeEvent
    public void onKeyInput(final InputEvent.KeyInputEvent event) {
        if (debugToggleScreen.isPressed()) {
//            if (WindowRegistry.INSTANCE.hasWindow()) {
//                Offscreen.LOG.info("[offscreen] M pressed: closing offscreen window");
//                WindowRegistry.INSTANCE.shutdown();
//            } else {
//                Offscreen.LOG.info("[offscreen] M pressed: opening offscreen vanilla screen");
//                OffscreenApi.open(new TestVanillaScreen());
//            }
        }
    }
}
