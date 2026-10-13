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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pigmee_p2")
@PrefixGameTestTemplate(false)
public final class PigmeeControlGameTests {
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void ownershipLeaseReplayAndLoss(GameTestHelper h) {
        var level = h.getLevel(); var manager = PigmeeWorkstationRuntime.manager(level.getServer());
        h.assertTrue(manager.ids().isEmpty(), "fresh lease test world");
        var owner = new Principal(level); var stranger = new Principal(level);
        for (int x = 17997; x < 18006; x++) for (int z = 17995; z < 18015; z++) level.setBlockAndUpdate(new BlockPos(x, 95, z), Blocks.STONE.defaultBlockState());
        owner.moveTo(18000.5, 96, 17998.5, 0, 0); stranger.moveTo(18000.5, 96, 17998.5, 0, 0);
        var origin = owner.position(); var mode = owner.gameMode.getGameModeForPlayer();
        var actor = manager.create(owner.getUUID(), level, new Vec3(18000.5, 96, 18000.5), 0);
        UUID id = actor.getUUID();
        actor.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        owner.getInventory().setItem(0, new ItemStack(ModItems.PIGMEE_REMOTE_CONTROL.get()));
        owner.getInventory().setItem(1, new ItemStack(Items.STICK, 19));
        PigmeeRemoteControlItem.bind(owner, owner.getMainHandItem(), actor);
        stranger.getInventory().setItem(0, owner.getMainHandItem().copy());
        var leases = manager.controls();
        var first = leases.acquire(owner, id);
        denied(h, () -> leases.acquire(stranger, id), "copied remote and OP cannot seize control");
        h.assertTrue(!leases.input(stranger, first.token, 0, 1, 0, 0), "other controller cannot submit input");
        h.assertTrue(leases.input(owner, first.token, 0, 1, 0, 0), "valid input accepted");
        h.assertTrue(!leases.input(owner, first.token, 0, 2, 0, 0), "duplicate sequence rejected");
        h.assertTrue(!leases.input(owner, first.token, 1, 1, Float.NaN, 0), "NaN rejected");
        h.assertTrue(!leases.input(owner, first.token, 1, 128, 0, 0), "unknown input flags rejected");
        leases.release(owner, UUID.randomUUID());
        h.assertTrue(leases.count() == 1, "wrong token cannot end current lease");
        var second = leases.acquire(owner, id);
        h.assertTrue(!first.token.equals(second.token) && !leases.input(owner, first.token, 2, 1, 0, 0), "new generation rejects old requests");
        h.assertTrue(leases.input(owner, second.token, 0, 1, 0, 0), "new lease has independent sequence");
        h.runAtTickTime(10, () -> {
            h.assertTrue(actor.getZ() > 18000.6, "lease drives native movement");
            h.assertTrue(!actor.hasControlInput() && actor.zza == 0, "missing input stops intent after five ticks");
            h.assertTrue(owner.position().equals(origin) && owner.gameMode.getGameModeForPlayer() == mode
                    && owner.getInventory().getItem(1).getCount() == 19 && actor.getInventory().getItem(0).getCount() == 7, "principal position/mode/inventory independent");
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(leases.count() == 0 && !leases.input(owner, second.token, 5, 1, 0, 0), "expired lease cannot resume");
            var lease = leases.acquire(owner, id); leases.input(owner, lease.token, 0, 1, 0, 0);
            owner.setHealth(0); leases.tick();
            h.assertTrue(leases.count() == 0 && !actor.hasControlInput(), "owner death clears input and lease");
            owner.setHealth(20);
            var next = leases.acquire(owner, id); leases.input(owner, next.token, 0, 1, 0, 0);
            leases.releaseOwner(owner);
            h.assertTrue(leases.count() == 0 && !actor.hasControlInput(), "logout release clears input");
            leases.acquire(owner, id); manager.deactivate(id);
            h.assertTrue(leases.count() == 0, "stop releases control");
            var restored = manager.activate(id);
            leases.acquire(owner, id);
            owner.moveTo(18100, 96, 18000, 0, 0); leases.tick();
            h.assertTrue(leases.count() == 0, "leaving supported view range releases control");
            owner.moveTo(origin.x, origin.y, origin.z, 0, 0);
            leases.acquire(owner, id);
            restored.hurt(level.damageSources().genericKill(), 1000);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(leases.count() == 0 && manager.active(id) == null && manager.status(id).contains("DEAD"), "actor death releases lease and remains tombstoned");
            System.out.println("PIGMEE_LEASE_PASS: owner/copy/OP gates, one lease, epochs/replay/NaN/flags, native movement, lost input timeout, expiry, owner death/logout, stop/range/actor death release; no real client claim");
            h.succeed();
        });
    }
    private static void denied(GameTestHelper h, Runnable action, String reason) {
        boolean denied = false; try { action.run(); } catch (IllegalStateException expected) { denied = true; }
        h.assertTrue(denied, reason);
    }
    /** Unconnected test principal; never treated as proof of connected-client view isolation. */
    private static final class Principal extends ServerPlayer {
        Principal(ServerLevel level) { super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "LeaseQA"), ClientInformation.createDefault()); }
        @Override protected int getPermissionLevel() { return 2; }
        @Override public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) { return pos; }
    }
}
