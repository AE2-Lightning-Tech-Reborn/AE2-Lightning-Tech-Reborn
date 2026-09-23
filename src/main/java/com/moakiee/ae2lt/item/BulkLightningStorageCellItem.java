package com.moakiee.ae2lt.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import appeng.core.localization.PlayerMessages;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.util.InteractionUtil;

/**
 * A two-counter storage cell dedicated to the two lightning key variants.
 *
 * <p>The complete inventory is stored directly on the item stack. Unlike the
 * general-purpose infinite cell, this cell never needs an external UUID-backed
 * index because its serialized shape is always exactly two longs.</p>
 */
public final class BulkLightningStorageCellItem extends Item {
    private static final String TAG_HIGH_VOLTAGE = "ae2lt:bulk_lightning_high_voltage";
    private static final String TAG_EXTREME_HIGH_VOLTAGE = "ae2lt:bulk_lightning_extreme_high_voltage";

    private final double idleDrain;

    public BulkLightningStorageCellItem(Properties properties, double idleDrain) {
        super(properties.stacksTo(1));
        this.idleDrain = idleDrain;
    }

    public double getIdleDrain() {
        return idleDrain;
    }

    public static StoredAmounts readStoredAmounts(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return new StoredAmounts(
                sanitize(tag.getLongOr(TAG_HIGH_VOLTAGE, 0L)),
                sanitize(tag.getLongOr(TAG_EXTREME_HIGH_VOLTAGE, 0L)));
    }

    public static void writeStoredAmounts(ItemStack stack, long highVoltage, long extremeHighVoltage) {
        long sanitizedHighVoltage = sanitize(highVoltage);
        long sanitizedExtremeHighVoltage = sanitize(extremeHighVoltage);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            putOrRemove(tag, TAG_HIGH_VOLTAGE, sanitizedHighVoltage);
            putOrRemove(tag, TAG_EXTREME_HIGH_VOLTAGE, sanitizedExtremeHighVoltage);
        });
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        disassembleCell(player.getItemInHand(hand), level, player);
        return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return disassembleCell(stack, context.getLevel(), context.getPlayer())
                ? (context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER)
                : InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        StoredAmounts amounts = readStoredAmounts(stack);
        tooltipComponents.accept(Component.translatable("tooltip.ae2lt.bulk_lightning_storage.capacity")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltipComponents.accept(Component.translatable(
                "tooltip.ae2lt.bulk_lightning_storage.high_voltage",
                String.format("%,d", amounts.highVoltage()))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(
                "tooltip.ae2lt.bulk_lightning_storage.extreme_high_voltage",
                String.format("%,d", amounts.extremeHighVoltage()))
                .withStyle(ChatFormatting.GRAY));
    }

    private boolean disassembleCell(ItemStack stack, Level level, Player player) {
        if (player == null || !InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }

        var disassembledStacks = level instanceof net.minecraft.server.level.ServerLevel serverLevel
                ? StorageCellDisassemblyRecipe.getDisassemblyResult(serverLevel, stack.getItem())
                : java.util.List.<ItemStack>of();
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        var playerInventory = player.getInventory();
        if (playerInventory.getSelectedItem() != stack) {
            return false;
        }

        var storedAmounts = readStoredAmounts(stack);
        if (storedAmounts.highVoltage() != 0 || storedAmounts.extremeHighVoltage() != 0) {
            com.moakiee.ae2lt.recipe.compat.LegacyPlayerMessages.display(player, PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.getSelectedSlot(), ItemStack.EMPTY);
        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }
        return true;
    }

    private static long sanitize(long amount) {
        return Math.max(0L, amount);
    }

    private static void putOrRemove(CompoundTag tag, String key, long amount) {
        if (amount == 0L) {
            tag.remove(key);
        } else {
            tag.putLong(key, amount);
        }
    }

    public record StoredAmounts(long highVoltage, long extremeHighVoltage) {
    }
}
