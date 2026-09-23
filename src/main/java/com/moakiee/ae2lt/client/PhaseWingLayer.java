package com.moakiee.ae2lt.client;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** 26.1's vanilla WingsLayer reads the equipment asset from the equipped chest stack. */
public final class PhaseWingLayer {
    private PhaseWingLayer() {
    }

    public static void syncVisual(ItemStack chestStack, boolean active) {
        Equippable equipped = chestStack.get(DataComponents.EQUIPPABLE);
        if (equipped == null) {
            return;
        }
        var asset = active ? Optional.of(EquipmentAssets.ELYTRA) : Optional.<net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset>>empty();
        if (equipped.assetId().equals(asset)) {
            return;
        }
        chestStack.set(DataComponents.EQUIPPABLE, new Equippable(
                equipped.slot(), equipped.equipSound(), asset, equipped.cameraOverlay(),
                equipped.allowedEntities(), equipped.dispensable(), equipped.swappable(),
                equipped.damageOnHurt(), equipped.equipOnInteract(), equipped.canBeSheared(),
                equipped.shearingSound()));
    }
}
