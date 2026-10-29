package com.moakiee.ae2lt.celestweave.service;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;

public final class ArmorTickService {
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private ArmorTickService() {
    }

    public static void tickEquipped(
            Player player,
            ItemStack armor,
            boolean equipped,
            HolderLookup.Provider registries,
            Dist dist) {
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        // Creative copies retain the armor id and therefore share its runtime entry. An idle
        // inventory copy must not switch off the equipped source (including phase-vault armor).
        if (!equipped && hasEquippedArmorId(player, armorId)) {
            return;
        }
        var installedSubmodules = CelestweaveArmorState.collectInstalledSubmoduleEntries(armor, registries);
        if (!equipped) {
            CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, false, dist);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ArmorEnergyService.refillFromBoundNetworkIfLow(serverPlayer, armor, registries);
            if (!ArmorEnergyService.consumePassiveDrain(serverPlayer, armor, installedSubmodules, registries)) {
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

    private static boolean hasEquippedArmorId(Player player, UUID armorId) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = CelestweaveEquipmentAccess.findArmor(player, slot);
            if (armorId.equals(CelestweaveArmorState.getArmorId(equipped))) {
                return true;
            }
        }
        return false;
    }
}
