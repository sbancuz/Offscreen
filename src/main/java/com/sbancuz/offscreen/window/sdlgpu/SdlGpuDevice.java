package com.sbancuz.offscreen.window.sdlgpu;

import org.lwjgl.sdl.SDLGPU;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.window.Window;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * Owns a single shared {@code SDL_GPUDevice} for all Offscreen second windows.
 *
 * <p>
 * The device is deliberately independent from Angelica's device: Angelica's
 * {@code Device}/{@code FrameManager}/{@code Presenter} track only the main window
 * ({@code getClaimedWindow()} hardcodes {@code Display.getWindow()}), and borrowing its command
 * buffers corrupts the main frame. Sharing one Offscreen-owned device across our windows keeps
 * present lifecycles isolated while working identically whether the main backend is OpenGL or
 * Vulkan. Refcounted: the device dies with the last window.
 */
@Lwjgl3Aware
public final class SdlGpuDevice {

    private SdlGpuDevice() {}

    private static final Object LOCK = new Object();
    private static long device;
    private static int refs;

    /**
     * @return the shared device handle, or 0 when no GPU driver is usable.
     */
    public static long acquire() {
        synchronized (LOCK) {
            if (device == 0L) {
                final int formats = SDLGPU.SDL_GPU_SHADERFORMAT_SPIRV | SDLGPU.SDL_GPU_SHADERFORMAT_DXIL
                    | SDLGPU.SDL_GPU_SHADERFORMAT_MSL;
                // Third parameter is the GPU driver to force (e.g. "vulkan"), NOT a label:
                // null lets SDL pick the best available driver.
                device = SDLGPU.SDL_CreateGPUDevice(formats, false, (CharSequence) null);
                if (device == 0L) {
                    Offscreen.LOG.error("[Offscreen] SDL_CreateGPUDevice failed: {}", Window.sdlError());
                    return 0L;
                }
                if (!SDLGPU.SDL_SetGPUAllowedFramesInFlight(device, 2)) {
                    Offscreen.LOG.warn("[Offscreen] SDL_SetGPUAllowedFramesInFlight failed: {}", Window.sdlError());
                }
            }
            refs++;
            return device;
        }
    }

    public static void release(final long handle) {
        synchronized (LOCK) {
            if (handle == 0L || handle != device) return;
            if (--refs > 0) return;
            refs = 0;
            SDLGPU.SDL_DestroyGPUDevice(device);
            device = 0L;
        }
    }
}
