package com.moakiee.ae2lt.machine.largeoverload;

import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places only frames/casings into air through normal item-use hooks, sixteen cells per tick. */
public final class LargeFactoryBuilder {
    private UUID player;
    private int cursor;
    public void start(ServerPlayer player) { this.player = player.getUUID(); cursor = 0; }
    public boolean active() { return player != null; }
    public void tick(LargeFactoryControllerBlockEntity controller) {
        if (player == null || !(controller.getLevel() instanceof ServerLevel level)) return;
        var actor = level.getServer().getPlayerList().getPlayer(player);
        if (actor == null || actor.level() != level || actor.distanceToSqr(controller.getBlockPos().getCenter()) > 64) { player = null; return; }
        var cells = LargeFactoryStructure.cells();
        int work = 0;
        while (cursor < cells.size() && work++ < 16) {
            if (!LargeFactoryWorkBudget.take(controller, LargeFactoryWorkBudget.Work.BUILD)) break;
            var cell = cells.get(cursor++);
            var component = switch (cell.role()) {
                case FRAME -> LargeFactoryComponent.FRAME;
                case CASING, HATCH -> LargeFactoryComponent.CASING;
                default -> null;
            };
            if (component == null) continue;
            var pos = LargeFactoryStructure.worldPosition(controller.getBlockPos(), cell.localPosition(), controller.facing());
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.mayInteract(actor, pos)) continue;
            var item = LargeFactoryRegistration.block(component).asItem();
            int source = -1;
            for (int slot = 0; slot < 36; slot++) if (actor.getInventory().getItem(slot).is(item)) { source = slot; break; }
            if (source < 0) continue;
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            if (source == actor.getInventory().selected) {
                if (actor.mayUseItemAt(pos, Direction.UP, actor.getMainHandItem())) actor.getMainHandItem().useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
            } else {
                // Server-thread synchronous item-use, with the real inventory stack, never a generated block.
                ItemStack held = actor.getMainHandItem();
                ItemStack material = actor.getInventory().removeItemNoUpdate(source);
                actor.setItemInHand(InteractionHand.MAIN_HAND, material);
                try {
                    if (actor.mayUseItemAt(pos, Direction.UP, material)) material.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
                } finally {
                    var remaining = actor.getMainHandItem();
                    actor.setItemInHand(InteractionHand.MAIN_HAND, held);
                    actor.getInventory().setItem(source, remaining);
                }
            }
        }
        if (cursor == cells.size()) { player = null; controller.requestScan(); }
        actor.getInventory().setChanged();
    }
    public static int[] missing(LargeFactoryControllerBlockEntity controller) {
        int[] counts = new int[2];
        var level = controller.getLevel();
        for (var cell : LargeFactoryStructure.cells()) {
            var pos = LargeFactoryStructure.worldPosition(controller.getBlockPos(), cell.localPosition(), controller.facing());
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
            if (cell.role() == LargeFactoryStructure.Role.FRAME) counts[0]++;
            if (cell.role() == LargeFactoryStructure.Role.CASING || cell.role() == LargeFactoryStructure.Role.HATCH) counts[1]++;
        }
        return counts;
    }
}
