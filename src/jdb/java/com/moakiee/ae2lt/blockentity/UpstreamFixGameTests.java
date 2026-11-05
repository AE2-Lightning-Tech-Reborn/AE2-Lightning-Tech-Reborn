package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.moakiee.ae2lt.compat.mining.SilentGearMiningTool;
import com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.logic.compat.AdvancedAECompat;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.machine.miningfactory.MiningLoot;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryConfig;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-only runtime regressions; run with scripts/upstream-fixes-test.init.gradle. */
@GameTestHolder("ae2lt_fixes")
@PrefixGameTestTemplate(false)
public final class UpstreamFixGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private UpstreamFixGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void nativeAppliedFluxStillPublishesSupportedEnergyApi(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("appflux") && AppFluxBridge.canUseEnergyHandler()
                && AppFluxBridge.FE_KEY != null && AppFluxBridge.TRANSFER_RATE > 0L,
                "Hardened bootstrap must not disable the supported Applied Flux Forge API");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void nativeAdvancedAeRoundTripPreservesSparseSlotsAndDirectionSemantics(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("advanced_ae") && AdvancedAECompat.canEncode(),
                "Actual AdvancedAE Forge encoder must be available");
        var source = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[] {
                new GenericStack(AEItemKey.of(Items.COBBLESTONE), 2), null,
                new GenericStack(AEItemKey.of(Items.DIRT), 3)
        }, new GenericStack[] {new GenericStack(AEItemKey.of(Items.STONE), 1)});
        var encoded = AdvancedAECompat.encodeWithDirections(source, h.getLevel(),
                List.of(Direction.NORTH.ordinal() + 1, 0, 0));
        h.assertTrue(encoded != null && !encoded.isEmpty(), "Forge directional encoding failed");
        var pattern = PatternDetailsHelper.decodePattern(encoded, h.getLevel());
        h.assertTrue(pattern instanceof AdvProcessingPattern, "Expected the real AdvancedAE processing pattern");
        var restored = AdvancedAECompat.restoreForEditing(pattern, 9, 3);
        h.assertTrue(restored != null && restored.inputs().size() >= 3 && restored.inputs().get(1) == null
                && restored.directions()[0] == Direction.NORTH.ordinal() + 1
                && restored.directions()[2] == 0, "Sparse slots or any-side directions were lost");
        var second = PatternDetailsHelper.decodePattern(encoded.copy(), h.getLevel());
        h.assertTrue(AdvancedAECompat.samePatternSemantics(pattern, second), "Identical real patterns must compare equal");
        var changed = AdvancedAECompat.encodeWithDirections(source, h.getLevel(),
                List.of(Direction.SOUTH.ordinal() + 1, 0, 0));
        h.assertTrue(changed != null && !AdvancedAECompat.samePatternSemantics(pattern,
                PatternDetailsHelper.decodePattern(changed, h.getLevel())), "Different target faces must not compare equal");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void nativeAdvancedAeFailedReadsRejectEqualityAndEditing(GameTestHelper h) {
        var source = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)},
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.STONE), 1)});
        var encoded = AdvancedAECompat.encodeAnySide(source, h.getLevel());
        h.assertTrue(encoded != null, "Actual Forge encoder must be available");
        for (boolean throwsError : new boolean[] {false, true}) {
            var first = new BrokenAdvancedPattern(AEItemKey.of(encoded), throwsError);
            var second = new BrokenAdvancedPattern(AEItemKey.of(encoded), throwsError);
            h.assertTrue(!AdvancedAECompat.samePatternSemantics(first, second), "Two failed reads are not equal recipes");
            h.assertTrue(AdvancedAECompat.restoreForEditing(first, 9, 3) == null,
                    "Failed reads must not produce an empty or any-side editable recipe");
        }
        h.succeed();
    }

    private static final class BrokenAdvancedPattern extends AdvProcessingPattern {
        private final boolean throwsError;
        BrokenAdvancedPattern(AEItemKey definition, boolean throwsError) {
            super(definition);
            this.throwsError = throwsError;
        }
        @Override public GenericStack[] getSparseInputs() {
            if (throwsError) throw new NoSuchMethodError("test-only incompatible optional accessor");
            return null;
        }
        @Override public GenericStack[] getSparseOutputs() { return null; }
        @Override public LinkedHashMap<AEKey, Direction> getDirectionMap() { return null; }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void vanillaBreakStopsWithinSampleAndPreservesInstalledTool(GameTestHelper h) {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(tool.getMaxDamage() - 4);
        var snapshot = tool.copy();
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 4 && result.samples() == 1 && result.tool().isEmpty()
                && drops(result) == 4, "Vanilla break must commit exactly four usable hits");
        h.assertTrue(ItemStack.matches(tool, snapshot), "Prospective damage changed the installed tool");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void exhaustedToolCannotMineEvenZeroHardnessBlocks(GameTestHelper h) {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(tool.getMaxDamage());
        h.assertTrue(!MiningLoot.isTool(tool), "Exhausted tool must fail insertion validation");
        assertNoLoot(h, tool);
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void usableToolsMineZeroHardnessWithoutDurabilityChanges(GameTestHelper h) throws Exception {
        var gear = nativePickaxe(h);
        gear.setDamageValue(gear.getMaxDamage() - 4);
        var vanilla = new ItemStack(Items.IRON_PICKAXE);
        vanilla.setDamageValue(vanilla.getMaxDamage() - 1);
        for (var tool : new ItemStack[]{vanilla, gear}) {
            var snapshot = tool.copy();
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS),
                    Blocks.TORCH.defaultBlockState(), tool, 256, 8);
            h.assertTrue(result.processed() == 256 && result.samples() == 8 && drops(result) == 256,
                    "Usable tool must process the complete zero-hardness batch");
            h.assertTrue(ItemStack.matches(snapshot, tool) && ItemStack.matches(snapshot, result.tool()),
                    "Zero-hardness batch must not damage or mutate either tool copy");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void unbreakableToolAndPlaneCompleteFullBatch(GameTestHelper h) {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.getOrCreateTag().putBoolean("Unbreakable", true);
        for (var installed : new ItemStack[]{tool, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem())}) {
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), installed, 64, 8);
            h.assertTrue(result.processed() == 64 && result.samples() == 8 && drops(result) == 64
                    && ItemStack.matches(installed, result.tool()), "Durability-free tools must complete all blocks");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void insufficientHarvestTierCannotGenerateOre(GameTestHelper h) {
        var tool = new ItemStack(Items.WOODEN_PICKAXE);
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 0 && result.samples() == 0 && result.drops().isEmpty()
                && ItemStack.matches(tool, result.tool()), "Insufficient harvest tier must not spend durability");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void togglingImportFilterChangesAutoReturnEligibilityWithoutPatterns(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
        var provider = (OverloadedPatternProviderBlockEntity) h.getBlockEntity(POS);
        var logic = (OverloadedPatternProviderLogic) provider.getLogic();
        provider.setReturnMode(OverloadedPatternProviderBlockEntity.ReturnMode.AUTO);
        provider.setFilteredImport(true);
        h.assertTrue(!logic.hasAnyTickWork(), "Filtered return without outputs must stay idle");
        provider.setFilteredImport(false);
        h.assertTrue(logic.hasAnyTickWork(), "Disabling filtering must enable AUTO without patterns");
        provider.setFilteredImport(true);
        h.assertTrue(!logic.hasAnyTickWork(), "Re-enabling filtering must restore the output restriction");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void nativeGearBreakIsRetainedAndCannotMineAgain(GameTestHelper h) throws Exception {
        var tool = nativePickaxe(h);
        tool.setDamageValue(tool.getMaxDamage() - 4);
        var snapshot = tool.copy();
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 3 && result.samples() == 1 && drops(result) == 3,
                "Native gear must stop after exactly three usable hits");
        h.assertTrue(!result.tool().isEmpty() && SilentGearMiningTool.isBroken(result.tool()),
                "Silent Gear must retain its repairable broken stack");
        h.assertTrue(ItemStack.matches(tool, snapshot), "Prospective damage changed installed gear");
        assertNoLoot(h, result.tool());
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void nativeUnbreakableGearRemainsUsable(GameTestHelper h) throws Exception {
        var tool = nativePickaxe(h);
        tool.getOrCreateTag().putBoolean("Unbreakable", true);
        var snapshot = tool.copy();
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 64 && drops(result) == 64
                && ItemStack.matches(snapshot, result.tool()), "Unbreakable gear must not take native durability damage");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void nativeGearPartialBatchChargesActualCostsAndStaysStoppedAfterReload(GameTestHelper h) throws Exception {
        var tool = nativePickaxe(h);
        tool.setDamageValue(tool.getMaxDamage() - 4);
        h.setBlock(POS, ModBlocks.MINING_FACTORY.get());
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS.north(), AEBlocks.DRIVE.block());
        var factory = (MiningFactoryBlockEntity) h.getBlockEntity(POS);
        factory.getInventory().setStackInSlot(MiningFactoryInventory.INPUT, new ItemStack(Items.IRON_ORE, 64));
        factory.getInventory().setStackInSlot(MiningFactoryInventory.TOOL, tool);
        factory.getInventory().setStackInSlot(MiningFactoryInventory.MATRIX,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        factory.getEnergyStorage().receiveEnergy(100_000, false);
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null && storage.insert(LightningKey.HIGH_VOLTAGE, 1000,
                Actionable.MODULATE, IActionSource.empty()) == 1000, "Could not initialize lightning cell");
        storage.persist();
        ((DriveBlockEntity) h.getBlockEntity(POS.north())).getInternalInventory().setItemDirect(0, cell);
        h.runAfterDelay(25, () -> {
            int processed = 64 - factory.getInventory().getStackInSlot(MiningFactoryInventory.INPUT).getCount();
            long fe = 100_000L - (long) processed * MiningFactoryConfig.energyPerBlock(false);
            h.assertTrue(processed == 3 && output(factory) == 3,
                    "Partial batch must consume only completed input blocks");
            h.assertTrue(factory.getEnergyStorage().getStoredEnergyLong() == fe
                    && factory.getAvailableLightning() == 999, "Partial batch costs: processed=" + processed
                            + ", FE=" + factory.getEnergyStorage().getStoredEnergyLong() + " expected=" + fe
                            + ", lightning=" + factory.getAvailableLightning()
                            + ", parallel=" + factory.getInstalledParallelCapacity());
            h.assertTrue(SilentGearMiningTool.isBroken(factory.getInventory().getStackInSlot(MiningFactoryInventory.TOOL)),
                    "Broken tool must survive the result write-back");
            var saved = factory.saveWithFullMetadata();
            factory.clearContent();
            factory.load(saved);
            h.runAfterDelay(10, () -> {
                h.assertTrue(factory.getStatus() == MiningFactoryBlockEntity.Status.TOOL
                        && output(factory) == processed
                        && factory.getInventory().getStackInSlot(MiningFactoryInventory.INPUT).getCount() == 64 - processed
                        && factory.getEnergyStorage().getStoredEnergyLong() == fe
                        && factory.getAvailableLightning() == 999, "Reloaded broken tool resumed mining or spent resources");
                h.succeed();
            });
        });
    }

    private static ItemStack nativePickaxe(GameTestHelper h) throws Exception {
        return nativePickaxe(h, true);
    }

    static ItemStack nativePickaxe(GameTestHelper h, boolean deterministic) throws Exception {
        h.assertTrue(ModList.get().isLoaded("silentgear"), "Supply Silent Gear and Silent Lib with -PupstreamFixMods");
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("silentgear", "pickaxe"));
        var gear = Class.forName("net.silentchaos512.gear.api.item.ICoreItem");
        var resource = Class.forName("net.silentchaos512.gear.api.util.DataResource");
        var iron = Class.forName("net.silentchaos512.gear.util.Const$Materials").getField("IRON").get(null);
        var create = Class.forName("net.silentchaos512.gear.util.GearHelper")
                .getDeclaredMethod("createSampleItem", gear, resource);
        create.setAccessible(true);
        var stack = (ItemStack) create.invoke(null, item, iron);
        // The wood parts add random durability saves. Remove cached traits only
        // in this fixture so the native break/cost boundary is deterministic.
        if (deterministic) {
            stack.getOrCreateTagElement("SGear_Data").getCompound("Properties").remove("Traits");
            var cachedTraits = (java.util.Map<?, ?>) Class.forName("net.silentchaos512.gear.util.TraitHelper")
                    .getMethod("getCachedTraits", ItemStack.class).invoke(null, stack);
            h.assertTrue(cachedTraits.isEmpty(), "Native fixture still has random durability traits");
        }
        h.assertTrue(MiningLoot.isTool(stack) && stack.getMaxDamage() > 8
                && stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()), "Invalid native iron pickaxe fixture");
        return stack;
    }

    private static void assertNoLoot(GameTestHelper h, ItemStack tool) {
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.DIRT, Blocks.TORCH, Blocks.IRON_ORE}) {
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), block.defaultBlockState(), tool, 64, 8);
            h.assertTrue(result.processed() == 0 && result.samples() == 0 && result.drops().isEmpty()
                    && ItemStack.matches(tool, result.tool()), "Broken tool generated loot for " + block);
        }
    }

    private static long drops(MiningLoot.Result result) {
        return result.drops().stream().mapToLong(MiningLoot.Drop::count).sum();
    }

    private static long output(MiningFactoryBlockEntity factory) {
        long count = factory.getPendingDrops().stream().mapToLong(MiningLoot.Drop::count).sum();
        for (int slot = MiningFactoryInventory.OUTPUT; slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
            count += factory.getInventory().getStackInSlot(slot).getCount();
        }
        return count;
    }
}
