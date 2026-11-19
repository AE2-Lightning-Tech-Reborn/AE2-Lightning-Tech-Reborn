package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.blockentity.MiningFactoryBlockEntity;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.MiningFactoryMenu;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the transformed AEBaseMenu.clicked boundary, not a copy of the transfer algorithm. */
@GameTestHolder("ae2lt_mining")
@PrefixGameTestTemplate(false)
public final class LargeStackSlotGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final String TEMPLATE = "empty";

    private record Fixture(ServerPlayer player, MiningFactoryMenu menu, MiningFactoryInventory inventory) {
        Slot output() { return menu.getSlots(SlotSemantics.MACHINE_OUTPUT).getFirst(); }
        void seed(ItemStack stack) { inventory.setItemDirect(MiningFactoryInventory.OUTPUT, stack); }
        int remaining() { return inventory.getStackInSlot(MiningFactoryInventory.OUTPUT).getCount(); }
        void shift() { menu.clicked(output().index, 0, ClickType.QUICK_MOVE, player); }
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "SlotTest"));
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        var pos = h.absolutePos(POS);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        return player;
    }

    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.MINING_FACTORY.get());
        MiningFactoryBlockEntity host = h.getBlockEntity(POS);
        var player = player(h);
        var menu = new MiningFactoryMenu(7, player.getInventory(), host);
        player.containerMenu = menu;
        return new Fixture(player, menu, host.getInventory());
    }

    private static void fill(ServerPlayer player, ItemStack stack) {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, stack.copy());
    }

    private static long count(ServerPlayer player, ItemStack key) {
        return player.getInventory().items.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, key))
                .mapToLong(ItemStack::getCount).sum();
    }

    private static void assertPlayerStacks(GameTestHelper h, ServerPlayer player, AEBaseMenu menu) {
        for (var stack : player.getInventory().items) assertNative(h, stack);
        assertNative(h, player.getOffhandItem());
        assertNative(h, menu.getCarried());
    }

    private static void assertNative(GameTestHelper h, ItemStack stack) {
        h.assertTrue(stack.isEmpty() || (!GenericStack.isWrapped(stack)
                && stack.getCount() <= stack.getMaxStackSize()), "Player received a wrapper or oversized stack");
    }

    private static void assertNoDrops(GameTestHelper h, ServerPlayer player) {
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(2)).isEmpty(), "A menu transfer dropped items on the ground");
    }

    private static void assertBalance(GameTestHelper h, Fixture f, ItemStack key, long expected) {
        long carried = ItemStack.isSameItemSameComponents(f.menu.getCarried(), key)
                ? f.menu.getCarried().getCount() : 0;
        h.assertTrue((long) f.remaining() + count(f.player, key) + carried == expected,
                "Source + inventory + cursor changed total, expected " + expected);
        assertPlayerStacks(h, f.player, f.menu);
        assertNoDrops(h, f.player);
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void oneShiftFillsInventoryAndLeavesExcessInOutput(GameTestHelper h) {
        var f = fixture(h);
        var stone = new ItemStack(Items.STONE, 4096);
        f.seed(stone);
        h.assertTrue(GenericStack.isWrapped(f.output().getItem()), "Fixture must use the oversized projection");
        f.shift();
        h.assertTrue(count(f.player, stone) == 36 * 64 && f.remaining() == 4096 - 36 * 64,
                "One Shift-click must move more than one stack, up to inventory capacity");
        h.assertTrue(((LargeStackAppEngSlot) f.output()).getDisplayedAmount() == f.remaining(),
                "Display count disagrees with real storage");
        for (int i = 0; i < 20; i++) f.shift();
        assertBalance(h, f, stone, 4096);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void fullInventoryNeverExtractsFromOutputEvenOnRepeatedClicks(GameTestHelper h) {
        var f = fixture(h);
        var stone = new ItemStack(Items.STONE, 1024);
        for (var filler : List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.STONE, 64))) {
            fill(f.player, filler);
            f.seed(stone);
            long initial = 1024 + count(f.player, stone);
            h.assertTrue(!f.output().mayPlace(stone), "Output must reject insertion/refunds");
            for (int i = 0; i < 100; i++) f.shift();
            h.assertTrue(f.remaining() == 1024, "Full inventory debited the machine");
            assertBalance(h, f, stone, initial);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void partialSpaceMergesBeforeUsingEmptySlots(GameTestHelper h) {
        var f = fixture(h);
        var stone = new ItemStack(Items.STONE, 1024);
        fill(f.player, new ItemStack(Items.DIRT, 64));
        f.player.getInventory().setItem(0, new ItemStack(Items.STONE, 61));
        f.player.getInventory().setItem(35, ItemStack.EMPTY);
        f.seed(stone);
        f.shift();
        h.assertTrue(f.remaining() == 957 && count(f.player, stone) == 128,
                "Only 3 + 64 items fit; the remainder must stay in the output");
        assertBalance(h, f, stone, 1085);

        f.player.getInventory().clearContent();
        f.player.getInventory().setItem(0, new ItemStack(Items.STONE, 60));
        f.seed(new ItemStack(Items.STONE, 3));
        f.shift();
        h.assertTrue(f.player.getInventory().getItem(0).getCount() == 63
                && f.player.getInventory().getItem(9).isEmpty(), "Hotbar merge must precede empty inventory slots");
        assertBalance(h, f, stone, 63);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void nativeLimitsAndLegacyCountsRemainConserved(GameTestHelper h) {
        var f = fixture(h);
        var custom = new ItemStack(Items.STONE);
        custom.set(DataComponents.MAX_STACK_SIZE, 32);
        for (var key : List.of(new ItemStack(Items.STONE), new ItemStack(Items.SNOWBALL),
                new ItemStack(Items.DIAMOND_PICKAXE), custom)) {
            for (int amount : new int[] {1, 63, 64, 65, 127, 128, 129, 1024, Integer.MAX_VALUE}) {
                f.player.getInventory().clearContent();
                f.seed(key.copyWithCount(amount));
                f.shift();
                int expected = Math.min(amount, 36 * key.getMaxStackSize());
                h.assertTrue(count(f.player, key) == expected && f.remaining() == amount - expected,
                        "Wrong transfer for native limit " + key.getMaxStackSize() + ", count " + amount);
                assertBalance(h, f, key, amount);
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void componentsCursorAndOffhandArePreserved(GameTestHelper h) {
        var f = fixture(h);
        var named = new ItemStack(Items.STONE, 200);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Owned stone"));
        named.set(DataComponents.REPAIR_COST, 7);
        fill(f.player, new ItemStack(Items.DIRT, 64));
        f.player.getInventory().setItem(0, named.copyWithCount(60));
        f.player.getInventory().setItem(1, new ItemStack(Items.STONE, 63));
        f.player.getInventory().setItem(35, ItemStack.EMPTY);
        f.player.getInventory().setItem(40, new ItemStack(Items.DIAMOND, 5));
        f.menu.setCarried(new ItemStack(Items.EMERALD, 3));
        f.seed(named);
        f.shift();
        h.assertTrue(f.remaining() == 132 && count(f.player, named) == 128,
                "Only identical components may merge");
        h.assertTrue(f.player.getInventory().getItem(1).getCount() == 63
                && f.player.getOffhandItem().getCount() == 5 && f.menu.getCarried().getCount() == 3,
                "Transfer changed unrelated player stacks");
        assertBalance(h, f, named, 260);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void randomizedInventoryCapacityAndReloadUseIndependentLedger(GameTestHelper h) {
        var f = fixture(h);
        var random = new Random(0x5107);
        var stone = new ItemStack(Items.STONE);
        for (int sample = 0; sample < 200; sample++) {
            f.player.getInventory().clearContent();
            int room = 0;
            long initial = 0;
            for (int i = 0; i < 36; i++) {
                int kind = random.nextInt(3);
                int amount = random.nextInt(64) + 1;
                if (kind == 0) room += 64;
                else if (kind == 1) {
                    f.player.getInventory().setItem(i, new ItemStack(Items.STONE, amount));
                    initial += amount;
                    room += 64 - amount;
                } else f.player.getInventory().setItem(i, new ItemStack(Items.DIRT, amount));
            }
            int source = random.nextInt(8192) + 1;
            f.seed(stone.copyWithCount(source));
            f.shift();
            h.assertTrue(f.remaining() == source - Math.min(source, room), "Capacity oracle disagrees, sample " + sample);
            assertBalance(h, f, stone, initial + source);
            var tag = new CompoundTag();
            f.inventory.saveToTag(tag, "items", h.getLevel().registryAccess());
            var restored = new MiningFactoryInventory(null);
            restored.loadFromTag(tag, "items", h.getLevel().registryAccess());
            h.assertTrue(restored.getStackInSlot(MiningFactoryInventory.OUTPUT).getCount() == f.remaining(),
                    "Reload changed the retained output");
            f.shift();
            assertBalance(h, f, stone, initial + source);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void shiftInsertionAndExtractionDoNotCopyPresentationStacks(GameTestHelper h) {
        var f = fixture(h);
        var input = f.menu.getSlots(SlotSemantics.MACHINE_INPUT).getFirst();
        f.inventory.setItemDirect(MiningFactoryInventory.INPUT, new ItemStack(Items.STONE, 1000));
        f.player.getInventory().setItem(9, new ItemStack(Items.STONE, 64));
        var playerSlot = f.menu.getSlots(SlotSemantics.PLAYER_INVENTORY).getFirst();
        f.menu.clicked(playerSlot.index, 0, ClickType.QUICK_MOVE, f.player);
        h.assertTrue(f.inventory.getStackInSlot(MiningFactoryInventory.INPUT).getCount() == 1064
                && count(f.player, new ItemStack(Items.STONE)) == 0, "Shift insertion lost or copied items");
        f.menu.clicked(input.index, 0, ClickType.QUICK_MOVE, f.player);
        h.assertTrue(f.inventory.getStackInSlot(MiningFactoryInventory.INPUT).isEmpty()
                && count(f.player, new ItemStack(Items.STONE)) == 1064, "Shift extraction lost or copied items");
        assertPlayerStacks(h, f.player, f.menu);
        assertNoDrops(h, f.player);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void pickupAndForgedSwapStillRespectNativeLimits(GameTestHelper h) {
        var f = fixture(h);
        var stone = new ItemStack(Items.STONE, 1024);
        f.seed(stone);
        f.menu.clicked(f.output().index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().getCount() == 64 && f.remaining() == 960, "Pickup escaped native cap");
        assertBalance(h, f, stone, 1024);
        for (int button : new int[] {0, 8, 40}) f.menu.clicked(f.output().index, button, ClickType.SWAP, f.player);
        f.menu.clicked(f.output().index, 0, ClickType.CLONE, f.player);
        f.menu.clicked(f.output().index, 1, ClickType.QUICK_CRAFT, f.player);
        h.assertTrue(f.menu.getCarried().getCount() == 64 && f.remaining() == 960, "Unsafe clicks changed storage");
        f.player.getInventory().setItem(0, f.menu.getCarried().copy());
        f.menu.setCarried(ItemStack.EMPTY);
        f.menu.clicked(f.output().index, 1, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().getCount() == 64 && f.remaining() == 896, "Right pickup escaped native cap");
        f.shift();
        assertBalance(h, f, stone, 1024);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = TEMPLATE)
    public static void allEightMachineMenusUseTheCommonShiftBoundary(GameTestHelper h) {
        var player = player(h);
        for (int layout = 0; layout < 8; layout++) {
            // Separate positions avoid dismantle drops between machine fixtures.
            var pos = new BlockPos(1 + layout % 4 * 2, 2, 1 + layout / 4 * 2);
            AEBaseMenu menu;
            switch (layout) {
                case 0 -> {
                    h.setBlock(pos, ModBlocks.ATMOSPHERIC_IONIZER.get());
                    menu = new AtmosphericIonizerMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                case 1 -> {
                    h.setBlock(pos, ModBlocks.CRYSTAL_CATALYZER.get());
                    menu = new CrystalCatalyzerMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                case 2 -> {
                    h.setBlock(pos, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get());
                    menu = new LightningAssemblyChamberMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                case 3 -> {
                    h.setBlock(pos, ModBlocks.LIGHTNING_COLLECTOR.get());
                    menu = new LightningCollectorMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                case 4 -> {
                    h.setBlock(pos, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get());
                    menu = new LightningSimulationChamberMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                case 5 -> {
                    h.setBlock(pos, ModBlocks.MINING_FACTORY.get());
                    menu = new MiningFactoryMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                case 6 -> {
                    h.setBlock(pos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
                    menu = new OverloadProcessingFactoryMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
                default -> {
                    h.setBlock(pos, ModBlocks.TESLA_COIL.get());
                    menu = new TeslaCoilMenu(7, player.getInventory(), h.getBlockEntity(pos));
                }
            }
            player.containerMenu = menu;
            var source = (LargeStackAppEngSlot) menu.slots.stream()
                    .filter(LargeStackAppEngSlot.class::isInstance).findFirst().orElseThrow();
            // Direct seeding also covers grandfathered stacks above a slot's current limit.
            source.set(new ItemStack(Items.STONE, 129));
            player.getInventory().clearContent();
            menu.clicked(source.index, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(!source.hasItem() && count(player, new ItemStack(Items.STONE)) == 129,
                    "Shared guard did not drain " + menu.getClass().getSimpleName());
            assertPlayerStacks(h, player, menu);
            assertNoDrops(h, player);
        }
        h.succeed();
    }
}
