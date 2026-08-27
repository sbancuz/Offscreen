package com.sbancuz.offscreen.window;

import com.sbancuz.offscreen.core.Driver;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;

import com.sbancuz.offscreen.Offscreen;

import lombok.Getter;
import org.lwjglx.opengl.Display;
import org.lwjgl.sdl.SDL_Surface;

public class Window {

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 800;

    @Getter
    private volatile int id;
    private long sdlPtr;

    @Getter
    private final String title;

    private final Renderer renderer = new Renderer();

    private int guiWidth = 0;
    private int guiHeight = 0;

    public Window(final String title) {
        this.title = title;

        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            Offscreen.LOG.error("[secondscreen] SDL_Init failed: {}", sdlError());
            return;
        }

        sdlPtr = SDLVideo.SDL_CreateWindow(
            title,
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            SDLVideo.SDL_WINDOW_RESIZABLE | SDLVideo.SDL_WINDOW_HIDDEN);

        restoreMcContext();
        if (sdlPtr == 0L) {
            Offscreen.LOG.error("[secondscreen] SDL_CreateWindow failed: {}", sdlError());
            return;
        }

        SDLVideo.SDL_ShowWindow(sdlPtr);
        restoreMcContext();
        id = SDLVideo.SDL_GetWindowID(sdlPtr);

        Driver.INSTANCE.trackWindow(this);
    }

    public boolean isOpen() {
        return sdlPtr != 0L;
    }

    public void destroy() {
        if (sdlPtr == 0L) return;
        SDLVideo.SDL_DestroyWindow(sdlPtr);
        sdlPtr = 0L;
        restoreMcContext();
        Driver.INSTANCE.removeWindow(this);
    }

    public static String sdlError() {
        return SDLError.SDL_GetError();
    }

    @SneakyThrows
    private static void restoreMcContext() {
        Display.getDrawable().makeCurrent();
        GL.createCapabilities();
    }

    public void tryRenderFrame() {
        if (!isOpen()) return;
        if (!renderer.isTimeToRender()) return;

        renderFrame();

        restoreMcContext();
    }

    private void renderFrame() {
//        SDLEvents.SDL_PumpEvents();
        final SDL_Surface surface = SDLVideo.SDL_GetWindowSurface(sdlPtr);
        if (surface == null) return;

        final int width = surface.w();
        final int height = surface.h();

        if (!renderer.ensureCorrectSize(width, height)) return;

        final Minecraft mc = Minecraft.getMinecraft();

        final ScaledResolution resolution = new ScaledResolution(mc, width, height);
        guiWidth = resolution.getScaledWidth();
        guiHeight = resolution.getScaledHeight();

        renderer.beginFrame(width, height, guiWidth, guiHeight);
        // Probably just for the integrations
        // dispatchInputAndDraw(mc, now);
        renderer.endFrame(mc, width, height);

        renderer.collectReady();
        if (!renderer.present(surface, width, height)) {
            return;
        }

//            SDLEvents.SDL_PumpEvents();
        SDLVideo.SDL_UpdateWindowSurface(sdlPtr);
//            SDLEvents.SDL_PumpEvents();
    }
}

// /**
// * Probes the authoritative pixel size of the window's surface. SDL_GetWindowSize proved
// * unreliable on Wayland (reports half-configured 0x{requested-height} states); the surface
// * always reflects the actually committed buffer geometry.
// *
// * @return the surface, or null if SDL could not provide one. The caller MUST call
// * {@link #restoreMcContext()} afterwards regardless of outcome - probing releases
// * the GL context.
// */
// public SDL_Surface probeSurface() {
// try {
// return SDLVideo.SDL_GetWindowSurface(sdlPtr);
// } catch (final Throwable t) {
// PlanNH.LOG.error("[secondscreen] SDL_GetWindowSurface crashed", t);
// return null;
// }
// }
//
// /** Pushes a locked-and-filled surface to the display. Returns false on failure. */
// public boolean updateSurface() {
// // Moves OS events into SDL's queue WITHOUT consuming any - the game keeps polling as
// // usual. Wayland needs frame callbacks dispatched or the compositor never presents.
// SDLEvents.SDL_PumpEvents();
// final boolean ok = SDLVideo.SDL_UpdateWindowSurface(sdlPtr);
// SDLEvents.SDL_PumpEvents();
// if (!ok) {
// PlanNH.LOG.error("[secondscreen] SDL_UpdateWindowSurface failed: {}", sdlError());
// }
// return ok;
// }
//
// /**
// * Re-makes Minecraft's GL context current on this thread and refreshes LWJGL capabilities.
// * Must be called after every SDL interaction that may have released it. Never throws:
// * a failed restore is logged loudly but must not take the game down.
// */
//
// /** Defensive: re-asserts Minecraft's context if anything stole it since last frame. */
// public static void ensureMcContextCurrent() {
// if (!Display.isCurrent()) {
// Diagnostics.trace("MC context was not current - restoring");
// restoreMcContext();
// }
// }
//
// /** True when running on the game's client thread; all entry points must verify this. */
// public static boolean onClientThread() {
// return Minecraft.getMinecraft().func_152345_ab();
// }
// }
