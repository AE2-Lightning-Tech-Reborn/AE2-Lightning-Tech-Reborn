package com.moakiee.ae2lt.debug;

import appeng.api.upgrades.IUpgradeableItem;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.FrequencyCardLinkShortcutPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

/** Real terminal upgrade inventories and the server-side link shortcut. */
@GameTestHolder("ae2lt_frequency_shortcut")
@PrefixGameTestTemplate(false)
public final class FrequencyCardShortcutGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CardShortcutQA"));
    }

    private static ItemStack card(ServerPlayer player, int frequency) {
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        OverloadedFrequencyCardItem.bindFrequency(card, frequency, player.level().dimension(),
                BlockPos.ZERO, player.getUUID());
        return card;
    }

    private static ItemStack terminal(Item item, ItemStack card) {
        var terminal = new ItemStack(item);
        ((IUpgradeableItem) item).getUpgrades(terminal).setItemDirect(0, card.copy());
        return terminal;
    }

    private static ItemStack terminal(ItemStack card) {
        return terminal(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get(), card);
    }

    private static int selectedFrequency(ServerPlayer player) {
        return OverloadedFrequencyCardItem.selectLinkCard(player).selected()
                .map(OverloadedFrequencyCardItem::getData).orElseThrow().frequencyId();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void carriedTerminalsSupportManualLinkWithAutoConnectOff(GameTestHelper helper) {
        var player = player(helper);
        for (var item : List.of(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get(),
                ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get())) {
            var terminal = terminal(item, card(player, 42));
            var before = TerminalCardAccess.readCardData(terminal);
            for (int slot : new int[] {0, 4, 20, 40}) {
                player.getInventory().clearContent();
                player.getInventory().setItem(slot, terminal);
                helper.assertTrue(selectedFrequency(player) == 42, "terminal card missing from slot " + slot);
                helper.assertTrue(OverloadedFrequencyCardItem.findAutoConnectCard(player).isEmpty(),
                        "manual linking enabled automatic placement connections");
                helper.assertTrue(OverloadedFrequencyCardItem.selectToggleCard(player).selected().isEmpty(),
                        "toggle path must not mutate an unpersisted upgrade snapshot");
                helper.assertTrue(TerminalCardAccess.readCardData(terminal).equals(before), "card data changed");
            }
            TerminalCardAccess.updateCard(terminal, data -> data.withAutoConnect(true));
            helper.assertTrue(OverloadedFrequencyCardItem.findAutoConnectCard(player).isPresent(),
                    "enabled automatic terminal connection regressed");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void curiosTerminalSupportsManualLink(GameTestHelper helper) {
        var player = player(helper);
        var equipped = CuriosApi.getCuriosInventory(player).orElseThrow().getEquippedCurios();
        helper.assertTrue(equipped.getSlots() > 0, "Curios fixture has no slots");
        equipped.setStackInSlot(0, terminal(card(player, 43)));
        helper.assertTrue(selectedFrequency(player) == 43, "equipped terminal card was skipped");
        equipped.setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(OverloadedFrequencyCardItem.selectLinkCard(player).selected().isEmpty(),
                "removed terminal remains selectable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void terminalCardsRespectOwnershipBindingPriorityAndAmbiguity(GameTestHelper helper) {
        var player = player(helper);
        var foreign = card(player, 44);
        OverloadedFrequencyCardItem.bindFrequency(foreign, 44, player.level().dimension(), BlockPos.ZERO, UUID.randomUUID());
        player.getInventory().setItem(10, terminal(foreign));
        player.getInventory().setItem(11, terminal(new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get())));
        helper.assertTrue(OverloadedFrequencyCardItem.selectLinkCard(player).selected().isEmpty(),
                "foreign or unbound card admitted");
        player.getInventory().setItem(12, terminal(card(player, 45)));
        helper.assertTrue(selectedFrequency(player) == 45, "invalid earlier terminals hide usable card");
        player.getInventory().setItem(13, terminal(card(player, 46)));
        var selection = OverloadedFrequencyCardItem.selectLinkCard(player);
        helper.assertTrue(selection.ambiguous() && selection.selected().isEmpty(), "multiple terminals picked arbitrarily");
        player.getInventory().setItem(14, card(player, 47));
        helper.assertTrue(selectedFrequency(player) == 47, "loose backpack card lost priority");
        player.setItemInHand(InteractionHand.MAIN_HAND, card(player, 48));
        helper.assertTrue(selectedFrequency(player) == 48, "held card lost priority");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 120)
    public static void shortcutConnectsAndDisconnectsUsingInstalledCard(GameTestHelper helper) {
        var player = player(helper);
        var controllerPos = new BlockPos(2, 2, 2);
        var target = helper.absolutePos(new BlockPos(2, 2, 6));
        helper.setBlock(controllerPos, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        helper.setBlock(controllerPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.getLevel().setBlockAndUpdate(target, AEBlocks.DRIVE.block().defaultBlockState());
        AdvancedWirelessOverloadedControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        var frequency = WirelessFrequencyManager.get().createFrequency(player, "Shortcut QA", 0xffffff,
                FrequencySecurityLevel.PRIVATE, "");
        controller.setFrequency(frequency.getId());
        var terminal = terminal(card(player, frequency.getId()));
        player.getInventory().setItem(20, terminal);
        player.setPos(target.getX() + .5, target.getY() - 1, target.getZ() - 2);
        var hit = new Vec3(target.getX() + .5, target.getY() + .5, target.getZ());
        var packet = new FrequencyCardLinkShortcutPacket(target, Direction.NORTH, hit);
        helper.runAfterDelay(30, () -> {
            var links = WirelessLinkRegistry.get(helper.getLevel().getServer());
            FrequencyCardLinkShortcutPacket.handle(packet, context(player));
            helper.assertTrue(links.inspectTarget(helper.getLevel(), target, Direction.NORTH, hit).isPresent(),
                    "shortcut did not connect using backpack terminal");
            FrequencyCardLinkShortcutPacket.handle(packet, context(player));
            helper.assertTrue(!links.inspectTarget(helper.getLevel(), target, Direction.NORTH, hit).isPresent(),
                    "second shortcut did not disconnect");
            helper.assertTrue(!TerminalCardAccess.readCardData(terminal).autoConnect(), "manual shortcut changed auto flag");
            helper.succeed();
        });
    }

    private static IPayloadContext context(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> {
                        ((Runnable) args[0]).run();
                        yield CompletableFuture.completedFuture(null);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
