package com.moakiee.ae2lt.workstation;

import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Runs two actual client JVMs against a copy of the existing three-actor disposable fixture. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeDeathClientTests {
    private static int ticks, phase, reports, nativeReports;
    private static volatile int dropEvents, droppedDiamonds;
    private static boolean done;
    private static volatile boolean cancel;
    private static volatile UUID id;
    private static volatile ListTag inventory;
    private static volatile PigmeeDeathRecord death;
    private static volatile Throwable failure;
    private static final Vec3 TARGET = new Vec3(-2.25, 100, -3.75);
    private static final Vec3 BODY = new Vec3(3.5, 100, .5);

    @SubscribeEvent public static void protect(LivingDeathEvent event) {
        if (cancel && event.getEntity().getUUID().equals(id)) {
            event.getEntity().setHealth(20);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void drops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().getUUID().equals(id)) {
            dropEvents++;
            droppedDiamonds += event.getDrops().stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
        }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if (System.getProperty("ae2lt.pigmeeClientDeathPhase", "").isEmpty()) return;
        if (event.getMessage().getContents() instanceof TranslatableContents c) {
            if (c.getKey().equals("message.ae2lt.pigmee_death")) reports++;
            if (c.getKey().startsWith("death.")) nativeReports++;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String mode = System.getProperty("ae2lt.pigmeeClientDeathPhase", "");
        if (mode.isEmpty() || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 3000) { finish(new AssertionError("timeout phase " + phase), mode); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 40 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            if (mode.equals("verify")) { verify(mc, mode); return; }
            switch (phase) {
                case 0 -> server(() -> {
                    var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var manager = PigmeeWorkstationRuntime.manager(p.server);
                    id = PigmeeRemoteControlItem.bound(p.getMainHandItem());
                    require(id != null && manager.activeCount() == 3 && manager.active(id) != null, "copied live fixture with held binding");
                    var level = p.serverLevel();
                    level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, p.server);
                    level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, p.server);
                    level.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(true, p.server);
                    for (int x = -8; x <= 12; x++) for (int z = -8; z <= 8; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                        for (int y = 100; y < 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    p.setGameMode(GameType.SURVIVAL); p.setHealth(20); p.getFoodData().setFoodLevel(20);
                    p.teleportTo(level, BODY.x, BODY.y, BODY.z, java.util.Set.of(), 0, 0);
                    inventory = p.getInventory().save(new ListTag());
                    var actor = manager.active(id);
                    actor.moveTo(TARGET.x, TARGET.y, TARGET.z, 0, 0); actor.setDeltaMovement(Vec3.ZERO);
                    actor.setCustomName(Component.literal("Death QA")); actor.setHealth(20);
                    actor.getInventory().clearContent(); actor.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
                });
                case 1 -> {
                    if (!mc.isWindowActive() || id == null || mc.level.getPlayerByUUID(id) == null) return;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
                case 2 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.workstation.PigmeeControlScreen, "real remote screen opened");
                    server(() -> {
                        var actor = PigmeeWorkstationRuntime.manager(mc.getSingleplayerServer()).active(id);
                        cancel = true;
                        try { actor.invulnerableTime = 0; actor.hurt(actor.damageSources().fellOutOfWorld(), 1000); }
                        finally { cancel = false; }
                    });
                }
                case 3 -> {
                    require(reports == 0 && nativeReports == 0, "cancelled death sends no message");
                    require(mc.screen instanceof com.moakiee.ae2lt.client.workstation.PigmeeControlScreen, "cancelled death keeps live control");
                    server(() -> {
                        var manager = PigmeeWorkstationRuntime.manager(mc.getSingleplayerServer()); var actor = manager.active(id);
                        require(actor.isAlive() && !actor.deathSettled() && manager.death(id) == null, "cancelled native death has no tombstone");
                        actor.invulnerableTime = 0; actor.hurt(actor.damageSources().fellOutOfWorld(), 1000);
                        actor.die(actor.damageSources().fellOutOfWorld());
                    });
                }
                case 4 -> {
                    require(reports == 1 && nativeReports == 0, "exactly one workstation message and no native duplicate: " + reports + "/" + nativeReports);
                    require(mc.screen == null && mc.getCameraEntity() == mc.player && mc.player.isAlive() && mc.player.getHealth() == 20, "death closes remote without killing body or opening respawn");
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var manager = PigmeeWorkstationRuntime.manager(p.server);
                        require(manager.active(id) == null && manager.controls().count() == 0 && manager.status(id).contains("DEAD"), "dead actor removed and lease released");
                        require(p.position().distanceToSqr(BODY) < .01 && p.getHealth() == 20 && p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && p.getInventory().save(new ListTag()).equals(inventory), "body position/health/mode/inventory independent of proxy death");
                        death = manager.death(id);
                        require(death.actorId().equals(id) && death.ownerId().equals(p.getUUID()) && death.ownerName().equals("PigmeeQA") && death.name().getString().equals("Death QA"), "persistent actor/owner/name identity");
                        require(death.dimension().equals(p.level().dimension().location()) && death.position().equals(new BlockPos(-3, 100, -4)) && death.exactPosition().distanceToSqr(TARGET) < .0001, "actual proxy negative block and precise coordinates");
                        int diamonds = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(-15, 90, -15, 15, 110, 15)).stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
                        require(diamonds == 7 && dropEvents == 1 && droppedDiamonds == 7, "single native item drop despite repeated callback: world=" + diamonds + " events=" + dropEvents + " drops=" + droppedDiamonds);
                        var snapshot = manager.store.save(new CompoundTag(), p.registryAccess());
                        var loaded = PigmeeActorStore.FACTORY.deserializer().apply(snapshot, p.registryAccess());
                        require(loaded.writable() && loaded.records.get(id).death.equals(death), "death metadata round trip");
                        var bad = snapshot.copy();
                        for (var row : bad.getList("Actors", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            var tag = (CompoundTag) row;
                            if (tag.getUUID("Id").equals(id)) tag.getCompound("Death").putDouble("X", Double.NaN);
                        }
                        var invalid = PigmeeActorStore.FACTORY.deserializer().apply(bad, p.registryAccess());
                        require(!invalid.writable() && invalid.save(new CompoundTag(), p.registryAccess()).equals(bad), "malformed death preserves full raw store");
                        try { Files.writeString(mc.gameDirectory.toPath().resolve("death-event.txt"), death.eventId().toString()); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                    try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve("pigmee-death-body-alive.png")); }
                }
                case 5 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case 6 -> {
                    require(reports == 2 && nativeReports == 0 && mc.screen == null, "bound remote queries the same record without respawn");
                    finish(null, mode);
                }
            }
            phase++;
        } catch (Throwable problem) { finish(problem, mode); }
    }
    private static void verify(Minecraft mc, String mode) throws Exception {
        if (phase == 0) {
            server(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var manager = PigmeeWorkstationRuntime.manager(p.server);
                id = PigmeeRemoteControlItem.bound(p.getMainHandItem()); death = manager.death(id);
                require(manager.ids().size() == 3 && manager.activeCount() == 2 && manager.active(id) == null && death != null, "dead tombstone not restored as body; other two unique");
                try { require(Files.readString(mc.gameDirectory.toPath().resolve("death-event.txt")).equals(death.eventId().toString()), "same event id survives real JVM restart"); }
                catch (Exception e) { throw new RuntimeException(e); }
                require(death.position().equals(new BlockPos(-3, 100, -4)) && death.exactPosition().distanceToSqr(TARGET) < .0001, "death coordinates survive restart");
                boolean refused = false;
                try { manager.activate(id); } catch (IllegalStateException expected) { refused = true; }
                require(refused && manager.controls().count() == 0, "no reactivation or persisted lease");
            });
        } else if (phase == 1) {
            require(reports == 0 && nativeReports == 0, "no historical broadcast on real restart");
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } else {
            require(reports == 1 && nativeReports == 0 && mc.screen == null && mc.player.isAlive(), "post-restart remote query only, real body alive");
            finish(null, mode);
        }
        phase++;
    }
    private static void server(Runnable task) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable problem) { failure = problem; } }); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void finish(Throwable problem, String mode) {
        done = true; var mc = Minecraft.getInstance();
        String result = problem == null ? "PIGMEE_DEATH_CLIENT_" + mode.toUpperCase(java.util.Locale.ROOT) + "_PASS: native death/cancellation, one message/drop, body and lease isolation, exact negative position and persistent event; real client" : "PIGMEE_DEATH_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-death-" + mode + "-result.txt"), result); } catch (Exception ignored) { }
        mc.stop();
    }
}
