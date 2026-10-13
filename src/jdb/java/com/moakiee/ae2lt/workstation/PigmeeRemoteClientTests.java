package com.moakiee.ae2lt.workstation;

import com.moakiee.ae2lt.client.workstation.PigmeeControlScreen;
import com.moakiee.ae2lt.client.workstation.PigmeeRemoteChunkAccess;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual connected client; body never moves to the remote site. Disposable world only. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeRemoteClientTests {
    private static final Vec3 TARGET = new Vec3(720.5, 100, .5);
    private static final Vec3 BODY = new Vec3(3.5, 100, .5);
    private static final BlockPos MARKER = new BlockPos(723, 100, 3);
    private static int ticks, phase, loadingSamples;
    private static boolean done, waitingForNativeStop;
    private static ClientLevel originalLevel;
    private static volatile UUID id, cowId;
    private static volatile ListTag inventory;
    private static volatile Throwable failure;
    private static volatile int stoppedChunkX, cowRuntimeId;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (waitingForNativeStop) {
            if (Minecraft.getInstance().getSingleplayerServer() == null) { waitingForNativeStop = false; Minecraft.getInstance().stop(); }
            return;
        }
        if (!Boolean.getBoolean("ae2lt.pigmeeRemoteClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 3000) { finish(new AssertionError("timeout phase " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 40 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            System.out.println("PIGMEE_REMOTE_PROGRESS phase=" + phase + " active=" + mc.isWindowActive() + " remote=" + remoteCount()
                    + " actor=" + (id == null ? "none" : mc.level.getPlayerByUUID(id)) + " screen=" + mc.screen);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Pigmee distant view QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.fov().set(70); mc.options.renderDistance().set(4);
                    originalLevel = mc.level;
                    String expectedIdentity = Boolean.getBoolean("ae2lt.pigmeeRemoteRestoreProbe") ? Files.readString(mc.gameDirectory.toPath().resolve("pigmee-remote-identity.txt")) : null;
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var manager = PigmeeWorkstationRuntime.manager(p.server); var level = p.serverLevel();
                        id = PigmeeRemoteControlItem.bound(p.getMainHandItem());
                        require(id != null && manager.activeCount() == 3, "three-actor copied fixture with binding");
                        if (expectedIdentity != null) {
                            require(id.toString().equals(expectedIdentity) && manager.active(id).position().distanceToSqr(TARGET) < .01, "same remote identity and saved position restored across process");
                            require(manager.controls().count() == 0 && manager.active(id).getHealth() == 20, "restore has no persisted control lease and retains health");
                            for (UUID actorId : manager.ids()) require(manager.active(actorId) != null && level.getEntity(actorId) == manager.active(actorId), "each of three identities restored exactly once");
                            System.out.println("PIGMEE_REMOTE_RESTORE_PASS: three unique bodies, same bound far identity/position/health, no persisted lease");
                        }
                        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, p.server);
                        for (int x = -5; x <= 12; x++) for (int z = -5; z <= 5; z++) level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                        for (int x = 712; x <= 800; x++) for (int z = -20; z <= 20; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 99, z), (x % 16 == 0 ? Blocks.GOLD_BLOCK : Blocks.BLUE_CONCRETE).defaultBlockState());
                            for (int y = 100; y <= 103; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        level.setBlockAndUpdate(MARKER, Blocks.EMERALD_BLOCK.defaultBlockState());
                        level.setBlockAndUpdate(MARKER.above(), Blocks.SEA_LANTERN.defaultBlockState());
                        p.setGameMode(GameType.SURVIVAL); p.setHealth(20); p.getFoodData().setFoodLevel(20);
                        p.getInventory().setItem(2, new ItemStack(Items.STICK, 17));
                        p.teleportTo(level, BODY.x, BODY.y, BODY.z, java.util.Set.of(), 0, 0);
                        inventory = p.getInventory().save(new ListTag());
                        if (Boolean.getBoolean("ae2lt.pigmeeRemoteTerrainOnly")) {
                            for (UUID actorId : manager.ids()) manager.deactivate(actorId);
                            require(manager.activeCount() == 0 && manager.controls().count() == 0, "terrain-only control has no active proxy or view");
                            if (Boolean.getBoolean("ae2lt.pigmeeRemoteRealPlayerControl")) {
                                // Separate shutdown negative control, never remote-view acceptance evidence.
                                p.teleportTo(level, TARGET.x, TARGET.y, TARGET.z, java.util.Set.of(), -90, 0);
                            }
                            return;
                        }
                        PigmeeServerPlayer actor;
                        if (Boolean.getBoolean("ae2lt.pigmeeRemoteFreshActivation")) {
                            // Alternate fixture: native admission at a dormant saved destination,
                            // without raw long-distance relocation of a live player entity.
                            manager.deactivate(id);
                            var position = new ListTag();
                            position.add(net.minecraft.nbt.DoubleTag.valueOf(TARGET.x));
                            position.add(net.minecraft.nbt.DoubleTag.valueOf(TARGET.y));
                            position.add(net.minecraft.nbt.DoubleTag.valueOf(TARGET.z));
                            manager.store.records.get(id).playerData.put("Pos", position);
                            actor = manager.activate(id);
                            actor.setYRot(-90); actor.setXRot(0);
                        } else {
                            actor = manager.active(id); actor.moveTo(TARGET.x, TARGET.y, TARGET.z, -90, 0);
                        }
                        actor.setYHeadRot(-90); actor.setDeltaMovement(Vec3.ZERO);
                        // Fixture-only remote relocation must update native player tickets now;
                        // raw Entity.moveTo into a non-ticking section cannot wait for its next tick.
                        level.getChunkSource().move(actor);
                        var cow = EntityType.COW.create(level); require(cow != null, "cow fixture");
                        cow.setNoAi(true); cow.moveTo(724.5, 100, .5, 90, 0); cowId = cow.getUUID(); cowRuntimeId = cow.getId(); level.addFreshEntity(cow);
                        require(PigmeeRemoteView.allowed(p, actor) && !p.getChunkTrackingView().contains(actor.chunkPosition()), "remote site outside body's native view");
                    });
                }
                case 1 -> {
                    if ((!mc.isWindowActive() && !Boolean.getBoolean("ae2lt.pigmeeRemoteShutdownControl")) || id == null) return;
                    if (!Boolean.getBoolean("ae2lt.pigmeeRemoteRestoreProbe")) Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-remote-identity.txt"), id.toString());
                    if (!Boolean.getBoolean("ae2lt.pigmeeRemoteRealPlayerControl")) require(mc.level.getPlayerByUUID(id) == null && mc.level.getChunkSource().getChunk(45, 0, false) == null, "distant actor/chunk absent before subscription");
                    if (!Boolean.getBoolean("ae2lt.pigmeeRemoteShutdownControl")) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
                case 2 -> {
                    if (Boolean.getBoolean("ae2lt.pigmeeRemoteShutdownControl")) {
                        require(remoteCount() == 0 && mc.screen == null, "control has no remote view");
                        done = true; System.out.println("PIGMEE_REMOTE_SHUTDOWN_CONTROL_READY: terrainOnly=" + Boolean.getBoolean("ae2lt.pigmeeRemoteTerrainOnly") + "; realPlayerControl=" + Boolean.getBoolean("ae2lt.pigmeeRemoteRealPlayerControl") + "; no view subscription; require normal process exit");
                        if (Boolean.getBoolean("ae2lt.pigmeeNativeStopControl")) {
                            waitingForNativeStop = true; server(() -> mc.getSingleplayerServer().halt(false));
                        } else mc.stop();
                        return;
                    }
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var manager = PigmeeWorkstationRuntime.manager(p.server);
                        var actor = manager.active(id); var view = manager.controls().remoteView(p);
                        System.out.println("PIGMEE_REMOTE_SERVER leases=" + manager.controls().count() + " view=" + (view == null ? -1 : view.chunkCount())
                                + " allowed=" + (actor != null && PigmeeRemoteView.allowed(p, actor)) + " held=" + p.getMainHandItem()
                                + " chunk=" + (actor == null ? "no actor" : actor.serverLevel().getChunkSource().chunkMap.getChunkToSend(actor.chunkPosition().toLong())));
                    });
                    require(++loadingSamples < 7, "remote startup did not complete in 12 seconds");
                    if (!(mc.screen instanceof PigmeeControlScreen) || mc.level.getPlayerByUUID(id) == null
                            || !mc.level.getBlockState(MARKER).is(Blocks.EMERALD_BLOCK) || mc.levelRenderer.countRenderedSections() == 0) return;
                    require(mc.getCameraEntity() == mc.player && mc.level == originalLevel, "logical body camera and world unchanged");
                    require(mc.gameRenderer.getMainCamera().getEntity() == mc.level.getPlayerByUUID(id), "render camera observes distant actor");
                    require(mc.level.getBlockState(new BlockPos(3, 99, 0)).is(Blocks.STONE), "body collision chunk still present despite modulo-ring distance");
                    require(remoteCount() > 0 && remoteCount() <= 25 && mc.levelRenderer.countRenderedSections() > 0, "bounded distant chunks are rendered");
                    require(mc.level.getEntity(cowRuntimeId) != null, "native distant entity pairing");
                    capture("pigmee-remote-terrain.png");
                    if (Boolean.getBoolean("ae2lt.pigmeeRemoteShutdownProbe")) {
                        done = true; System.out.println("PIGMEE_REMOTE_SHUTDOWN_READY: active remote lease and 25 chunks; require normal process exit and clean save marker removal");
                        mc.stop(); return;
                    }
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 0, 0);
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setBlockAndUpdate(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState());
                        var cow = level.getEntity(cowId); cow.moveTo(726.5, 100, .5, 90, 0);
                    });
                }
                case 3 -> {
                    require(mc.screen instanceof PigmeeControlScreen && mc.level.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "native remote block updates");
                    require(mc.level.getEntity(cowRuntimeId).getX() > 725, "native remote entity movement updates");
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(p.position().distanceToSqr(BODY) < .01 && p.getInventory().save(new ListTag()).equals(inventory), "remote input leaves body position/inventory unchanged");
                    });
                }
                case 4 -> {
                    require(mc.screen instanceof PigmeeControlScreen, "remote view remains active across movement");
                    mc.screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 0, 0);
                    require(mc.level.getPlayerByUUID(id).chunkPosition().x > 45 && remoteCount() <= 25, "remote player crosses chunk boundary with bounded moving window");
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var at = p.blockPosition();
                        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                            p.serverLevel().setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.AIR.defaultBlockState());
                            p.serverLevel().setBlockAndUpdate(at.offset(dx, -7, dz), Blocks.STONE.defaultBlockState());
                        }
                        p.hurt(p.damageSources().generic(), 3); p.knockback(.4, 1, 0);
                    });
                }
                case 5 -> {
                    require(mc.screen instanceof PigmeeControlScreen && mc.player.getY() < 96 && mc.getCameraEntity() == mc.player, "body falls while remote render view remains active");
                    double clientY = mc.player.getY();
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(Math.abs(p.getY() - clientY) < .25 && Math.abs(p.getX() - BODY.x) > .1 && p.getHealth() < 20, "body fall/knockback/damage packets sync during far view");
                        require(p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && p.getInventory().save(new ListTag()).equals(inventory), "body mode/inventory remain isolated");
                    });
                    capture("pigmee-remote-body-physics.png"); mc.screen.onClose();
                }
                case 6 -> {
                    require(mc.screen == null && remoteCount() == 0 && mc.level.getChunkSource().getChunk(46, 0, false) == null, "exit releases supplemental chunks");
                    require(mc.level.getPlayerByUUID(id) == null && mc.level.getEntity(cowRuntimeId) == null, "exit unpairs distant entities");
                    require(mc.level == originalLevel && mc.getCameraEntity() == mc.player && mc.level.getBlockState(new BlockPos(3, 93, 0)).is(Blocks.STONE), "body world/camera/collision remain intact after release");
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var manager = PigmeeWorkstationRuntime.manager(p.server);
                        var actor = manager.active(id); stoppedChunkX = actor.chunkPosition().x;
                        require(manager.controls().count() == 0 && !PigmeeRemoteView.watches(p, stoppedChunkX, 0), "server subscription/lease released");
                        manager.deactivate(id);
                    });
                }
                case 12 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    require(level.getChunkSource().getChunkNow(stoppedChunkX, 0) == null, "actor stop actually unloads far chunk without view ticket leak");
                });
                case 13 -> finish(null);
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static int remoteCount() { return ((PigmeeRemoteChunkAccess) Minecraft.getInstance().level.getChunkSource()).ae2lt$remoteChunks().count(); }
    private static void server(Runnable task) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable problem) { failure = problem; } }); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void finish(Throwable problem) {
        done = true; var mc = Minecraft.getInstance();
        String result = problem == null ? "PIGMEE_REMOTE_CLIENT_PASS: 720-block distant terrain/entities/block updates, moving bounded window, unchanged logical body/world with native fall/knockback sync, exit releases client/server view and actor stop unloads chunk" : "PIGMEE_REMOTE_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-remote-result.txt"), result); } catch (Exception ignored) { }
        mc.stop();
    }
}
