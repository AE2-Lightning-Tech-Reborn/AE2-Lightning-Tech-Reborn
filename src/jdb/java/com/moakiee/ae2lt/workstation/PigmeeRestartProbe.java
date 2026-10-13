package com.moakiee.ae2lt.workstation;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Two real dedicated-server processes, no mock NBT-only restart claim. */
@EventBusSubscriber(modid = "ae2lt")
public final class PigmeeRestartProbe {
    private static final UUID OWNER = UUID.fromString("537303b7-7bfa-452d-a8c4-9ca470398122");
    private static int ticks;
    private static boolean failed;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        String phase = System.getProperty("ae2lt.pigmeeRestartPhase", "");
        if (phase.isEmpty()) return;
        var server = event.getServer();
        if (failed) return;
        try {
            ticks++;
            var manager = PigmeeWorkstationRuntime.manager(server);
            var level = server.overworld();
            check(server.getPlayerList().getPlayerCount() == 0, "real login list stays empty");
            if (phase.equals("prepare")) {
                if (ticks == 1) {
                    check(manager.ids().isEmpty(), "prepare requires a fresh isolated test world");
                    level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
                    for (int x = 12000; x < 12032; x++) for (int z = 12000; z < 12008; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 95, z), Blocks.STONE.defaultBlockState());
                    }
                    var a = manager.create(OWNER, level, new Vec3(12004.5, 96, 12002.5), -90);
                    var b = manager.create(OWNER, level, new Vec3(12004.5, 96, 12005.5), -90);
                    a.getInventory().setItem(0, new ItemStack(Items.EMERALD, 13));
                    a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    a.setHealth(11);
                    a.getFoodData().setFoodLevel(9);
                    b.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 23));
                    a.walkFor(30);
                }
                if (ticks == 100) {
                    check(manager.activeCount() == 2, "two live bodies before real save");
                    String ids = manager.ids().stream().sorted(Comparator.naturalOrder()).map(UUID::toString)
                            .reduce((a, b) -> a + "\n" + b).orElseThrow();
                    Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("pigmee-probe-ids.txt"), ids);
                    System.out.println("PIGMEE_RESTART_PREPARE_PASS: two independent bodies; stopping for real disk save");
                    server.halt(false);
                }
            } else if (phase.equals("verify")) {
                if (ticks == 100) {
                    var ids = Files.readAllLines(server.getWorldPath(LevelResource.ROOT).resolve("pigmee-probe-ids.txt"));
                    check(manager.ids().size() == 2 && manager.activeCount() == 2, "exactly two restored bodies");
                    for (String saved : ids) {
                        UUID id = UUID.fromString(saved);
                        var actor = manager.active(id);
                        check(actor != null && level.getEntity(id) == actor, "same stable UUID has exactly one body");
                        check(manager.activate(id) == actor, "repeat activation does not create another body");
                        check(actor.owner().equals(OWNER), "owner survives save independently of actor identity");
                        check(actor.bodyTicks() >= 90 && actor.isFakePlayer() && !actor.hasPermissions(1), "restored native ticks and identity");
                        check(actor.position().x > 12004 && actor.position().z > 12000, "position survives restart");
                        var stack = actor.getInventory().getItem(0);
                        if (stack.is(Items.EMERALD)) {
                            check(stack.getCount() == 13 && actor.getHealth() == 11 && actor.getFoodData().getFoodLevel() == 9, "inventory, health, hunger survive real restart");
                            check(actor.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "equipment survives real restart");
                        } else check(stack.is(Items.REDSTONE) && stack.getCount() == 23 && actor.getHealth() == 20, "second actor state remains separate");
                        check(!Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat")), "no parallel vanilla playerdata save");
                    }
                    manager.ids().forEach(manager::deactivate);
                }
                if (ticks == 220) {
                    check(manager.activeCount() == 0, "all restored bodies deactivated");
                    System.out.println("PIGMEE_RESTART_VERIFY_PASS: stable IDs, one body each, separate inventory/equipment/health/food, ticks, no vanilla playerdata");
                    server.halt(false);
                }
            } else throw new IllegalArgumentException("Unknown restart phase " + phase);
            if (ticks > 400) throw new AssertionError("probe exceeded tick budget");
        } catch (Throwable error) {
            failed = true;
            System.out.println("PIGMEE_RESTART_FAIL: " + error);
            error.printStackTrace();
            server.halt(false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
