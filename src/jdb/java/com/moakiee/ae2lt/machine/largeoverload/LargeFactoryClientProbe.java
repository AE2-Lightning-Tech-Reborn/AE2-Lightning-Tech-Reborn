package com.moakiee.ae2lt.machine.largeoverload;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.client.machine.LargeFactoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Disposable client QA: real menus, slot paging, server-validated buttons and resource rendering. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class LargeFactoryClientProbe {
    private static final BlockPos CONTROLLER = new BlockPos(4, 103, 0);
    private static final BlockPos HATCH = new BlockPos(4, 103, 8);
    private static int phase, ticks;
    private static boolean done;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static volatile boolean retryPhase;
    private static int formationWaits;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.largeFactoryClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks % 40 != 0 || pending) return;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (ticks == 200) {
                capture("factory-client-loading.png");
                com.mojang.logging.LogUtils.getLogger().info("Large factory client waiting at {}", mc.screen);
            }
            return;
        }
        try {
            if (failure != null) throw new AssertionError(failure);
            if (retryPhase) { phase--; retryPhase = false; }
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("LARGE FACTORY QA - disposable world");
                    mc.getWindow().setWindowed(1400, 1000); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn");
                    pending = true;
                    mc.reloadResourcePacks().whenComplete((unused, error) -> { if (error != null) failure = error; pending = false; });
                }
                case 1 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    for (var cell : LargeFactoryStructure.cells()) {
                        var part = switch (cell.role()) {
                            case CONTROLLER -> LargeFactoryComponent.CONTROLLER;
                            case FRAME -> LargeFactoryComponent.FRAME;
                            case CORE -> LargeFactoryComponent.CORE_T4;
                            case CASING, HATCH -> LargeFactoryComponent.CASING;
                            case AIR -> LargeFactoryComponent.AIR;
                        };
                        var pos = LargeFactoryStructure.worldPosition(CONTROLLER, cell.localPosition(), Direction.NORTH);
                        if (pos.equals(HATCH)) part = LargeFactoryComponent.EXPANDED_PATTERN_HATCH;
                        level.setBlockAndUpdate(pos, part == LargeFactoryComponent.AIR ? Blocks.AIR.defaultBlockState() : LargeFactoryRegistration.block(part).defaultBlockState());
                    }
                    level.setBlockAndUpdate(HATCH.south(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.teleportTo(level, 4.5, 103, -2.5, Set.of(), 0, 0);
                });
                case 2 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    var hatch = (LargeFactoryHatchBlockEntity) level.getBlockEntity(HATCH);
                    if (!hatch.ready()) {
                        if (++formationWaits > 15) throw new AssertionError("QA factory not ready after bounded scan wait");
                        retryPhase = true; return;
                    }
                    if (hatch.passive()) hatch.togglePassive();
                    for (int i = 0; i < 144; i++) hatch.inventory().setItemDirect(i, PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(AEItemKey.of(Items.STONE), i + 1)), List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), (i + 1) * 2))));
                    open(CONTROLLER);
                });
                case 3 -> {
                    assertScreen();
                    if (((LargeFactoryMenu) mc.player.containerMenu).snapshot.operationsPerTick() != LargeFactoryOperationBudget.UNLIMITED)
                        throw new AssertionError("unlimited T4 status missing");
                    capture("factory-controller-scale2.png"); server(() -> open(HATCH));
                }
                case 4 -> {
                    assertScreen(); capture("factory-expanded-page1-scale2.png");
                    mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, LargeFactoryMenu.MODE);
                    ((LargeFactoryMenu) mc.player.containerMenu).setPage(1);
                    mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, LargeFactoryMenu.PAGE_NEXT);
                }
                case 5 -> {
                    var menu = (LargeFactoryMenu) mc.player.containerMenu;
                    if (!menu.snapshot.passive() || menu.page() != 1 || menu.snapshot.entryCount() != 144) throw new AssertionError("mode/page/status packets failed: " + menu.page() + " / " + menu.snapshot);
                    capture("factory-expanded-page2-passive-scale2.png");
                    mc.options.guiScale().set(3); mc.resizeDisplay();
                }
                case 6 -> {
                    capture("factory-expanded-scale3.png");
                    mc.getWindow().setWindowed(1280, 720); mc.resizeDisplay();
                }
                case 7 -> {
                    capture("factory-expanded-720p-scale3.png");
                    mc.player.closeContainer(); server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setBlockAndUpdate(CONTROLLER.offset(-4, -3, 0), Blocks.AIR.defaultBlockState());
                        open(CONTROLLER);
                    });
                }
                case 8 -> {
                    if (((LargeFactoryMenu) mc.player.containerMenu).snapshot.formed()) throw new AssertionError("unformed UI stale");
                    capture("factory-controller-missing-frame-scale3.png");
                    Files.writeString(mc.gameDirectory.toPath().resolve("factory-client-result.txt"),
                            "PASS: unlimited T4 controller, 144-slot hatch, page change, passive mode C2S, S2C status, missing structure; Chinese GUI scales 2 and 3 including 720p.\n");
                    done = true; mc.stop();
                }
            }
            phase++;
        } catch (Throwable error) {
            error.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("factory-client-result.txt"), "FAIL: " + error); } catch (Exception ignored) { }
            done = true; mc.stop();
        }
    }
    private static void assertScreen() { if (!(Minecraft.getInstance().screen instanceof LargeFactoryScreen)) throw new AssertionError("factory screen missing"); }
    private static void open(BlockPos pos) {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.teleportTo(player.serverLevel(), pos.getX() + .5, pos.getY(), pos.getZ() + (pos.equals(HATCH) ? 2.5 : -2.5), Set.of(), 0, 0);
        LargeFactoryMenu.open(player.serverLevel(), pos, player);
    }
    private static void server(Runnable action) {
        pending = true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { failure = error; } finally { pending = false; }
        });
    }
    private static void capture(String name) {
        var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> { });
    }
}
