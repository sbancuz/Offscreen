package com.sbancuz.offscreen.window.sdlgpu;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLGPU;
import org.lwjgl.sdl.SDL_GPUBlitInfo;
import org.lwjgl.system.MemoryStack;

import com.sbancuz.offscreen.Offscreen;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * Presents second-window content through our own SDL GPU command buffers.
 *
 * <p>
 * Never touches Angelica's {@code FrameManager} (its buffers and swapchain bookkeeping belong to
 * the main window). Every present owns its command buffer: acquire, optionally upload, acquire
 * swapchain (non-blocking — a busy swapchain cancels and skips the frame instead of stalling the
 * MC thread), blit, submit.
 */
@Lwjgl3Aware
public final class SdlGpuPresenter {

    /** {@code SDL_FLIP_NONE} — for GPU-side textures, already top-left. */
    public static final int FLIP_NONE = 0;
    /** {@code SDL_FLIP_VERTICAL} — for {@code glReadPixels} bytes, which are bottom-up. */
    public static final int FLIP_VERTICAL = 2;

    private SdlGpuPresenter() {}

    public static int swapchainFormat(final long device, final long window) {
        if (device == 0L || window == 0L) return SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM;
        final int format = SDLGPU.SDL_GetGPUSwapchainTextureFormat(device, window);
        return format == SDLGPU.SDL_GPU_TEXTUREFORMAT_INVALID ? SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM : format;
    }

    public static boolean isBgrFormat(final int swapchainFormat) {
        return swapchainFormat == SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM
            || swapchainFormat == SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB;
    }

    /**
     * Present an already-GPU-resident texture. Source and swapchain must live on the same
     * {@code device} — never mix handles across devices.
     */
    public static boolean present(final long device, final long window, final long srcTexture, final int srcWidth,
        final int srcHeight, final int flip) {
        if (device == 0L || window == 0L || srcTexture == 0L || srcWidth <= 0 || srcHeight <= 0) return false;
        final long cmdbuf = SDLGPU.SDL_AcquireGPUCommandBuffer(device);
        if (cmdbuf == 0L) {
            Offscreen.LOG.debug("[Offscreen] SDL_AcquireGPUCommandBuffer failed: {}", sdlError());
            return false;
        }
        return blitAcquired(device, window, cmdbuf, srcTexture, srcWidth, srcHeight, flip);
    }

    /** Upload pixel bytes then present them on a single command buffer (portable path). */
    public static boolean presentPixels(final long device, final long window, final SdlGpuUpload upload,
        final ByteBuffer pixels, final int width, final int height, final int flip) {
        if (device == 0L || window == 0L || upload == null || upload.texture() == 0L) return false;
        if (pixels == null || pixels.remaining() < width * height * 4) return false;
        final long cmdbuf = SDLGPU.SDL_AcquireGPUCommandBuffer(device);
        if (cmdbuf == 0L) {
            Offscreen.LOG.debug("[Offscreen] SDL_AcquireGPUCommandBuffer failed: {}", sdlError());
            return false;
        }
        if (!upload.upload(device, cmdbuf, pixels, width, height)) {
            Offscreen.LOG.debug("[Offscreen] SDLGPU upload failed: {}", sdlError());
            SDLGPU.SDL_CancelGPUCommandBuffer(cmdbuf);
            return false;
        }
        return blitAcquired(device, window, cmdbuf, upload.texture(), width, height, flip);
    }

    private static boolean blitAcquired(final long device, final long window, final long cmdbuf, final long srcTexture,
        final int srcWidth, final int srcHeight, final int flip) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer texPtr = stack.mallocPointer(1);
            final IntBuffer wPtr = stack.mallocInt(1);
            final IntBuffer hPtr = stack.mallocInt(1);
            if (!SDLGPU.SDL_AcquireGPUSwapchainTexture(cmdbuf, window, texPtr, wPtr, hPtr)) {
                Offscreen.LOG.debug("[Offscreen] SDL_AcquireGPUSwapchainTexture failed: {}", sdlError());
                SDLGPU.SDL_CancelGPUCommandBuffer(cmdbuf);
                return false;
            }
            final long swapchainTex = texPtr.get(0);
            if (swapchainTex == 0L) {
                SDLGPU.SDL_CancelGPUCommandBuffer(cmdbuf);
                return false;
            }
            final int dstWidth = wPtr.get(0);
            final int dstHeight = hPtr.get(0);
            if (dstWidth <= 0 || dstHeight <= 0) {
                SDLGPU.SDL_CancelGPUCommandBuffer(cmdbuf);
                return false;
            }
            final var blit = SDL_GPUBlitInfo.calloc(stack);
            blit.source()
                .texture(srcTexture);
            blit.source()
                .mip_level(0);
            blit.source()
                .layer_or_depth_plane(0);
            blit.source()
                .x(0);
            blit.source()
                .y(0);
            blit.source()
                .w(srcWidth);
            blit.source()
                .h(srcHeight);

            blit.destination()
                .texture(swapchainTex);
            blit.destination()
                .mip_level(0);
            blit.destination()
                .layer_or_depth_plane(0);
            blit.destination()
                .x(0);
            blit.destination()
                .y(0);
            blit.destination()
                .w(dstWidth);
            blit.destination()
                .h(dstHeight);

            blit.load_op(SDLGPU.SDL_GPU_LOADOP_DONT_CARE);
            blit.flip_mode(flip);
            blit.filter(SDLGPU.SDL_GPU_FILTER_LINEAR);
            blit.cycle(false);

            SDLGPU.SDL_BlitGPUTexture(cmdbuf, blit);
            if (!SDLGPU.SDL_SubmitGPUCommandBuffer(cmdbuf)) {
                Offscreen.LOG.debug("[Offscreen] SDL_SubmitGPUCommandBuffer failed: {}", sdlError());
                return false;
            }
            return true;
        } catch (final Exception e) {
            try {
                SDLGPU.SDL_CancelGPUCommandBuffer(cmdbuf);
            } catch (final Exception ignored) {}
            Offscreen.LOG.warn("[Offscreen] SDLGPU present failed", e);
            return false;
        }
    }

    private static String sdlError() {
        return SDLError.SDL_GetError();
    }
}
