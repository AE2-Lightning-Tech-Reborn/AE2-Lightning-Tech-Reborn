package com.moakiee.ae2lt.workstation;

import com.moakiee.ae2lt.client.workstation.PigmeeControlScreen;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in connected-client action and hotbar smoke test in a disposable copied world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeActionClientTests {
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile UUID id;
    private static volatile net.minecraft.nbt.ListTag ownerInventory;
    private static final BlockPos TARGET = new BlockPos(0, 101, 3);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeActionClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 1800) { finish(new AssertionError("timeout phase " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Pigmee action QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.renderDistance().set(4);
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                        var manager = PigmeeWorkstationRuntime.manager(p.server);
                        for (var existing : manager.ids()) manager.deactivate(existing);
                        for (int x = -10; x <= 10; x++) for (int z = -5; z <= 8; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                            for (int y = 100; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        level.setDayTime(6000); level.setBlockAndUpdate(TARGET, Blocks.STONE.defaultBlockState());
                        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                        p.teleportTo(level, -4.5, 100, .5, java.util.Set.of(), 0, 0);
                        p.getInventory().clearContent(); p.getInventory().selected = 0;
                        p.getInventory().setItem(0, new ItemStack(ModItems.PIGMEE_REMOTE_CONTROL.get()));
                        p.getInventory().setItem(2, new ItemStack(Items.STICK, 19));
                        var actor = manager.create(p.getUUID(), level, new Vec3(.5, 100, .5), 0); id = actor.getUUID();
                        actor.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
                        actor.getInventory().setItem(1, new ItemStack(Items.STONE, 3));
                        PigmeeRemoteControlItem.bind(p, p.getMainHandItem(), actor);
                        ownerInventory = p.getInventory().save(new net.minecraft.nbt.ListTag());
                    });
                }
                case 1 -> {
                    if (!mc.isWindowActive() || id == null || mc.level.getPlayerByUUID(id) == null) return;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
                case 2 -> {
                    require(mc.screen instanceof PigmeeControlScreen, "native remote opens control screen");
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_2, 0, 0);
                    mc.screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_2, 0, 0);
                }
                case 3 -> {
                    require(state().selected() == 1 && state().hotbar().get(1).getCount() == 3, "server hotbar selection and inventory synchronized");
                    require(mc.getCameraEntity() == mc.player && mc.gameRenderer.getMainCamera().getEntity() == mc.level.getPlayerByUUID(id), "camera identity isolation");
                    mc.screen.mouseClicked(100, 100, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                }
                case 4 -> {
                    require(state().hotbar().get(1).getCount() == 2, "native placement consumption returned through action snapshot");
                    require(mc.level.getBlockState(TARGET.north()).is(Blocks.STONE), "native placement tracked to client");
                    try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve("pigmee-action-hotbar.png")); }
                    mc.screen.mouseReleased(100, 100, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    mc.screen.onClose();
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(p.getInventory().save(new net.minecraft.nbt.ListTag()).equals(ownerInventory), "real player inventory unchanged");
                    });
                }
                case 5 -> {
                    require(mc.screen == null, "control screen exits");
                    finish(null); return;
                }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static PigmeeControlStatePacket state() throws Exception {
        var field = PigmeeControlScreen.class.getDeclaredField("state"); field.setAccessible(true);
        return (PigmeeControlStatePacket) field.get(Minecraft.getInstance().screen);
    }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void finish(Throwable problem) {
        done = true; String result = problem == null ? "PIGMEE_ACTION_CLIENT_PASS: real packets, isolated render camera, selected slot, native placement, hotbar count sync, unchanged owner inventory and screen exit"
                : "PIGMEE_ACTION_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-action-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
