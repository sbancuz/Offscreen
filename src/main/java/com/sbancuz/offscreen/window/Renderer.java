package com.sbancuz.offscreen.window;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.system.MemoryUtil;

import com.sbancuz.offscreen.Offscreen;

public final class Renderer {

    private int targetFps = 60;
    private int step = 1000 / targetFps;
    private long nextFrameMs = 0;

    private int framebuffer;
    private int colorTexture;
    private int depthStencilBuffer;

    private int bufferWidth = -1;
    private int bufferHeight = -1;

    // Double-buffered async readback: one slot is filling while the other presents.
    private final int[] pbos = new int[] { 0, 0 };
    private final long[] fences = new long[] { 0L, 0L };
    private final int[] slotWidths = new int[2];
    private final int[] slotHeights = new int[2];

    private int fillSlot;
    /** Slot of the most recent readback kick (this frame's data, still possibly in flight). */
    private int lastFilledSlot = -1;
    /**
     * Slot holding the PREVIOUS frame's finished pixels; presented with one frame of lag so
     * the GPU->CPU copy overlaps the next frame's game rendering instead of stalling it.
     */
    private int prevFilledSlot = -1;

    /** Currently mapped PBO slot during collectReady()/present(), -1 when none. */
    private int presentingSlot = -1;
    private ByteBuffer mappedPixels;

    private int savedFbo;
    private final float[] savedClear = new float[4];
    private final ByteBuffer maskScratch = ByteBuffer.allocateDirect(4);

    private boolean savedMaskR = true;
    private boolean savedMaskG = true;
    private boolean savedMaskB = true;
    private boolean savedMaskA = true;

    public boolean isTimeToRender() {
        final long now = System.currentTimeMillis();
        if (now < nextFrameMs) return false;
        // TODO: move this
        nextFrameMs = now + step;

        return true;
    }

