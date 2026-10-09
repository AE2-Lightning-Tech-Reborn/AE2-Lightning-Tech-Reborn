package com.moakiee.ae2lt.machine.largeoverload;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** A one-time ledger reference, so copying item NBT cannot duplicate stored fluid/lightning/products. */
public final class LargeFactoryRecoveryItem extends Item {
    public LargeFactoryRecoveryItem(Properties properties) { super(properties); }
    public static ItemStack create(UUID account) {
        var stack = new ItemStack(LargeFactoryRegistration.RECOVERY.get());
        stack.getOrCreateTag().putUUID("FactoryAccount", account);
        return stack;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var tag = context.getItemInHand().getOrCreateTag();
        if (!tag.hasUUID("FactoryAccount") || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof LargeFactoryHatchBlockEntity hatch)) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            boolean complete = hatch.recoverParcel(tag.getUUID("FactoryAccount"));
            if (complete) context.getItemInHand().shrink(1);
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable(
                    complete ? "ae2lt.large_factory.recovery_complete" : "ae2lt.large_factory.recovery_pending"), true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ae2lt.large_factory.recovery_hint"));
    }
}
