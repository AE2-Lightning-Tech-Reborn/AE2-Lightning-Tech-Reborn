package com.moakiee.ae2lt.workstation;

import com.moakiee.ae2lt.client.workstation.PigmeeClientIdentity;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real integrated-client packet/render smoke test, confined to a disposable save. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeClientProbe {
    private static int ticks, phase;
    private static boolean done;
    private static UUID bound;
    private static volatile net.minecraft.nbt.ListTag ownerInventory;
    private static volatile Vec3 ownerOrigin;
    private static double actorStartZ;
    private static volatile Throwable failure;
    private static final BlockPos BASE = new BlockPos(1, 100, 3);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks > 3000) { finish(new AssertionError("Client probe timeout, phase " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 40 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            if (Boolean.getBoolean("ae2lt.pigmeeClientRestoreProbe")) { restore(mc); return; }
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Pigmee P1 QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.fov().set(65); mc.options.renderDistance().set(4);
                    server(() -> {
                        var srv = mc.getSingleplayerServer(); var p = srv.getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                        require(PigmeeWorkstationRuntime.manager(srv).ids().isEmpty(), "requires a fresh actor store");
                        for (int x = -5; x <= 12; x++) for (int z = -5; z <= 40; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                            for (int y = 100; y < 105; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        level.setDayTime(6000);
                        for (var v : PigmeeVariant.values()) level.setBlockAndUpdate(BASE.offset(v.ordinal() * 2, 0, 0), BuiltInRegistries.BLOCK.get(v.blockId()).defaultBlockState());
                        p.setGameMode(GameType.CREATIVE); p.getInventory().clearContent();
                        p.getInventory().setItem(0, new ItemStack(ModBlocks.OVERLOADED_CONTROLLER.get()));
                        p.getInventory().setItem(1, new ItemStack(ModItems.PIGMEE_REMOTE_CONTROL.get()));
                        p.teleportTo(level, 3.5, 100, .5, java.util.Set.of(), 0, 5);
                    });
                }
                case 1, 2, 3 -> {
                    var pos = BASE.offset((phase - 1) * 2, 0, 0);
                    if (PigmeeVariant.fromBlock(mc.level.getBlockState(pos)) == null) return;
                    mc.player.getInventory().selected = 0; mc.gameMode.tick();
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                }
                case 4 -> {
                    var actors = mc.level.players().stream().filter(p -> PigmeeClientIdentity.variant(p.getUUID()) != null).toList();
                    if (actors.size() != 3 || actors.stream().anyMatch(p -> !PigmeeClientIdentity.rendered(p.getUUID()))) return;
                    require(actors.stream().map(p -> PigmeeClientIdentity.variant(p.getUUID())).distinct().count() == 3, "three distinct client identities");
                    require(mc.getConnection().getListedOnlinePlayers().size() == 1, "proxies must not enter online player list");
                    capture("pigmee-p1-three-variants.png");
                    var actor = actors.stream().filter(p -> PigmeeClientIdentity.variant(p.getUUID()) == PigmeeVariant.HYPERDIMENSIONAL).findFirst().orElseThrow();
                    bound = actor.getUUID();
                    mc.player.getInventory().selected = 1; mc.gameMode.tick(); shift(true);
                    mc.gameMode.interact(mc.player, actor, InteractionHand.MAIN_HAND); shift(false);
                }
                case 5 -> {
                    require(bound.equals(PigmeeRemoteControlItem.bound(mc.player.getMainHandItem())), "binding must sync through real interact packet");
                    shift(true); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); shift(false);
                }
                case 6 -> {
                    require(mc.level.getPlayerByUUID(bound) == null && PigmeeClientIdentity.variant(bound) == null, "stop removes client body and visual identity");
                    shift(true); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); shift(false);
                }
                case 7 -> {
                    require(mc.level.getPlayerByUUID(bound) != null && PigmeeClientIdentity.variant(bound) == PigmeeVariant.HYPERDIMENSIONAL, "start restores same client identity");
                    server(() -> { var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); p.teleportTo(p.serverLevel(), 600.5, 100, .5, java.util.Set.of(), 0, 5); });
                }
                case 8 -> {
                    require(mc.level.getPlayerByUUID(bound) == null, "out-of-range stops entity tracking");
                    server(() -> { var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); p.teleportTo(p.serverLevel(), 3.5, 100, .5, java.util.Set.of(), 0, 5); });
                }
                case 9 -> {
                    if (mc.level.getPlayerByUUID(bound) == null) return;
                    require(mc.player.isCreative(), "owner mode preserved");
                    capture("pigmee-p1-retracked.png");
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        p.setGameMode(GameType.SURVIVAL);
                        p.getInventory().setItem(2, new ItemStack(net.minecraft.world.item.Items.STICK, 17));
                        var actor = PigmeeWorkstationRuntime.manager(p.server).active(bound);
                        actor.setYRot(0); actor.setYHeadRot(0);
                        ownerOrigin = p.position(); ownerInventory = p.getInventory().save(new net.minecraft.nbt.ListTag());
                    });
                }
                case 10 -> {
                    if (!mc.isWindowActive()) return;
                    require(!mc.player.isCreative() && mc.player.getInventory().getItem(2).getCount() == 17, "survival body fixture synced");
                    // Reproduce stale acceleration at the moment control opens, without changing velocity.
                    mc.player.xxa = .6F; mc.player.zza = 1; mc.player.setJumping(true);
                    mc.player.input.forwardImpulse = 1; mc.player.input.jumping = true;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
                case 11 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.workstation.PigmeeControlScreen, "control screen opened and remained focused");
                    require(mc.getCameraEntity() == mc.player, "logical camera remains real player for native position packets");
                    require(mc.gameRenderer.getMainCamera().getEntity() == mc.level.getPlayerByUUID(bound), "render camera observes Pigmee");
                    require(mc.player.xxa == 0 && mc.player.zza == 0 && !mc.player.input.jumping, "no retained body acceleration/jump");
                    capture("pigmee-p2-control-view.png");
                    actorStartZ = mc.level.getPlayerByUUID(bound).getZ();
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 0, 0);
                }
                case 12 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.workstation.PigmeeControlScreen, "control stays open during movement");
                    mc.screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 0, 0);
                    require(mc.level.getPlayerByUUID(bound).getZ() > actorStartZ + 1, "screen input drives server-authoritative proxy movement");
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(p.position().distanceToSqr(ownerOrigin) < .01, "remote movement does not move the real body");
                        require(p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && p.getInventory().save(new net.minecraft.nbt.ListTag()).equals(ownerInventory), "real mode/inventory unchanged");
                        var at = p.blockPosition();
                        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                            p.serverLevel().setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.AIR.defaultBlockState());
                            p.serverLevel().setBlockAndUpdate(at.offset(dx, -7, dz), Blocks.STONE.defaultBlockState());
                        }
                        p.hurt(p.damageSources().generic(), 3);
                        p.knockback(.4, 1, 0);
                    });
                }
                case 13 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.workstation.PigmeeControlScreen, "environment damage does not hijack control view");
                    require(mc.player.getY() < 96 && Math.abs(mc.player.getX() - ownerOrigin.x) > .1, "real client body falls and receives knockback");
                    double clientY = mc.player.getY();
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(p.getY() < 96 && Math.abs(p.getY() - clientY) < .25, "real server body receives native falling movement packets during preview");
                        require(Math.abs(p.getX() - ownerOrigin.x) > .1 && p.getHealth() < 20, "server receives body knockback and damage");
                        require(p.getInventory().save(new net.minecraft.nbt.ListTag()).equals(ownerInventory), "body inventory still independent after environmental damage");
                    });
                    capture("pigmee-p2-body-physics.png");
                    mc.screen.onClose();
                }
                case 14 -> {
                    require(mc.getCameraEntity() == mc.player && mc.gameRenderer.getMainCamera().getEntity() == mc.player, "exit restores view with same logical body");
                    server(() -> require(PigmeeWorkstationRuntime.manager(mc.getSingleplayerServer()).controls().count() == 0, "client exit releases server lease"));
                }
                case 15 -> {
                    finish(null);
                }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static void restore(Minecraft mc) throws Exception {
        if (phase == 0) {
            server(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var manager = PigmeeWorkstationRuntime.manager(p.server);
                require(manager.ids().size() == 3 && manager.activeCount() == 3, "three saved proxies restored before real client joined");
                require(manager.controls().count() == 0, "no persisted control leases");
                for (var id : manager.ids()) require(manager.activate(id) == manager.active(id), "restart identity is unique");
                p.serverLevel().setBlockAndUpdate(new BlockPos(3, 99, 0), Blocks.STONE.defaultBlockState());
                p.teleportTo(p.serverLevel(), 3.5, 100, .5, java.util.Set.of(), 0, 5);
            });
            phase = 1;
        } else {
            var actors = mc.level.players().stream().filter(p -> PigmeeClientIdentity.variant(p.getUUID()) != null).toList();
            if (actors.size() != 3) return;
            require(actors.stream().map(p -> PigmeeClientIdentity.variant(p.getUUID())).distinct().count() == 3, "late join has all three saved variants");
            for (var actor : actors) require(mc.getConnection().getPlayerInfo(actor.getUUID()) != null, "profile precedes late join entity spawn");
            require(mc.getConnection().getListedOnlinePlayers().size() == 1, "restarted proxies still absent from login list");
            require(actors.stream().anyMatch(p -> p.getUUID().equals(PigmeeRemoteControlItem.bound(mc.player.getMainHandItem()))), "held binding survived client/server restart");
            capture("pigmee-client-late-join.png");
            finish(null);
        }
    }
    private static void server(Runnable task) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable problem) { failure = problem; } }); }
    private static void shift(boolean down) { var mc = Minecraft.getInstance(); mc.player.input.shiftKeyDown = down; mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, down ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY)); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { img.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void finish(Throwable problem) {
        done = true; var mc = Minecraft.getInstance();
        String result = problem == null ? (Boolean.getBoolean("ae2lt.pigmeeClientRestoreProbe") ? "PIGMEE_CLIENT_RESTORE_PASS: real client late joins three uniquely restored identities, all variant/profile data present, binding persists and no control lease persisted" : "PIGMEE_CLIENT_PASS: P1 three rendered variants/controller/binding/stop-start/retracking; P2 real connected survival body keeps logical camera, position packets, mode/inventory; proxy moves; body falls/knockback/damage sync; exit releases lease") : "PIGMEE_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-client-result.txt"), result); } catch (Exception ignored) { }
        mc.stop();
    }
}
