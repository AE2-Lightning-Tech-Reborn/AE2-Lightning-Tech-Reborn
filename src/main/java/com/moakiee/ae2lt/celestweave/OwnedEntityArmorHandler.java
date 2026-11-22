package com.moakiee.ae2lt.celestweave;

import java.util.List;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorTickService;

/** Mobs do not reliably call Item.inventoryTick for armor, so tick equipped items explicitly. */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OwnedEntityArmorHandler {
    private static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private OwnedEntityArmorHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity wearer && !(wearer instanceof Player)
                && !wearer.level().isClientSide()) {
            tickArmor(wearer);
        }
    }

    public static void tickArmor(LivingEntity wearer) {
        boolean hasArmor = false;
        for (var slot : SLOTS) {
            var armor = CelestweaveEquipmentAccess.findArmor(wearer, slot);
            if (armor.isEmpty()) {
                continue;
            }
            hasArmor = true;
            ArmorTickService.tickEquipped(wearer, armor, true, wearer.registryAccess(), Dist.DEDICATED_SERVER);
        }
        if (hasArmor) {
            ArmorCapabilityCollector.clearCache(wearer);
            CelestweaveArmorUtilityHandler.tickEntityUtilities(wearer);
            CelestweaveArmorUndyingHandler.tryProtectDeadOrDying(wearer);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity wearer) || wearer instanceof Player
                || wearer.level().isClientSide()) {
            return;
        }
        ArmorCapabilityCollector.clearCache(wearer);
        for (var slot : SLOTS) {
            var armor = CelestweaveEquipmentAccess.findArmor(wearer, slot);
            if (!armor.isEmpty()) {
                CelestweaveArmorState.syncSubmoduleActiveState(
                        wearer, armor, wearer.registryAccess(), false, Dist.DEDICATED_SERVER);
                CelestweaveArmorState.clearTransientRuntimeAndCaches(armor);
            }
        }
    }
}
