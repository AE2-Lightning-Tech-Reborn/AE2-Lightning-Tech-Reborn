package com.moakiee.ae2lt.machine.largeoverload;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Loaded factories only. Never loads a chunk and never scans candidate cubes on unrelated changes. */
@EventBusSubscriber(modid = "ae2lt")
public final class LargeFactoryWorld {
    private static final Map<Level, LargeFactoryWorld> WORLDS = new WeakHashMap<>();
    public final LargeFactoryStructureOwnership ownership = new LargeFactoryStructureOwnership();
    private final Map<BlockPos, LargeFactoryControllerBlockEntity> controllers = new HashMap<>();
    private final Map<net.minecraft.world.level.ChunkPos, java.util.Set<LargeFactoryControllerBlockEntity>> candidates = new HashMap<>();
    public static LargeFactoryWorld get(Level level) { return WORLDS.computeIfAbsent(level, ignored -> new LargeFactoryWorld()); }
    public void add(LargeFactoryControllerBlockEntity controller) {
        controllers.put(controller.getBlockPos(), controller);
        var covered = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        var a = LargeFactoryStructure.worldPosition(controller.getBlockPos(), BlockPos.ZERO, controller.facing());
        var b = LargeFactoryStructure.worldPosition(controller.getBlockPos(), new BlockPos(8, 6, 8), controller.facing());
        for (int x = Math.min(a.getX(), b.getX()) >> 4; x <= Math.max(a.getX(), b.getX()) >> 4; x++)
            for (int z = Math.min(a.getZ(), b.getZ()) >> 4; z <= Math.max(a.getZ(), b.getZ()) >> 4; z++)
                covered.add(new net.minecraft.world.level.ChunkPos(x, z));
        for (var chunk : covered) candidates.computeIfAbsent(chunk, ignored -> new java.util.HashSet<>()).add(controller);
    }
    public void remove(LargeFactoryControllerBlockEntity controller) {
        controllers.remove(controller.getBlockPos(), controller);
        candidates.values().forEach(set -> set.remove(controller));
        candidates.values().removeIf(java.util.Set::isEmpty);
        controller.suspend();
    }
    public static void changed(Level level, BlockPos position) {
        if (level == null || level.isClientSide) return;
        var runtime = WORLDS.get(level);
        if (runtime == null) return;
        for (var binding : runtime.ownership.invalidateAt(position)) {
            var controller = runtime.controllers.get(binding.formation().controller());
            if (controller != null) controller.suspend();
        }
        for (var controller : runtime.candidates.getOrDefault(new net.minecraft.world.level.ChunkPos(position), java.util.Set.of()))
            if (controller.contains(position)) controller.requestScan();
    }
    public static boolean firmamentOwned(Level level, BlockPos position) {
        var runtime = WORLDS.get(level);
        var binding = runtime == null ? null : runtime.ownership.at(position);
        return binding != null && binding.formation().firmament();
    }
    @SubscribeEvent public static void unloadChunk(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var runtime = WORLDS.get(level);
        if (runtime == null) return;
        for (var controller : runtime.candidates.getOrDefault(event.getChunk().getPos(), java.util.Set.of())) controller.requestScan();
        for (var binding : runtime.ownership.invalidateChunk(event.getChunk().getPos())) {
            var controller = runtime.controllers.get(binding.formation().controller());
            if (controller != null) { controller.suspend(); controller.requestScan(); }
        }
    }
    @SubscribeEvent public static void loadChunk(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var runtime = WORLDS.get(level);
        if (runtime == null) return;
        // Loading does not inspect block entities before the chunk is ready; controllers scan on their tick.
        for (var controller : runtime.candidates.getOrDefault(event.getChunk().getPos(), java.util.Set.of())) controller.requestScan();
    }
    @SubscribeEvent public static void endTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        var runtime = WORLDS.get(event.getLevel());
        if (runtime != null && !event.getLevel().isClientSide) {
            for (var controller : java.util.List.copyOf(runtime.controllers.values())) controller.endTick();
        }
    }
    @SubscribeEvent public static void unloadWorld(LevelEvent.Unload event) { WORLDS.remove(event.getLevel()); }
}
