package com.sbancuz.offscreen.window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.DrawableGL;

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
    private long sharedContext;

    @Getter
    private final String title;

    private final Renderer renderer = new Renderer();
    private final HostedStack screenStack = new HostedStack();

    private int pixelWidth = DEFAULT_WIDTH;
    private int pixelHeight = DEFAULT_HEIGHT;

    private int guiWidth = 0;
    private int guiHeight = 0;

    // TODO: InputRouter
    // 1. SDL_SetEventFilter to capture events before lwjgl3ify's shared queue
    // 2. Private ring buffer (256 entries, drop oldest on overflow)
    // 3. Keyboard.sdlKeyPressedArray shadow swap on focus gain/loss
    // 4. Per-frame drain(): translate coords/keycodes, accumulate TEXT_INPUT
    // 5. Dispatch via screenStack.runScoped()

    public Window(final String title) {
        this.title = title;

        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            Offscreen.LOG.error("[secondscreen] SDL_Init failed: {}", sdlError());
            return;
        }

        Display.glContextMutex.lock();
        try {
            if (!(Display.getDrawable() instanceof DrawableGL drawable)) {
                Offscreen.LOG.error(
                    "[secondscreen] unexpected drawable type {}",
                    Display.getDrawable()
                        .getClass());
                return;
            }
            sharedContext = drawable.createSharedContext().sdlContext;
        } finally {
            Display.glContextMutex.unlock();
        }

        restoreMcContext();

        sdlPtr = SDLVideo.SDL_CreateWindow(
            title,
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_RESIZABLE | SDLVideo.SDL_WINDOW_HIDDEN);
        restoreMcContext();

        if (sdlPtr == 0L) {
            Offscreen.LOG.error("[secondscreen] SDL_CreateWindow failed: {}", sdlError());
            return;
        }

        id = SDLVideo.SDL_GetWindowID(sdlPtr);

        if (!SDLVideo.SDL_GL_MakeCurrent(sdlPtr, sharedContext)) {
            Offscreen.LOG.error("[secondscreen] SDL_GL_MakeCurrent failed: {}", sdlError());
            return;
        }
        SDLVideo.SDL_GL_SetSwapInterval(0);
        restoreMcContext();

        probeDrawableSize();

        if (!SDLVideo.SDL_ShowWindow(sdlPtr)) {
            Offscreen.LOG.warn("[secondscreen] SDL_ShowWindow failed: {}", sdlError());
        }
        restoreMcContext();

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
    static void restoreMcContext() {
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
        probeDrawableSize();

        final Minecraft mc = Minecraft.getMinecraft();

        final ScaledResolution resolution = new ScaledResolution(mc, pixelWidth, pixelHeight);
        guiWidth = resolution.getScaledWidth();
        guiHeight = resolution.getScaledHeight();

        if (!renderer.ensureCorrectSize(pixelWidth, pixelHeight)) return;
        if (screenStack.isEmpty()) return;

        renderer.beginFrame(pixelWidth, pixelHeight, guiWidth, guiHeight);
        try {
            final long now = System.currentTimeMillis();
            final float partialTicks = ((MinecraftAccessor) mc).getTimer().renderPartialTicks;

            screenStack.runScoped(s -> {
                if (s.needsResize(guiWidth, guiHeight)) {
                    s.resize(pixelWidth, pixelHeight, guiWidth, guiHeight);
                }
                s.draw(mc, partialTicks, now);
            });
        } finally {
            renderer.endFrame(mc);
        }

        if (renderer.fbo() != 0) {
            if (!SDLVideo.SDL_GL_MakeCurrent(sdlPtr, sharedContext)) {
                Offscreen.LOG.error("[secondscreen] present MakeCurrent failed");
                Window.restoreMcContext();
                return;
            }

            renderer.present(pixelWidth, pixelHeight);

            if (!SDLVideo.SDL_GL_SwapWindow(sdlPtr)) {
                Offscreen.LOG.warn("[secondscreen] SDL_GL_SwapWindow failed");
            }
        }
    }

    private final IntBuffer wPtr = ByteBuffer.allocateDirect(4)
        .order(ByteOrder.nativeOrder())
        .asIntBuffer();
    private final IntBuffer hPtr = ByteBuffer.allocateDirect(4)
        .order(ByteOrder.nativeOrder())
        .asIntBuffer();

    private void probeDrawableSize() {
        if (!SDLVideo.SDL_GetWindowSizeInPixels(sdlPtr, wPtr, hPtr)) {
            return;
        }
        pixelWidth = wPtr.get(0);
        pixelHeight = hPtr.get(0);
        restoreMcContext();
    }

    public void setUI(HostUI ui) {
        screenStack.clear();
        screenStack.push(new HostedScreen<>(ui));
    }

    public void update() {
        if (screenStack.isEmpty()) return;
        screenStack.runScoped(HostedScreen::update);
    }
}
