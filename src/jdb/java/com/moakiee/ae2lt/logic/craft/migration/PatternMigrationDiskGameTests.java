package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;

import static com.moakiee.ae2lt.logic.craft.migration.PatternMigrationGameTests.*;

@GameTestHolder("ae2lt_migration_disk")
@PrefixGameTestTemplate(false)
public final class PatternMigrationDiskGameTests {
    @GameTest(templateNamespace="ae2lt_migration_disk", template="migration_test", timeoutTicks=300)
    public static void ecoDiskRowsUseRealApi(GameTestHelper h) throws Exception {
        PatternMigrationGameTests.ecoPhysicalSlotsAndSharedLeaseUseActualApi(h);
    }

    @GameTest(templateNamespace="ae2lt_migration_disk", template="migration_test", timeoutTicks=300)
    @SuppressWarnings("unchecked")
    public static void batchAssemblerDiskMigratesAndPendingOutputsAreDetected(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2_pattern_disk")) { h.succeed(); return; }
        fixture(h, f -> {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    net.minecraft.resources.ResourceLocation.parse("ae2_pattern_disk:batch_molecular_assembler"));
            h.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, "real batch assembler registered");
            h.getLevel().setBlockAndUpdate(f.provider().getBlockPos(), block.defaultBlockState());
            var assembler = h.getLevel().getBlockEntity(f.provider().getBlockPos());
            var disks = (appeng.api.inventories.InternalInventory) MigrationReflection.call(assembler, "getDiskInventory");
            var first = pattern(h, false); disks.setItemDirect(0, disk(h, first));
            var probe = PatternMigrationSources.executionProbe(assembler);
            h.assertTrue(!probe.getAsBoolean(), "an idle assembler with no cell is not executing");
            var outputs = (Map<appeng.api.stacks.AEKey, long[]>) MigrationReflection.field(MigrationReflection.field(assembler, "returns"), "pending");
            outputs.put(AEItemKey.of(Items.OAK_PLANKS), new long[]{4, 4});
            h.assertTrue(probe.getAsBoolean(), "undelivered output blocks migration even with empty work lanes");
            outputs.clear();
            f.port().getGrid().getStorageService().getInventory().insert(AEItemKey.of(AEItems.BLANK_PATTERN), 1,
                    Actionable.MODULATE, IActionSource.ofPlayer(f.player()));
            h.runAfterDelay(30, () -> {
                f.controller().getPatternMigration().start(f.player());
                await(h, f, () -> {
                    h.assertTrue(f.controller().getPatternMigration().snapshot().moved() == 1 && count(f, first) == 1,
                            "batch assembler disk recipe migrates through actual source discovery");
                    h.assertTrue(diskPatterns(disks.getStackInSlot(0)).isEmpty(), "batch assembler disk really emptied");
                    System.out.println("MIGRATION_REAL_DISK_BATCH_PASS"); done(h, f);
                });
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration_disk", template="migration_test", timeoutTicks=400)
    public static void diskProviderMigratesRealRecipesAndNeverItsMirror(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2_pattern_disk")) { h.succeed(); return; }
        fixture(h, f -> {
            var pos = f.provider().getBlockPos();
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    net.minecraft.resources.ResourceLocation.parse("ae2_pattern_disk:pattern_disk_provider"));
            h.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, "real disk provider registered");
            h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            var provider = h.getLevel().getBlockEntity(pos);
            var disks = (appeng.api.inventories.InternalInventory) MigrationReflection.call(provider, "getDiskInventory");
            var first = pattern(h, false);
            var raw = h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("birch_planks")).orElseThrow();
            var inputs = new ItemStack[9]; Arrays.fill(inputs, ItemStack.EMPTY); inputs[0] = new ItemStack(Items.BIRCH_LOG);
            var second = PatternDetailsHelper.encodeCraftingPattern(new RecipeHolder<CraftingRecipe>(raw.id(), (CraftingRecipe) raw.value()),
                    inputs, new ItemStack(Items.BIRCH_PLANKS, 4), true, false);
            var processing = PatternDetailsHelper.encodeProcessingPattern(List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                    List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
            disks.setItemDirect(0, disk(h, first, second));
            disks.setItemDirect(1, disk(h, first));
            disks.setItemDirect(2, disk(h, processing));
            var network = f.port().getGrid().getStorageService().getInventory();
            network.insert(AEItemKey.of(AEItems.BLANK_PATTERN), 5, Actionable.MODULATE, IActionSource.ofPlayer(f.player()));
            h.runAfterDelay(30, () -> {
                f.controller().getPatternMigration().start(f.player());
                await(h, f, () -> {
                    var result = f.controller().getPatternMigration().snapshot();
                    h.assertTrue(result.moved() == 2 && result.recovered() == 1 && result.incompatible() == 1, "disk rows migrate once: " + result);
                    h.assertTrue(count(f, first) == 1 && count(f, second) == 1, "both adjacent disk recipes arrive with all settings");
                    h.assertTrue(diskPatterns(disks.getStackInSlot(0)).isEmpty() && diskPatterns(disks.getStackInSlot(1)).isEmpty(), "source disks really emptied");
                    h.assertTrue(diskPatterns(disks.getStackInSlot(2)).size() == 1, "processing recipe retained on disk");
                    h.assertTrue(network.extract(AEItemKey.of(AEItems.BLANK_PATTERN), 99, Actionable.SIMULATE, IActionSource.ofPlayer(f.player())) == 2, "exactly three blanks consumed");
                    h.assertTrue(network.extract(AEItemKey.of(first), 99, Actionable.SIMULATE, IActionSource.ofPlayer(f.player())) == 1, "duplicate materialized only once");
                    System.out.println("MIGRATION_REAL_DISK_PROVIDER_PASS"); done(h, f);
                });
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration_disk", template="migration_test", timeoutTicks=300)
    public static void diskGuardRollbackReturnsBlankAndMissingBlankLeavesRecipe(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2_pattern_disk")) { h.succeed(); return; }
        fixture(h, f -> {
            var physical = new appeng.util.inv.AppEngInternalInventory(null, 1);
            var original = pattern(h, true); physical.setItemDirect(0, disk(h, original));
            var recipes = PatternMigrationDiskInventory.create(f.provider(), physical, f.port()::getGrid);
            var network = f.port().getGrid().getStorageService().getInventory();
            var source = recipes.slot(0, () -> true);
            var held = new ItemStack[]{ItemStack.EMPTY};
            var rejecting = new PatternMigrationTransfer.Destination() {
                public boolean canAccept(ItemStack stack) { return true; }
                public ItemStack insert(ItemStack stack) { return stack; }
            };
            h.assertTrue(PatternMigrationTransfer.move(source, original, rejecting, stack -> held[0] = stack)
                    == PatternMigrationTransfer.Result.SOURCE_CHANGED, "no blank means no removal");
            h.assertTrue(diskPatterns(physical.getStackInSlot(0)).size() == 1 && held[0].isEmpty(), "recipe stays without blank");
            network.insert(AEItemKey.of(AEItems.BLANK_PATTERN), 1, Actionable.MODULATE, IActionSource.ofPlayer(f.player()));
            h.assertTrue(PatternMigrationTransfer.move(source, original, rejecting, stack -> held[0] = stack)
                    == PatternMigrationTransfer.Result.RESTORED, "real API rolls back using the captured guard");
            h.assertTrue(ItemStack.matches(original, diskPatterns(physical.getStackInSlot(0)).getFirst()) && held[0].isEmpty(), "rollback preserves exact recipe");
            h.assertTrue(network.extract(AEItemKey.of(AEItems.BLANK_PATTERN), 2, Actionable.SIMULATE, IActionSource.ofPlayer(f.player())) == 1, "rollback returns blank");
            System.out.println("MIGRATION_REAL_DISK_ROLLBACK_PASS"); done(h, f);
        });
    }

    private static ItemStack disk(GameTestHelper h, ItemStack... patterns) {
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("ae2_pattern_disk:pattern_disk_1k"));
            var disk = new ItemStack(item);
            var api = Class.forName("io.github.lounode.ae2pattern.api.PatternDiskApi");
            var insert = Arrays.stream(api.getMethods()).filter(method -> method.getName().equals("insert") && method.getParameterCount() == 4).findFirst().orElseThrow();
            for (var pattern : patterns) h.assertTrue(((ItemStack) insert.invoke(null, disk, pattern.copy(), h.getLevel(), null)).isEmpty(), "real disk accepts test recipe");
            return disk;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> diskPatterns(ItemStack disk) {
        try {
            var contents = Class.forName("io.github.lounode.ae2pattern.api.PatternDiskApi").getMethod("contents", ItemStack.class).invoke(null, disk);
            return (List<ItemStack>) MigrationReflection.call(contents, "patterns");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

}
