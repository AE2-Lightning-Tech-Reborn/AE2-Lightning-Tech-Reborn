package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.machine.miningfactory.MiningLoot;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import net.silentchaos512.gear.api.item.ICoreItem;
import net.silentchaos512.gear.api.part.IPartData;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.material.MaterialManager;
import net.silentchaos512.gear.gear.part.PartData;
import net.silentchaos512.gear.item.CompoundPartItem;
import net.silentchaos512.gear.setup.SgItems;
import net.silentchaos512.gear.util.GearHelper;

/**
 * Native Silent Gear coverage for the mining factory. This suite needs the real mod on the classpath:
 * run it through {@code scripts/mining-silentgear-test.init.gradle} with {@code -PminingSilentGearMods}.
 *
 * <p>Silent Gear keeps broken gear and marks it broken one durability point before vanilla would.
 * Its pickaxe, axe and digger types also stop reporting their Forge tool actions once broken, so the
 * shared tool check already rejects those. Its shovel, sword and shears keep reporting them, which is
 * the gap this suite locks down.
 */
@GameTestHolder("ae2lt_mining")
@PrefixGameTestTemplate(false)
public final class MiningFactorySilentGearGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    /** Types whose own tool actions survive breakage and therefore need the explicit broken check. */
    private static final List<Item> PERMISSIVE_WHEN_BROKEN = List.of(
            SgItems.SHOVEL.get(), SgItems.SWORD.get(), SgItems.SHEARS.get());

    /** Iron parts keep the fixture deterministic: no random durability traits or indestructible gear. */
    private static ItemStack gear(GameTestHelper h, Item item) {
        var iron = MaterialManager.get(new ResourceLocation("silentgear", "iron"));
        h.assertTrue(iron != null, "Silent Gear iron material is missing");
        ICoreItem core = (ICoreItem) item;
        List<IPartData> parts = new ArrayList<>();
        for (PartType type : core.getRequiredParts()) {
            CompoundPartItem part = type.getCompoundPartItem(core.getGearType()).orElse(null);
            h.assertTrue(part != null, "Silent Gear has no " + type + " part for " + core.getGearType());
            PartData data = PartData.from(part.create(MaterialInstance.of(iron)));
            h.assertTrue(data != null, "Silent Gear could not read its own " + type + " part data");
            parts.add(data);
        }
        ItemStack stack = core.construct(parts);
        h.assertTrue(!stack.isEmpty() && stack.hasTag() && stack.getMaxDamage() > 8, "Invalid native gear fixture");
        h.assertTrue(!GearHelper.isBroken(stack) && !GearHelper.isUnbreakable(stack), "Fixture starts broken");
        h.assertTrue(MiningLoot.isTool(stack), "Intact Silent Gear gear was rejected as a tool");
        return stack;
    }

    /** Vanilla still accepts the last durability point, which is exactly where Silent Gear breaks. */
    private static ItemStack broken(ItemStack gear) {
        ItemStack copy = gear.copy();
        copy.setDamageValue(copy.getMaxDamage() - 1);
        return copy;
    }

    private static long output(MiningFactoryBlockEntity be, Item item) {
        long count = be.getPendingDrops().stream().filter(d -> d.stack().is(item))
                .mapToLong(MiningLoot.Drop::count).sum();
        for (int slot = MiningFactoryInventory.OUTPUT;
                slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
            var stack = be.getInventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    @GameTest(template = "empty")
    public static void brokenGearWithoutNativeToolActionGateCannotMine(GameTestHelper h) {
        for (Item item : PERMISSIVE_WHEN_BROKEN) {
            var tool = broken(gear(h, item));
            h.assertTrue(GearHelper.isBroken(tool), "Fixture must be broken for Silent Gear: " + item);
            h.assertTrue(tool.getDamageValue() < tool.getMaxDamage(),
                    "Fixture must still pass a vanilla damage check: " + item);
            h.assertTrue(tool.canPerformAction(net.minecraftforge.common.ToolActions.SHOVEL_DIG)
                            || tool.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_DIG)
                            || tool.canPerformAction(net.minecraftforge.common.ToolActions.SHEARS_DIG),
                    "Fixture must keep its own tool actions: " + item);
            h.assertTrue(!MiningLoot.isTool(tool), "Broken Silent Gear gear was accepted: " + item);
        }
        var shovel = broken(gear(h, SgItems.SHOVEL.get()));
        for (Block block : new Block[] {Blocks.DIRT, Blocks.GRAVEL, Blocks.IRON_ORE}) {
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), block.defaultBlockState(), shovel, 64, 8);
            h.assertTrue(result.processed() == 0 && result.samples() == 0 && result.drops().isEmpty()
                    && ItemStack.matches(shovel, result.tool()),
                    "Broken Silent Gear shovel generated loot for " + block);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void brokenGearCannotConsumeInputsThroughTheFactory(GameTestHelper h) {
        var shovel = broken(gear(h, SgItems.SHOVEL.get()));
        var be = MiningFactoryGameTests.fixture(h, new ItemStack(Items.DIAMOND_SHOVEL), 64, 1_000_000);
        be.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        h.assertTrue(!be.getInventory().isItemValid(MiningFactoryInventory.TOOL, shovel),
                "Tool slot accepts broken Silent Gear gear");
        // Simulate gear that broke after insertion or an older save that kept the pre-break stack.
        be.getInventory().setItemDirect(MiningFactoryInventory.TOOL, shovel);
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            h.assertTrue(be.getStatus() == MiningFactoryBlockEntity.Status.TOOL && output(be, Items.DIRT) == 0
                    && be.getInventory().getStackInSlot(0).getCount() == 64
                    && be.getAvailableLightning() == 1000 && be.getEnergyStorage().getEnergyStored() == 1_000_000,
                    "Broken Silent Gear gear spent input, energy or lightning");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void gearBreakingInsideBatchStopsAtLastUsableHit(GameTestHelper h) {
        var tool = gear(h, SgItems.SHOVEL.get());
        tool.setDamageValue(tool.getMaxDamage() - 4);
        var snapshot = tool.copy();
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.DIRT.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 3 && result.samples() == 1
                && result.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == 3,
                "Three usable hits: processed=" + result.processed() + ", samples=" + result.samples()
                        + ", drops=" + result.drops());
        h.assertTrue(!result.tool().isEmpty() && GearHelper.isBroken(result.tool()),
                "Broken Silent Gear must be retained");
        h.assertTrue(ItemStack.matches(tool, snapshot), "Prospective damage changed the installed gear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void intactGearStillMinesAFullBatch(GameTestHelper h) {
        var shovel = gear(h, SgItems.SHOVEL.get());
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.DIRT.defaultBlockState(),
                shovel, 64, 8);
        h.assertTrue(result.processed() == 64 && !GearHelper.isBroken(result.tool())
                && result.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == 64,
                "Intact Silent Gear failed to mine a full batch");

        // A shovel is this factory's native tool for dirt, sand and gravel, so drive a real batch with it.
        var be = MiningFactoryGameTests.fixture(h, shovel.copy(), 64, 1_000_000);
        be.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            var retained = be.getInventory().getStackInSlot(MiningFactoryInventory.TOOL);
            h.assertTrue(output(be, Items.DIRT) == 64 && be.getInventory().getStackInSlot(0).isEmpty()
                    && retained.getDamageValue() == 64 && !GearHelper.isBroken(retained)
                    && MiningLoot.isTool(retained) && be.getAvailableLightning() == 999,
                    "Intact Silent Gear shovel did not run a factory batch: output=" + output(be, Items.DIRT)
                            + ", input=" + be.getInventory().getStackInSlot(0).getCount()
                            + ", tool=" + retained + ", lightning=" + be.getAvailableLightning());
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void partialGearBatchCommitsCostsOnceAndStops(GameTestHelper h) {
        var tool = gear(h, SgItems.SHOVEL.get());
        tool.setDamageValue(tool.getMaxDamage() - 4);
        var be = MiningFactoryGameTests.fixture(h, tool, 64, 1_000_000);
        be.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            var retained = be.getInventory().getStackInSlot(MiningFactoryInventory.TOOL);
            h.assertTrue(output(be, Items.DIRT) == 3 && be.getInventory().getStackInSlot(0).getCount() == 61
                    && !retained.isEmpty() && GearHelper.isBroken(retained) && retained.getCount() == 1
                    && be.getAvailableLightning() == 999
                    && be.getEnergyStorage().getEnergyStored() == 1_000_000 - 3 * 256,
                    "Partial gear batch committed wrong blocks, costs or tool: output=" + output(be, Items.DIRT)
                            + ", input=" + be.getInventory().getStackInSlot(0).getCount()
                            + ", tool=" + retained + ", lightning=" + be.getAvailableLightning()
                            + ", energy=" + be.getEnergyStorage().getEnergyStored());
            var saved = be.saveWithFullMetadata();
            be.clearContent();
            be.load(saved);
            h.runAfterDelay(8, () -> {
                be.processTick();
                h.assertTrue(be.getStatus() == MiningFactoryBlockEntity.Status.TOOL
                        && output(be, Items.DIRT) == 3
                        && be.getInventory().getStackInSlot(0).getCount() == 61 && be.getAvailableLightning() == 999
                        && GearHelper.isBroken(be.getInventory().getStackInSlot(MiningFactoryInventory.TOOL)),
                        "Retained broken gear restarted a batch after reload");
                h.succeed();
            });
        });
    }
}
