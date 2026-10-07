package com.moakiee.ae2lt.machine.largeoverload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import appeng.api.stacks.AEKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative factory-only resources. Block/item NBT carries identities, never a second spendable copy. */
public final class LargeFactoryLedger extends SavedData {
    private final Map<UUID, Account> accounts = new HashMap<>();
    public static final class Account {
        public final UUID id;
        public String dimension;
        public BlockPos owner;
        public UUID origin;
        public final Map<AEKey, Long> resources = new LinkedHashMap<>();
        public double energyCreditAE;
        public long externalFE;
        public long commitSequence;
        public String lastRecipe = "";
        public long lastOperations, lastEnergyFE, lastHigh, lastExtreme;
        public boolean parcel;
        private Account(UUID id) { this.id = id; }
        public boolean empty() { return resources.isEmpty() && energyCreditAE <= 0 && externalFE == 0; }
    }

    public static LargeFactoryLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LargeFactoryLedger::new, LargeFactoryLedger::load, null), "ae2lt_large_factory_ledger");
    }
    public Account claim(UUID id, ServerLevel level, BlockPos position) {
        var account = accounts.get(id);
        String dimension = level.dimension().location().toString();
        if (account == null) {
            account = new Account(id);
            account.dimension = dimension;
            account.owner = position.immutable();
            accounts.put(id, account);
            setDirty();
        }
        return !account.parcel && dimension.equals(account.dimension) && position.equals(account.owner) ? account : null;
    }
    public Account parcel(UUID id) {
        var account = accounts.get(id);
        return account != null && account.parcel ? account : null;
    }
    public void release(Account account) {
        if (account.externalFE > 0) {
            account.energyCreditAE += appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, account.externalFE);
            account.externalFE = 0;
        }
        if (account.empty()) accounts.remove(account.id);
        else { account.parcel = true; account.owner = null; }
        setDirty();
    }
    public void discardEmptyParcel(Account account) {
        if (account.parcel && account.empty()) { accounts.remove(account.id); setDirty(); }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var account : accounts.values()) {
            var entry = new CompoundTag();
            entry.putUUID("Id", account.id);
            entry.putString("Dimension", account.dimension);
            if (account.owner != null) entry.putLong("Owner", account.owner.asLong());
            if (account.origin != null) entry.putUUID("Origin", account.origin);
            entry.putBoolean("Parcel", account.parcel);
            entry.putDouble("EnergyAE", account.energyCreditAE);
            entry.putLong("ExternalFE", account.externalFE);
            entry.putLong("CommitSequence", account.commitSequence);
            entry.putString("LastRecipe", account.lastRecipe);
            entry.putLong("LastOperations", account.lastOperations);
            entry.putLong("LastEnergyFE", account.lastEnergyFE);
            entry.putLong("LastHigh", account.lastHigh);
            entry.putLong("LastExtreme", account.lastExtreme);
            entry.put("Resources", LargeFactoryAmounts.save(account.resources, registries));
            list.add(entry);
        }
        tag.put("Accounts", list);
        return tag;
    }
    private static LargeFactoryLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new LargeFactoryLedger();
        for (Tag value : tag.getList("Accounts", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (!entry.hasUUID("Id")) continue;
            var account = new Account(entry.getUUID("Id"));
            account.dimension = entry.getString("Dimension");
            account.owner = entry.contains("Owner") ? BlockPos.of(entry.getLong("Owner")) : null;
            account.origin = entry.hasUUID("Origin") ? entry.getUUID("Origin") : null;
            account.parcel = entry.getBoolean("Parcel");
            double credit = entry.getDouble("EnergyAE");
            account.energyCreditAE = Double.isFinite(credit) && credit > 0 ? credit : 0;
            account.externalFE = Math.max(0, entry.getLong("ExternalFE"));
            account.commitSequence = Math.max(0, entry.getLong("CommitSequence"));
            account.lastRecipe = entry.getString("LastRecipe");
            account.lastOperations = Math.max(0, entry.getLong("LastOperations"));
            account.lastEnergyFE = Math.max(0, entry.getLong("LastEnergyFE"));
            account.lastHigh = Math.max(0, entry.getLong("LastHigh"));
            account.lastExtreme = Math.max(0, entry.getLong("LastExtreme"));
            account.resources.putAll(LargeFactoryAmounts.load(entry.getList("Resources", Tag.TAG_COMPOUND), registries));
            ledger.accounts.put(account.id, account);
        }
        return ledger;
    }
}
