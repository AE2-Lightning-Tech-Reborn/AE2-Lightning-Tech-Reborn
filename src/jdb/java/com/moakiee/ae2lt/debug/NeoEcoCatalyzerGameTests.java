package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_machine_recharge")
@PrefixGameTestTemplate(false)
public final class NeoEcoCatalyzerGameTests {
    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 200)
    public static void optionalRecipeProducesSixteenAndRetainsCatalyst(GameTestHelper h) {
        boolean expected = Boolean.getBoolean("ae2lt.neoecoTestPresent");
        h.assertTrue(ModList.get().isLoaded("neoecoae") == expected, "unexpected Neo ECO test environment");
        var candidate = CrystalCatalyzerRecipeService.findRecipeById(h.getLevel(),
                new ResourceLocation("ae2lt:crystal_catalyzer/flawless_budding_energized_crystal"));
        h.assertTrue(candidate.isPresent() == expected, "optional recipe loading differs from mod presence");
        if (!expected) {
            h.succeed();
            return;
        }
        var recipe = candidate.orElseThrow().recipe();
        var catalyst = BuiltInRegistries.ITEM.get(new ResourceLocation("neoecoae:flawless_budding_energized_crystal"));
        var output = BuiltInRegistries.ITEM.get(new ResourceLocation("neoecoae:energized_crystal"));
        h.assertTrue(recipe.getOutputTemplate().is(output) && recipe.getOutputTemplate().getCount() == 16,
                "incorrect Neo ECO output");
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, ModBlocks.CRYSTAL_CATALYZER.get());
        h.setBlock(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(pos.east(), AEBlocks.DRIVE.block());
        var host = (CrystalCatalyzerBlockEntity) h.getBlockEntity(pos);
        var cell = new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null, "missing lightning cell storage");
        storage.insert(LightningKey.HIGH_VOLTAGE, 10, Actionable.MODULATE, IActionSource.ofMachine(host));
        storage.persist();
        ((DriveBlockEntity) h.getBlockEntity(pos.east())).getInternalInventory().setItemDirect(0, cell);
        h.runAfterDelay(20, () -> {
            host.getInventory().setItemDirect(CrystalCatalyzerInventory.SLOT_CATALYST, new ItemStack(catalyst));
            host.getTank().setFluid(recipe.fluidInput());
            host.getEnergyStorage().receiveEnergy(100_000, false);
        });
        h.succeedWhen(() -> {
            var result = host.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_OUTPUT);
            h.assertTrue(result.is(output) && result.getCount() == 16, "waiting for sixteen energized crystals");
            h.assertTrue(host.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST).getCount() == 1,
                    "catalyst was consumed");
            h.assertTrue(host.getFluid().isEmpty() && host.getMachineStoredEnergy() == 0, "incorrect cycle cost");
            long remaining = host.getMainNode().getGrid().getStorageService().getInventory().extract(
                    LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofMachine(host));
            h.assertTrue(remaining == 9, "incorrect lightning cost");
        });
    }
}
