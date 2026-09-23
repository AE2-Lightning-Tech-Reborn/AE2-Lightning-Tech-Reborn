package com.moakiee.ae2lt.celestweave;

import java.util.Map;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class CelestweaveArmorMaterials {
    public static final ArmorMaterial CELESTWEAVE = new ArmorMaterial(
            0,
            Map.of(
                    ArmorType.HELMET, 6,
                    ArmorType.CHESTPLATE, 12,
                    ArmorType.LEGGINGS, 8,
                    ArmorType.BOOTS, 5),
            32,
            SoundEvents.ARMOR_EQUIP_GENERIC,
            5.0F,
            0.2F,
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "repairs_celestweave")),
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave")));

    private CelestweaveArmorMaterials() {
    }
}
