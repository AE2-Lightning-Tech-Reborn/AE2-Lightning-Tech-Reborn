package com.moakiee.ae2lt.debug;

import appeng.api.config.*;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.*;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.ISubMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.locator.MenuLocators;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.integration.ae2wtlib.*;
import com.moakiee.ae2lt.logic.tianshu.terminal.*;
import com.moakiee.ae2lt.menu.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Unit;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Runs native menus and optional mixins in a real server; excluded from the published jar. */
@GameTestHolder("ae2lt_workstation")
@PrefixGameTestTemplate(false)
public final class TianshuCraftingGameTests {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void passed(String message) { System.out.println("TIANSHU_WORKSTATION_PASS " + message); }
    private static ServerPlayer player(ServerLevel level, String name) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.experienceLevel = 100;
        return player;
    }
    private static Slot slot(TianshuCraftingTermMenu menu, SlotSemantic semantic, int index) {
        return menu.getSlots(semantic).get(index);
    }
    private static long count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    private static TianshuCraftingTermMenu menu(ServerPlayer player, TestHost host) {
        var menu = new TianshuCraftingTermMenu(7, player.getInventory(), host);
        player.containerMenu = menu;
        menu.broadcastChanges();
        return menu;
    }

    @GameTest(templateNamespace = "ae2lt_workstation", template = "workstation_test", timeoutTicks = 100)
    public static void workstations(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(level, "TianshuWork");
        var host = new TestHost(level, helper.absolutePos(BlockPos.ZERO));
        var menu = menu(player, host);
        try {
            menu.setWorkPage(TianshuWorkPage.STONECUTTING);
            var stoneInput = slot(menu, Ae2ltSlotSemantics.TIANSHU_STONECUTTING, 0);
            var stoneResult = slot(menu, Ae2ltSlotSemantics.TIANSHU_STONECUTTING, 1);
            stoneInput.set(new ItemStack(Items.STONE, 3));
            var recipes = menu.getStonecutter().getRecipes();
            int slab = -1;
            for (int i = 0; i < recipes.size(); i++) if (recipes.get(i).getResultItem(level.registryAccess()).is(Items.STONE_SLAB)) slab = i;
            require(slab >= 0, "stone slab recipe unavailable");
            menu.selectStoneRecipe(slab);
            require(stoneResult.getItem().getCount() == 2, "stone preview count");
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            require(menu.quickMoveStack(player, stoneResult.index).isEmpty() && stoneInput.getItem().getCount() == 3,
                    "full inventory must not consume stone input");
            player.getInventory().setItem(9, ItemStack.EMPTY);
            menu.quickMoveStack(player, stoneResult.index);
            require(!stoneInput.hasItem() && count(player, Items.STONE_SLAB) == 6,
                    "Shift crafts available complete batches, consuming exactly one stone per two slabs");
            stoneInput.set(new ItemStack(Items.STONE, 2));
            var changed = stoneInput.getItem().copy();
            changed.setHoverName(Component.literal("component changed"));
            stoneInput.set(changed);
            require(menu.getStonecutter().getSelectedRecipeIndex() == -1 && stoneResult.getItem().isEmpty(),
                    "same-item component replacement must invalidate selection and preview");
            player.getInventory().clearContent();
            passed("stone preview, full inventory, exact consumption, component invalidation");

            menu.setWorkPage(TianshuWorkPage.ANVIL);
            var anvilInput = slot(menu, Ae2ltSlotSemantics.TIANSHU_ANVIL, 0);
            var anvilResult = slot(menu, Ae2ltSlotSemantics.TIANSHU_ANVIL, 2);
            anvilInput.set(new ItemStack(Items.DIAMOND_SWORD));
            menu.setAnvilName("Tianshu native rename");
            menu.broadcastChanges();
            int cost = menu.anvilCost;
            require(cost > 0 && !anvilResult.getItem().isEmpty(), "native rename preview");
            player.experienceLevel = 0;
            require(menu.quickMoveStack(player, anvilResult.index).isEmpty() && anvilInput.hasItem(), "insufficient levels");
            player.experienceLevel = 30;
            menu.quickMoveStack(player, anvilResult.index);
            require(player.experienceLevel == 30 - cost
                    && !anvilInput.hasItem(), "native rename charges levels once");
            passed("anvil preview, insufficient levels, exact XP charge");

            menu.setWorkPage(TianshuWorkPage.SMITHING);
            var smith = Ae2ltSlotSemantics.TIANSHU_SMITHING;
            slot(menu, smith, 0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            slot(menu, smith, 1).set(new ItemStack(Items.DIAMOND_SWORD));
            slot(menu, smith, 2).set(new ItemStack(Items.NETHERITE_INGOT));
            var smithResult = slot(menu, smith, 3);
            require(smithResult.getItem().is(Items.NETHERITE_SWORD), "smithing native recipe preview");
            menu.quickMoveStack(player, smithResult.index);
            require(count(player, Items.NETHERITE_SWORD) == 1, "smithing take");
            require(!slot(menu, smith, 0).hasItem() && !slot(menu, smith, 1).hasItem() && !slot(menu, smith, 2).hasItem(),
                    "smithing consumes all three inputs exactly once");
            passed("native smithing preview and three-input consumption");

            player.getInventory().clearContent();
            player.getInventory().setItem(10, new ItemStack(Items.STONE, 5));
            stoneInput.set(ItemStack.EMPTY);
            menu.fillWorkRecipe("minecraft:stone_slab_from_stone_stonecutting");
            require(stoneInput.getItem().is(Items.STONE) && stoneInput.getItem().getCount() == 1
                    && count(player, Items.STONE) == 4 && menu.workPage == TianshuWorkPage.STONECUTTING,
                    "recipe transfer takes one real player item and selects target page");
            require(!stoneResult.getItem().isEmpty(), "recipe transfer selects requested recipe");
            menu.setWorkPage(TianshuWorkPage.ANVIL);
            menu.clicked(stoneResult.index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty() && stoneInput.getItem().getCount() == 1, "inactive result click rejected");
            passed("real ingredient transfer and stale-page click rejection");

            var other = player(level, "TianshuOther");
            var otherMenu = menu(other, host);
            require(slot(otherMenu, Ae2ltSlotSemantics.TIANSHU_STONECUTTING, 0).getItem().getCount() == 1,
                    "viewers share the same terminal's extra inputs");
            otherMenu.removed(other);
            other.containerMenu = other.inventoryMenu;
            long before = count(player, Items.STONE);
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, player.getServer()));
            require(count(player, Items.STONE) == before, "close retains hidden page input in the terminal");
            menu.removed(player);
            require(count(player, Items.STONE) == before, "repeated close must not duplicate input");
            var reopened = menu(player, host);
            require(slot(reopened, Ae2ltSlotSemantics.TIANSHU_STONECUTTING, 0).getItem().getCount() == 1,
                    "hidden input survives closing and reopening");
            reopened.removed(player); player.containerMenu = player.inventoryMenu;
            passed("terminal-owned shared inputs and close/reopen retention without duplicates");
        } finally { menu.removed(player); player.containerMenu = player.inventoryMenu; helper.getLevel().removeBlockEntity(helper.absolutePos(BlockPos.ZERO)); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_workstation", template = "workstation_test", timeoutTicks = 100)
    public static void cellWorkbench(GameTestHelper helper) {
        var player = player(helper.getLevel(), "TianshuCell");
        var cellHost = new TestHost(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        var menu = menu(player, cellHost);
        try {
            menu.setWorkPage(TianshuWorkPage.CELL);
            var cellStack = AEItems.ITEM_CELL_1K.stack();
            var cell = (ICellWorkbenchItem) cellStack.getItem();
            slot(menu, Ae2ltSlotSemantics.TIANSHU_CELL, 0).set(cellStack);
            menu.broadcastChanges();
            require(menu.cellConfigSize == 63 && menu.getCellConfigRows() == 21 && menu.getMaxCellConfigRow() == 18, "full 63-slot cell config");
            var marks = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
            menu.setFilter(marks.get(0).index, new ItemStack(Items.DIAMOND));
            menu.setCellConfigRow(18);
            menu.setFilter(marks.get(54).index, new ItemStack(Items.GOLD_INGOT));
            menu.setFilter(marks.get(0).index, new ItemStack(Items.DIRT));
            require(AEItemKey.of(Items.DIAMOND).equals(cell.getConfigInventory(cellStack).getKey(0))
                    && AEItemKey.of(Items.GOLD_INGOT).equals(cell.getConfigInventory(cellStack).getKey(54)),
                    "last page must preserve other marks and reject stale-page writes");
            require(count(player, Items.DIAMOND) == 0, "marks do not create real materials");
            var upgrade = slot(menu, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE, 0);
            require(!upgrade.mayPlace(new ItemStack(Items.DIRT)), "upgrade rejects ordinary items");
            require(upgrade.mayPlace(AEItems.FUZZY_CARD.stack()), "native supported card accepted");
            upgrade.set(AEItems.FUZZY_CARD.stack());
            menu.setCellFuzzyMode(FuzzyMode.PERCENT_50);
            require(cell.getFuzzyMode(cellStack) == FuzzyMode.PERCENT_50, "fuzzy config persists");
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, player.getServer()));
            var reopened = menu(player, cellHost);
            var retainedCell = reopened.getCell();
            require(count(player, AEItems.ITEM_CELL_1K.asItem()) == 0 && cell.getUpgrades(retainedCell).isInstalled(AEItems.FUZZY_CARD)
                    && cell.getConfigInventory(retainedCell).getKey(54) != null
                    && cell.getFuzzyMode(retainedCell) == FuzzyMode.PERCENT_50, "close retains the whole cell with cards and marks");
            reopened.removed(player); player.containerMenu = player.inventoryMenu;
            passed("cell 21 rows, stale clicks, card validation, fuzzy persistence and whole-cell retention");
        } finally { menu.removed(player); player.containerMenu = player.inventoryMenu; helper.getLevel().removeBlockEntity(helper.absolutePos(BlockPos.ZERO)); }

        var copyPlayer = player(helper.getLevel(), "TianshuCellCopy");
        var copyMenu = menu(copyPlayer, new TestHost(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
        try {
            copyMenu.setWorkPage(TianshuWorkPage.CELL);
            var real = slot(copyMenu, Ae2ltSlotSemantics.TIANSHU_CELL, 0);
            var marks = copyMenu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
            var source = AEItems.ITEM_CELL_1K.stack();
            var cell = (ICellWorkbenchItem) source.getItem();
            real.set(source);
            copyMenu.setFilter(marks.get(0).index, new ItemStack(Items.DIAMOND));
            copyMenu.setCellConfigRow(18);
            copyMenu.setFilter(marks.get(54).index, new ItemStack(Items.GOLD_INGOT));
            slot(copyMenu, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE, 0).set(AEItems.FUZZY_CARD.stack());
            copyMenu.setCellCopyMode(CopyMode.KEEP_ON_REMOVE);
            copyMenu.clicked(real.index, 0, ClickType.PICKUP, copyPlayer);
            require(copyMenu.getCell().isEmpty() && copyMenu.getCarried().is(AEItems.ITEM_CELL_1K.asItem())
                    && marks.get(0).getItem().is(Items.DIAMOND) && marks.get(54).getItem().is(Items.GOLD_INGOT),
                    "native KEEP_ON_REMOVE preserves all marks after an actual cell pickup");
            require(cell.getUpgrades(copyMenu.getCarried()).isInstalled(AEItems.FUZZY_CARD)
                    && !slot(copyMenu, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE, 0).hasItem(),
                    "real upgrade card travels only with the removed cell");
            var blank = AEItems.ITEM_CELL_1K.stack();
            copyMenu.setCarried(blank);
            copyMenu.clicked(real.index, 0, ClickType.PICKUP, copyPlayer);
            require(cell.getConfigInventory(copyMenu.getCell()).getKey(54).equals(AEItemKey.of(Items.GOLD_INGOT))
                    && cell.getUpgrades(copyMenu.getCell()).isEmpty(), "empty cell receives retained marks without copied cards");
            var configured = AEItems.ITEM_CELL_1K.stack();
            cell.getConfigInventory(configured).createMenuWrapper().setItemDirect(6, new ItemStack(Items.IRON_INGOT));
            real.set(configured);
            require(cell.getConfigInventory(configured).getKey(0) == null
                    && marks.get(6).getItem().is(Items.IRON_INGOT), "configured cell replaces the template rather than being overwritten");
            copyMenu.setCellCopyMode(CopyMode.CLEAR_ON_REMOVE);
            real.set(ItemStack.EMPTY);
            require(copyMenu.cellConfigSize == 0 && !marks.get(6).hasItem(), "native CLEAR_ON_REMOVE clears only the empty workbench template");
            real.set(AEItems.ITEM_CELL_1K.stack());
            require(cell.getConfigInventory(copyMenu.getCell()).isEmpty(), "clear mode leaves the next blank cell unconfigured");
            copyMenu.setCellCopyMode(CopyMode.KEEP_ON_REMOVE);
            copyMenu.setFilter(marks.get(0).index, new ItemStack(Items.DIAMOND));
            real.set(ItemStack.EMPTY);
            var fluid = AEItems.FLUID_CELL_1K.stack();
            real.set(fluid);
            require(((ICellWorkbenchItem)fluid.getItem()).getConfigInventory(fluid).isEmpty(),
                    "native copy filters incompatible item marks when inserting a fluid cell");
            passed("native cell keep/clear mode, actual removal/insertion, existing-config priority, card isolation and key-type filtering");
        } finally { copyMenu.removed(copyPlayer); copyPlayer.containerMenu = copyPlayer.inventoryMenu; helper.getLevel().removeBlockEntity(helper.absolutePos(BlockPos.ZERO)); }

        var fluidMenu = menu(player, new TestHost(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
        try {
            fluidMenu.setWorkPage(TianshuWorkPage.CELL);
            var fluidCell = AEItems.FLUID_CELL_1K.stack();
            slot(fluidMenu, Ae2ltSlotSemantics.TIANSHU_CELL, 0).set(fluidCell);
            fluidMenu.broadcastChanges();
            fluidMenu.setFilter(slot(fluidMenu, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG, 0).index,
                    GenericStack.wrapInItemStack(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)));
            require(AEFluidKey.of(Fluids.WATER).equals(((ICellWorkbenchItem) fluidCell.getItem()).getConfigInventory(fluidCell).getKey(0)),
                    "fluid ghost mark uses AE fluid key");
            passed("fluid cell ghost configuration");
        } finally { fluidMenu.removed(player); player.containerMenu = player.inventoryMenu; helper.getLevel().removeBlockEntity(helper.absolutePos(BlockPos.ZERO)); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_workstation", template = "workstation_test", timeoutTicks = 100)
    public static void nativeAnvilCallbacks(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(level, "TianshuAnvil");
        var pos = helper.absolutePos(BlockPos.ZERO);
        int[] repairs = {0};
        Consumer<AnvilRepairEvent> repair = event -> { if (event.getEntity() == player) { repairs[0]++; event.setBreakChance(1); } };
        Consumer<AnvilUpdateEvent> update = event -> {
            if (event.getPlayer() == player && event.getLeft().is(Items.IRON_INGOT) && event.getRight().is(Items.GOLD_INGOT)) {
                event.setOutput(new ItemStack(Items.DIAMOND)); event.setCost(7); event.setMaterialCost(2);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(repair);
        MinecraftForge.EVENT_BUS.addListener(update);
        try {
            level.setBlockAndUpdate(pos, ModBlocks.OVERLOAD_ALLOY_ANVIL.get().defaultBlockState());
            var alloy = new OverloadAlloyAnvilMenu(4, player.getInventory(), ContainerLevelAccess.create(level, pos));
            alloy.getSlot(0).set(new ItemStack(Items.IRON_INGOT));
            alloy.getSlot(1).set(new ItemStack(Items.GOLD_INGOT, 3));
            require(alloy.getCost() == 7 && alloy.getSlot(2).getItem().is(Items.DIAMOND), "mod AnvilUpdateEvent result and cost honored");
            var result = alloy.getSlot(2).remove(1);
            alloy.getSlot(2).onTake(player, result);
            require(player.experienceLevel == 93 && alloy.getSlot(1).getItem().getCount() == 1 && repairs[0] == 1,
                    "mod callback, material and XP honored once");
            require(level.getBlockState(pos).is(ModBlocks.OVERLOAD_ALLOY_ANVIL.get()), "alloy anvil survives forced wear");
            level.setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
            var normal = new AnvilMenu(4, player.getInventory(), ContainerLevelAccess.create(level, pos));
            normal.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD)); normal.setItemName("control");
            normal.getSlot(2).onTake(player, normal.getSlot(2).remove(1));
            require(level.getBlockState(pos).is(Blocks.CHIPPED_ANVIL) && repairs[0] == 2, "ordinary anvil retains native wear");
            passed("Forge anvil callbacks and alloy-only wear suppression");
        } finally { MinecraftForge.EVENT_BUS.unregister(repair); MinecraftForge.EVENT_BUS.unregister(update); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_workstation", template = "workstation_test", timeoutTicks = 100)
    public static void wireless(GameTestHelper helper) {
        if (ModList.get().isLoaded("ae2wtlib")) { TianshuWirelessGameTestChecks.run(helper); }
        helper.succeed();
    }

    private static final class TestHost extends BlockEntity implements TianshuCraftingTerminalHost, appeng.api.implementations.menuobjects.IPortableTerminal {
        private final com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage workstations =
                new com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage(data -> {});
        @Override public com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage getWorkstationStorage() { return workstations; }
        private final InternalInventory grid = new AppEngInternalInventory(null, 9);
        private final IConfigManager config = createConfig();
        private static IConfigManager createConfig() {
            var config = new appeng.util.ConfigManager(() -> {});
            config.registerSetting(Settings.SORT_BY, SortOrder.NAME);
            config.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
            config.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
            return config;
        }
        TestHost(ServerLevel level, BlockPos pos) {
            super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState()); setLevel(level); level.setBlockEntity(this);
        }
        @Override public InternalInventory getSubInventory(ResourceLocation id) { return grid; }
        @Override public IGridNode getActionableNode() { return null; }
        @Override public MEStorage getInventory() { return () -> Component.literal("empty test storage"); }
        @Override public IConfigManager getConfigManager() { return config; }
        @Override public void returnToMainMenu(Player player, ISubMenu menu) {}
        @Override public ItemStack getMainMenuIcon() { return new ItemStack(Items.CRAFTING_TABLE); }
        @Override public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) { return amount; }
    }
}
