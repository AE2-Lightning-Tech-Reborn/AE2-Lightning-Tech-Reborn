package com.moakiee.ae2lt.celestweave.service;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import com.moakiee.ae2lt.celestweave.ArmorWearer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;

public final class ArmorTickService {
    private static final WeakHashMap<LivingEntity, EntityTicks> ENTITY_TICKS = new WeakHashMap<>();
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private ArmorTickService() {
    }

    public static void tickEquipped(
            LivingEntity player,
            ItemStack armor,
            boolean equipped,
            HolderLookup.Provider registries,
            Dist dist) {
        equipped = equipped && ArmorWearer.isEligible(player);
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        // Creative copies retain the armor id and therefore share its runtime entry. An idle
        // inventory copy must not switch off the equipped source (including phase-vault armor).
        if (!equipped && ArmorWearer.isEligible(player) && hasEquippedArmorId(player, armorId)) {
            return;
        }
        if (equipped && !(player instanceof Player) && !firstEntityTick(player, armorId)) {
            return;
        }
        var installedSubmodules = CelestweaveArmorState.collectInstalledSubmoduleEntries(armor, registries);
        if (!equipped) {
            CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, false, dist);
            return;
        }

        if (!player.level().isClientSide()) {
            ArmorEnergyService.refillFromBoundNetworkIfLow(player, armor, registries);
            if (!ArmorEnergyService.consumePassiveDrain(player, armor, installedSubmodules, registries)) {
                // Publish the forced-off state to the client so derived activity (e.g. dig affinity
                // in BreakSpeed) matches the server instead of staying on with no power.
                CelestweaveArmorState.setModulesPowered(armor, false);
                // A paid shield window and free multidimensional protection survive depletion.
                // Core presence and the user's enabled toggle still gate each module.
                CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, true, dist);
                CelestweaveArmorState.tickEquipped(player, armor, installedSubmodules, registries);
                return;
            }
            CelestweaveArmorState.setModulesPowered(armor, true);
        }

        CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, true, dist);
        CelestweaveArmorState.tickActiveSubmodules(player, armor, installedSubmodules, dist);
        CelestweaveArmorState.tickEquipped(player, armor, installedSubmodules, registries);
    }

    private static synchronized boolean firstEntityTick(LivingEntity wearer, UUID armorId) {
        var ticks = ENTITY_TICKS.computeIfAbsent(wearer, ignored -> new EntityTicks());
        long now = wearer.level().getGameTime();
        if (ticks.gameTime != now) {
            ticks.gameTime = now;
            ticks.armorIds.clear();
        }
        return ticks.armorIds.add(armorId);
    }

    private static final class EntityTicks {
        private long gameTime = Long.MIN_VALUE;
        private final Set<UUID> armorIds = new HashSet<>();
    }

    private static boolean hasEquippedArmorId(LivingEntity player, UUID armorId) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = CelestweaveEquipmentAccess.findArmor(player, slot);
            if (armorId.equals(CelestweaveArmorState.getArmorId(equipped))) {
                return true;
            }
        }
        return false;
    }
}
