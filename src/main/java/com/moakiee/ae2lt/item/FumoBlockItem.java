package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.registry.ModFumos;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public class FumoBlockItem extends BlockItem  {
    @Nullable
    private final String tooltipKey;

    public FumoBlockItem(Block block, Item.Properties properties) {
        this(block, properties, null);
    }

    public FumoBlockItem(Block block, Item.Properties properties, String tooltipKey) {
        super(block, properties.useBlockDescriptionPrefix().equippable(EquipmentSlot.HEAD));
        this.tooltipKey = tooltipKey;
    }

    public static boolean isPigmee(ItemStack stack) {
        return stack.is(ModFumos.PIGMEE_FUMO_ITEM.get())
                || stack.is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())
                || stack.is(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get())
                || stack.is(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get());
    }

    public static boolean isWearingPigmee(Player player) {
        return isPigmee(player.getItemBySlot(EquipmentSlot.HEAD))
                || (ModList.get().isLoaded("curios") && CuriosBridge.isWearingPigmee(player));
    }

    /** Keep optional Curios classes behind the mod-presence check; cosmetic slots grant no effect. */
    private static final class CuriosBridge {
        private static boolean isWearingPigmee(Player player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.getStacksHandler("head"))
                    .map(handler -> {
                        var stacks = handler.getStacks();
                        for (int slot = 0; slot < stacks.getSlots(); slot++) {
                            if (isPigmee(stacks.getStackInSlot(slot))) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .orElse(false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
                                java.util.function.Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, tooltipFlag);
        if (tooltipKey == null) {
            return;
        }
        tooltipComponents.accept(Component.translatable(tooltipKey + ".1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(tooltipKey + ".2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(tooltipKey + ".3").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(tooltipKey + ".4").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (isPigmee(stack)) {
            tooltipComponents.accept(Component.translatable("tooltip.ae2lt.pigmee_lightning_sense")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
