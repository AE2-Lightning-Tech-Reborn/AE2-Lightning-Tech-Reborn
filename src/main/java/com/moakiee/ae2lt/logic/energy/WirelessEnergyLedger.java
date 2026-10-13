package com.moakiee.ae2lt.logic.energy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned balances survive removal of their source node; node NBT stores only a claim ID. */
public final class WirelessEnergyLedger extends SavedData {
    public record Origin(String dimension, long pos, String source, UUID player) {}
    public record Balance(UUID id, Origin origin, long available, long uncertain) {}
    private final Map<UUID, Origin> origins = new LinkedHashMap<>();
    private final Map<UUID, EnergyEscrow> accounts = new LinkedHashMap<>();
    public static WirelessEnergyLedger get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(WirelessEnergyLedger::new, WirelessEnergyLedger::load), "ae2lt_wireless_energy_ledger");
    }
    public EnergyEscrow claim(UUID id, appeng.api.networking.IGridNode node) {
        if (!origins.containsKey(id)) {
            var owner = node.getOwner();
            var be = owner instanceof net.minecraft.world.level.block.entity.BlockEntity block ? block
                    : owner instanceof appeng.parts.AEBasePart part ? part.getBlockEntity() : null;
            if (be != null) {
                origins.put(id, new Origin(node.getLevel().dimension().location().toString(), be.getBlockPos().asLong(),
                        owner.getClass().getName(), node.getOwningPlayerProfileId()));
                setDirty();
            }
        }
        return account(id);
    }
    /** Data for a future permission-gated diagnostics UI; no withdrawal or reassignment is exposed. */
    public java.util.List<Balance> inspect(UUID viewer, boolean administrator) {
        return accounts.entrySet().stream().filter(e -> administrator
                || (origins.get(e.getKey()) != null && viewer.equals(origins.get(e.getKey()).player())))
                .map(e -> new Balance(e.getKey(), origins.get(e.getKey()), e.getValue().available(), e.getValue().uncertain())).toList();
    }
    public EnergyEscrow account(UUID id) {
        return accounts.computeIfAbsent(id, ignored -> {
            setDirty();
            return new EnergyEscrow(0, 0, this::setDirty);
        });
    }
    static WirelessEnergyLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new WirelessEnergyLedger();
        var list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var row = list.getCompound(i);
            if (!row.hasUUID("id")) continue;
            // Invalid balances must not silently turn into fresh spendable FE.
            if (row.contains("dimension")) ledger.origins.putIfAbsent(row.getUUID("id"), new Origin(
                    row.getString("dimension"), row.getLong("pos"), row.getString("source"),
                    row.hasUUID("player") ? row.getUUID("player") : null));
            ledger.accounts.putIfAbsent(row.getUUID("id"), new EnergyEscrow(
                    row.getLong("available"), row.getLong("uncertain"), ledger::setDirty));
        }
        return ledger;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        accounts.forEach((id, account) -> {
            var row = new CompoundTag();
            row.putUUID("id", id);
            row.putLong("available", account.available());
            row.putLong("uncertain", account.uncertain());
            var origin = origins.get(id);
            if (origin != null) {
                row.putString("dimension", origin.dimension()); row.putLong("pos", origin.pos());
                row.putString("source", origin.source());
                if (origin.player() != null) row.putUUID("player", origin.player());
            }
            list.add(row);
        });
        tag.put("accounts", list);
        return tag;
    }
}
