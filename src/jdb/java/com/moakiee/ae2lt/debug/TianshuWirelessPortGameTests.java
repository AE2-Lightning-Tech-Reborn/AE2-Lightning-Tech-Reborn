package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.wut.recipe.Common;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_workstations")
@PrefixGameTestTemplate(false)
public final class TianshuWirelessPortGameTests {
    private static ItemStack source() {
        return new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
    }

    private static ItemStack target() {
        var stack = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
        stack.getOrCreateTag().putBoolean("crafting", true);
        return stack;
    }

    private static CompoundTag inputs() {
        var data = new CompoundTag();
        var list = new ListTag();
        list.add(new ItemStack(Items.NETHERITE_INGOT, 2).save(new CompoundTag()));
        data.put("inputs", list);
        return data;
    }

    @GameTest(template = "workstation_test")
    public static void nativeMergePreservesInputsAndEnergy(GameTestHelper helper) {
        var source = source();
        var target = target();
        var data = inputs();
        ModDataComponents.TIANSHU_WORKSTATIONS.set(source, data);
        ((ItemWT) source.getItem()).injectAEPower(source, 20, Actionable.MODULATE);
        ((ItemWT) target.getItem()).injectAEPower(target, 10, Actionable.MODULATE);
        var beforeSource = source.copy();
        var beforeTarget = target.copy();
        var result = Common.mergeTerminal(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME);
        helper.assertTrue(!result.isEmpty(), "Native recipe must accept a single occupied terminal");
        helper.assertTrue(ModDataComponents.TIANSHU_WORKSTATIONS.get(result).equals(data), "All real inputs must survive");
        helper.assertTrue(((ItemWT) result.getItem()).getAECurrentPower(result) == 30, "Merged energy must be conserved");
        helper.assertTrue(ItemStack.matches(source, beforeSource) && ItemStack.matches(target, beforeTarget), "Recipe preview must leave source items unchanged");
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void equalOccupiedInventoriesCannotDiscardItems(GameTestHelper helper) {
        var source = source();
        var target = target();
        ModDataComponents.TIANSHU_WORKSTATIONS.set(source, inputs());
        ModDataComponents.TIANSHU_WORKSTATIONS.set(target, inputs());
        var beforeSource = source.copy();
        var beforeTarget = target.copy();
        helper.assertTrue(Common.mergeTerminal(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME).isEmpty(), "Two equal occupied workstations own four ingots and cannot be deduplicated");
        helper.assertTrue(ItemStack.matches(source, beforeSource) && ItemStack.matches(target, beforeTarget), "Rejected merge must preserve both inputs");
        ModDataComponents.TIANSHU_WORKSTATIONS.remove(source);
        ModDataComponents.TIANSHU_WORKSTATIONS.remove(target);
        var grid = new AppEngInternalInventory(9);
        grid.setItemDirect(0, new ItemStack(Items.DIAMOND, 3));
        grid.writeToNBT(source.getOrCreateTag(), "craftingGrid");
        grid.writeToNBT(target.getOrCreateTag(), "craftingGrid");
        helper.assertTrue(Common.mergeTerminal(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME).isEmpty(), "Two equal occupied native crafting grids must also be rejected");
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void equalOccupiedCellWorkbenchesCannotDiscardCells(GameTestHelper helper) {
        var source = source();
        var target = target();
        var cell = new AppEngInternalInventory(1);
        cell.setItemDirect(0, new ItemStack(appeng.core.definitions.AEItems.ITEM_CELL_1K.asItem()));
        var workbench = new CompoundTag();
        cell.writeToNBT(workbench, "cell");
        var data = new CompoundTag();
        data.put("cellWorkbench", workbench);
        helper.assertTrue(TianshuWorkstationStorage.containsItems(data), "Native AE2 cell NBT must be detected");
        ModDataComponents.TIANSHU_WORKSTATIONS.set(source, data);
        ModDataComponents.TIANSHU_WORKSTATIONS.set(target, data);
        helper.assertTrue(Common.mergeTerminal(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME).isEmpty(), "Two configured cell workbenches cannot discard a real cell");
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void storageSnapshotsAndViewerOwnershipAreIsolated(GameTestHelper helper) {
        var saved = new CompoundTag();
        var storage = new TianshuWorkstationStorage(data -> saved.put("data", data));
        var owner = new Object();
        var other = new Object();
        int[] notifications = {0, 0};
        storage.subscribe(owner, () -> notifications[0]++);
        storage.subscribe(other, () -> notifications[1]++);
        var original = inputs();
        storage.update(original, owner);
        original.getList("inputs", 10).clear();
        helper.assertTrue(notifications[0] == 0 && notifications[1] == 1, "Consumption must invalidate other viewers synchronously");
        helper.assertTrue(TianshuWorkstationStorage.containsItems(storage.read()) && TianshuWorkstationStorage.containsItems(saved.getCompound("data")), "Input and saved NBT must be independent snapshots");
        var copied = storage.read();
        copied.getList("inputs", 10).clear();
        helper.assertTrue(TianshuWorkstationStorage.containsItems(storage.read()), "Readers cannot mutate terminal ownership");
        storage.unsubscribe(other);
        storage.clear();
        helper.assertTrue(notifications[1] == 1 && !TianshuWorkstationStorage.containsItems(storage.read()), "Closed viewers must not be retained");
        helper.succeed();
    }
}
