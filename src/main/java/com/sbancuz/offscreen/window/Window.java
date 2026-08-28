package com.sbancuz.offscreen.window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjglx.opengl.Display;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.api.HostUI;
import com.sbancuz.offscreen.core.Driver;
import com.sbancuz.offscreen.mixins.MinecraftAccessor;

import lombok.Getter;
import lombok.SneakyThrows;

public final class Window {

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

    private final HostedStack screenStack = new HostedStack();

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
        Display.getDrawable()
            .makeCurrent();
        GL.createCapabilities();
    }

    public void tryRenderFrame() {
        if (!isOpen()) return;
        if (!renderer.isTimeToRender()) return;

        renderFrame();

        restoreMcContext();
    }

    private void renderFrame() {
        // SDLEvents.SDL_PumpEvents();
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
        if (!screenStack.isEmpty()) {
            final HostedScreen<?> screen = screenStack.top();
            if (screen.needsResize(guiWidth, guiHeight)) {
                screen.resize(guiWidth, guiHeight);
            }

            final long now = System.currentTimeMillis();
            final float partialTicks = ((MinecraftAccessor) mc).getTimer().renderPartialTicks;
            // screen.handleInput()
            screen.draw(mc, partialTicks, now);
            // TODO
        }
        renderer.endFrame(mc, width, height);

        renderer.collectReady();
        if (!renderer.present(surface, width, height)) {
            return;
        }

        // SDLEvents.SDL_PumpEvents();
        SDLVideo.SDL_UpdateWindowSurface(sdlPtr);
        // SDLEvents.SDL_PumpEvents();
    }

    public void setUI(HostUI ui) {
        screenStack.clear();
        screenStack.push(new HostedScreen<>(ui));
    }

    public void update() {
        if (screenStack.isEmpty()) return;
        screenStack.top().runWith(HostUI::update);
    }
}
