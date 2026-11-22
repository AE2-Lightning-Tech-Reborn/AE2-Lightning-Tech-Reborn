package com.moakiee.ae2lt.client.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;

final class VisionTargets {
    private static final Map<Block, Integer> COLORS = new HashMap<>();
    private static boolean loaded;

    private VisionTargets() {}

    static Map<Block, Integer> colors() {
        if (!loaded) {
            loaded = true;
            var file = FMLPaths.CONFIGDIR.get().resolve("ae2lt-vision.properties");
            if (Files.isRegularFile(file)) {
                var settings = new Properties();
                try (var reader = Files.newBufferedReader(file)) {
                    settings.load(reader);
                    for (var key : settings.stringPropertyNames()) {
                        var id = ResourceLocation.tryParse(key);
                        String value = settings.getProperty(key);
                        if (id == null || !value.matches("[0-9a-fA-F]{6}")) continue;
                        BuiltInRegistries.BLOCK.getOptional(id).filter(block -> block != Blocks.AIR
                                && !block.defaultBlockState().isAir()).ifPresent(block -> COLORS.put(block, Integer.parseInt(value, 16)));
                    }
                } catch (IOException | IllegalArgumentException ex) {
                    LogUtils.getLogger().warn("Could not load visual enhancement targets", ex);
                }
            }
        }
        return COLORS;
    }

    static void set(Block block, Integer color) {
        var next = new HashMap<>(colors());
        if (color == null) next.remove(block);
        else next.put(block, color);
        var settings = new Properties();
        next.forEach((target, rgb) -> settings.setProperty(BuiltInRegistries.BLOCK.getKey(target).toString(),
                "%06x".formatted(rgb)));
        var file = FMLPaths.CONFIGDIR.get().resolve("ae2lt-vision.properties");
        try {
            var temp = Files.createTempFile(file.getParent(), "ae2lt-vision-", ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(temp)) {
                    settings.store(writer, "Celestweave block outlines");
                }
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            LogUtils.getLogger().warn("Could not save visual enhancement targets", ex);
            return;
        }
        COLORS.clear();
        COLORS.putAll(next);
        BlockVision.reset();
    }
}
