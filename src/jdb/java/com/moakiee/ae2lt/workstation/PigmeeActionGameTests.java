package com.moakiee.ae2lt.workstation;

import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pigmee_actions")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt")
public final class PigmeeActionGameTests {
    private static UUID deniedActor;
    private static int nativeHooks;
    private static Runnable onUse;
    @SubscribeEvent public static void protect(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof PigmeeServerPlayer) { nativeHooks++; if (onUse != null) onUse.run(); }
        if (event.getEntity().getUUID().equals(deniedActor)) event.setCanceled(true);
    }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void nativeActionsRespectInventoryHooksReplayAndView(GameTestHelper h) {
        var level = h.getLevel(); var manager = PigmeeWorkstationRuntime.manager(level.getServer());
        var base = new BlockPos(24008, 100, 24008);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 7; z++) {
            level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var owner = new Principal(level); var stranger = new Principal(level);
        owner.moveTo(base.getX() + .5, base.getY(), base.getZ() - 2.5, 0, 0);
        var actor = manager.create(owner.getUUID(), level, new Vec3(base.getX() + .5, base.getY(), base.getZ() + .5), 0);
        actor.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        actor.getInventory().setItem(1, new ItemStack(Items.STONE, 3));
        actor.getInventory().setItem(2, new ItemStack(Items.APPLE, 5));
        owner.getInventory().setItem(0, new ItemStack(ModItems.PIGMEE_REMOTE_CONTROL.get()));
        owner.getInventory().setItem(1, new ItemStack(Items.STICK, 19));
        PigmeeRemoteControlItem.bind(owner, owner.getMainHandItem(), actor);
        var ownerInventory = owner.getInventory().save(new net.minecraft.nbt.ListTag());
        var leases = manager.controls(); var lease = leases.acquire(owner, actor.getUUID());
        h.assertTrue(!leases.action(stranger, lease.token, 0, 0, 1, 0, 0), "stranger cannot select slot");
        h.assertTrue(!leases.action(owner, lease.token, 0, 0, 9, 0, 0), "invalid slot rejected");
        h.assertTrue(!leases.action(owner, lease.token, 0, 20, 0, 0, 0), "unknown action rejected");
        h.assertTrue(!leases.action(owner, lease.token, 0, 1, 0, Float.NaN, 0), "nonfinite aim rejected");
        h.assertTrue(leases.action(owner, lease.token, 0, 0, 1, 0, 0), "valid slot selected");
        h.assertTrue(!leases.action(owner, lease.token, 1, 0, 2, 0, 0), "same tick action bounded");
        var target = base.offset(0, 1, 2); var placement = target.north();
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        h.runAtTickTime(5, () -> {
            leases.input(owner, lease.token, 0, 0, 0, 0);
            deniedActor = actor.getUUID(); int before = nativeHooks;
            try { h.assertTrue(leases.action(owner, lease.token, 1, 1, 0, 0, 0), "valid action reached native hook"); }
            finally { deniedActor = null; }
            h.assertTrue(nativeHooks > before && level.isEmptyBlock(placement) && actor.getInventory().getItem(1).getCount() == 3,
                    "native protection denial preserves block and inventory");
        });
        h.runAtTickTime(10, () -> {
            leases.input(owner, lease.token, 1, 0, 0, 0);
            h.assertTrue(leases.action(owner, lease.token, 2, 1, 0, 0, 0), "placement action");
            h.assertTrue(level.getBlockState(placement).is(Blocks.STONE) && actor.getInventory().getItem(1).getCount() == 2,
                    "native survival placement consumes exactly one actor item");
            h.assertTrue(!leases.action(owner, lease.token, 2, 1, 0, 0, 0), "replayed action refused");
        });
        h.runAtTickTime(15, () -> {
            leases.input(owner, lease.token, 2, 0, 0, -75);
            leases.action(owner, lease.token, 3, 0, 2, 0, -75);
            actor.getFoodData().setFoodLevel(10);
        });
        h.runAtTickTime(20, () -> {
            leases.input(owner, lease.token, 3, 0, 0, -75);
            leases.action(owner, lease.token, 4, 1, 0, 0, -75);
            h.assertTrue(actor.isUsingItem(), "native item use started");
            // Release is accepted even in the same tick; lease loss must stop ongoing use too.
            h.assertTrue(leases.action(owner, lease.token, 5, 3, 0, 0, -75) && !actor.isUsingItem(), "same tick release not dropped");
        });
        h.runAtTickTime(25, () -> {
            leases.input(owner, lease.token, 4, 0, 0, -75);
            leases.action(owner, lease.token, 6, 1, 0, 0, -75);
            h.assertTrue(actor.isUsingItem(), "second item use started");
        });
        h.runAtTickTime(33, () -> {
            h.assertTrue(!actor.isUsingItem() && actor.getInventory().getItem(2).getCount() == 5, "lost heartbeat aborts native item use");
            level.setBlockAndUpdate(placement, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            var cow = EntityType.COW.create(level); cow.setNoAi(true); cow.setNoGravity(true);
            cow.moveTo(base.getX() + .5, base.getY(), base.getZ() + 2.5, 0, 0); level.addFreshEntity(cow);
            float health = cow.getHealth();
            level.setBlockAndUpdate(base.south(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.south().above(), Blocks.STONE.defaultBlockState());
            leases.input(owner, lease.token, 5, 0, 0, 15);
            leases.action(owner, lease.token, 7, 4, 0, 0, 15);
            h.assertTrue(cow.getHealth() == health && lease.actionResult.equals("no_target"), "attack cannot pass through wall");
            h.runAfterDelay(5, () -> {
                level.setBlockAndUpdate(base.south(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(base.south().above(), Blocks.AIR.defaultBlockState());
                leases.input(owner, lease.token, 6, 0, 0, 15);
                leases.action(owner, lease.token, 8, 4, 0, 0, 15);
                h.assertTrue(cow.getHealth() < health, "unobstructed native attack damages target");
                h.assertTrue(owner.getInventory().save(new net.minecraft.nbt.ListTag()).equals(ownerInventory), "owner inventory untouched");
                var renewed = leases.acquire(owner, actor.getUUID());
                h.assertTrue(!leases.action(owner, lease.token, 9, 0, 1, 0, 0), "old generation cannot act");
                level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
                onUse = () -> leases.release(owner, renewed.token);
                try { leases.action(owner, renewed.token, 0, 1, 0, 0, 0); }
                finally { onUse = null; }
                h.assertTrue(leases.count() == 0 && !actor.isUsingItem(), "native block hook revocation prevents item-use fallback");
                var spectatorLease = leases.acquire(owner, actor.getUUID());
                owner.spectator = true;
                h.assertTrue(!leases.action(owner, spectatorLease.token, 0, 1, 0, 0, 0) && leases.count() == 0, "spectator releases lease");
                manager.deactivate(actor.getUUID()); cow.discard();
                System.out.println("PIGMEE_ACTIONS_PASS: native protection/placement/consumption, isolated inventory, aim/slot validation, replay/rate/generation, heartbeat/release and occluded attack");
                h.succeed();
            });
        });
    }
    private static final class Principal extends ServerPlayer {
        boolean spectator;
        @Override public boolean isSpectator() { return spectator; }
        Principal(ServerLevel level) { super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ActionsQA"), ClientInformation.createDefault()); }
        @Override public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) { return pos; }
    }
}
