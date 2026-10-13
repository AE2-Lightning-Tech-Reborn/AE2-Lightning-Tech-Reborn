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
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Pigmee P1 QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.fov().set(65); mc.options.renderDistance().set(4);
                    server(() -> {
                        var srv = mc.getSingleplayerServer(); var p = srv.getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                        require(PigmeeWorkstationRuntime.manager(srv).ids().isEmpty(), "requires a fresh actor store");
                        for (int x = -5; x <= 12; x++) for (int z = -5; z <= 12; z++) {
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
                    var actor = actors.stream().filter(p -> PigmeeClientIdentity.variant(p.getUUID()) == PigmeeVariant.NORMAL).findFirst().orElseThrow();
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
                    require(mc.level.getPlayerByUUID(bound) != null && PigmeeClientIdentity.variant(bound) == PigmeeVariant.NORMAL, "start restores same client identity");
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
                    finish(null);
                }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static void server(Runnable task) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable problem) { failure = problem; } }); }
    private static void shift(boolean down) { var mc = Minecraft.getInstance(); mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, down ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY)); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { img.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void finish(Throwable problem) {
        done = true; var mc = Minecraft.getInstance();
        String result = problem == null ? "PIGMEE_CLIENT_PASS: three rendered variants, controller use packets, binding, stop/start and re-tracking; owner remains creative and sole listed player" : "PIGMEE_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-client-result.txt"), result); } catch (Exception ignored) { }
        mc.stop();
    }
}
