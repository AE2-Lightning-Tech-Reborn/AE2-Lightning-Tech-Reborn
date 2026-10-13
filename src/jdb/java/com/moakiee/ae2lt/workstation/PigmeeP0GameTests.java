package com.moakiee.ae2lt.workstation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pigmee_p0")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt")
public final class PigmeeP0GameTests {
    private static final Map<UUID, Integer> PLAYER_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof PigmeeServerPlayer actor) PLAYER_TICKS.merge(actor.getUUID(), 1, Integer::sum);
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void remoteBodyLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var manager = PigmeeWorkstationRuntime.manager(server);
        helper.assertTrue(server.getPlayerList().getPlayerCount() == 0, "requires zero real connected players");
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
        int x = 10000, y = 96, z = 10000;
        for (int dx = -4; dx <= 84; dx++) for (int dz = -4; dz <= 6; dz++) {
            level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE.defaultBlockState());
        }
        UUID owner = UUID.randomUUID();
        var a = manager.create(owner, level, new Vec3(x + .5, y, z + .5), -90);
        var b = manager.create(owner, level, new Vec3(x + .5, y, z + 3.5), -90);
        a.getInventory().setItem(0, new ItemStack(Items.STICK, 5));
        b.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        a.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2000));
        a.getFoodData().setFoodLevel(12);
        helper.assertTrue(!a.getUUID().equals(owner) && !a.getUUID().equals(b.getUUID()), "identities must differ");
        helper.assertTrue(a.isFakePlayer() && !a.hasPermissions(1), "honest automation identity without op permissions");
        helper.assertTrue(manager.activate(a.getUUID()) == a && manager.activeCount() == 2, "activation must be idempotent");
        ChunkPos origin = a.chunkPosition();
        long[] start = new long[2];
        helper.runAtTickTime(80, () -> {
            start[0] = a.bodyTicks();
            start[1] = level.getGameTime();
            helper.assertTrue(a.hurt(a.damageSources().generic(), 6), "native damage must be accepted after spawn protection");
            a.walkFor(160);
        });
        helper.runAtTickTime(240, () -> {
            helper.assertTrue(a.getX() > x + 16, "server physics must cross at least one chunk without client input packets: " + a.position());
            helper.assertTrue(a.bodyTicks() - start[0] == level.getGameTime() - start[1], "exactly one body tick per server tick");
            helper.assertTrue(PLAYER_TICKS.get(a.getUUID()) == a.bodyTicks(), "native PlayerTickEvent must run once per body tick");
            helper.assertTrue(a.tickCount == a.bodyTicks(), "world tickCount must not be incremented twice");
            helper.assertTrue(a.getLastSectionPos().x() == a.chunkPosition().x, "native player loading center follows movement");
            helper.assertTrue(level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(a.chunkPosition().toLong()), "destination must be entity ticking");
            helper.assertTrue(level.getForcedChunks().isEmpty(), "test must not rely on /forceload");
            helper.assertTrue(a.getHealth() < 20 && b.getHealth() == 20, "independent damage");
            helper.assertTrue(a.getInventory().getItem(0).is(Items.STICK) && b.getInventory().getItem(0).getCount() == 7, "independent inventories");
            helper.assertTrue(b.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "independent equipment");
            helper.assertTrue(b.getFoodData().getFoodLevel() == 20 && !b.hasEffect(MobEffects.NIGHT_VISION), "independent food/effects");
            manager.deactivate(a.getUUID());
            helper.assertTrue(level.getEntity(a.getUUID()) == null, "deactivation removes body");
            helper.assertTrue(level.getChunkSource().chunkMap.getDistanceManager().hasPlayersNearby(origin.toLong()), "overlapping body retains its own source");
            var restored = manager.activate(a.getUUID());
            helper.assertTrue(restored != a && restored.getInventory().getItem(0).getCount() == 5, "restore saved independent inventory");
            helper.assertTrue(restored.getHealth() == a.getHealth() && restored.getFoodData().getFoodLevel() == 12, "restore health and hunger");
            helper.assertTrue(restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && restored.hasEffect(MobEffects.NIGHT_VISION), "restore equipment/effects");
            verifyStoreFailures(helper, manager);
            manager.deactivate(a.getUUID());
            b.hurt(b.damageSources().fellOutOfWorld(), 1000);
            b.die(b.damageSources().fellOutOfWorld()); // Duplicate callback must not duplicate native drops.
        });
        helper.runAtTickTime(245, () -> {
            helper.assertTrue(manager.activeCount() == 0, "dead actor releases runtime body");
            helper.assertTrue(manager.status(b.getUUID()).contains("state=DEAD"), "death persists a tombstone");
            int diamonds = level.getEntitiesOfClass(ItemEntity.class, new AABB(x - 5, y - 3, z - 5, x + 8, y + 4, z + 9)).stream()
                    .filter(item -> item.getItem().is(Items.DIAMOND)).mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(diamonds == 7, "native death drops exactly once: " + diamonds);
            boolean rejected = false;
            try { manager.activate(b.getUUID()); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "dead identity must not respawn with a second inventory");
        });
        helper.runAtTickTime(440, () -> {
            helper.assertTrue(!level.getChunkSource().chunkMap.getDistanceManager().hasPlayersNearby(origin.toLong()), "all player loading sources released");
            helper.assertTrue(!level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(origin.toLong()), "no leaked entity-ticking ticket");
            helper.assertTrue(level.getChunkSource().getChunkNow(origin.x, origin.z) == null, "remote origin chunk actually unloads");
            helper.assertTrue(server.getPlayerList().getPlayerCount() == 0, "no login-list pollution");
            PLAYER_TICKS.clear();
            System.out.println("PIGMEE_P0_PASS: remote movement, single tick, isolated state, native damage/death, restore, overlapping tickets and actual unload");
            helper.succeed();
        });
    }

    private static void verifyStoreFailures(GameTestHelper helper, PigmeeActorManager manager) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag snapshot = manager.store.save(new CompoundTag(), registries);
        var dirty = PigmeeActorStore.FACTORY.deserializer().apply(snapshot, registries);
        helper.assertTrue(dirty.records.values().stream().allMatch(r -> r.state == PigmeeActorStore.State.QUARANTINED), "unclean active snapshots fail closed");
        CompoundTag duplicate = snapshot.copy();
        var rows = duplicate.getList("Actors", Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        var corrupt = PigmeeActorStore.FACTORY.deserializer().apply(duplicate, registries);
        helper.assertTrue(!corrupt.writable() && corrupt.save(new CompoundTag(), registries).equals(duplicate), "duplicate identity preserves quarantined source");
        CompoundTag future = snapshot.copy();
        future.putInt("Schema", 999);
        var unknown = PigmeeActorStore.FACTORY.deserializer().apply(future, registries);
        helper.assertTrue(!unknown.writable() && unknown.save(new CompoundTag(), registries).equals(future), "unknown schema is not overwritten");
    }
}
