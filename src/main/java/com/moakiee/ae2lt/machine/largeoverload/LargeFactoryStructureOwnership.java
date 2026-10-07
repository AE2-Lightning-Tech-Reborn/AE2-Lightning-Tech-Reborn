package com.moakiee.ae2lt.machine.largeoverload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Server-thread ownership for this factory only. Each level owns one instance; no inventory lives here. */
public final class LargeFactoryStructureOwnership {
    private final Map<UUID, Binding> machines = new HashMap<>();
    private final Map<BlockPos, Binding> members = new HashMap<>();
    private final Map<ChunkPos, Set<Binding>> chunks = new HashMap<>();

    /** All members are checked before any are claimed. A failed candidate acquires nothing. */
    public Optional<Binding> claim(UUID machineId, LargeFactoryStructure.Formation formation) {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(formation, "formation");
        if (machines.containsKey(machineId)) return Optional.empty();
        for (var member : formation.members()) {
            if (members.containsKey(member.position())) return Optional.empty();
        }
        Binding binding = new Binding(this, machineId, formation);
        machines.put(machineId, binding);
        for (var member : formation.members()) members.put(member.position(), binding);
        for (ChunkPos chunk : binding.chunks) {
            chunks.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(binding);
        }
        return Optional.of(binding);
    }

    public boolean isCurrent(Binding binding) {
        return binding != null && binding.owner == this && binding.valid;
    }

    public boolean owns(Binding binding, BlockPos member) {
        return isCurrent(binding) && members.get(member) == binding;
    }

    public Binding at(BlockPos position) { return members.get(position); }

    public void release(Binding binding) {
        if (binding == null || !machines.remove(binding.machineId, binding)) return;
        binding.valid = false;
        for (var member : binding.formation.members()) members.remove(member.position(), binding);
        for (ChunkPos chunk : binding.chunks) {
            Set<Binding> set = chunks.get(chunk);
            set.remove(binding);
            if (set.isEmpty()) chunks.remove(chunk);
        }
    }

    /** Interior air is watched too: filling it must close the old service gate immediately. */
    public List<Binding> invalidateAt(BlockPos changed) {
        List<Binding> affected = chunks.getOrDefault(new ChunkPos(changed), Set.of()).stream()
                .filter(binding -> binding.contains(changed)).toList();
        affected.forEach(this::release);
        return affected;
    }

    public List<Binding> invalidateChunk(ChunkPos unloaded) {
        List<Binding> affected = List.copyOf(chunks.getOrDefault(unloaded, Set.of()));
        affected.forEach(this::release);
        return affected;
    }

    /** Opaque identity token: an earlier wrapper cannot silently inherit a newly formed machine. */
    public static final class Binding {
        private final LargeFactoryStructureOwnership owner;
        private boolean valid = true;
        private final UUID machineId;
        private final LargeFactoryStructure.Formation formation;
        private final BlockPos min;
        private final BlockPos max;
        private final Set<ChunkPos> chunks;

        private Binding(LargeFactoryStructureOwnership owner, UUID machineId, LargeFactoryStructure.Formation formation) {
            this.owner = owner;
            this.machineId = machineId;
            this.formation = formation;
            BlockPos a = LargeFactoryStructure.worldPosition(formation.controller(), BlockPos.ZERO, formation.facing());
            BlockPos b = LargeFactoryStructure.worldPosition(formation.controller(), new BlockPos(
                    LargeFactoryStructure.WIDTH - 1, LargeFactoryStructure.HEIGHT - 1,
                    LargeFactoryStructure.DEPTH - 1), formation.facing());
            min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
            Set<ChunkPos> covered = new HashSet<>();
            for (int x = min.getX() >> 4; x <= (max.getX() >> 4); x++) {
                for (int z = min.getZ() >> 4; z <= (max.getZ() >> 4); z++) covered.add(new ChunkPos(x, z));
            }
            chunks = Set.copyOf(covered);
        }

        private boolean contains(BlockPos p) {
            return p.getX() >= min.getX() && p.getX() <= max.getX()
                    && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        }

        public UUID machineId() { return machineId; }
        public LargeFactoryStructure.Formation formation() { return formation; }
    }
}
