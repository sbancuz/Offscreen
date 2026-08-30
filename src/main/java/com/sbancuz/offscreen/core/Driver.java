package com.sbancuz.offscreen.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sbancuz.offscreen.window.Window;
import com.sbancuz.offscreen.window.input.InputRouter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class Driver {

    public static final Driver INSTANCE = new Driver();

    private final Map<Integer, Window> windows = new ConcurrentHashMap<>();
    private final InputRouter router = new InputRouter(windows);

    @SubscribeEvent
    public void onRenderTick(final TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final Window w : windows.values()) {
            w.tryRenderFrame();
        }
    }

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final Window w : windows.values()) {
            w.update();
        }
    }

    public void trackWindow(final Window window) {
        if (windows.containsKey(window.getId())) return;
        windows.put(window.getId(), window);
    }

    public void removeWindow(final Window window) {
        if (!windows.containsKey(window.getId())) return;
        windows.remove(window.getId());
    }
}
