package com.sbancuz.offscreen.scope;

import net.minecraft.client.Minecraft;

import com.sbancuz.offscreen.Offscreen;

public final class FocusScope implements Scope {

    private boolean savedFocus;

    @Override
    public void enter() {
        savedFocus = Minecraft.getMinecraft().inGameHasFocus;
    }

    @Override
    public void restore() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (savedFocus && mc.currentScreen == null && !mc.inGameHasFocus) {
            try {
                mc.setIngameFocus();
            } catch (final Throwable t) {
                Offscreen.LOG.warn("[secondscreen] setIngameFocus restoration failed", t);
            }
        }
    }
}
