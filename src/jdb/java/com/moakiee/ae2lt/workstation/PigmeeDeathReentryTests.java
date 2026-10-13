package com.moakiee.ae2lt.workstation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pigmee_death")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt")
public final class PigmeeDeathReentryTests {
    private static final Map<UUID, Probe> PROBES = new HashMap<>();
    private static final class Probe {
        String action; boolean fired; int deathEvents, dropEvents, diamonds, xpEvents, xp;
        Probe(String action) { this.action = action; }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        var p = PROBES.get(event.getEntity().getUUID());
        if (p == null) return;
        p.deathEvents++;
        if (p.fired) return;
        p.fired = true;
        var actor = (PigmeeServerPlayer) event.getEntity();
        if (p.action.equals("reenter")) actor.die(event.getSource());
        if (p.action.equals("stop")) PigmeeWorkstationRuntime.manager(actor.server).deactivate(actor.getUUID());
        if (p.action.startsWith("cancelStop")) {
            PigmeeWorkstationRuntime.manager(actor.server).deactivate(actor.getUUID());
            actor.setHealth(20); event.setCanceled(true);
            if (p.action.equals("cancelStopRemove")) actor.discard();
        }
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        var p = PROBES.get(event.getEntity().getUUID());
        if (p == null) return;
        p.dropEvents++;
        var actor = (PigmeeServerPlayer) event.getEntity();
        if (p.action.equals("dropReenter")) actor.die(event.getSource());
        if (p.action.equals("dropStop")) PigmeeWorkstationRuntime.manager(actor.server).deactivate(actor.getUUID());
        p.diamonds += event.getDrops().stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
    }
    @SubscribeEvent public static void experience(LivingExperienceDropEvent event) {
        var p = PROBES.get(event.getEntity().getUUID());
        if (p != null) { p.xpEvents++; p.xp += event.getDroppedExperience(); }
    }
    private static PigmeeServerPlayer fixture(GameTestHelper h, String action, int x) {
        var level = h.getLevel(); var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            level.setBlockAndUpdate(new BlockPos(x + dx, 95, x + dz), Blocks.STONE.defaultBlockState());
        var actor = PigmeeWorkstationRuntime.manager(server).create(UUID.randomUUID(), level, new Vec3(x+.5, 96, x+.5), 0);
        actor.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        actor.experienceLevel = 10;
        PROBES.put(actor.getUUID(), new Probe(action));
        return actor;
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void nativeControlSettlesOnce(GameTestHelper h) { run(h, "control", 20000); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void reentrantNativeDeathMustNotRepeatSettlement(GameTestHelper h) { run(h, "reenter", 20004); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void stopInsideNativeDeathMustNotHideTombstone(GameTestHelper h) { run(h, "stop", 20008); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void reentrantDropHookSettlesOnce(GameTestHelper h) { run(h, "dropReenter", 20012); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void stopInDropHookKeepsDeath(GameTestHelper h) { run(h, "dropStop", 20016); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void cancelledDeathDefersStopWithoutLoss(GameTestHelper h) { run(h, "cancelStop", 20020); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void cancelledRemovedBodyRemainsQuarantined(GameTestHelper h) { run(h, "cancelStopRemove", 20024); }
    private static void run(GameTestHelper h, String action, int x) {
        var actor = fixture(h, action, x);
        h.runAfterDelay(10, () -> actor.hurt(actor.damageSources().fellOutOfWorld(), 1000));
        h.runAfterDelay(20, () -> {
            var p = PROBES.get(actor.getUUID());
            var manager = PigmeeWorkstationRuntime.manager(h.getLevel().getServer());
            System.out.println("INDEPENDENT_DEATH action=" + action + " deathEvents=" + p.deathEvents
                    + " dropEvents=" + p.dropEvents + " diamonds=" + p.diamonds + " xpEvents=" + p.xpEvents
                    + " xp=" + p.xp + " record=" + manager.status(actor.getUUID()) + " death=" + (manager.death(actor.getUUID()) != null));
            if (action.equals("cancelStopRemove")) {
                h.assertTrue(manager.status(actor.getUUID()).contains("QUARANTINED") && manager.death(actor.getUUID()) == null,
                        "unexpected removal wins over deferred stop and cannot become spendable dormant state");
                PROBES.remove(actor.getUUID()); h.succeed(); return;
            }
            if (action.equals("cancelStop")) {
                h.assertTrue(p.deathEvents == 1 && p.dropEvents == 0 && p.xpEvents == 0 && manager.death(actor.getUUID()) == null,
                        "cancelled death does not settle or report");
                h.assertTrue(manager.active(actor.getUUID()) == null && manager.status(actor.getUUID()).contains("DORMANT"), "cancelled death completes deferred stop");
                var restored = manager.activate(actor.getUUID());
                h.assertTrue(restored.getHealth() == 20 && restored.getInventory().getItem(0).getCount() == 7 && restored.experienceLevel == 10,
                        "cancelled stop preserves inventory/health/XP exactly once");
                manager.deactivate(actor.getUUID());
                PROBES.remove(actor.getUUID()); h.succeed(); return;
            }
            h.assertTrue(p.deathEvents == 1, "native death hook executes once");
            h.assertTrue(p.dropEvents == 1 && p.diamonds == 7 && p.xpEvents == 1 && p.xp == 70,
                    "native settlement repeated: dropEvents=" + p.dropEvents + " xpEvents=" + p.xpEvents + " xp=" + p.xp);
            h.assertTrue(manager.status(actor.getUUID()).contains("DEAD") && manager.death(actor.getUUID()) != null,
                    "death-phase stop lost the confirmed death tombstone: " + manager.status(actor.getUUID()));
            PROBES.remove(actor.getUUID());
            h.succeed();
        });
    }
}
