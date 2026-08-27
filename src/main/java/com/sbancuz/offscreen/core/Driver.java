package com.sbancuz.offscreen.core;

import com.sbancuz.offscreen.window.Window;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public class Driver {

    public static final Driver INSTANCE = new Driver();

    private final List<Window> windows = new ObjectArrayList<>();

    @SubscribeEvent
    public void onRenderTick(final TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final Window w : windows) {
            w.tryRenderFrame();
        }
    }

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
//        try {
//            SecondScreenManager.tickHostedScreen();
//        } catch (final Throwable t) {
//            PlanNH.LOG.error("[secondscreen] tick dispatch failed", t);
//        }
    }

    public void trackWindow(final Window window) {
        if (windows.contains(window)) return;
        windows.add(window);
    }

    public void removeWindow(final Window window) {
        if (!windows.contains(window)) return;
        windows.remove(window);
    }

}
