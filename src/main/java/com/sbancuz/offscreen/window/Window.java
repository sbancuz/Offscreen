package com.sbancuz.offscreen.window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_CommonEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryUtil;
import org.lwjglx.input.KeyCodes;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.DrawableGL;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.api.HostUI;
import com.sbancuz.offscreen.core.Driver;
import com.sbancuz.offscreen.mixins.MinecraftAccessor;
import com.sbancuz.offscreen.window.input.FrameEvent;
import com.sbancuz.offscreen.window.input.InputRouter;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

public final class Window {

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 800;
    private static final int RING_CAPACITY = 256;

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

    private final SDL_Event.Buffer ring = SDL_Event.calloc(RING_CAPACITY);
    private int ringHead;
    private int ringTail;

    @Setter
    boolean focusState;

    private final FrameEvent frameEvent = new FrameEvent();

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

        if (!SDLKeyboard.SDL_StartTextInput(sdlPtr)) {
            Offscreen.LOG.warn("[secondscreen] SDL_StartTextInput failed: {}", sdlError());
        }

        Driver.INSTANCE.trackWindow(this);
    }

    public boolean isOpen() {
        return sdlPtr != 0L;
    }

    public void destroy() {
        if (sdlPtr == 0L) return;
        SDLKeyboard.SDL_StopTextInput(sdlPtr);
        SDLVideo.SDL_DestroyWindow(sdlPtr);
        sdlPtr = 0L;
        freeRing();
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
        renderer.setFocused(focusState);
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

        drainFrameEvent();

        renderer.beginFrame(pixelWidth, pixelHeight, guiWidth, guiHeight);
        try {
            final long now = System.currentTimeMillis();
            final float partialTicks = ((MinecraftAccessor) mc).getTimer().renderPartialTicks;

            screenStack.runScoped(frameEvent, pixelWidth, pixelHeight, s -> {
                if (s.needsResize(guiWidth, guiHeight)) {
                    s.resize(guiWidth, guiHeight);
                }
                s.dispatchInput(frameEvent, partialTicks);
                s.draw(mc, frameEvent.mouseX, frameEvent.mouseY, partialTicks, now);
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

    public void capture(final long eventPtr) {
        MemoryUtil.memCopy(eventPtr, ring.address(ringTail), SDL_Event.SIZEOF);
        ringTail = (ringTail + 1) % RING_CAPACITY;
        if (ringTail == ringHead) {
            ringHead = (ringHead + 1) % RING_CAPACITY;
        }
    }

    private void freeRing() {
        ringHead = 0;
        ringTail = 0;
        ring.free();
    }

    private void drainFrameEvent() {
        frameEvent.keyCount = 0;
        frameEvent.text.setLength(0);
        final float scaleX = (float) guiWidth / pixelWidth;
        final float scaleY = (float) guiHeight / pixelHeight;
        while (ringHead != ringTail) {
            final long evAddr = ring.address(ringHead);
            ringHead = (ringHead + 1) % RING_CAPACITY;
            final var event = SDL_Event.create(evAddr);

            switch (SDL_CommonEvent.ntype(evAddr)) {
                case SDLEvents.SDL_EVENT_MOUSE_MOTION -> {
                    final var motion = event.motion();
                    frameEvent.mouseX = Math.round(motion.x() * scaleX);
                    frameEvent.mouseY = Math.round(motion.y() * scaleY);
                }
                case SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN -> {
                    frameEvent.pressButton = InputRouter.sdlToGuiButton(
                        event.button()
                            .button());
                }
                case SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP -> {
                    frameEvent.releaseButton = InputRouter.sdlToGuiButton(
                        event.button()
                            .button());
                }
                case SDLEvents.SDL_EVENT_MOUSE_WHEEL -> {
                    frameEvent.wheelDelta += event.wheel()
                        .y();
                }
                case SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED -> frameEvent.closeRequested = true;
                case SDLEvents.SDL_EVENT_KEY_DOWN, SDLEvents.SDL_EVENT_KEY_UP -> {
                    final var key = event.key();
                    if (key.repeat() && !key.down()) break;
                    final int sdlKeyCode = key.key();
                    final int lwjglKey = KeyCodes.sdlKeycodeToLwjgl(sdlKeyCode);
                    final int rawKeyCode = SDLKeyboard.SDL_GetKeyFromScancode(key.scancode(), key.mod(), false);
                    char c = Character.MIN_VALUE;
                    if (rawKeyCode >= SDLKeycode.SDLK_SPACE && rawKeyCode <= SDLKeycode.SDLK_TILDE) {
                        c = (char) rawKeyCode;
                        if ((key.mod() & SDLKeycode.SDL_KMOD_CTRL) != 0) {
                            c = (char) (sdlKeyCode & 0x1f);
                        }
                    }
                    if (frameEvent.keyCount < frameEvent.keys.length) {
                        frameEvent.keys[frameEvent.keyCount++].set(lwjglKey, c, key.down())
                            .setSdl(sdlKeyCode, key.scancode(), key.mod());
                    }
                }
                case SDLEvents.SDL_EVENT_TEXT_INPUT -> {
                    final String text = event.text()
                        .textString();
                    if (text != null) frameEvent.text.append(text);
                }
                default -> {}
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

    public void push(HostUI ui) {
        screenStack.push(new HostedScreen<>(ui));
    }

    public boolean pop() {
        if (screenStack.size() <= 1) return false;
        final HostedScreen<?> popped = screenStack.pop();
        popped.dispose();
        if (!screenStack.isEmpty()) {
            screenStack.top()
                .requestResize();
        }
        return true;
    }

    public HostUI getCurrentUI() {
        if (screenStack.isEmpty()) return null;
        return screenStack.top()
            .screen();
    }

    public int getScreenDepth() {
        return screenStack.size();
    }

    public void resize(int width, int height) {
        SDLVideo.SDL_SetWindowSize(sdlPtr, width, height);
        probeDrawableSize();
        if (!screenStack.isEmpty()) {
            screenStack.top()
                .requestResize();
        }
        restoreMcContext();
    }

    public void setTitle(String title) {
        SDLVideo.SDL_SetWindowTitle(sdlPtr, title);
        restoreMcContext();
    }

    public void update() {
        if (screenStack.isEmpty()) return;
        screenStack.runScoped(HostedScreen::update);
    }
}
