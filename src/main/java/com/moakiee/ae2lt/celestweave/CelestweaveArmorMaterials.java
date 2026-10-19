package com.moakiee.ae2lt.celestweave;

import java.util.Map;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 1.20.1 defines {@link ArmorMaterial} as an interface (1.21 turned it into a
 * registered record), so the celestweave material is implemented directly
 * instead of being registered through a DeferredRegister.
 */
public final class CelestweaveArmorMaterials {
    private static final int ENCHANTMENT_VALUE = 32;

    private static final Map<net.minecraft.world.entity.EquipmentSlot, Integer> DEFENSE = Map.of(
            net.minecraft.world.entity.EquipmentSlot.HEAD, 6,
            net.minecraft.world.entity.EquipmentSlot.CHEST, 12,
            net.minecraft.world.entity.EquipmentSlot.LEGS, 8,
            net.minecraft.world.entity.EquipmentSlot.FEET, 5);

    public static final ArmorMaterial CELESTWEAVE = new ArmorMaterial() {
        @Override
        public int getDurabilityForSlot(net.minecraft.world.entity.EquipmentSlot type) {
            return 0;
        }

        @Override
        public int getDefenseForSlot(net.minecraft.world.entity.EquipmentSlot type) {
            return DEFENSE.getOrDefault(type, 0);
        }

        @Override
        public int getEnchantmentValue() {
            return ENCHANTMENT_VALUE;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_GENERIC;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "celestweave";
        }

        @Override
        public float getToughness() {
            return 5.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.2F;
        }
    };

    private CelestweaveArmorMaterials() {
    }
}
