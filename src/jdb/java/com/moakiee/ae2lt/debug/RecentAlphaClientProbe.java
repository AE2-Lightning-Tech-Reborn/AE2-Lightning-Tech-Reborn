package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.registry.ModItems;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.common.Internal;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.util.ImmutableSize2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Runs the real 26.1 JEI service and layout mixins in a disposable integrated world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class RecentAlphaClientProbe {
    private static final BlockPos NETWORK = new BlockPos(0, 100, 0);
    private static int ticks, phase, since;
    private static boolean done;
    private static volatile boolean ready;
    private static volatile Throwable failure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.recentAlphaProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 2400) throw new AssertionError("client probe timeout in phase " + phase);
            if (failure != null) throw new AssertionError("server fixture failed", failure);
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (phase == 0) {
                if (mc.getOverlay() != null) return;
                AE2LTClientConfig.setJeiWirelessSupply(true);
                mc.setScreen(null);
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level = player.level();
                    player.getInventory().clearContent();
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, .5, 101, 2.5, java.util.Set.of(), 180, 30, false);
                    level.setBlockAndUpdate(NETWORK, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    level.setBlockAndUpdate(NETWORK.east(), AEBlocks.DRIVE.block().defaultBlockState());
                    level.setBlockAndUpdate(NETWORK.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                    level.setBlockAndUpdate(NETWORK.south(), Blocks.CRAFTING_TABLE.defaultBlockState());
                    var cell = AEItems.ITEM_CELL_1K.stack();
                    var storage = StorageCells.getCellInventory(cell, null);
                    storage.insert(AEItemKey.of(Items.OAK_PLANKS), 64, Actionable.MODULATE, IActionSource.ofPlayer(player));
                    storage.persist();
                    ((DriveBlockEntity) level.getBlockEntity(NETWORK.east())).getInternalInventory().setItemDirect(0, cell);
                    var terminal = new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
                    terminal.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), NETWORK.north()));
                    terminal.set(AEComponents.STORED_ENERGY, 1000000.0);
                    player.getInventory().setItem(0, terminal);
                    ready = true;
                });
                phase = 1; since = ticks;
            } else if (phase == 1 && ready && ticks - since > 100) {
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.openMenu(Blocks.CRAFTING_TABLE.defaultBlockState().getMenuProvider(player.level(), NETWORK.south()));
                });
                phase = 2;
            } else if (phase == 2 && mc.player.containerMenu instanceof CraftingMenu
                    && mc.screen instanceof AbstractContainerScreen<?> screen) {
                var runtime = Internal.getJeiRuntime();
                var manager = runtime.getRecipeManager();
                var recipe = manager.createRecipeLookup(RecipeTypes.CRAFTING).get()
                        .filter(r -> r.id().identifier().toString().equals("minecraft:crafting_table")).findFirst().orElseThrow();
                var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(RecipeTypes.CRAFTING),
                        recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                var service = new RecipeTransferService(runtime.getRecipeTransferManager());
                var error = service.getTransferRecipeError(screen, layout, mc.player);
                if (error.isPresent()) return;
                require(mc.player.getInventory().countItem(Items.OAK_PLANKS) == 0, "preview mutated real inventory");
                service.transferRecipe(screen, layout, mc.player, false);
                phase = 3; since = ticks;
            } else if (phase == 3 && ticks - since > 40) {
                if (!(mc.player.containerMenu instanceof CraftingMenu menu)
                        || !menu.getSlot(0).getItem().is(Items.CRAFTING_TABLE)) return;
                require(menu.slots.stream().filter(s -> s.getItem().is(Items.OAK_PLANKS))
                        .mapToInt(s -> s.getItem().getCount()).sum() == 4, "refill was not the exact recipe deficit");
                verifyLayout();
                finish("PASS: real JEI context/service wireless refill, exact deficit, preview isolation; multiblock resize and cache rebuild");
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finish("FAIL: " + error);
        }
    }

    private static void verifyLayout() throws Exception {
        var gui = (RecipesGui) Internal.getJeiRuntime().getRecipesGui();
        gui.showTypes(List.of(MultiblockStructureCategory.TYPE));
        var logicField = RecipesGui.class.getDeclaredField("logic"); logicField.setAccessible(true);
        var bookmarksField = RecipesGui.class.getDeclaredField("bookmarks"); bookmarksField.setAccessible(true);
        var logic = (RecipeGuiLogic) logicField.get(gui);
        var bookmarks = (BookmarkList) bookmarksField.get(gui);
        var category = (MultiblockStructureCategory) logic.getSelectedRecipeCategory();
        var tall = logic.getVisibleRecipeLayoutsWithButtons(new ImmutableSize2i(600, 500), null, bookmarks, gui);
        require(category.getHeight() == 484 && !tall.isEmpty(), "26.1 layout hook did not expand preview");
        var first = tall.getFirst().getRecipeLayout();
        require(first.getRect().getHeight() == 484, "expanded layout has stale bounds");
        var small = logic.getVisibleRecipeLayoutsWithButtons(new ImmutableSize2i(600, 300), null, bookmarks, gui);
        require(category.getHeight() == Math.max(MultiblockStructureCategory.HEIGHT, 284), "resize did not shrink category");
        require(small.getFirst().getRecipeLayout() != first, "resize reused cached layout");
        require(first.getRect().getHeight() == 484, "existing layout changed with category");
    }

    private static void server(Runnable work) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { work.run(); } catch (Throwable error) { failure = error; }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(String result) {
        done = true;
        var mc = Minecraft.getInstance();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("recent-alpha-result.txt"), result); }
        catch (Exception error) { error.printStackTrace(); }
        System.out.println("RECENT_ALPHA_CLIENT " + result);
        mc.stop();
    }
}