    /**
     * Saves game state and binds our target with the overlay matrix recipe. Call immediately
     * before drawing UI; pair with exactly one {@link #endFrame} (which runs even if drawing
     * threw - the manager wraps both in try/finally).
     */
    public void beginFrame(final int width, final int height, final int guiWidth, final int guiHeight) {
        savedFbo = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, savedClear);
        maskScratch.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskScratch);
        savedMaskR = maskScratch.get(0) != 0;
        savedMaskG = maskScratch.get(1) != 0;
        savedMaskB = maskScratch.get(2) != 0;
        savedMaskA = maskScratch.get(3) != 0;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
        // TODO: BG color
        GL11.glClearColor(0.08f, 0.09f, 0.11f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

        // Fragments never touch dst alpha: the presented surface stays opaque without any
        // per-pixel fixup, regardless of what blending the UI does.
        GL11.glColorMask(true, true, true, false);

        // Normalize the mutable GL state our pass INHERITS from the game frame. The game's
        // last draws leak their leftovers into us: with a container GUI open game-side, the
        // final draw before this callback is the inventory's dim gradient (blend on, current
        // color translucent black) - without this reset every subsequent uncolored vertex
        // renders pre-darkened
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Matrix order mirrors EntityRenderer.setupOverlayRendering EXACTLY:
        // PROJECTION mode -> identity -> ortho (while still in projection mode!),
        // then MODELVIEW mode -> identity -> translate. Any other ordering leaked an identity
        // projection into Angelica's lazily-flushing font renderer in v1.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, guiWidth, guiHeight, 0, 1000D, 3000D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        // CRITICAL: vanilla GUI geometry lives at z = -2000. Without this translate every
        // vertex sits outside the ortho clip range [1000, 3000] and the scene is silently
        GL11.glTranslatef(0f, 0f, -2000f);
    }

    /**
     * Kicks the async readback of the frame just drawn into a free PBO, then restores the
     * game's framebuffer/viewport/clear color/color mask and normalizes the matrix stacks via
     * {@code setupOverlayRendering()} so Angelica's batched flushes capture canonical MVP.
     * Never throws on its own GL work: failures are logged and reported via return value.
     */
    public void endFrame(final Minecraft mc, final int width, final int height) {
        final int slot = fillSlot;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[slot]);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        if (fences[slot] != 0L) GL32.glDeleteSync(fences[slot]);
        fences[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        slotWidths[slot] = width;
        slotHeights[slot] = height;
        prevFilledSlot = lastFilledSlot;
        lastFilledSlot = slot;
        fillSlot = (fillSlot + 1) % pbos.length;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glColorMask(savedMaskR, savedMaskG, savedMaskB, savedMaskA);
        GL11.glClearColor(savedClear[0], savedClear[1], savedClear[2], savedClear[3]);

        // Normalize matrices to EXACTLY what the game pipeline expects to find here. Our
        // drawing leaves an ortho with the MIRROR's GUI dimensions behind; any late draw
        // capturing the current MVP (Angelica's batched font renderer flushes lazily)
        // would render HUD content shrunk by mirrorGui/gameGui
        mc.entityRenderer.setupOverlayRendering();
    }

    /**
     * Waits for the oldest completed readback and maps its buffer for presentation. Must run
     * while MC's context is current and BEFORE any SDL surface operation. The mapped buffer
     * stays mapped until {@link #present} finishes (or fails).
     */
    public void collectReady() {
        if (prevFilledSlot == -1 || presentingSlot != -1) return;
        final int slot = prevFilledSlot;

        if (!waitForFence(slot)) return;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[slot]);
        final long size = (long) slotWidths[slot] * slotHeights[slot] * 4L;
        final ByteBuffer pix = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, size, GL30.GL_MAP_READ_BIT);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        presentingSlot = slot;
        mappedPixels = pix;
    }

    /**
     * Copies the collected pixels into the SDL window surface and presents it. Runs AFTER the
     * game's GL state has been restored; the caller re-asserts MC's context afterwards since
     * surface operations release it as a side effect.
     *
     * @return true if a frame reached the display.
     */
    public boolean present(final SDL_Surface surface, final int width, final int height) {
        final boolean ok = presentNoRelease(surface, width, height);
        SDLSurface.SDL_UnlockSurface(surface);
        return ok;
    }

    private boolean presentNoRelease(final SDL_Surface surface, final int width, final int height) {
        if (presentingSlot == -1 || mappedPixels == null) return false;
        if (slotWidths[presentingSlot] != width || slotHeights[presentingSlot] != height) {
            // Surface lags one event behind a resize; drop the stale frame.
            return false;
        }

        if (surface.w() != width || surface.h() != height) return false;
        final int format = surface.format();
        final boolean argb = format == SDLPixels.SDL_PIXELFORMAT_ARGB8888
            || format == SDLPixels.SDL_PIXELFORMAT_XRGB8888;
        if (!argb) {
            Offscreen.LOG.error("fmt {} unsupported window surface format {}", format, format);
            return false;
        }
        if (!SDLSurface.SDL_LockSurface(surface)) {
            Offscreen.LOG.error("[secondscreen] SDL_LockSurface failed: {}", sdlError());
            return false;
        }
        // Read the pixel pointer AFTER locking: locking can relocate or re-wrap
        // the surface memory on Wayland/EGL-style backends.
        final ByteBuffer dst = surface.pixels();
        final int pitch = surface.pitch();
        if (dst == null || dst.capacity() < pitch * (long) height) {
            return false;
        }

        final ByteBuffer src = mappedPixels;
        src.rewind();

        if (pitch == width * 4) {
            // Fast path: BGRA bytes little-endian ARE ARGB packed ints; bulk-copy
            // each row, flipping GL's bottom-up origin to top-down.
            final long srcAddr = MemoryUtil.memAddress(src);
            final long dstAddr = MemoryUtil.memAddress(dst);
            final long rowBytes = width * 4L;
            for (int y = 0; y < height; y++) {
                MemoryUtil.memCopy(srcAddr + (long) (height - 1 - y) * rowBytes, dstAddr + (long) y * pitch, rowBytes);
            }
        } else {
            // Slow path: honor arbitrary pitch with an explicit row loop.
            for (int y = 0; y < height; y++) {
                src.position((height - 1 - y) * width * 4);
                final int dstRow = y * pitch;
                for (int x = 0; x < width; x++) {
                    final int b = src.get() & 0xFF;
                    final int g = src.get() & 0xFF;
                    final int r = src.get() & 0xFF;
                    src.get(); // alpha forced opaque by the color-mask trick
                    dst.putInt(dstRow + 4 * x, 0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
        }
        return true;
    }

    public boolean ensureCorrectSize(int width, int height) {
        if (bufferWidth == width && bufferHeight == height && framebuffer != 0) return true;
        resetGLContext();

        bufferWidth = width;
        bufferHeight = height;

        // Whatever binding is active when we tear down/setup must be restored afterwards;
        // savedFbo may not be populated yet on the very first frame.
        final int fboToRestore = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        colorTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            width,
            height,
            0,
            GL12.GL_BGRA,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTexture,
            0);
        // MUI2 clips widgets via the stencil buffer and widgets may depth-test: without
        // this attachment every stencil op silently no-ops.
        depthStencilBuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilBuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glFramebufferRenderbuffer(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL30.GL_RENDERBUFFER,
            depthStencilBuffer);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        for (int i = 0; i < pbos.length; i++) {
            pbos[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, width * (long) height * 4L, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

        final int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            Offscreen.LOG.error("[secondscreen] offscreen framebuffer incomplete: {}", status);
            resetGLContext();
            return false;
        }

        fillSlot = 0;
        prevFilledSlot = -1;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboToRestore);
        return true;
    }

    private void resetGLContext() {
        unmapPresentingSlot();
        for (int i = 0; i < fences.length; i++) {
            if (fences[i] != 0L) {
                GL32.glDeleteSync(fences[i]);
                fences[i] = 0L;
            }
        }
        for (int i = 0; i < pbos.length; i++) {
            if (pbos[i] != 0) {
                GL15.glDeleteBuffers(pbos[i]);
                pbos[i] = 0;
            }
        }
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (colorTexture != 0) {
            GL11.glDeleteTextures(colorTexture);
            colorTexture = 0;
        }
        if (depthStencilBuffer != 0) {
            GL30.glDeleteRenderbuffers(depthStencilBuffer);
            depthStencilBuffer = 0;
        }
        bufferWidth = -1;
        bufferHeight = -1;
        fillSlot = 0;
        lastFilledSlot = -1;
        prevFilledSlot = -1;
    }

    private void unmapPresentingSlot() {
        if (presentingSlot == -1) return;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbos[presentingSlot]);
        GL30.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

        presentingSlot = -1;
        mappedPixels = null;
    }

    private boolean waitForFence(final int slot) {
        final long fence = fences[slot];
        if (fence == 0L) return false;

        for (int i = 0; i < 5; i++) {
            // Timeout in ns
            final int rc = GL32.glClientWaitSync(fence, 0, 50_000_000L);
            if (rc == GL32.GL_ALREADY_SIGNALED || rc == GL32.GL_CONDITION_SATISFIED) return true;
            if (rc == GL32.GL_WAIT_FAILED) break;
        }

        return false;
    }

    private static String sdlError() {
        return SDLError.SDL_GetError();
    }
}
