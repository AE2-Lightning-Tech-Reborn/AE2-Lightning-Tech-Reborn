package com.moakiee.ae2lt.item;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.server.level.ServerLevel;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorMaterials;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockProjectionRules;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockService;

/**
 * Armor-slot projection for a UUID-bound private armor stack. Its registry identity fixes the
 * expected equipment slot; a versioned data-component mirror exposes non-private armor state to
 * vanilla and third-party equipment systems.
 */
public final class PhaseLockProjectionItem extends Item {
    private final EquipmentSlot equipmentSlot;

    public PhaseLockProjectionItem(Properties properties, EquipmentSlot equipmentSlot) {
        super(properties.overrideDescription("item.ae2lt.phase_lock_projection")
                .stacksTo(1).fireResistant().equippable(equipmentSlot)
                .attributes(CelestweaveArmorMaterials.CELESTWEAVE.createAttributes(armorType(equipmentSlot)))
                .enchantable(CelestweaveArmorMaterials.CELESTWEAVE.enchantmentValue()));
        this.equipmentSlot = equipmentSlot;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return equipmentSlot;
    }

    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (slot != equipmentSlot) {
            stack.setCount(0);
            return;
        }
        if (!PhaseLockService.hasPrivateArmor(player, equipmentSlot)) {
            stack.setCount(0);
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                    SoundSource.PLAYERS,
                    0.8F,
                    0.7F);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // The projection always carries binding and vanishing curses. Suppress their glint so the
        // empty armor material cannot leave a visible armor-shaped overlay on the player.
        return false;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.ae2lt.phase_lock_projection.desc"));
    }

    private static ArmorType armorType(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorType.HELMET;
            case CHEST -> ArmorType.CHESTPLATE;
            case LEGS -> ArmorType.LEGGINGS;
            case FEET -> ArmorType.BOOTS;
            default -> throw new IllegalArgumentException("Phase-lock projections require an armor slot");
        };
    }
}
