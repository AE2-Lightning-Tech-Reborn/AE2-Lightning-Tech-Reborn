package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Binding is a reference. Authority always comes from the world's owner record. */
public final class PigmeeRemoteControlItem extends Item {
    private static final String ACTOR = "PigmeeActor";
    public PigmeeRemoteControlItem(Properties properties) { super(properties.stacksTo(1)); }
    public static UUID bound(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(ACTOR) ? tag.getUUID(ACTOR) : null;
    }
    public static void bind(ServerPlayer owner, ItemStack stack, PigmeeServerPlayer actor) {
        if (!PigmeeWorkstationRuntime.enabled() || owner.isFakePlayer() || actor.isRemoved()
                || PigmeeWorkstationRuntime.manager(owner.server).active(actor.getUUID()) != actor
                || owner.level() != actor.level() || !owner.canInteractWithEntity(actor.getBoundingBox(), 0)
                || !PigmeeWorkstationRuntime.manager(owner.server).ownedBy(actor.getUUID(), owner.getUUID())) {
            throw new IllegalStateException("Only the owner can bind a nearby active Pigmee");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(ACTOR, actor.getUUID()));
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner) || !(target instanceof PigmeeServerPlayer actor)) return InteractionResult.PASS;
        try { bind(owner, stack, actor); owner.displayClientMessage(Component.translatable("message.ae2lt.pigmee_bound", actor.getUUID()), true); }
        catch (IllegalStateException failure) { owner.displayClientMessage(Component.literal(failure.getMessage()), true); }
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!PigmeeWorkstationRuntime.enabled() || !(player instanceof ServerPlayer owner) || owner.isFakePlayer()) return InteractionResultHolder.fail(stack);
        var manager = PigmeeWorkstationRuntime.manager(owner.server);
        UUID id = bound(stack);
        if (id == null || !manager.ownedBy(id, owner.getUUID())) {
            owner.displayClientMessage(Component.translatable("message.ae2lt.pigmee_unbound"), true);
            return InteractionResultHolder.fail(stack);
        }
        try {
            if (owner.isShiftKeyDown()) {
                if (manager.active(id) == null) manager.activate(id); else manager.deactivate(id);
            }
            owner.displayClientMessage(Component.literal(manager.status(id)), true);
        } catch (IllegalStateException failure) { owner.displayClientMessage(Component.literal(failure.getMessage()), true); }
        return InteractionResultHolder.consume(stack);
    }
}
