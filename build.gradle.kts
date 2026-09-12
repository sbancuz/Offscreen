
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    // Opt-in only: Angelica's SDL GPU (Vulkan) backend is experimental and loses the device on
    // several drivers (e.g. ANV here) with zero mod involvement. Uncomment to test it; our
    // second windows work on both backends. Leave commented for the stable OpenGL backend.
     extraRunJvmArguments.add("-Dangelica.sdlgpu.enable=true")
    lwjgl3Bindings.addAll("shaderc", "spvc")
    lwjgl3Version = "3.4.2-SNAPSHOT"
}
