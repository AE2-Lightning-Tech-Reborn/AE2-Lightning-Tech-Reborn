package com.moakiee.ae2lt.debug;

import appeng.core.definitions.AEItems;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.lhy.mest.registry.ModItems;
import com.lhy.mest.terminal.MESTMenu;
import com.lhy.mest.terminal.MESTMenuHost;
import com.moakiee.ae2lt.integration.mest.MestSlot;
import com.moakiee.ae2lt.integration.mest.MestTianshuAccess;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_mest")
@PrefixGameTestTemplate(false)
public final class MestTianshuGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static ServerPlayer player(GameTestHelper h, String name) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.getInventory().clearContent();
        return p;
    }
    private static MESTMenu open(ServerPlayer player, ItemStack terminal) {
        player.getInventory().setItem(0, terminal);
        var host = new MESTMenuHost(ModItems.splicedTerminalItem(), player,
                MenuLocators.forInventorySlot(0), (p, menu) -> {});
        var menu = new MESTMenu(17, player.getInventory(), host);
        player.containerMenu = menu;
        menu.setLocator(host.getLocator());
        return menu;
    }
    private static Slot outerSlot(MESTMenu menu, Slot original) {
        return menu.slots.stream().filter(s -> s instanceof MestSlot proxy && proxy.original() == original)
                .findFirst().orElseThrow();
    }
    private static void close(ServerPlayer player, MESTMenu menu) {
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void smithingRoutesOnceAndRejectsHiddenPage(GameTestHelper h) {
        var p = player(h, "MestSmith");
        var menu = open(p, new ItemStack(ModItems.splicedTerminalItem()));
        var crafting = ((MestTianshuAccess) menu).tianshuCraftingMenu();
        var inputs = crafting.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING);
        inputs.get(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        inputs.get(1).set(new ItemStack(Items.DIAMOND_SWORD));
        inputs.get(2).set(new ItemStack(Items.NETHERITE_INGOT));
        var result = outerSlot(menu, inputs.get(3));
        check(result.index != inputs.get(3).index, "outer/native slot indices must remain independent");
        menu.doAction(p, InventoryAction.CRAFT_ITEM, result.index, 0);
        check(menu.getCarried().isEmpty() && inputs.get(0).hasItem(), "hidden workstation must reject action");
        menu.receiveClientAction("ae2lt.mest.crafting.workPage", "\"SMITHING\"");
        check(crafting.workPage == TianshuWorkPage.SMITHING, "namespaced action reaches native controller");
        var inventorySlot = menu.slots.stream().filter(s -> s.container == p.getInventory() && s.getContainerSlot() == 2).findFirst().orElseThrow();
        menu.swapSlotContents(result.index, inventorySlot.index);
        check(!inventorySlot.hasItem() && inputs.get(0).hasItem(), "direct swap cannot bypass workstation consumption");
        menu.clicked(result.index, 0, ClickType.PICKUP, p);
        check(menu.getCarried().is(Items.NETHERITE_SWORD) && !inputs.get(0).hasItem()
                && !inputs.get(1).hasItem() && !inputs.get(2).hasItem(), "native craft consumes exactly one recipe");
        check(crafting.getCarried() == menu.getCarried(), "one authoritative cursor stack");
        var first = menu.getCarried().copy();
        menu.doAction(p, InventoryAction.CRAFT_ITEM, result.index, 0);
        check(ItemStack.matches(first, menu.getCarried()), "stale result action cannot duplicate output");
        close(p, menu);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void itemReloadPreservesIndependentInputsAndEncoders(GameTestHelper h) {
        var p = player(h, "MestSave");
        var terminal = new ItemStack(ModItems.splicedTerminalItem());
        var menu = open(p, terminal);
        var access = (MestTianshuAccess) menu;
        var crafting = access.tianshuCraftingMenu();
        crafting.setWorkPage(TianshuWorkPage.ANVIL);
        crafting.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).getFirst().set(new ItemStack(Items.DIAMOND_PICKAXE));
        crafting.setAnvilName("MEST retained rename");
        crafting.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst().set(AEItems.ITEM_CELL_1K.stack());
        menu.getMestHost().getLogic().getEncodedPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(2));
        access.tianshuPatternMenu().getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(AEItems.BLANK_PATTERN.stack(3));
        check(terminal.has(ModDataComponents.MEST_TIANSHU_PATTERN), "separate Tianshu encoder persisted");
        var saved = (CompoundTag) terminal.save(h.getLevel().registryAccess());
        close(p, menu);
        var loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
        var reopened = open(p, loaded);
        var reopenedAccess = (MestTianshuAccess) reopened;
        check(reopenedAccess.tianshuCraftingMenu().getAnvilInput().is(Items.DIAMOND_PICKAXE)
                && reopenedAccess.tianshuCraftingMenu().anvilItemName.equals("MEST retained rename"),
                "anvil input and name survive item codec");
        check(reopenedAccess.tianshuCraftingMenu().getCell().is(AEItems.ITEM_CELL_1K.asItem()), "cell survives item codec");
        check(reopened.getMestHost().getLogic().getEncodedPatternInv().getStackInSlot(0).getCount() == 2,
                "native MEST encoded slot retained independently");
        check(reopenedAccess.tianshuPatternMenu().getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().getCount() == 3,
                "Tianshu encoded slot retained independently");
        close(p, reopened);
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void fixedSlotOrderAndGhostFiltersNeverCreateRealItems(GameTestHelper h) {
        var p = player(h, "MestGhost");
        var menu = open(p, new ItemStack(ModItems.splicedTerminalItem()));
        var access = (MestTianshuAccess) menu;
        var seen = new HashSet<Slot>();
        int count = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            check(slot.index == i, "outer slot index equals packet index");
            if (slot instanceof MestSlot proxy) {
                count++;
                check(seen.add(proxy.original()), "each native slot registered only once");
                check(proxy.controller().getSlot(proxy.original().index) == proxy.original(), "native index unchanged");
            }
        }
        check(count > 80, "workstation and pattern modules have their actual slots");
        var pattern = access.tianshuPatternMenu();
        var ghost = outerSlot(menu, pattern.getSlots(SlotSemantics.CRAFTING_GRID).getFirst());
        menu.setFilter(ghost.index, new ItemStack(Items.DIAMOND));
        check(ghost.hasItem(), "filter action reaches active native ghost slot");
        menu.quickMoveStack(p, ghost.index);
        check(menu.getCarried().isEmpty() && !p.getInventory().contains(new ItemStack(Items.DIAMOND)),
                "ghost filter cannot be shift-extracted as a real item");
        close(p, menu);
        h.succeed();
    }
    private static net.neoforged.neoforge.network.handling.IPayloadContext context(ServerPlayer player) {
        return (net.neoforged.neoforge.network.handling.IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(
                MestTianshuGameTests.class.getClassLoader(), new Class<?>[]{net.neoforged.neoforge.network.handling.IPayloadContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield java.util.concurrent.CompletableFuture.completedFuture(null); }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 200)
    public static void poweredEncodingUploadAndMaintenanceUseNativeServices(GameTestHelper h) {
        var level = h.getLevel();
        var p = player(h, "MestNetwork");
        var origin = h.absolutePos(new net.minecraft.core.BlockPos(7, 5, 3));
        var facing = net.minecraft.core.Direction.NORTH;
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            var local = new net.minecraft.core.BlockPos(x, y, z);
            var state = switch (com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate.roleAt(local)) {
                case CASING -> com.moakiee.ae2lt.registry.ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
                case COOLING -> com.moakiee.ae2lt.registry.ModBlocks.PHASE_CHANGE_COOLING_UNIT.get().defaultBlockState();
                case GLASS -> com.moakiee.ae2lt.registry.ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get().defaultBlockState();
                case CONTROLLER -> com.moakiee.ae2lt.registry.ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState()
                        .setValue(com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock.FACING, facing);
                case PORT_CANDIDATE -> local.equals(com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate.LOWER_PORT)
                        ? com.moakiee.ae2lt.registry.ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().defaultBlockState()
                        : com.moakiee.ae2lt.registry.ModBlocks.PHASE_CHANGE_COOLING_UNIT.get().defaultBlockState();
                case CORE_RESERVED -> (local.equals(new net.minecraft.core.BlockPos(3, 3, 3))
                        ? com.moakiee.ae2lt.registry.ModBlocks.BASELINE_SUPERCOMPUTING_UNIT
                        : com.moakiee.ae2lt.registry.ModBlocks.TIANSHU_BLANK_UNIT).get().defaultBlockState();
                case IGNORED -> net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            };
            level.setBlockAndUpdate(com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner.worldPos(origin, local, facing), state);
        }
        var controller = (com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(origin);
        var portPos = com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner.worldPos(origin,
                com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate.LOWER_PORT, facing);
        var port = (com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity) level.getBlockEntity(portPos);
        var cable = portPos.below();
        appeng.api.parts.PartHelper.setPart(level, cable, null, p, appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        level.setBlockAndUpdate(cable.below(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.west(), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        ((appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(cable.west())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        level.setBlockAndUpdate(cable.east(), appeng.core.definitions.AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        level.setBlockAndUpdate(cable.east(2), appeng.core.definitions.AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
        level.setBlockAndUpdate(cable.north(), appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        p.setPos(cable.getX() + .5, cable.getY() + 1, cable.getZ() + .5);
        h.runAfterDelay(45, () -> {
            controller.scanNow();
            check(controller.isFormed(), "test Tianshu structure forms: " + controller.issueText());
            var grid = port.getGrid();
            check(grid != null && grid.getEnergyService().isNetworkPowered(), "native powered grid");
            var storage = grid.getStorageService().getInventory();
            var source = appeng.api.networking.security.IActionSource.ofPlayer(p);
            var blank = appeng.api.stacks.AEItemKey.of(AEItems.BLANK_PATTERN);
            storage.insert(blank, 32, appeng.api.config.Actionable.MODULATE, source);
            var terminal = new ItemStack(ModItems.splicedTerminalItem());
            terminal.set(appeng.api.ids.AEComponents.WIRELESS_LINK_TARGET, net.minecraft.core.GlobalPos.of(level.dimension(), cable.north()));
            terminal.set(appeng.api.ids.AEComponents.STORED_ENERGY, 1000000.0);
            var menu = open(p, terminal);
            menu.broadcastChanges();
            var access = (MestTianshuAccess) menu;
            var pattern = access.tianshuPatternMenu();
            check(menu.getLinkStatus().connected() && pattern.getLinkStatus().connected(), "controllers use the actual outer wireless link");
            var matrix = pattern.getSlots(SlotSemantics.CRAFTING_GRID);
            menu.setFilter(outerSlot(menu, matrix.getFirst()).index, new ItemStack(Items.OAK_LOG));
            long before = storage.extract(blank, 999, appeng.api.config.Actionable.SIMULATE, source)
                    + menu.getBlankPatternSlot().getItem().getCount();
            menu.receiveClientAction("ae2lt.mest.pattern.encodeTianshu", "false");
            var result = outerSlot(menu, pattern.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst());
            var encoded = result.getItem().copy();
            check(appeng.api.crafting.PatternDetailsHelper.decodePattern(encoded, level) != null, "native Tianshu encoding through outer action");
            check(storage.extract(blank, 999, appeng.api.config.Actionable.SIMULATE, source)
                    + menu.getBlankPatternSlot().getItem().getCount() == before - 1, "exactly one real blank charged across network and MEST reserve");
            check(menu.getMestHost().getLogic().getEncodedPatternInv().getStackInSlot(0).isEmpty(), "MEST encoder remains independent");
            pattern.uploadEncodedPattern();
            var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) level.getBlockEntity(cable.east());
            check(result.getItem().isEmpty() && ItemStack.matches(provider.getLogic().getPatternInv().getStackInSlot(0), encoded), "upload transfers the paid pattern to native provider");
            menu.receiveClientAction("ae2lt.mest.pattern.clear", "");
            long refundBefore = storage.extract(blank, 999, appeng.api.config.Actionable.SIMULATE, source)
                    + menu.getBlankPatternSlot().getItem().getCount();
            menu.receiveClientAction("ae2lt.mest.pattern.encodeTianshu", "false");
            check(result.getItem().isEmpty() && storage.extract(blank, 999, appeng.api.config.Actionable.SIMULATE, source)
                    + menu.getBlankPatternSlot().getItem().getCount() == refundBefore, "failed encoding refunds the staged reserved blank");
            h.runAfterDelay(5, () -> {
                menu.broadcastChanges();
                var maintenance = com.moakiee.ae2lt.menu.extension.TianshuMenuContainer.maintenance(menu);
                check(maintenance != null && maintenance.isMaintenanceAvailable(), "formed Tianshu exposes maintenance through MEST");
                var key = appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS);
                var packet = new com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket(menu.containerId, maintenance.getTianshuSelectionRevision(), key,
                        null, false, 8, 32, 4, false, java.util.List.of());
                com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket.handle(packet, context(p));
                var service = port.getInventoryMaintenance();
                var rule = service.repository().get(key);
                check(rule != null && rule.lowerThreshold() == 8 && rule.upperThreshold() == 32, "actual maintenance packet creates native rule");
                var stale = new com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket(menu.containerId, maintenance.getTianshuSelectionRevision() + 1, key,
                        rule.id(), false, 100, 200, 4, false, java.util.List.of());
                com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket.handle(stale, context(p));
                check(service.repository().get(key).lowerThreshold() == 8, "stale selection revision rejected");
                close(p, menu);
                check(com.moakiee.ae2lt.menu.extension.TianshuMenuContainer.maintenance(menu) == null, "closed outer container no longer grants access");
                h.succeed();
            });
        });
    }

}
