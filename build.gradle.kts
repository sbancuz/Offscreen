
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Opt the WHOLE mod jar out of lwjgl3ify's org.lwjgl -> org.lwjglx bytecode remapper
// (spike approach). Per-class @Lwjgl3Aware cannot protect compiler-generated lambdas.
tasks.withType<Jar>().configureEach {
    manifest.attributes["Lwjgl3ify-Aware"] = "true"
}
