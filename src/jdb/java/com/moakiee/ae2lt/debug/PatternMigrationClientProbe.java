package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.client.MatrixControllerScreen;
import com.moakiee.ae2lt.client.MatrixPortScreen;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationGameTests;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.*;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Real menus, payloads and framebuffers in a disposable world. Never included in the release jar. */
@EventBusSubscriber(modid="ae2lt", value=Dist.CLIENT)
public final class PatternMigrationClientProbe {
    private static PatternMigrationGameTests.Fixture fixture;
    private static volatile Throwable failure;
    private static int ticks, phase;
    private static boolean done;
    private static volatile boolean serverPending, resourcesPending;
    private static appeng.me.cluster.implementations.CraftingCPUCluster cpu;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.patternMigrationClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if (mc.player == null || mc.getSingleplayerServer() == null || serverPending || resourcesPending || ++ticks % 100 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("TIANSHU MIGRATION QA - disposable world");
                    mc.getWindow().setWindowed(1400, 1000); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn");
                    resourcesPending=true;
                    mc.reloadResourcePacks().whenComplete((ignored,error)->{if(error!=null)failure=error;resourcesPending=false;});
                    server(() -> {
                        var srv = mc.getSingleplayerServer(); var level = srv.overworld();
                        fixture = PatternMigrationGameTests.build(level, new BlockPos(0, 100, 0));
                        for (var storage : fixture.port().getPatternStorages()) {
                            storage.beginPatternBatch();
                            try {
                                for (int slot = 0; slot < storage.capacity(); slot++)
                                    storage.getInventory().setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                            } finally { storage.endPatternBatch(); }
                        }
                        PatternMigrationGameTests.unregister(fixture.player());
                        var player = srv.getPlayerList().getPlayer(mc.player.getUUID()); player.setGameMode(GameType.CREATIVE); player.getAbilities().flying=true; player.onUpdateAbilities();
                        player.teleportTo(level, .5, 106, 3.5, Set.of(), -90, 0);
                    });
                }
                case 1 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    fixture.controller().scanAndForm(player);
                    if (!fixture.port().isLinkConnected()) throw new AssertionError("network not active: controller=" + fixture.controller().isFormed() + ", port=" + fixture.port().isFormed() + ", active=" + fixture.port().getMainNode().isActive() + ", grid=" + fixture.port().getGrid());
                    var encoded = PatternMigrationGameTests.pattern(player.serverLevel(), false);
                    for (int i = 0; i < 9; i++) fixture.provider().getLogic().getPatternInv().setItemDirect(i, encoded.copy());
                    open(false);
                });
                case 2 -> {
                    if (!(mc.screen instanceof MatrixControllerScreen screen)) throw new AssertionError("controller screen missing: " + mc.screen + "; menu=" + mc.player.containerMenu);
                    capture("migration-controller-scale2-idle.png");
                    screen.mouseClicked(screen.getGuiLeft() - 10, screen.getGuiTop() + 52, 0);
                }
                case 3 -> {
                    var menu = (MatrixMigrationMenu) mc.player.containerMenu;
                    if (menu.getMigrationSnapshot().stage() != PatternMigrationSnapshot.Stage.COMPLETE
                            || menu.getMigrationSnapshot().moved() != 1 || menu.getMigrationSnapshot().recovered() != 8)
                        throw new AssertionError("button payload or status sync failed: " + menu.getMigrationSnapshot());
                    capture("migration-controller-scale2-complete.png"); server(() -> open(true));
                }
                case 4 -> {
                    if (!(mc.screen instanceof MatrixPortScreen)) throw new AssertionError("port screen missing");
                    if (((MatrixMigrationMenu) mc.player.containerMenu).getMigrationSnapshot().recovered() != 8)
                        throw new AssertionError("shared port report missing");
                    capture("migration-port-scale2-complete.png"); mc.options.guiScale().set(3); mc.resizeDisplay();
                }
                case 5 -> { capture("migration-port-scale3-complete.png"); mc.options.guiScale().set(4); mc.resizeDisplay(); }
                case 6 -> { capture("migration-port-scale4-complete.png"); server(() -> open(false)); }
                case 7 -> {
                    capture("migration-controller-scale4-complete.png");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var first = PatternMigrationGameTests.pattern(player.serverLevel(), false);
                        var tag = new net.minecraft.nbt.CompoundTag(); tag.put("PatternMigrationRecovery", first.save(new net.minecraft.nbt.CompoundTag()));
                        fixture.controller().getPatternMigration().readFrom(tag);
                    });
                }
                case 8 -> {
                    capture("migration-controller-scale4-recovery.png");
                    if (((MatrixMigrationMenu) mc.player.containerMenu).getMigrationSnapshot().reason() != PatternMigrationSnapshot.Reason.RECOVERY)
                        throw new AssertionError("recovery state not synchronized");
                    server(() -> open(true));
                }
                case 9 -> {
                    if (((MatrixMigrationMenu) mc.player.containerMenu).getMigrationSnapshot().stage() != PatternMigrationSnapshot.Stage.IDLE)
                        throw new AssertionError("reopening port does not refund recovery");
                    capture("migration-port-scale4-recovered.png");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var encoded = PatternMigrationGameTests.pattern(player.serverLevel(), false);
                        fixture.provider().getLogic().getPatternInv().setItemDirect(0, encoded.copy());
                        var grid=fixture.port().getGrid();
                        cpu=(appeng.me.cluster.implementations.CraftingCPUCluster)grid.getCraftingService().getCpus().iterator().next();
                        var details=appeng.api.crafting.PatternDetailsHelper.decodePattern(encoded,player.serverLevel());
                        fixture.controller().getPatternMigration().start(player);
                        var plan=new appeng.crafting.CraftingPlan(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.OAK_PLANKS),4),
                                8,false,false,new appeng.api.stacks.KeyCounter(),new appeng.api.stacks.KeyCounter(),new appeng.api.stacks.KeyCounter(),java.util.Map.of(details,1L));
                        if(!cpu.submitJob(grid,plan,appeng.api.networking.security.IActionSource.ofPlayer(player),null).successful())throw new AssertionError("client pause fixture CPU submit failed");
                    });
                }
                case 10 -> {
                    if (((MatrixMigrationMenu) mc.player.containerMenu).getMigrationSnapshot().stage()!=PatternMigrationSnapshot.Stage.WAITING)
                        throw new AssertionError("waiting state missing");
                    var screen=(MatrixPortScreen)mc.screen;
                    var button=screen.children().stream().filter(child -> child instanceof com.moakiee.ae2lt.client.TextureToggleButton)
                            .map(child -> (com.moakiee.ae2lt.client.TextureToggleButton)child).findFirst().orElseThrow();
                    if(button.getStateIndex()!=1 || !button.getMessage().getString().contains("停止"))throw new AssertionError("stop button state missing");
                    capture("migration-port-scale4-waiting.png");
                    screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+8,0);
                }
                case 11 -> {
                    if (((MatrixMigrationMenu) mc.player.containerMenu).getMigrationSnapshot().stage()!=PatternMigrationSnapshot.Stage.STOPPED)
                        throw new AssertionError("stop payload missing");
                    capture("migration-port-scale4-stopped.png");server(() -> {cpu.cancelJob();open(false);});
                }
                case 12 -> {
                    var screen=(MatrixControllerScreen)mc.screen;
                    screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+52,0);
                    mc.player.closeContainer(); // Task survives closing its last menu.
                }
                case 13 -> server(() -> {
                    var s=fixture.controller().getPatternMigration().snapshot();
                    if(s.stage()!=PatternMigrationSnapshot.Stage.COMPLETE||s.recovered()!=1)throw new AssertionError("closed menu task does not continue: "+s);
                    open(true);
                });
                case 14 -> {
                    capture("migration-port-scale4-reopened.png");
                    Files.writeString(mc.gameDirectory.toPath().resolve("migration-client-result.txt"),
                            "PASS: real controller/port screens and C2S; exact refunds and shared status; recovery; GUI scales 2/3/4; CPU waiting; stop button and cancellation; closed-menu completion.\n");
                    done=true;mc.stop();
                }
            }
            phase++;
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("migration-client-result.txt"), "FAIL: " + e); }
            catch (Exception ignored) {}
            done = true; mc.stop();
        }
    }

    private static void open(boolean port) {
        var mc = Minecraft.getInstance(); var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var be = port ? fixture.port() : fixture.controller();
        player.teleportTo(player.serverLevel(), be.getBlockPos().getX() + (port ? 1.5 : -1.5), be.getBlockPos().getY(), be.getBlockPos().getZ() + .5, Set.of(), 0, 0);
        if (port) net.minecraftforge.network.NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new MatrixPortMenu(id, inv, fixture.port()), Component.literal("天枢样板管理")),
                buf -> MatrixPortMenu.writeExtraData(buf, fixture.port()));
        else net.minecraftforge.network.NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new MatrixControllerMenu(id, inv, fixture.controller()), Component.literal("天枢物质扭曲矩阵")),
                buf -> MatrixControllerMenu.writeExtraData(buf, fixture.controller()));
    }
    private static void server(Runnable action) {
        serverPending=true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable e) { failure = e; }
            finally { serverPending=false; }
        });
    }
    private static void capture(String name) {
        var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), text -> {});
    }
}
