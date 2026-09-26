package com.moakiee.ae2lt.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Swaps the vanilla armor shell for the Celestweave model; pose and visibility are copied by NeoForge. */
public final class CelestweaveArmorClientExtensions implements IClientItemExtensions {
    public static final CelestweaveArmorClientExtensions INSTANCE = new CelestweaveArmorClientExtensions();

    private CelestweaveArmorClientExtensions() {
    }

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(
            LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
        HumanoidModel<?> model = CelestweaveArmorModel.forSlot(slot);
        return model != null ? model : original;
    }
}
