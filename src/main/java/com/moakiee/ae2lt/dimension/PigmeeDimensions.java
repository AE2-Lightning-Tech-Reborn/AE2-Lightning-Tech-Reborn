package com.moakiee.ae2lt.dimension;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.mixin.PigmeeDimensionServerAccessor;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class PigmeeDimensions {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("ae2lt", "pigmee");
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, AE2LightningTech.MODID);
    static { GENERATORS.register("pigmee", () -> PigmeeChunkGenerator.CODEC); }

    private static final ChunkProgressListener QUIET_PROGRESS = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos pos) {}
        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    };

    private PigmeeDimensions() {}

    public static ResourceKey<Level> key(int id) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("ae2lt", "pigmee/" + id));
    }

    public static boolean isPigmee(Level level) {
        return level.dimension().location().getNamespace().equals("ae2lt")
                && level.dimension().location().getPath().startsWith("pigmee/");
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Restore identities before players log in; no chunks are force-loaded.
        for (var entry : PigmeeDimensionData.get(event.getServer()).entries()) {
            if (entry.generated()) getOrCreate(event.getServer(), entry);
        }
    }

    @SuppressWarnings("deprecation")
    public static ServerLevel getOrCreate(MinecraftServer server, PigmeeDimensionData.Entry entry) {
        if (!server.isSameThread()) throw new IllegalStateException("Dimension creation must run on the server thread");
        var key = key(entry.id());
        var existing = server.getLevel(key);
        if (existing != null) return existing;
        var registries = server.registryAccess();
        var type = registries.registryOrThrow(Registries.DIMENSION_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, TYPE));
        var biome = registries.registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(ResourceKey.create(Registries.BIOME, TYPE));
        var generator = new PigmeeChunkGenerator(biome, entry.even(), entry.palette().frame(), entry.palette().center(), entry.palette().cells());
        var accessor = (PigmeeDimensionServerAccessor) server;
        var levelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        var level = new ServerLevel(server, accessor.ae2lt$pigmeeExecutor(), accessor.ae2lt$pigmeeStorageSource(),
                levelData, key, new LevelStem(type, generator), QUIET_PROGRESS, false,
                BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed()), List.of(), false,
                server.overworld().getRandomSequences());
        level.getWorldBorder().applySettings(server.overworld().getWorldBorder().createSettings());
        server.overworld().getWorldBorder().addListener(
                new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder()));
        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();
        PigmeeDimensionData.get(server).markGenerated(entry);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        return level;
    }
}
