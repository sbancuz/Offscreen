package com.sbancuz.offscreen.window.sdlgpu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * Reflective bridge to Angelica's SDL GPU backend.
 *
 * <p>
 * Used only to <em>borrow</em> Angelica's {@code SDL_GPUDevice} and to resolve our emulated GL
 * textures to same-device {@code SDL_GPUTexture} handles for direct blits. Everything here degrades
 * to 0/-1 on any failure (Angelica absent, GL backend, reflection blocked), in which case callers
 * use the owned-device upload path instead. Never stores frame state and never submits anything on
 * Angelica's behalf: presenting stays on our own command buffers.
 */
@Lwjgl3Aware
public final class SdlGpuBackend {

    /** {@code SDL_GPU_TEXTUREUSAGE_SAMPLER}. */
    public static final int TEXTURE_USAGE_SAMPLER = 1 << 0;

    private SdlGpuBackend() {}

    private static volatile boolean deviceProbed;
    private static volatile Method gateDeviceMethod;
    private static volatile Method getDeviceMethod;

    /**
     * @return Angelica's raw {@code SDL_GPUDevice} handle, or 0 when there is none (GL backend,
     *         Angelica absent, probe failed). Borrowed: never destroy or release the device itself.
     */
    public static long angelicaDevice() {
        if (!deviceProbed) {
            probeDevice();
        }
        if (gateDeviceMethod == null || getDeviceMethod == null) return 0L;
        try {
            final Object device = gateDeviceMethod.invoke(null);
            if (device == null) return 0L;
            final Object handle = getDeviceMethod.invoke(device);
            return handle instanceof Long value ? value : 0L;
        } catch (ReflectiveOperationException | LinkageError | SecurityException | ClassCastException e) {
            return 0L;
        }
    }

    private static synchronized void probeDevice() {
        if (deviceProbed) return;
        deviceProbed = true;
        try {
            final Class<?> gate = Class.forName("com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate");
            gateDeviceMethod = gate.getMethod("device");
            final Object device = gateDeviceMethod.invoke(null);
            if (device == null) {
                gateDeviceMethod = null;
                return;
            }
            getDeviceMethod = device.getClass()
                .getMethod("getDevice");
        } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
            gateDeviceMethod = null;
            getDeviceMethod = null;
        }
    }

    private static volatile boolean resourceProbed;
    private static volatile Object resourceManager;
    private static volatile Method getTextureHandleMethod;
    private static volatile Method getTextureMetaMethod;
    private static volatile Method textureUsageMethod;

    /**
     * @return the raw {@code SDL_GPUTexture} on Angelica's device for an emulated GL texture id,
     *         or 0 when unknown. Same-device only: blit this solely on {@link #angelicaDevice()}.
     */
    public static long gpuTexture(final int glTexId) {
        if (glTexId == 0) return 0L;
        if (!resourceProbed) {
            probeResourceManager();
        }
        if (resourceManager == null || getTextureHandleMethod == null) return 0L;
        try {
            final Object result = getTextureHandleMethod.invoke(resourceManager, glTexId);
            return result instanceof Long handle ? handle : 0L;
        } catch (ReflectiveOperationException | LinkageError | SecurityException | ClassCastException e) {
            return 0L;
        }
    }

    /**
     * @return {@code true} when the emulated texture is usable as a blit source (has
     *         {@code SAMPLER} usage). Unknown (no meta) counts as not usable.
     */
    public static boolean isSampleable(final int glTexId) {
        if (glTexId == 0) return false;
        if (!resourceProbed) {
            probeResourceManager();
        }
        if (resourceManager == null || getTextureMetaMethod == null) return false;
        try {
            final Object meta = getTextureMetaMethod.invoke(resourceManager, glTexId);
            if (meta == null) return false;
            Method usage = textureUsageMethod;
            if (usage == null) {
                usage = meta.getClass()
                    .getMethod("usage");
                textureUsageMethod = usage;
            }
            final Object bits = usage.invoke(meta);
            return bits instanceof Integer value && (value & TEXTURE_USAGE_SAMPLER) != 0;
        } catch (ReflectiveOperationException | LinkageError | SecurityException | ClassCastException e) {
            return false;
        }
    }

    private static synchronized void probeResourceManager() {
        if (resourceProbed) return;
        resourceProbed = true;
        try {
            final Class<?> backendManager = Class.forName("com.gtnewhorizons.angelica.glsm.backend.BackendManager");
            final Field renderBackend = backendManager.getField("RENDER_BACKEND");
            final Object backend = renderBackend.get(null);
            if (backend == null) return;
            final Method isSdlGpu = backend.getClass()
                .getMethod("isSDLGPU");
            if (!Boolean.TRUE.equals(isSdlGpu.invoke(backend))) return;
            final Field managerField = backend.getClass()
                .getDeclaredField("resourceManager");
            managerField.setAccessible(true);
            final Object manager = managerField.get(backend);
            if (manager == null) return;
            getTextureHandleMethod = manager.getClass()
                .getMethod("getTextureHandle", int.class);
            getTextureMetaMethod = manager.getClass()
                .getMethod("getTextureMeta", int.class);
            resourceManager = manager;
        } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
            resourceManager = null;
            getTextureHandleMethod = null;
            getTextureMetaMethod = null;
            textureUsageMethod = null;
        }
    }
}
