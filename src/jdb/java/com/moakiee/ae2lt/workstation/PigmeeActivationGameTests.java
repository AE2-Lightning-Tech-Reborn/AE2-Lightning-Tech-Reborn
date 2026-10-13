package com.moakiee.ae2lt.workstation;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.registry.ModFumos;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pigmee_p1")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt")
public final class PigmeeActivationGameTests {
    private static BlockPos denied;
    private static boolean denyJoin;

    @SubscribeEvent
    public static void protect(BlockEvent.BreakEvent event) {
        if (event.getPos().equals(denied)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectJoin(EntityJoinLevelEvent event) {
        if (denyJoin && event.getEntity() instanceof PigmeeServerPlayer) event.setCanceled(true);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void threeVariantsAndProtectedConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = PigmeeWorkstationRuntime.manager(level.getServer());
        helper.assertTrue(manager.ids().isEmpty(), "activation test requires fresh world");
        var owner = new AdminFixture(level);
        owner.getInventory().setItem(0, new ItemStack(Items.STICK, 19));
        BlockPos base = new BlockPos(16000, 96, 16000);
        for (int dx = -2; dx < 22; dx++) for (int dz = -3; dz < 4; dz++) {
            level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
        }
        for (PigmeeVariant variant : PigmeeVariant.values()) {
            BlockPos pos = base.offset(variant.ordinal() * 4, 0, 0);
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(variant.blockId());
            var source = block.defaultBlockState().setValue(FumoBlock.WATERLOGGED, variant == PigmeeVariant.HYPERDIMENSIONAL);
            level.setBlockAndUpdate(pos, source);
            owner.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5, 0, 0);
            var actor = PigmeeFumoActivation.activate(owner, pos);
            helper.assertTrue(actor.variant() == variant && actor.gameMode.getGameModeForPlayer() == variant.gameType(), "exact variant and game mode");
            helper.assertTrue(actor.owner().equals(owner.getUUID()) && !actor.getUUID().equals(owner.getUUID()), "independent identity");
            helper.assertTrue(!actor.hasPermissions(1) && actor.isFakePlayer(), "creative body is still unprivileged automation");
            helper.assertTrue(variant != PigmeeVariant.HYPERDIMENSIONAL || level.getFluidState(pos).is(Fluids.WATER), "waterlogged conversion retains water");
            var remote = new ItemStack(com.moakiee.ae2lt.registry.ModItems.PIGMEE_REMOTE_CONTROL.get());
            PigmeeRemoteControlItem.bind(owner, remote, actor);
            helper.assertTrue(actor.getUUID().equals(PigmeeRemoteControlItem.bound(remote)), "binding is a stable identity reference");
            var outsider = new AdminFixture(level);
            outsider.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
            expectDenied(helper, () -> PigmeeRemoteControlItem.bind(outsider, remote, actor), "OP cannot bind another owner's Pigmee");
            helper.assertTrue(!manager.ownedBy(actor.getUUID(), outsider.getUUID()), "copied binding NBT cannot grant ownership");
            int count = manager.ids().size();
            expectDenied(helper, () -> PigmeeFumoActivation.activate(owner, pos), "repeat conversion");
            helper.assertTrue(manager.ids().size() == count, "repeat cannot create another identity");
            manager.deactivate(actor.getUUID());
            var restored = manager.activate(actor.getUUID());
            helper.assertTrue(restored.variant() == variant && restored.gameMode.getGameModeForPlayer() == variant.gameType(), "variant and mode survive stop/start");
            manager.deactivate(restored.getUUID());
        }
        helper.assertTrue(!owner.isCreative() && owner.getInventory().getItem(0).getCount() == 19, "owner mode and inventory unchanged");
        BlockPos target = base.offset(12, 0, 0);
        owner.moveTo(target.getX() + .5, target.getY(), target.getZ() - 1.5, 0, 0);
        var normal = ModFumos.PIGMEE_FUMO.get().defaultBlockState();
        level.setBlockAndUpdate(target, normal);
        var fumo = (FumoBlockEntity) level.getBlockEntity(target);
        normal.useWithoutItem(level, owner, new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, false));
        helper.assertTrue(fumo.isSpinning(), "ordinary Fumo rotation remains intact");
        denied = target;
        try { expectDenied(helper, () -> PigmeeFumoActivation.activate(owner, target), "protection cancellation"); }
        finally { denied = null; }
        helper.assertTrue(level.getBlockState(target).equals(normal) && manager.ids().size() == 3, "denied activation consumes no source");
        level.setBlockAndUpdate(target.above(), Blocks.STONE.defaultBlockState());
        expectDenied(helper, () -> PigmeeFumoActivation.activate(owner, target), "blocked spawn space");
        level.setBlockAndUpdate(target.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(target, ModFumos.RAINBOW_PIGMEE_FUMO.get().defaultBlockState());
        expectDenied(helper, () -> PigmeeFumoActivation.activate(owner, target), "rainbow Fumo outside promised variants");
        level.setBlockAndUpdate(target, normal);
        var beforeRollback = (FumoBlockEntity) level.getBlockEntity(target);
        beforeRollback.setCustomName(Component.literal("rollback-fixture"));
        beforeRollback.toggleSpinning();
        denyJoin = true;
        try { expectDenied(helper, () -> PigmeeFumoActivation.activate(owner, target), "world admission cancellation"); }
        finally { denyJoin = false; }
        helper.assertTrue(level.getBlockState(target).equals(normal) && manager.activeCount() == 0, "failed admission restores source without a body");
        var afterRollback = (FumoBlockEntity) level.getBlockEntity(target);
        helper.assertTrue(afterRollback.isSpinning() && afterRollback.getCustomName() != null
                && afterRollback.getCustomName().getString().equals("rollback-fixture"), "rollback preserves block entity metadata");
        helper.assertTrue(manager.status(manager.ids().getLast()).contains("QUARANTINED"), "failed activation retained for diagnosis");
        owner.moveTo(target.getX() + 100, target.getY(), target.getZ(), 0, 0);
        expectDenied(helper, () -> PigmeeFumoActivation.activate(owner, target), "out of reach");
        // Reappearance of an activation source never silently makes a second spendable copy.
        UUID first = manager.ids().getFirst();
        level.setBlockAndUpdate(base, normal);
        expectDenied(helper, () -> manager.activate(first), "source/actor conflict on recovery");
        helper.assertTrue(manager.status(first).contains("QUARANTINED"), "source conflict quarantines identity");
        helper.assertTrue(level.getServer().getPlayerList().getPlayerCount() == 0, "fixture does not spoof login");
        System.out.println("PIGMEE_P1_ACTIVATION_PASS: exact three variants, native game modes, duplicate rejection, water preservation, protection/reach/space gates, join rollback and source-conflict quarantine");
        helper.succeed();
    }

    private static void expectDenied(GameTestHelper helper, Runnable action, String reason) {
        boolean denied = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { denied = true; }
        helper.assertTrue(denied, "must deny " + reason);
    }

    /** Test-only command principal; never registered as a connected or world player. */
    private static final class AdminFixture extends ServerPlayer {
        AdminFixture(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "P1TestAdmin"), ClientInformation.createDefault());
        }
        @Override protected int getPermissionLevel() { return 2; }
        @Override public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) { return pos; }
    }
}
