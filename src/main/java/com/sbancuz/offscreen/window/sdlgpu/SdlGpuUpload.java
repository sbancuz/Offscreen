package com.sbancuz.offscreen.window.sdlgpu;

import java.nio.ByteBuffer;

import org.lwjgl.sdl.SDLGPU;
import org.lwjgl.sdl.SDL_GPUTextureCreateInfo;
import org.lwjgl.sdl.SDL_GPUTextureRegion;
import org.lwjgl.sdl.SDL_GPUTextureTransferInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferCreateInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * Per-window GPU upload texture plus staging transfer buffer.
 *
 * <p>
 * Used on the pixel-upload path (main backend is OpenGL, or the zero-copy resolve missed): raw
 * bytes from {@code glReadPixels} are staged through the transfer buffer and copied into the upload
 * texture on the present command buffer, then blitted to the swapchain. Textures and buffers are
 * persistent across frames and recreated only on size/format change.
 */
@Lwjgl3Aware
public final class SdlGpuUpload {

    private long texture;
    private long transfer;
    private int texWidth = -1;
    private int texHeight = -1;
    private int texFormat = -1;

    private ByteBuffer pixels;

    public long texture() {
        return texture;
    }

    /**
     * Reusable direct buffer for {@code glReadPixels} output. Call {@link ByteBuffer#rewind()}
     * after the read, before {@link #upload}.
     */
    public ByteBuffer pixels(final int width, final int height) {
        final int size = width * height * 4;
        if (pixels == null || pixels.capacity() < size) {
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
                pixels = null;
            }
            pixels = MemoryUtil.memAlloc(size);
        }
        pixels.clear();
        return pixels;
    }

    public boolean ensure(final long device, final int width, final int height, final int format) {
        if (device == 0L || width <= 0 || height <= 0) return false;
        if (texture != 0L && transfer != 0L && texWidth == width && texHeight == height && texFormat == format) {
            return true;
        }
        destroy(device);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var textureInfo = SDL_GPUTextureCreateInfo.calloc(stack);
            textureInfo.type(SDLGPU.SDL_GPU_TEXTURETYPE_2D);
            textureInfo.format(format);
            textureInfo.usage(SDLGPU.SDL_GPU_TEXTUREUSAGE_SAMPLER | SDLGPU.SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
            textureInfo.width(width);
            textureInfo.height(height);
            textureInfo.layer_count_or_depth(1);
            textureInfo.num_levels(1);
            textureInfo.sample_count(SDLGPU.SDL_GPU_SAMPLECOUNT_1);
            textureInfo.props(0);
            texture = SDLGPU.SDL_CreateGPUTexture(device, textureInfo);
            if (texture == 0L) return false;

            final var transferInfo = SDL_GPUTransferBufferCreateInfo.calloc(stack);
            transferInfo.usage(SDLGPU.SDL_GPU_TRANSFERBUFFERUSAGE_UPLOAD);
            transferInfo.size(width * height * 4);
            transferInfo.props(0);
            transfer = SDLGPU.SDL_CreateGPUTransferBuffer(device, transferInfo);
            if (transfer == 0L) {
                destroy(device);
                return false;
            }
        }
        texWidth = width;
        texHeight = height;
        texFormat = format;
        return true;
    }

    /**
     * Stage tightly-packed 4-bytes-per-pixel rows into the upload texture. The caller must own
     * {@code cmdbuf} with no copy pass active; the copy pass is opened and closed here.
     */
    public boolean upload(final long device, final long cmdbuf, final ByteBuffer src, final int width,
        final int height) {
        final int size = width * height * 4;
        if (device == 0L || cmdbuf == 0L || texture == 0L || transfer == 0L) return false;
        if (src == null || src.remaining() < size) return false;

        final ByteBuffer dst = SDLGPU.SDL_MapGPUTransferBuffer(device, transfer, true, size);
        if (dst == null) return false;
        MemoryUtil.memCopy(MemoryUtil.memAddress(src), MemoryUtil.memAddress(dst), size);
        SDLGPU.SDL_UnmapGPUTransferBuffer(device, transfer);

        final long copyPass = SDLGPU.SDL_BeginGPUCopyPass(cmdbuf);
        if (copyPass == 0L) return false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var transferInfo = SDL_GPUTextureTransferInfo.calloc(stack);
            transferInfo.transfer_buffer(transfer);
            transferInfo.offset(0);
            transferInfo.pixels_per_row(width);
            transferInfo.rows_per_layer(height);

            final var region = SDL_GPUTextureRegion.calloc(stack);
            region.texture(texture);
            region.mip_level(0);
            region.layer(0);
            region.x(0);
            region.y(0);
            region.z(0);
            region.w(width);
            region.h(height);
            region.d(1);

            SDLGPU.SDL_UploadToGPUTexture(copyPass, transferInfo, region, true);
        } finally {
            SDLGPU.SDL_EndGPUCopyPass(copyPass);
        }
        return true;
    }

    public void destroy(final long device) {
        if (device != 0L) {
            if (texture != 0L) {
                SDLGPU.SDL_ReleaseGPUTexture(device, texture);
            }
            if (transfer != 0L) {
                SDLGPU.SDL_ReleaseGPUTransferBuffer(device, transfer);
            }
        }
        texture = 0L;
        transfer = 0L;
        texWidth = -1;
        texHeight = -1;
        texFormat = -1;
    }

    public void freePixels() {
        if (pixels != null) {
            MemoryUtil.memFree(pixels);
            pixels = null;
        }
    }
}
