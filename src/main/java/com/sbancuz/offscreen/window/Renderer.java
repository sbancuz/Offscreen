package com.sbancuz.offscreen.window;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import com.sbancuz.offscreen.Config;
import com.sbancuz.offscreen.Offscreen;

public final class Renderer {

    private int targetFps = Config.focusedFps;
    private int maxFps = Integer.MAX_VALUE;
    private int step = 1000 / targetFps;
    private long nextFrameMs = 0;

    private int framebuffer;
    private int colorTexture;
    private int depthStencilBuffer;

    private int bufferWidth = -1;
    private int bufferHeight = -1;

    private int savedFbo;
    // Non-annotated on purpose: Angelica's redirector only matches org.lwjgl call sites (never
    // org.lwjglx) and routes them to the active backend, so this class is emulated under SDL GPU
    // and runs on real GL otherwise. @Lwjgl3Aware would leave these calls unrouted -> segfault.
    // Note: only vanilla-common calls are used here. The 'v' state getters (glGetFloatv and
    // glGetBooleanv) are deliberately avoided: after lwjgl3ify remaps them for the GL backend they
    // don't exist in org.lwjglx and link fails. Background is painted instead of cleared.

    public boolean isTimeToRender() {
        final long now = System.currentTimeMillis();
        if (now < nextFrameMs) return false;
        nextFrameMs = now + step;
        return true;
    }

    public void setFocused(boolean focused) {
        final int newFps = Math.min(focused ? Config.focusedFps : Config.unfocusedFps, maxFps);
        if (targetFps != newFps) {
            targetFps = newFps;
            step = 1000 / targetFps;
            nextFrameMs = 0;
        }
    }

    public void setMaxFps(final int maxFps) {
        this.maxFps = Math.max(maxFps, 1);
    }

    public void beginFrame(final int width, final int height, final int guiWidth, final int guiHeight) {
        savedFbo = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
        // Depth/stencil only: color is painted below, so the ambient clear color is never touched
        // and never needs saving.
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, guiWidth, guiHeight, 0, 1000D, 3000D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glTranslatef(0f, 0f, -2000f);

        // Opaque background via vanilla drawing (balanced GL state). The ambient color mask is
        // left alone, so nothing needs restoring in endFrame.
        Gui.drawRect(0, 0, guiWidth, guiHeight, 0xFF14171C);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public void endFrame(final Minecraft mc) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        mc.entityRenderer.setupOverlayRendering();
    }

    /**
     * Read the offscreen color buffer. Rebinds our framebuffer first: drawing code is free to leave
     * any binding behind, so reading the ambient binding would return the wrong buffer. Call after
     * drawing and before {@link #endFrame(Minecraft)} (which restores the saved binding); the caller
     * rewinds {@code dst} before uploading.
     *
     * @param glFormat {@code GL_BGRA} for B8G8R8A8 swapchains, {@code GL_RGBA} otherwise.
     */
    public void readback(final ByteBuffer dst, final int width, final int height, final int glFormat) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glReadPixels(0, 0, width, height, glFormat, GL11.GL_UNSIGNED_BYTE, dst);
    }

    public int fbo() {
        return framebuffer;
    }

    public int colorTexture() {
        return colorTexture;
    }

    public int bufferWidth() {
        return bufferWidth;
    }

    public int bufferHeight() {
        return bufferHeight;
    }

    public boolean ensureCorrectSize(int width, int height) {
        if (bufferWidth == width && bufferHeight == height && framebuffer != 0) return true;
        resetGLContext();

        bufferWidth = width;
        bufferHeight = height;

        final int fboToRestore = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        colorTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL30.GL_SRGB8_ALPHA8,
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
        depthStencilBuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilBuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glFramebufferRenderbuffer(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL30.GL_RENDERBUFFER,
            depthStencilBuffer);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        final int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            Offscreen.LOG.error("[Offscreen] offscreen framebuffer incomplete: {}", status);
            resetGLContext();
            return false;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboToRestore);
        return true;
    }

    private void resetGLContext() {
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
    }
}
