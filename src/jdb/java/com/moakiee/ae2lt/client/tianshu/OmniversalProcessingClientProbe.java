package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.compat.ProcessingPatternTransferStacks;

import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternPayload;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.GridInventoryEntry;
import com.moakiee.ae2lt.integration.useless.UselessModClientTransfer;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Opt-in real JEI/menu/packet acceptance probe; use only in the copied OmniversalProcessingQA world. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="ae2lt", value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class OmniversalProcessingClientProbe {

    private static int phase, ticks;
    private static boolean done, wireless;
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.omniversalProcessingProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks > 4000) { finish("FAIL: timeout"); return; }
        if (ticks % 100 == 0) System.out.println("OMNI_QA tick=" + ticks + " phase=" + phase + " runtime=" + (runtime != null) + " screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()));
        if (mc.player == null || ticks % 50 != 0) return;
        try { runtime = mezz.jei.common.Internal.getJeiRuntime(); } catch (IllegalStateException e) { return; }
        if (runtime == null) return;
        if (report.startsWith("FAILED:")) { finish(report); return; }
        try {
            System.out.println("OMNI_QA phase " + phase);
            switch (phase++) {
                case 0 -> { mc.options.guiScale().set(2); mc.resizeDisplay(); openTerminal(false); }
                case 1 -> seedOrdinaryDraft();
                case 2 -> transfer();
                case 3 -> verifyFillAndEdit(false);
                case 4 -> verifySavedAmount();
                case 5 -> transfer();
                case 6 -> verifyFillAndEdit(true);
                case 7 -> verifySavedAmount();
                case 8 -> { screenshot(); }
                case 9 -> { wireless = true; openTerminal(true); }
                case 10 -> seedOrdinaryDraft();
                case 11 -> transfer();
                case 12 -> verifyFillAndEdit(false);
                case 13 -> verifySavedAmount();
                case 14 -> transfer();
                case 15 -> verifyFillAndEdit(true);
                case 16 -> verifySavedAmount();
                case 17 -> screenshot();
                case 18 -> finish("OMNIVERSAL_CLIENT_PASS\n" + String.join("\n", checks));
            }
        } catch (Throwable e) { e.printStackTrace(); finish("FAILED: " + e); }
    }
    private static void finish(String result) {
        done = true;
        try { Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("omniversal-processing-probe.txt"), result); }
        catch (Exception e) { e.printStackTrace(); }
        Minecraft.getInstance().stop();
    }

    private static IJeiRuntime runtime;
    private static volatile String report = "idle";
    private static final List<String> checks = new ArrayList<>();
    private static ItemStack transferred;
    private static int editedSlot;

    public static String openTerminal(boolean wireless) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "server not ready";
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            player.closeContainer();
            if (wireless) {
                var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
                var terminal = new ItemStack(item);
                terminal.getOrCreateTag().putDouble("internalCurrentPower", 1000000.0);
                appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(terminal, net.minecraft.core.GlobalPos.of(player.level().dimension(), new net.minecraft.core.BlockPos(0,100,-1)));
                player.getInventory().setItem(0, terminal);
                player.inventoryMenu.broadcastChanges();
                ((de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem)item).open(player, terminal, MenuLocators.forInventorySlot(0), false);
            } else {
                com.moakiee.ae2lt.debug.OmniversalTerminalClientProbe.setup(player);
            }
            report = "opening " + (wireless ? "wireless" : "wired");
        });
        return "queued terminal";
    }

    public static String seedOrdinaryDraft() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var menu = (TianshuPatternEncodingTermMenu) server.getPlayerList().getPlayers().get(0).containerMenu;
            menu.setTianshuMode(TianshuEncodingMode.PROCESSING);
            menu.getProcessingInputSlots()[0].set(new ItemStack(Items.SAND, 3));
            menu.broadcastChanges();
            report = "seeded ordinary sand x3";
        });
        return "queued ordinary draft";
    }

    public static String transfer() {
        return enqueue(() -> {
            var mc = Minecraft.getInstance();
            var menu = (TianshuPatternEncodingTermMenu) mc.player.containerMenu;
            var birch = AEItemKey.of(Items.BIRCH_PLANKS);
            menu.getClientRepo().handleUpdate(true, List.of(
                    new GridInventoryEntry(41001, AEItemKey.of(Items.OAK_PLANKS), 999, 0, false),
                    new GridInventoryEntry(41002, birch, 0, 0, true),
                    new GridInventoryEntry(41003, AEItemKey.of(Items.STICK), 0, 0, true),
                    new GridInventoryEntry(41004, AEItemKey.of(Items.AMETHYST_SHARD), 0, 0, true)));
            var choice = AlloyFurnaceRecipeCatalog.entries(mc.level).stream()
                    .filter(e -> e.identity().recipeId().toString().equals("ae2lt_omniversal:tagged_input"))
                    .findFirst().orElseThrow();
            var prepared = UselessModClientTransfer.prepare(menu, choice);
            require(!prepared.isEmpty(), "native binding accepts network-selected alternative");
            var data = ProcessingPatternPayload.read(prepared);
            require(data.sparseInputs().get(0).equals(new GenericStack(birch, 2)),
                    "craftable zero-stock birch preferred over stocked oak, count remains 2");
            require(data.sparseOutputs().get(0).equals(new GenericStack(AEItemKey.of(Items.AMETHYST_SHARD), 7)),
                    "exact recipe output preserved");
            require(UselessModCompat.preview(new com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft(prepared),
                            mc.level).molds().get(0).is(Items.STICK), "native mold preserved");
            var category = runtime.getRecipeManager().getRecipeCategory(AdvancedAlloyFurnaceRecipeCategory.TYPE);
            var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, choice,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
            var previousMode = menu.tianshuMode;
            var error = handler.transferRecipe(menu, choice, layout.getRecipeSlotsView(), mc.player, false, false);
            System.out.println("OMNI_QA handler=" + handler.getClass() + " error=" + error + " type=" + (error == null ? null : error.getType()));
            require(error != null && error.getType() == IRecipeTransferError.Type.COSMETIC,
                    "registered JEI transfer returns native cosmetic feedback");
            boolean highlighted = false;
            for (var field : error.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.get(error) instanceof Collection<?> slots && !slots.isEmpty()) highlighted = true;
            }
            require(highlighted, "real JEI layout contains blue craftable ingredient slots");
            require(menu.tianshuMode == previousMode,
                    "JEI simulation does not switch mode or overwrite ordinary draft");
            var water = AEFluidKey.of(Fluids.WATER);
            var merged = ProcessingPatternTransferStacks.select(List.of(
                    List.of(new GenericStack(birch, 64)), List.of(new GenericStack(birch, 65)),
                    List.of(new GenericStack(water, 3_000_000_000L)), List.of(new GenericStack(water, 1_000L))), Map.of());
            require(merged.equals(List.of(new GenericStack(birch, 129), new GenericStack(water, 3_000_001_000L))),
                    "native merging preserves overstacked items and fluid amounts beyond int");
            var overflow = ProcessingPatternTransferStacks.select(List.of(
                    List.of(new GenericStack(water, Long.MAX_VALUE)), List.of(new GenericStack(water, 1))), Map.of());
            require(overflow.size() == 2 && overflow.get(0).amount() == Long.MAX_VALUE && overflow.get(1).amount() == 1,
                    "native long-overflow split remains lossless");
            require(handler.transferRecipe(menu, choice, layout.getRecipeSlotsView(), mc.player, false, true) == null,
                    "actual registered JEI handler accepts fill");
            transferred = prepared;
            checks.add(menu.getClass().getSimpleName() + ": native JEI feedback, selection, merge and bound transfer passed");
        });
    }

    public static String verifyFillAndEdit(boolean output) {
        return enqueue(() -> {
            var mc = Minecraft.getInstance();
            var screen = (TianshuPatternEncodingTermScreen<?>) mc.screen;
            var menu = screen.getMenu();
            require(menu.tianshuMode == TianshuEncodingMode.OMNIVERSAL, "server synchronized Omniversal mode");
            require(ItemStack.matches(menu.omniversalDraft.pattern(), transferred), "server retained exact native pattern binding");
            require(menu.getProcessingInputSlots()[0].getItem().is(Items.SAND)
                    && menu.getProcessingInputSlots()[0].getItem().getCount() == 3, "ordinary draft remains sand x3");
            var marker = TianshuPatternEncodingTermScreen.class.getDeclaredMethod("shouldShowCraftableIndicatorForSlot", Slot.class);
            marker.setAccessible(true);
            require((boolean) marker.invoke(screen, menu.getOmniversalInputSlots()[0]), "filled craftable input has plus marker");
            require((boolean) marker.invoke(screen, menu.getOmniversalMoldSlots()[0]), "craftable reusable mold has plus marker");
            require(!(boolean) marker.invoke(screen, menu.getOmniversalOutputSlots()[0]), "output has no ingredient plus marker");
            var slot = output ? menu.getOmniversalOutputSlots()[0] : menu.getOmniversalInputSlots()[0];
            editedSlot = slot.index;
            var hovered = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            hovered.setAccessible(true);
            hovered.set(screen, slot);
            screen.mouseClicked(0, 0, 2);
            require(mc.screen instanceof TianshuSetProcessingPatternAmountScreen<?>, "middle click opens shared amount editor");
            var amountField = TianshuSetProcessingPatternAmountScreen.class.getDeclaredField("amount");
            amountField.setAccessible(true);
            ((NumberEntryWidget) amountField.get(mc.screen)).setLongValue(4096);
            var confirm = TianshuSetProcessingPatternAmountScreen.class.getDeclaredMethod("confirm");
            confirm.setAccessible(true);
            confirm.invoke(mc.screen);
            require(mc.screen == screen, "saving amount returns to same terminal screen");
            checks.add(menu.getClass().getSimpleName() + ": plus markers and middle-click " + (output ? "output" : "input") + " editor passed");
        });
    }

    public static String verifySavedAmount() {
        return enqueue(() -> {
            var menu = (TianshuPatternEncodingTermMenu) Minecraft.getInstance().player.containerMenu;
            var stack = GenericStack.fromItemStack(menu.getSlot(editedSlot).getItem());
            require(stack != null && stack.amount() == 4096, "native SET_FILTER packet saves 4096 and synchronizes back");
            checks.add(menu.getClass().getSimpleName() + ": amount edit server round trip passed");
        });
    }

    public static String screenshot() {
        return enqueue(() -> {
            var mc = Minecraft.getInstance();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, wireless ? "omniversal-wireless.png" : "omniversal-wired.png", mc.getMainRenderTarget(), ignored -> {});
        });
    }

    private interface Action { void run() throws Exception; }
    private static String enqueue(Action action) {
        Minecraft.getInstance().tell(() -> {
            try {
                action.run();
                report = String.join("\n", checks);
                Files.writeString(Path.of("omniversal-processing-probe.txt"), report);
            } catch (Throwable e) {
                report = "FAILED: " + e;
                e.printStackTrace();
            }
        });
        return "queued";
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    public static String status() { return report; }
}
