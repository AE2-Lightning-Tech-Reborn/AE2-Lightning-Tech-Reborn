package com.moakiee.ae2lt.debug;

import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.moakiee.ae2lt.block.FirmamentConversionCoreBlock;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.event.FirmamentCoreUpgradeHandler;
import com.moakiee.ae2lt.event.FirmamentPlanePlacementHandler;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipeInput;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_firmament")
@PrefixGameTestTemplate(false)
public final class FirmamentWorkflowGameTests {
    @GameTest(template = "empty")
    public static void assemblyRecipeCoexistsWithEarlyLightningRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var holder = manager.byKey(ResourceLocation.parse("ae2lt:lightning_assembly/lightning_simulation_room"))
                .orElseThrow();
        h.assertTrue(holder.value() instanceof LightningAssemblyRecipe, "assembly recipe loaded");
        var recipe = (LightningAssemblyRecipe) holder.value();
        var inventory = new LightningAssemblyChamberInventory(null);
        inventory.setStackInSlot(0, new ItemStack(ModItems.OVERLOAD_PROCESSOR.get(), 32));
        inventory.setStackInSlot(1, new ItemStack(ModItems.OVERLOAD_ALLOY.get(), 32));
        inventory.setStackInSlot(2, new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get(), 16));
        h.assertTrue(recipe.planMatch(LightningAssemblyRecipeInput.fromInventory(inventory)).isPresent(),
                "original materials match the new assembly recipe");
        inventory.getStackInSlot(0).shrink(1);
        h.assertTrue(recipe.planMatch(LightningAssemblyRecipeInput.fromInventory(inventory)).isEmpty(),
                "one missing processor prevents crafting");
        h.assertTrue(recipe.getResultStack().is(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.asItem()),
                "assembly produces a simulation room");
        h.assertTrue(manager.byKey(ResourceLocation.parse("ae2lt:lightning_transform/lightning_simulation_room"))
                .isPresent(), "early recipe retained");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coreProcessesAllAvailableEssenceCopiesInOneCycle(GameTestHelper h) throws Exception {
        var core = core(h, true);
        for (int i = 0; i < 3; i++) core.getInventory().setStackInSlot(i, new ItemStack(ModItems.FIRMAMENT_DUST.get(), 64));
        tick(h, core, 40);
        h.assertTrue(count(core, ModItems.FIRMAMENT_ESSENCE.get(), 3, 6) == 0, "full cycle required");
        tick(h, core, 1);
        h.assertTrue(count(core, ModItems.FIRMAMENT_ESSENCE.get(), 3, 6) == 12, "192 dust becomes 12 essence");
        h.assertTrue(count(core, ModItems.FIRMAMENT_DUST.get(), 0, 2) == 0, "all 192 dust consumed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coreStopsBeforeFullOutputsAndPreservesRemainingInputs(GameTestHelper h) throws Exception {
        var core = core(h, true);
        core.getInventory().setStackInSlot(0, new ItemStack(ModItems.FIRMAMENT_DUST.get(), 64));
        h.assertTrue(core.getInventory().insertRecipeOutput(
                new ItemStack(ModItems.FIRMAMENT_ESSENCE.get(), 254), false).isEmpty(), "seed machine outputs");
        tick(h, core, 41);
        h.assertTrue(count(core, ModItems.FIRMAMENT_ESSENCE.get(), 3, 6) == 256, "only two copies fit");
        h.assertTrue(count(core, ModItems.FIRMAMENT_DUST.get(), 0, 2) == 32, "only two copies consumed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coreCapsBatchAndCompletesAllFourSpiritOutputs(GameTestHelper h) throws Exception {
        var core = core(h, true);
        core.getInventory().setStackInSlot(0, new ItemStack(ModItems.INACTIVE_FIRMAMENT_SPIRIT_CORE.get(), 64));
        tick(h, core, 201);
        h.assertTrue(count(core, ModItems.INACTIVE_FIRMAMENT_SPIRIT_CORE.get(), 0, 2) == 48, "batch capped at 16");
        for (int i = 3; i <= 6; i++) h.assertTrue(core.getInventory().getStackInSlot(i).getCount() == 16,
                "each spirit output receives 16 items");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coreStillRequiresStarshipAndCompleteIngredients(GameTestHelper h) throws Exception {
        var core = core(h, false);
        core.getInventory().setStackInSlot(0, new ItemStack(ModItems.FIRMAMENT_DUST.get(), 31));
        tick(h, core, 100);
        h.assertTrue(count(core, ModItems.FIRMAMENT_DUST.get(), 0, 2) == 31, "outside starship consumes nothing");
        setInside(core, true);
        tick(h, core, 41);
        h.assertTrue(count(core, ModItems.FIRMAMENT_ESSENCE.get(), 3, 6) == 1, "one complete recipe");
        h.assertTrue(count(core, ModItems.FIRMAMENT_DUST.get(), 0, 2) == 15, "partial next recipe remains");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bulkPlacementConsumesOnlyInstalledPlanesAndPreservesParts(GameTestHelper h) {
        var end = h.getLevel().getServer().getLevel(Level.END);
        var origin = endOrigin(h, end);
        var player = FakePlayerFactory.get(end, new GameProfile(UUID.randomUUID(), "FirmamentPlacement"));
        for (int x = 0; x < 4; x++) PartHelper.setPart(end, origin.east(x), null, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(end, origin, Direction.UP, player, AEParts.ANNIHILATION_PLANE.get());
        PartHelper.setPart(end, origin.east(2), Direction.UP, player, AEParts.FORMATION_PLANE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem(), 8));
        player.setShiftKeyDown(true);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, origin,
                new BlockHitResult(Vec3.atCenterOf(origin).add(0, 0.5, 0), Direction.UP, origin, false));
        FirmamentPlanePlacementHandler.onRightClickBlock(event);
        h.assertTrue(event.isCanceled(), "sneak-right-click triggers bulk placement");
        h.assertTrue(PartHelper.getPart(end, origin.east(), Direction.UP) != null
                && PartHelper.getPart(end, origin.east(3), Direction.UP) != null,
                "fills two free cables through existing planes");
        h.assertTrue(player.getMainHandItem().getCount() == 6, "consumes exactly two planes");
        h.assertTrue(PartHelper.getPart(end, origin.east(2), Direction.UP).getPartItem().asItem()
                == AEParts.FORMATION_PLANE.asItem(), "formation plane preserved");
        cleanup(end, origin, 4);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bulkPlacementRespectsProtectionAndStopsAtHeldStack(GameTestHelper h) {
        var end = h.getLevel().getServer().getLevel(Level.END);
        var origin = endOrigin(h, end);
        var player = FakePlayerFactory.get(end, new GameProfile(UUID.randomUUID(), "FirmamentProtection"));
        for (int x = 0; x < 4; x++) PartHelper.setPart(end, origin.east(x), null, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem(), 3));
        Consumer<PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getLevel() == end && event.getPos().equals(origin.east())) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(deny);
        try {
            h.assertTrue(FirmamentPlanePlacementHandler.placePlanes(player, end, origin,
                    InteractionHand.MAIN_HAND) == 1, "protected cable stops this path");
        } finally { NeoForge.EVENT_BUS.unregister(deny); }
        h.assertTrue(player.getMainHandItem().getCount() == 2, "protected placements consume nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem(), 1));
        h.assertTrue(FirmamentPlanePlacementHandler.placePlanes(player, end, origin,
                InteractionHand.MAIN_HAND) == 1, "held stack bounds placement");
        h.assertTrue(player.getMainHandItem().isEmpty(), "last plane consumed");
        cleanup(end, origin, 4);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeBulkPlacementStillHasSixtyFourPlaneLimit(GameTestHelper h) {
        var end = h.getLevel().getServer().getLevel(Level.END);
        var origin = endOrigin(h, end);
        var player = FakePlayerFactory.get(end, new GameProfile(UUID.randomUUID(), "FirmamentLimit"));
        player.setGameMode(GameType.CREATIVE);
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) {
            var pos = origin.offset(x, 0, z);
            end.getChunkAt(pos);
            PartHelper.setPart(end, pos, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem()));
        h.assertTrue(FirmamentPlanePlacementHandler.placePlanes(player, end, origin,
                InteractionHand.MAIN_HAND) == 64, "creative mode still stops at 64");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "native creative placement keeps the item");
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) end.removeBlock(origin.offset(x, 0, z), false);
        h.succeed();
    }

    private static FirmamentConversionCoreBlockEntity core(GameTestHelper h, boolean inside) throws Exception {
        return core(h, inside, true);
    }

    private static FirmamentConversionCoreBlockEntity core(GameTestHelper h, boolean inside, boolean advanced) throws Exception {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, ModBlocks.FIRMAMENT_CONVERSION_CORE.get().defaultBlockState()
                .setValue(FirmamentConversionCoreBlock.ADVANCED, advanced));
        FirmamentConversionCoreBlockEntity core = h.getBlockEntity(pos);
        setInside(core, inside);
        return core;
    }

    private static void setInside(FirmamentConversionCoreBlockEntity core, boolean inside) throws Exception {
        var field = FirmamentConversionCoreBlockEntity.class.getDeclaredField("insideStarship");
        field.setAccessible(true);
        field.set(core, inside);
    }

    private static void tick(GameTestHelper h, FirmamentConversionCoreBlockEntity core, int ticks) {
        for (int i = 0; i < ticks; i++) FirmamentConversionCoreBlockEntity.serverTick(
                h.getLevel(), core.getBlockPos(), core.getBlockState(), core);
    }

    private static int count(FirmamentConversionCoreBlockEntity core, net.minecraft.world.item.Item item, int first, int last) {
        int count = 0;
        for (int slot = first; slot <= last; slot++) {
            var stack = core.getInventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static BlockPos endOrigin(GameTestHelper h, ServerLevel end) {
        var pos = h.absolutePos(BlockPos.ZERO);
        var origin = new BlockPos(pos.getX() + 1000, end.getMaxBuildHeight() - 1, pos.getZ() + 1000);
        for (int x = 0; x < 4; x++) end.getChunkAt(origin.east(x));
        return origin;
    }

    private static void cleanup(ServerLevel end, BlockPos origin, int count) {
        for (int x = 0; x < count; x++) end.removeBlock(origin.east(x), false);
    }

    @GameTest(template = "empty")
    public static void ordinaryCoreKeepsSingleRecipeAndSixtyFourInputLimit(GameTestHelper h) throws Exception {
        var core = core(h, true, false);
        var remainder = core.getAutomationInventory().insertItem(0,
                new ItemStack(ModItems.FIRMAMENT_DUST.get(), 256), false);
        h.assertTrue(remainder.getCount() == 192, "ordinary input stops at 64");
        h.assertTrue(core.getBatchSize() == 1, "ordinary core remains serial");
        tick(h, core, 41);
        h.assertTrue(count(core, ModItems.FIRMAMENT_ESSENCE.get(), 3, 6) == 1, "only one recipe completed");
        h.assertTrue(count(core, ModItems.FIRMAMENT_DUST.get(), 0, 2) == 48, "only 16 dust consumed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void matrixUpgradePreservesProgressConsumesOnceAndPersistsLargeInputs(GameTestHelper h) throws Exception {
        var core = core(h, true, false);
        core.getAutomationInventory().insertItem(0, new ItemStack(ModItems.FIRMAMENT_DUST.get(), 64), false);
        tick(h, core, 10);
        int progress = core.getProgress();
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "CoreUpgrade"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), 2));
        h.assertTrue(!core.tryUpgrade(player, InteractionHand.MAIN_HAND), "upgrade requires sneaking");
        player.setShiftKeyDown(true);
        var pos = core.getBlockPos();
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        FirmamentCoreUpgradeHandler.onRightClickBlock(event);
        h.assertTrue(event.isCanceled() && core.isAdvanced(), "sneak-right-click upgrades the core");
        h.assertTrue(h.getLevel().getBlockEntity(pos) == core, "upgrade preserves block entity");
        h.assertTrue(core.getProgress() == progress && count(core, ModItems.FIRMAMENT_DUST.get(), 0, 2) == 64,
                "inventory and progress retained");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "one matrix consumed");
        h.assertTrue(!core.tryUpgrade(player, InteractionHand.MAIN_HAND)
                && player.getMainHandItem().getCount() == 1, "repeated upgrade consumes nothing");
        tick(h, core, 40 - progress);
        h.assertTrue(count(core, ModItems.FIRMAMENT_ESSENCE.get(), 3, 6) == 4, "upgraded cycle completes four copies");
        var remainder = core.getAutomationInventory().insertItem(0,
                new ItemStack(ModItems.FIRMAMENT_DUST.get(), 300), false);
        h.assertTrue(remainder.getCount() == 44 && core.getInventory().getStackInSlot(0).getCount() == 256,
                "advanced input accepts exactly 256 through automation");
        for (int slot = 3; slot <= 6; slot++) h.assertTrue(core.getAutomationInventory().getSlotLimit(slot) == 64,
                "output slots remain limited to 64");
        var restored = (FirmamentConversionCoreBlockEntity) BlockEntity.loadStatic(pos, core.getBlockState(),
                core.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored != null && restored.isAdvanced() && restored.getBatchSize() == 16
                && restored.getInventory().getStackInSlot(0).getCount() == 256,
                "saved advanced core keeps all 256 input items");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeRespectsProtectionAndDoesNotConsumeMatrix(GameTestHelper h) throws Exception {
        var core = core(h, true, false);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "CoreProtected"));
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        Consumer<PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getPos().equals(core.getBlockPos()) && event.getLevel() == h.getLevel()) {
                h.assertTrue(event.getHand() == InteractionHand.OFF_HAND, "permission check preserves the used hand");
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(deny);
        try {
            h.assertTrue(!core.tryUpgrade(player, InteractionHand.OFF_HAND), "protected core cannot be upgraded");
        } finally { NeoForge.EVENT_BUS.unregister(deny); }
        h.assertTrue(!core.isAdvanced() && player.getOffhandItem().getCount() == 1, "core and matrix unchanged");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bulkPlacementWorksAtOverworldAndNetherBuildLimits(GameTestHelper h) {
        var levels = new ServerLevel[] { h.getLevel(), h.getLevel().getServer().getLevel(Level.NETHER) };
        for (var level : levels) {
            var origin = endOrigin(h, level);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WorldTopPlacement"));
            for (int x = 0; x < 3; x++) PartHelper.setPart(level, origin.east(x), null, player,
                    AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem(), 3));
            h.assertTrue(FirmamentPlanePlacementHandler.placePlanes(player, level, origin,
                    InteractionHand.MAIN_HAND) == 3, "world top supports bulk placement in " + level.dimension().location());
            h.assertTrue(player.getMainHandItem().isEmpty(), "three planes consumed");
            cleanup(level, origin, 3);
        }
        h.succeed();
    }
}
