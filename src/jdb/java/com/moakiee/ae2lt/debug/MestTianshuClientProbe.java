package com.moakiee.ae2lt.debug;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.lhy.mest.client.MESTScreen;
import com.lhy.mest.terminal.MESTMenu;
import com.lhy.mest.registry.ModItems;
import com.moakiee.ae2lt.integration.mest.MestTianshuAccess;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import com.moakiee.ae2lt.client.tianshu.TianshuGlobalReserveScreen;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "ae2lt")
public final class MestTianshuClientProbe {
    private static ServerPlayer pending;
    private static int delay;
    private static net.minecraft.core.BlockPos network;
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("ae2lt.mestClientProbe") && event.getEntity() instanceof ServerPlayer player) {
            var level = player.serverLevel();
            network = new net.minecraft.core.BlockPos(0, 80, 0);
            player.setPos(.5, 81, .5);
            appeng.api.parts.PartHelper.setPart(level, network, null, player, appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
            level.setBlockAndUpdate(network.below(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(network.west(), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
            var cell = appeng.core.definitions.AEItems.ITEM_CELL_1K.stack();
            var contents = appeng.api.storage.StorageCells.getCellInventory(cell, null);
            var inserted = contents.insert(appeng.api.stacks.AEItemKey.of(appeng.core.definitions.AEItems.BLANK_PATTERN), 32, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.ofPlayer(player));
            contents.persist();
            if (inserted != 32) throw new AssertionError("Test cell could not hold blank patterns");
            ((appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(network.west())).getInternalInventory().setItemDirect(0, cell);
            level.setBlockAndUpdate(network.north(), appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
            var terminal = new ItemStack(ModItems.splicedTerminalItem());
            terminal.set(appeng.api.ids.AEComponents.WIRELESS_LINK_TARGET, net.minecraft.core.GlobalPos.of(level.dimension(), network.north()));
            terminal.set(appeng.api.ids.AEComponents.STORED_ENERGY, 1000000.0);
            player.getInventory().setItem(0, terminal);
            pending = player; delay = 40;
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (pending != null && --delay == 0) {
            pending.teleportTo(network.getX() + .5, network.getY() + 1, network.getZ() + .5);
            MenuOpener.open(MESTMenu.TYPE, pending, MenuLocators.forInventorySlot(0));
            var nativeMenu = ((MestTianshuAccess) pending.containerMenu).tianshuCraftingMenu();
            var slots = nativeMenu.getSlots(com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_SMITHING);
            slots.get(0).set(new ItemStack(net.minecraft.world.item.Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            slots.get(1).set(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            slots.get(2).set(new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT));
            pending = null;
        }
    }
    @EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
    public static final class Client {
        static int watchdog; static int ticks; static MESTScreen root; static boolean done;
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("ae2lt.mestClientProbe") || done) return;
            var mc = Minecraft.getInstance();
            if (++watchdog > 600) {
                try { Files.writeString(mc.gameDirectory.toPath().resolve("result.txt"), "FAIL: timed out at " + mc.screen); } catch (Exception ignored) {}
                done = true; mc.stop(); return;
            }
            if (mc.getOverlay() != null || mc.player == null) return;
            try {
                if (root == null) {
                    if (!(mc.screen instanceof MESTScreen screen)) return;
                    root = screen;
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                }
                ticks++;
                if (ticks == 10) arrange();
                if (ticks == 35) {
                    capture("mest-panels.png");
                    var dump = new StringBuilder();
                    var field = MESTScreen.class.getDeclaredField("dock"); field.setAccessible(true);
                    var dock = (com.lhy.mest.client.dock.DockManager) field.get(root);
                    for (var p : dock.panels()) if (p instanceof com.moakiee.ae2lt.integration.mest.client.TianshuModulePanel panel) {
                        dump.append(panel.id()).append(" crop=").append(panel.source.embeddedPanelBounds()).append(" left/top=")
                            .append(panel.source.embeddedLeft()).append('/').append(panel.source.embeddedTop()).append('\n');
                        for (var child : panel.source.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible)
                            dump.append(w.getClass().getSimpleName()).append(' ').append(w.getMessage().getString()).append(' ').append(w.getX()).append(',').append(w.getY()).append(' ').append(w.getWidth()).append('x').append(w.getHeight()).append('\n');
                    }
                    Files.writeString(mc.gameDirectory.toPath().resolve("layout.txt"), dump);
                }
                if (ticks == 40) {
                    var crafting = MestTianshuClient.crafting(root);
                    if (crafting == null) throw new AssertionError("Missing Tianshu panel");
                    crafting.source.switchToScreen(new TianshuGlobalReserveScreen<>(crafting.source));
                }
                if (ticks == 55) {
                    if (!(mc.screen instanceof TianshuGlobalReserveScreen<?>)) throw new AssertionError("Editor did not open");
                    capture("mest-maintenance.png");
                    var back = appeng.client.gui.AESubScreen.class.getDeclaredMethod("returnToParent");
                    back.setAccessible(true); back.invoke(mc.screen);
                }
                if (ticks == 70) {
                    if (mc.screen != root || mc.player.containerMenu != root.getMenu()) throw new AssertionError("Editor did not return to original MEST container: " + mc.screen + " menu=" + mc.player.containerMenu);
                    capture("mest-return.png");
                }
                if (ticks == 75) {
                    var panel = patternPanel();
                    panel.mouseClicked(panel.contentLeft() + 180, panel.contentTop() + 2, 0);
                    panel.mouseReleased(panel.contentLeft() + 180, panel.contentTop() + 2, 0);
                    if (com.lhy.mest.integration.MestRecipeTransferContext.targetFor(root.getMenu()) != com.lhy.mest.integration.MestRecipeTransferContext.Target.PATTERN_ENCODING)
                        throw new AssertionError("Recipe viewer did not target focused native pane");
                    com.lhy.mest.integration.MestEncodingHelper.encode(root.getMenu(), com.lhy.mest.integration.MestEncodingHelper.RecipeKind.PROCESSING, null,
                            java.util.List.of(), java.util.List.of(java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 3))),
                            java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 2)), stack -> true);
                }
                if (ticks == 90) {
                    var pattern = ((MestTianshuAccess) root.getMenu()).tianshuPatternMenu();
                    var input = appeng.api.stacks.GenericStack.fromItemStack(pattern.getProcessingInputSlots()[0].getItem());
                    var output = appeng.api.stacks.GenericStack.fromItemStack(pattern.getProcessingOutputSlots()[0].getItem());
                    if (input == null || input.amount() != 3 || !input.what().equals(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT))
                            || output == null || output.amount() != 2) throw new AssertionError("Mapped viewer transfer did not round-trip to native pattern slots");
                    if (!root.getMenu().getPatternCraftingSlots()[0].getItem().isEmpty()) throw new AssertionError("Viewer transfer touched built-in encoder");
                    pattern.encode();
                }
                if (ticks == 105) {
                    var pattern = ((MestTianshuAccess) root.getMenu()).tianshuPatternMenu();
                    var result = pattern.getSlots(appeng.menu.SlotSemantics.ENCODED_PATTERN).getFirst().getItem();
                    if (appeng.api.crafting.PatternDetailsHelper.decodePattern(result, mc.level) == null) throw new AssertionError("Native encode did not return paid valid pattern: " + result + " link=" + pattern.getLinkStatus() + " mode=" + pattern.tianshuMode + " ack=" + pattern.triggeredUploadAck + " screen=" + mc.screen);
                    capture("mest-encoded.png");
                    if (mc.screen instanceof appeng.client.gui.AESubScreen<?, ?>) {
                        var back = appeng.client.gui.AESubScreen.class.getDeclaredMethod("returnToParent");
                        back.setAccessible(true); back.invoke(mc.screen);
                    }
                    if (mc.screen != root) throw new AssertionError("Upload editor did not return to MEST");
                    ((MestTianshuAccess) root.getMenu()).tianshuCraftingMenu().setWorkPage(com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.SMITHING);
                }
                if (ticks == 120) {
                    var crafting = MestTianshuClient.crafting(root);
                    crafting.layoutSlots();
                    var original = crafting.source.getMenu().getSlots(com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_SMITHING).get(3);
                    var slot = root.getMenu().slots.stream().filter(s -> s instanceof com.moakiee.ae2lt.integration.mest.MestSlot proxy && proxy.original() == original).findFirst().orElseThrow();
                    crafting.mouseClicked(slot.x + 8, slot.y + 8, 0);
                    crafting.mouseReleased(slot.x + 8, slot.y + 8, 0);
                }
                if (ticks == 135) {
                    if (!root.getMenu().getCarried().is(net.minecraft.world.item.Items.NETHERITE_SWORD)) throw new AssertionError("Translated native result click did not craft exactly once");
                    Files.writeString(mc.gameDirectory.toPath().resolve("result.txt"), "PASS: native MEST panes, editor return, focused recipe fill, paid encoding and translated smithing result click through real client/server packets.\n");
                    done = true; mc.stop();
                }
            } catch (Throwable error) {
                error.printStackTrace();
                try { Files.writeString(mc.gameDirectory.toPath().resolve("result.txt"), "FAIL: " + error); } catch (Exception ignored) {}
                done = true; mc.stop();
            }
        }
        static com.moakiee.ae2lt.integration.mest.client.TianshuModulePanel patternPanel() throws Exception {
            var field = MESTScreen.class.getDeclaredField("dock"); field.setAccessible(true);
            var dock = (com.lhy.mest.client.dock.DockManager) field.get(root);
            return dock.panels().stream().filter(p -> p instanceof com.moakiee.ae2lt.integration.mest.client.TianshuModulePanel panel && panel.isEncoding())
                    .map(p -> (com.moakiee.ae2lt.integration.mest.client.TianshuModulePanel) p).findFirst().orElseThrow();
        }
        static void arrange() throws Exception {
            var field = MESTScreen.class.getDeclaredField("dock"); field.setAccessible(true);
            var dock = (com.lhy.mest.client.dock.DockManager) field.get(root);
            for (var panel : dock.panels()) {
                if (panel.id().startsWith("ae2lt:")) dock.revealModule(panel); else dock.hideModule(panel);
            }
            var ws = dock.workspaceSnapshot();
            var roots = new java.util.ArrayList<com.lhy.mest.client.dock.model.FloatingRoot>();
            int index = 0;
            for (var r : ws.roots()) {
                if (r.content() instanceof com.lhy.mest.client.dock.model.LeafNode leaf && leaf.moduleId().startsWith("ae2lt:")) {
                    r = r.withBounds(new com.lhy.mest.client.dock.model.DockRect(25 + index++ * 220, 100, r.bounds().width(), r.bounds().height()));
                }
                roots.add(r);
            }
            var replace = dock.getClass().getDeclaredMethod("replaceWorkspace", com.lhy.mest.client.dock.model.DockWorkspace.class, boolean.class);
            replace.setAccessible(true); replace.invoke(dock, ws.withRoots(roots), true);
        }
        static void capture(String name) throws Exception {
            var mc = Minecraft.getInstance();
            try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { pixels.writeToFile(mc.gameDirectory.toPath().resolve(name)); }
        }
    }
}
