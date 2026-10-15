package com.moakiee.ae2lt.workstation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Small native action surface. Targets and item stacks are resolved exclusively on the server. */
final class PigmeeNativeActions {
    static final int SELECT_SLOT = 0, USE_MAIN = 1, USE_OFF = 2, RELEASE_USE = 3, ATTACK = 4;
    private PigmeeNativeActions() {}

    static String perform(PigmeeServerPlayer actor, int action, int slot, java.util.function.BooleanSupplier current) {
        if (action == SELECT_SLOT) {
            if (actor.getInventory().selected != slot && actor.getUsedItemHand() == InteractionHand.MAIN_HAND) actor.stopUsingItem();
            actor.getInventory().selected = slot;
            return "selected";
        }
        if (action == RELEASE_USE) { actor.releaseUsingItem(); return "released"; }
        if (actor.isUsingItem()) return "busy";
        var level = actor.serverLevel();
        double reach = Math.min(6, action == ATTACK ? actor.entityInteractionRange() : actor.blockInteractionRange());
        if (!Double.isFinite(reach) || reach <= 0) return "out_of_reach";
        var from = actor.getEyePosition();
        var to = from.add(actor.getLookAngle().scale(reach));
        // Clip/vanilla item code may ask for block states. Check the entire small footprint first.
        int minX = BlockPos.containing(Math.min(from.x, to.x) - 1, from.y, Math.min(from.z, to.z) - 1).getX() >> 4;
        int maxX = BlockPos.containing(Math.max(from.x, to.x) + 1, from.y, Math.max(from.z, to.z) + 1).getX() >> 4;
        int minZ = BlockPos.containing(from.x, from.y, Math.min(from.z, to.z) - 1).getZ() >> 4;
        int maxZ = BlockPos.containing(from.x, from.y, Math.max(from.z, to.z) + 1).getZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return "unloaded";
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, actor));
        if (action == ATTACK) {
            var target = ProjectileUtil.getEntityHitResult(actor, from, to,
                    actor.getBoundingBox().expandTowards(to.subtract(from)).inflate(1),
                    entity -> entity instanceof LivingEntity && entity != actor && entity.isAlive()
                            && !entity.isSpectator() && entity.isPickable(), from.distanceToSqr(hit.getLocation()));
            if (target == null || !actor.canInteractWithEntity(target.getEntity().getBoundingBox(), 0)
                    || !level.getWorldBorder().isWithinBounds(target.getEntity().blockPosition())) return "no_target";
            if (!actor.getMainHandItem().isItemEnabled(level.enabledFeatures())) return "blocked";
            if (actor.getAttackStrengthScale(0) < 1) return "cooldown";
            actor.attack(target.getEntity()); // Native damage, tool wear and NeoForge attack hooks.
            actor.swing(InteractionHand.MAIN_HAND, true);
            return "attacked";
        }
        var hand = action == USE_OFF ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var stack = actor.getItemInHand(hand);
        if (!stack.isItemEnabled(level.enabledFeatures())) return "blocked";
        InteractionResult result = InteractionResult.PASS;
        if (hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var adjacent = pos.relative(hit.getDirection());
            if (!actor.canInteractWithBlock(pos, 0) || !level.mayInteract(actor, pos)
                    || !level.mayInteract(actor, adjacent) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getWorldBorder().isWithinBounds(adjacent) || level.isOutsideBuildHeight(pos)) return "blocked";
            result = actor.gameMode.useItemOn(actor, level, stack, hand, hit);
        }
        if (result == InteractionResult.PASS && current.getAsBoolean()) {
            // A native block hook may replace the held item or revoke the lease synchronously.
            stack = actor.getItemInHand(hand);
            if (!stack.isEmpty() && stack.isItemEnabled(level.enabledFeatures()))
                result = actor.gameMode.useItem(actor, level, stack, hand);
        }
        if (result.shouldSwing()) actor.swing(hand, true);
        // Container forwarding needs its own menu/session contract; never leave an invisible open menu.
        if (actor.containerMenu != actor.inventoryMenu) { actor.closeContainer(); return "container_unavailable"; }
        return result.consumesAction() ? "used" : "blocked";
    }
}
