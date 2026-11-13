package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.client.tianshu.TianshuPatternStorageScreen;
import com.moakiee.ae2lt.menu.TianshuPatternStorageMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class TianshuPatternStorageClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.patternStorageProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        try {
            if (++ticks > 2400) throw new AssertionError("client probe timed out");
            if (mc.player == null || mc.getSingleplayerServer() == null || ticks % 20 != 0) return;
            if (serverFailure != null) throw new AssertionError("server action failed", serverFailure);
            switch (phase++) {
                case 0 -> {
                    mc.setScreen(null);
                    server(player -> {
                        var level = player.serverLevel();
                        level.getChunkAt(POS);
                        level.setDayTime(6000);
                        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                            level.setBlockAndUpdate(POS.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(POS, ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get().defaultBlockState());
                        var storage = (TianshuPatternStorageBlockEntity) level.getBlockEntity(POS);
                        for (int slot : new int[] {0, 7, 35}) {
                            storage.getTerminalPatternInventory().insertItem(slot, TianshuPatternStorageGameTests.pattern(level), false);
                        }
                        player.setGameMode(GameType.SURVIVAL);
                        player.teleportTo(level, .5, 100, -2, Set.of(), 0, 15);
                        player.getInventory().clearContent();
                    });
                }
                case 1 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(.5, 100.5, 0), Direction.NORTH, POS, false));
                case 2 -> {
                    var screen = screen();
                    check(screen.getMenu().slots.size() == 72, "wrong visible inventory size");
                    check(!screen.getMenu().getSlot(35).getItem().isEmpty(), "last pattern row did not synchronize");
                    Screenshot.grab(mc.gameDirectory, "closed-loop-pattern-storage.png", mc.getMainRenderTarget(), ignored -> {});
                    click(0);
                }
                case 3 -> {
                    check(!screen().getMenu().getCarried().isEmpty(), "mouse pickup did not reach client");
                    server(player -> check(((TianshuPatternStorageBlockEntity) player.level().getBlockEntity(POS))
                            .patterns().size() == 2, "mouse pickup did not remove server payload"));
                    click(36);
                }
                case 4 -> {
                    check(screen().getMenu().getCarried().isEmpty(), "mouse drop into backpack failed");
                    check(!screen().getMenu().getSlot(36).getItem().isEmpty(), "backpack item did not synchronize");
                    server(player -> check(player.getInventory().countItem(
                            com.moakiee.ae2lt.registry.ModItems.CLOSED_LOOP_PATTERN.get()) == 1, "server inventory lost pattern"));
                }
                case 5 -> {
                    mc.player.closeContainer();
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(new Vec3(.5, 100.5, 0), Direction.NORTH, POS, false));
                }
                case 6 -> {
                    check(screen().getMenu().getSlot(0).getItem().isEmpty(), "reopened GUI resurrected extracted pattern");
                    finish("PASS: native warehouse GUI, right-click, slot sync, mouse transfer and reopen");
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish("FAIL: " + failure);
        }
    }

    private static TianshuPatternStorageScreen screen() {
        if (Minecraft.getInstance().screen instanceof TianshuPatternStorageScreen screen) return screen;
        throw new AssertionError("warehouse screen did not open: " + Minecraft.getInstance().screen);
    }

    private static void click(int index) {
        var screen = screen();
        var slot = screen.getMenu().getSlot(index);
        double x = (screen.width - 176) / 2 + slot.x + 8;
        double y = (screen.height - (TianshuPatternStorageMenu.ROWS * 18 + 114)) / 2 + slot.y + 8;
        check(screen.mouseClicked(x, y, 0), "screen rejected click");
        screen.mouseReleased(x, y, 0);
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(String result) {
        done = true;
        try { Files.writeString(Path.of(System.getProperty("ae2lt.patternStorageReport")), result + "\n"); }
        catch (java.io.IOException error) { throw new RuntimeException(error); }
        Minecraft.getInstance().stop();
    }
}
