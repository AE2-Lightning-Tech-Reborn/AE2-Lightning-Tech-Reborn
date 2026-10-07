package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AEColor;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.Ae2PlainPatternResolver;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.core.crafting.planner.FastCraftingPlanner;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real terminal menus and Re-Avaritia machine ticks; never included in the release JAR. */
@GameTestHolder("ae2lt_overload_refill")
@PrefixGameTestTemplate(false)
public final class OverloadQuantityRefillGameTests {
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 180)
    public static void idOnlyInputUsesCraftedPlainVariant(GameTestHelper helper) {
        craftVariant(helper, 0);
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 180)
    public static void idOnlyInputUsesCraftedStrictOverloadVariant(GameTestHelper helper) {
        craftVariant(helper, 1);
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 180)
    public static void idOnlyInputUsesCraftedIdOnlyOverloadVariant(GameTestHelper helper) {
        craftVariant(helper, 2);
    }

    private static void craftVariant(GameTestHelper helper, int producerMode) {
        var level = helper.getLevel();
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
        helper.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos.west(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) level.getBlockEntity(helper.absolutePos(pos.west()))).getInternalInventory()
                .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var provider = (OverloadedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        var b1 = namedStone("B1");
        var b2 = namedStone("B2");
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var producer = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(raw, 1)), List.of(new GenericStack(b1, 1)));
        var consumer = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(b2, 1)), List.of(new GenericStack(output, 1)));
        var conversion = new PatternConversionService();
        var overloadItem = (OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get();
        var resolver = new Ae2PlainPatternResolver(level);
        if (producerMode != 0) {
            producer = conversion.createOverloadPatternStack(overloadItem, resolver.resolve(producer),
                    EncodedOverloadPattern.builder().input(0, MatchMode.STRICT)
                            .output(0, producerMode == 2 ? MatchMode.ID_ONLY : MatchMode.STRICT).build());
        }
        consumer = conversion.createOverloadPatternStack(overloadItem, resolver.resolve(consumer),
                EncodedOverloadPattern.builder().input(0, MatchMode.ID_ONLY).output(0, MatchMode.STRICT).build());
        provider.getExposedPatternInventory().setItemDirect(0, producer);
        provider.getExposedPatternInventory().setItemDirect(1, consumer);
        helper.runAfterDelay(40, () -> {
            provider.getLogic().updatePatterns();
            var service = provider.getMainNode().getGrid().getCraftingService();
            var stock = new ListCraftingInventory(key -> {});
            stock.insert(raw, 5, Actionable.MODULATE);
            var attempt = FastCraftingPlanner.tryAttempt(service, new ChildCraftingSimulationState(stock),
                    level, output, 5, false);
            var missing = attempt.simulationFallback() == null ? null : attempt.simulationFallback().missingItems();
            System.out.println("OVERLOAD_ID_ONLY_CHAIN_OBSERVED producer=" + producerMode + " stockA=5 stockB=0"
                    + " handled=" + attempt.handled() + " plan=" + (attempt.plan() != null) + " missing=" + missing
                    + " craftableB1=" + service.getCraftingFor(b1).size());
            helper.assertTrue(attempt.handled() && attempt.plan() != null,
                    "A -> B1 -> C must satisfy the ID_ONLY B2 input without stocked B: " + missing);
            helper.assertTrue(attempt.plan().usedItems().get(raw) == 5
                    && attempt.plan().missingItems().isEmpty(), "exact raw input and no missing B2");
            var source = IActionSource.ofMachine(provider);
            var inventory = provider.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(inventory.insert(raw, 5, Actionable.MODULATE, source) == 5,
                    "network holds only the A ingredients");
            var requester = new ICraftingSimulationRequester() {
                @Override public IActionSource getActionSource() { return source; }
                @Override public appeng.api.networking.IGridNode getGridNode() {
                    return provider.getMainNode().getNode();
                }
            };
            var future = service.beginCraftingCalculation(level, requester, output, 5,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            helper.succeedWhen(() -> {
                helper.assertTrue(future.isDone(), "normal crafting request completes");
                try {
                    var plan = future.get();
                    System.out.println("OVERLOAD_ID_ONLY_REQUEST_OBSERVED producer=" + producerMode
                            + " simulation=" + plan.simulation() + " missing=" + plan.missingItems());
                    helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(),
                            "normal crafting entry resolves B1 for the ID_ONLY B2 input");
                    helper.assertTrue(plan.usedItems().get(raw) == 5, "normal request uses exactly five A");
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 180)
    public static void sameIdInputsShareStockAcrossComponentVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
        helper.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var provider = (OverloadedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        var a = namedStone("a");
        var b = namedStone("b");
        var c = namedStone("c");
        var output = AEItemKey.of(Items.DIAMOND);
        var source = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(a, 7), new GenericStack(b, 11)),
                List.of(new GenericStack(output, 1)));
        var conversion = new PatternConversionService();
        var parsed = new Ae2PlainPatternResolver(level).resolve(source);
        var flags = EncodedOverloadPattern.builder().input(0, MatchMode.ID_ONLY).input(1, MatchMode.ID_ONLY)
                .output(0, MatchMode.STRICT).build();
        var encoded = conversion.createOverloadPatternStack((OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get(),
                parsed, flags);
        provider.getExposedPatternInventory().setItemDirect(0, encoded);
        helper.runAfterDelay(40, () -> {
            provider.getLogic().updatePatterns();
            var service = provider.getMainNode().getGrid().getCraftingService();
            var cases = List.of(Map.<AEKey, Long>of(a, 18L), Map.<AEKey, Long>of(b, 18L),
                    Map.<AEKey, Long>of(c, 18L), Map.<AEKey, Long>of(a, 7L, b, 11L),
                    Map.<AEKey, Long>of(a, 9L, b, 9L), Map.<AEKey, Long>of(a, 6L, b, 12L));
            for (var stock : cases) {
                var inventory = new ListCraftingInventory(key -> {});
                stock.forEach((key, amount) -> inventory.insert(key, amount, Actionable.MODULATE));
                var attempt = FastCraftingPlanner.tryAttempt(service, new ChildCraftingSimulationState(inventory),
                        level, output, 1, false);
                var missing = attempt.simulationFallback() == null ? null : attempt.simulationFallback().missingItems();
                System.out.println("OVERLOAD_ID_ONLY_OBSERVED stock=" + stock + " handled=" + attempt.handled()
                        + " plan=" + (attempt.plan() != null) + " missing=" + missing);
                helper.assertTrue(attempt.handled() && attempt.plan() != null,
                        "same-ID stock must satisfy both component-distinct slots: " + stock + " missing=" + missing);
                helper.assertTrue(attempt.plan().missingItems().isEmpty(), "no missing items for sufficient same-ID stock");
                long used = 0;
                for (var entry : attempt.plan().usedItems()) used += entry.getLongValue();
                helper.assertTrue(used == 18, "each physical item is charged exactly once");
            }
            helper.succeed();
        });
    }

    private static AEItemKey namedStone(String name) {
        var stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 180)
    public static void wiredEncodingRetainsInputAmounts(GameTestHelper helper) {
        encoding(helper, false);
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 180)
    public static void wirelessEncodingRetainsInputAmounts(GameTestHelper helper) {
        encoding(helper, true);
    }

    private static void encoding(GameTestHelper helper, boolean wireless) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "OverloadAmount"));
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        PartHelper.setPart(level, cable, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, cable, Direction.SOUTH, player,
                ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.west(), AEBlocks.DRIVE.block().defaultBlockState());
        ((DriveBlockEntity) level.getBlockEntity(cable.west())).getInternalInventory()
                .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        level.setBlockAndUpdate(cable.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        player.setPos(cable.getX() + .5, cable.getY() + 1, cable.getZ() + .5);
        helper.runAfterDelay(40, () -> {
            var storage = terminal.getActionableNode().getGrid().getStorageService().getInventory();
            storage.insert(AEItemKey.of(AEItems.BLANK_PATTERN), 16, Actionable.MODULATE, IActionSource.ofPlayer(player));
            var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
            var stack = new ItemStack(item);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), cable.north()));
            stack.set(AEComponents.STORED_ENERGY, 1000000.0);
            player.getInventory().setItem(0, stack);
            var wirelessHost = wireless ? new TianshuWirelessPatternEncodingTermMenuHost(
                    item, player, MenuLocators.forInventorySlot(0), (p, m) -> {}) : null;
            TianshuPatternEncodingTermMenu menu = wireless
                    ? new TianshuWirelessPatternEncodingTermMenu(17, player.getInventory(), wirelessHost)
                    : new TianshuPatternEncodingTermMenu(17, player.getInventory(), terminal);
            player.containerMenu = menu;
            var logic = wireless ? wirelessHost.getLogic() : terminal.getLogic();
            menu.setTianshuMode(TianshuEncodingMode.PROCESSING);
            for (long amount : new long[] {64, 100000, 999999}) {
                logic.getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
                logic.getEncodedInputInv().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), amount));
                logic.getEncodedOutputInv().setStack(0, new GenericStack(AEItemKey.of(Items.DIAMOND), 7));
                menu.armOverloadEncoding(new ProcessingPatternEncodingType.OverloadConfig(new int[] {0}, new int[0]));
                menu.broadcastChanges();
                menu.encode();
                menu.broadcastChanges();
                var encoded = logic.getEncodedPatternInv().getStackInSlot(0).copy();
                var details = PatternDetailsHelper.decodePattern(encoded, level);
                var draft = logic.getEncodedInputInv().getStack(0);
                long decoded = inputAmount(details);
                System.out.println("OVERLOAD_AMOUNT_OBSERVED wireless=" + wireless + " requested=" + amount
                        + " encoded=" + decoded + " draft=" + (draft == null ? 0 : draft.amount()));
                helper.assertTrue(encoded.getItem() instanceof OverloadPatternItem, "encoded as overload pattern");
                helper.assertTrue(decoded == amount, "encoded runtime retains complete input amount");
                helper.assertTrue(draft != null && draft.amount() == amount, "editing draft retains complete input amount");
                menu.encode();
                menu.broadcastChanges();
                details = PatternDetailsHelper.decodePattern(logic.getEncodedPatternInv().getStackInSlot(0), level);
                helper.assertTrue(inputAmount(details) == amount, "re-encoding does not overwrite amount with one");
                logic.getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
                logic.getEncodedPatternInv().setItemDirect(0, encoded);
                menu.broadcastChanges();
                helper.assertTrue(logic.getEncodedInputInv().getStack(0).amount() == amount,
                        "reinserting a saved overload pattern restores its exact amount");
                helper.assertTrue(menu.getOverloadEncodingConfig().isInputIdOnly(0), "match mode survives reload");
            }
            logic.getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
            for (int slot = 0; slot < logic.getEncodedInputInv().size(); slot++) {
                logic.getEncodedInputInv().setStack(slot, null);
            }
            for (int slot = 0; slot < logic.getEncodedOutputInv().size(); slot++) {
                logic.getEncodedOutputInv().setStack(slot, null);
            }
            var b1 = namedStone("sparse-B1");
            var b2 = namedStone("sparse-B2");
            logic.getEncodedInputInv().setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2));
            logic.getEncodedInputInv().setStack(3, new GenericStack(b2, 11));
            logic.getEncodedOutputInv().setStack(0, new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
            logic.getEncodedOutputInv().setStack(2, new GenericStack(b1, 7));
            menu.armOverloadEncoding(new ProcessingPatternEncodingType.OverloadConfig(new int[] {3}, new int[] {2}));
            menu.broadcastChanges();
            menu.encode();
            menu.broadcastChanges();
            var sparse = PatternDetailsHelper.decodePattern(logic.getEncodedPatternInv().getStackInSlot(0), level);
            boolean accepted = java.util.Arrays.stream(sparse.getInputs())
                    .anyMatch(input -> input.getPossibleInputs()[0].what().equals(b2) && input.isValid(b1, level));
            System.out.println("OVERLOAD_SPARSE_FLAG_OBSERVED wireless=" + wireless + " accepted=" + accepted);
            helper.assertTrue(accepted, "ID_ONLY follows its item when blank slots are compacted during encoding");
            var flags = (OverloadedProviderOnlyPatternDetails) sparse;
            helper.assertTrue(!flags.acceptsSameIdVariants(0) && flags.acceptsSameIdVariants(1),
                    "compaction keeps the first input strict and the B input ID_ONLY");
            helper.assertTrue(!flags.producesSameIdVariants(0) && flags.producesSameIdVariants(1),
                    "compaction keeps the primary output strict and the B output ID_ONLY");
            menu.encode();
            menu.broadcastChanges();
            var repeated = (OverloadedProviderOnlyPatternDetails) PatternDetailsHelper.decodePattern(
                    logic.getEncodedPatternInv().getStackInSlot(0), level);
            helper.assertTrue(!repeated.acceptsSameIdVariants(0) && repeated.acceptsSameIdVariants(1)
                    && !repeated.producesSameIdVariants(0) && repeated.producesSameIdVariants(1),
                    "re-encoding the compact draft preserves both strict and ID_ONLY modes");
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 180)
    public static void adjacentCompressorReceivesEveryTick(GameTestHelper helper) {
        compressor(helper, false);
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 180)
    public static void wirelessCompressorReceivesEveryTick(GameTestHelper helper) {
        compressor(helper, true);
    }

    private static void compressor(GameTestHelper helper, boolean wireless) {
        var level = helper.getLevel();
        var relative = new BlockPos(2, 2, 2);
        var pos = helper.absolutePos(relative);
        helper.setBlock(relative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(relative, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        var provider = (OverloadedPatternProviderBlockEntity) level.getBlockEntity(pos);
        var targetPos = wireless ? pos.south(3) : pos.south();
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("avaritia:neutron_compressor"));
        level.setBlockAndUpdate(targetPos, block.defaultBlockState());
        try {
            // New compressors start with every side disabled; enable their normal passive ports.
            var machine = level.getBlockEntity(targetPos);
            var cycle = machine.getClass().getMethod("cycleSideModeForNeutronCollector", Direction.class);
            for (var direction : Direction.values()) cycle.invoke(machine, direction);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
        provider.setReturnMode(OverloadedPatternProviderBlockEntity.ReturnMode.OFF);
        if (wireless) {
            provider.setProviderMode(OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS);
            helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), targetPos, Direction.NORTH),
                    "compressor connected");
        }
        var source = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 100000)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        var conversion = new PatternConversionService();
        var parsed = new Ae2PlainPatternResolver(level).resolve(source);
        var overload = conversion.createOverloadPatternStack((OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get(),
                parsed, conversion.createDefaultEncoding(parsed));
        provider.getExposedPatternInventory().setItemDirect(0, overload);
        helper.runAfterDelay(40, () -> {
            var logic = provider.getLogic();
            logic.updatePatterns();
            var pattern = logic.getAvailablePatterns().getFirst();
            var inputs = new KeyCounter[] {new KeyCounter()};
            inputs[0].add(AEItemKey.of(Items.STONE), 100000);
            helper.assertTrue(logic.pushPattern(pattern, inputs), "large input admitted with provider-owned remainder");
        });
        helper.runAfterDelay(100, () -> {
            var machine = level.getBlockEntity(targetPos);
            try {
                long received = ((Number) machine.getClass().getMethod("getMaterialCount").invoke(machine)).longValue();
                System.out.println("OVERLOAD_REFILL_OBSERVED wireless=" + wireless + " elapsed=60 material=" + received);
                helper.assertTrue(received >= 59 * 64,
                        "machine should receive close to one stack per tick while consuming: " + received);
            } catch (ReflectiveOperationException failure) {
                throw new RuntimeException(failure);
            }
            helper.succeed();
        });
    }

    private static long inputAmount(IPatternDetails details) {
        if (details == null || details.getInputs().length == 0) return 0;
        var input = details.getInputs()[0];
        return Math.multiplyExact(input.getPossibleInputs()[0].amount(), input.getMultiplier());
    }
}
