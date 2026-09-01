package com.sbancuz.offscreen;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int focusedFps = 60;
    public static int unfocusedFps = 10;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        focusedFps = configuration.getInt(
            "focusedFps",
            Configuration.CATEGORY_GENERAL,
            60,
            1,
            240,
            "FPS limit when the offscreen window is focused");

        unfocusedFps = configuration.getInt(
            "unfocusedFps",
            Configuration.CATEGORY_GENERAL,
            10,
            1,
            60,
            "FPS limit when the offscreen window is not focused");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
