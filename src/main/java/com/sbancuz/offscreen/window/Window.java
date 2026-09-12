package com.sbancuz.offscreen.window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLGPU;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_CommonEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryUtil;
import org.lwjglx.input.KeyCodes;
import org.lwjglx.opengl.Display;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.api.HostUI;
import com.sbancuz.offscreen.core.Driver;
import com.sbancuz.offscreen.mixins.MinecraftAccessor;
import com.sbancuz.offscreen.window.input.FrameEvent;
import com.sbancuz.offscreen.window.input.InputRouter;
import com.sbancuz.offscreen.window.sdlgpu.SdlGpuBackend;
import com.sbancuz.offscreen.window.sdlgpu.SdlGpuDevice;
import com.sbancuz.offscreen.window.sdlgpu.SdlGpuPresenter;
import com.sbancuz.offscreen.window.sdlgpu.SdlGpuUpload;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public final class Window {

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 800;
    private static final int RING_CAPACITY = 256;
    /**
     * Present cap while readbacks go through backend emulation: every emulated readback forces a
     * mid-frame submit plus a full GPU drain on the client thread, racing the backend's own
     * presenter thread. Does not apply to borrowed-device blits, which submit no extra work that
     * needs draining.
     */
    private static final int EMULATED_BACKEND_MAX_FPS = 20;

    @Getter
    private volatile int id;
    private long sdlPtr;
    private long device;
    private boolean borrowedDevice;
    private final SdlGpuUpload upload = new SdlGpuUpload();

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
            Offscreen.LOG.error("[Offscreen] SDL_Init failed: {}", sdlError());
            return;
        }

        device = SdlGpuBackend.angelicaDevice();
        if (device != 0L) {
            // Borrow Angelica's device: our emulated textures live on it, so direct blits stay
            // same-device (cross-device submission hangs the GPU). Never destroy it; only claim
            // and release our own window. Angelica's bookkeeping tracks just the main window, so
            // a raw claim here disturbs nothing of theirs.
            borrowedDevice = true;
        } else {
            device = SdlGpuDevice.acquire();
            if (device == 0L) {
                Offscreen.LOG.error("[Offscreen] no usable SDL GPU device, cannot open '{}'", title);
                return;
            }
        }

        // Plain window: no SDL_WINDOW_OPENGL, no shared GL context. Presentation always goes
        // through our own SDL GPU command buffers, whatever backend the main window uses.
        sdlPtr = SDLVideo.SDL_CreateWindow(
            title,
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            SDLVideo.SDL_WINDOW_RESIZABLE | SDLVideo.SDL_WINDOW_HIDDEN);
        if (sdlPtr == 0L) {
            Offscreen.LOG.error("[Offscreen] SDL_CreateWindow failed: {}", sdlError());
            releaseDevice();
            return;
        }

        id = SDLVideo.SDL_GetWindowID(sdlPtr);

        if (!SDLGPU.SDL_ClaimWindowForGPUDevice(device, sdlPtr)) {
            Offscreen.LOG.error("[Offscreen] SDL_ClaimWindowForGPUDevice failed: {}", sdlError());
            SDLVideo.SDL_DestroyWindow(sdlPtr);
            sdlPtr = 0L;
            releaseDevice();
            return;
        }

        probeDrawableSize();

        if (!SDLVideo.SDL_ShowWindow(sdlPtr)) {
            Offscreen.LOG.warn("[Offscreen] SDL_ShowWindow failed: {}", sdlError());
        }
        restoreMcContext();

        if (!SDLKeyboard.SDL_StartTextInput(sdlPtr)) {
            Offscreen.LOG.warn("[Offscreen] SDL_StartTextInput failed: {}", sdlError());
        }

        Offscreen.LOG.info(
            "[Offscreen] opened '{}' via SDLGPU ({}x{}, swapchain format {}, device {})",
            title,
            pixelWidth,
            pixelHeight,
            SdlGpuPresenter.swapchainFormat(device, sdlPtr),
            borrowedDevice ? "borrowed" : "owned");

        Driver.INSTANCE.trackWindow(this);
    }

    private void releaseDevice() {
        if (!borrowedDevice) {
            SdlGpuDevice.release(device);
        }
        device = 0L;
        borrowedDevice = false;
    }

    public boolean isOpen() {
        return sdlPtr != 0L;
    }

    public void destroy() {
        if (sdlPtr == 0L) return;
        SDLKeyboard.SDL_StopTextInput(sdlPtr);
        if (device != 0L) {
            SDLGPU.SDL_ReleaseWindowFromGPUDevice(device, sdlPtr);
        }
        SDLVideo.SDL_DestroyWindow(sdlPtr);
        sdlPtr = 0L;
        upload.destroy(device);
        upload.freePixels();
        releaseDevice();
        freeRing();
        Driver.INSTANCE.removeWindow(this);
    }

    public static String sdlError() {
        return SDLError.SDL_GetError();
    }

    @SneakyThrows
    static void restoreMcContext() {
        // Under the SDL GPU backend the main window owns no GL context; there is nothing to
        // restore and creating capabilities without a context would be wrong.
        if (!Display.hasGLContext()) return;
        Display.getDrawable()
            .makeCurrent();
        GL.createCapabilities();
    }

    public void tryRenderFrame() {
        if (!isOpen()) return;
        // Borrowed-device blits submit no drain-inducing work, so only the owned-device upload
        // path on an emulated backend is capped.
        renderer.setMaxFps(borrowedDevice || Display.hasGLContext() ? Integer.MAX_VALUE : EMULATED_BACKEND_MAX_FPS);
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

        if (frameEvent.closeRequested) {
            destroy();
            return;
        }

        final int srcWidth = renderer.bufferWidth();
        final int srcHeight = renderer.bufferHeight();

        renderer.beginFrame(pixelWidth, pixelHeight, guiWidth, guiHeight);
        long blitTexture = 0L;
        ByteBuffer pixels = null;
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

            if (borrowedDevice) {
                // Same-device direct blit: our emulated FBO texture already lives on Angelica's
                // device, so presenting it needs no readback, no transfer and no fence-wait.
                // Content trails the recording by one submit (it executes with the main frame),
                // which is invisible at UI rates. Falls back to the upload path below when the
                // texture is unknown or not blit-source-capable.
                final int colorTex = renderer.colorTexture();
                final long gpuTex = SdlGpuBackend.gpuTexture(colorTex);
                if (gpuTex != 0L && SdlGpuBackend.isSampleable(colorTex)) {
                    blitTexture = gpuTex;
                }
            }
            if (blitTexture == 0L) {
                // Portable path, always same-device: read the FBO back while it is still bound,
                // then stage it through a transfer buffer owned by whichever device we present on.
                final int swapFormat = SdlGpuPresenter.swapchainFormat(device, sdlPtr);
                if (upload.ensure(device, srcWidth, srcHeight, swapFormat)) {
                    final int glFormat = SdlGpuPresenter.isBgrFormat(swapFormat) ? GL12.GL_BGRA : GL11.GL_RGBA;
                    pixels = upload.pixels(srcWidth, srcHeight);
                    renderer.readback(pixels, srcWidth, srcHeight, glFormat);
                    pixels.rewind();
                } else {
                    pixels = null;
                    Offscreen.LOG.warn("[Offscreen] SDLGPU upload surface unavailable, dropping frame: {}", sdlError());
                }
            }
        } finally {
            renderer.endFrame(mc);
        }

        if (renderer.fbo() == 0) return;
        if (blitTexture != 0L) {
            SdlGpuPresenter.present(device, sdlPtr, blitTexture, srcWidth, srcHeight, SdlGpuPresenter.FLIP_NONE);
        } else if (pixels != null) {
            SdlGpuPresenter
                .presentPixels(device, sdlPtr, upload, pixels, srcWidth, srcHeight, SdlGpuPresenter.FLIP_VERTICAL);
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
                    final int sdlScanCode = key.scancode();
                    final int lwjglKey = KeyCodes.sdlKeycodeToLwjgl(sdlKeyCode);
                    // Use sdlKeyCode directly for char (respects layout, avoids scancode->keycode roundtrip
                    // which can be off by one if struct layout mismatches). PlanNH does the same
                    // scancode->keycode via SDL_GetKeyFromScancode, but we keep both for comparison
                    // and prefer sdlKeyCode when it is printable.
                    final int rawKeyCode = SDLKeyboard.SDL_GetKeyFromScancode(sdlScanCode, key.mod(), false);
                    char c = Character.MIN_VALUE;
                    int charSource = sdlKeyCode;
                    // Prefer sdlKeyCode if it is printable, else fall back to raw
                    if (sdlKeyCode >= SDLKeycode.SDLK_SPACE && sdlKeyCode <= SDLKeycode.SDLK_TILDE) {
                        charSource = sdlKeyCode;
                    } else if (rawKeyCode >= SDLKeycode.SDLK_SPACE && rawKeyCode <= SDLKeycode.SDLK_TILDE) {
                        charSource = rawKeyCode;
                    } else {
                        charSource = -1;
                    }
                    if (charSource != -1) {
                        c = (char) charSource;
                        if ((key.mod() & SDLKeycode.SDL_KMOD_CTRL) != 0) {
                            c = (char) (sdlKeyCode & 0x1f);
                        }
                        // Debug aid for a->s shift: log mismatches between scancode-derived and keycode
                        if (rawKeyCode != sdlKeyCode && rawKeyCode >= 32
                            && rawKeyCode <= 126
                            && sdlKeyCode >= 32
                            && sdlKeyCode <= 126) {
                            Offscreen.LOG.debug(
                                "[Offscreen] keycode mismatch scancode {} -> raw {} ('{}') vs sdlKey {} ('{}') lwjgl {}",
                                sdlScanCode,
                                rawKeyCode,
                                (char) rawKeyCode,
                                sdlKeyCode,
                                (char) sdlKeyCode,
                                lwjglKey);
                        }
                    }
                    if (frameEvent.keyCount < frameEvent.keys.length) {
                        frameEvent.keys[frameEvent.keyCount++].set(lwjglKey, c, key.down())
                            .setSdl(sdlKeyCode, sdlScanCode, key.mod());
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
        if (!isOpen() || screenStack.isEmpty()) return;
        screenStack.runScoped(HostedScreen::update);
    }
}
