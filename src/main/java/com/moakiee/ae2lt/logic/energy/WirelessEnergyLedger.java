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
    private final Map<UUID, EnergyEscrow> accounts = new LinkedHashMap<>();
    public static WirelessEnergyLedger get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(WirelessEnergyLedger::new, WirelessEnergyLedger::load), "ae2lt_wireless_energy_ledger");
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
            list.add(row);
        });
        tag.put("accounts", list);
        return tag;
    }
}
