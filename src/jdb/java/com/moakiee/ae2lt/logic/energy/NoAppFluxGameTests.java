package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.logic.management.ManagementMode;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("ae2lt_no_appflux")
@PrefixGameTestTemplate(false)
public final class NoAppFluxGameTests {
    private static final BlockPos C = new BlockPos(2, 2, 2);

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void missingAppFluxKeepsOneCraftableAdvancedControllerRecipe(GameTestHelper h) {
        assertAbsent(h);
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getResultItem(h.getLevel().registryAccess()).is(
                        ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().asItem())).toList();
        h.assertTrue(recipes.size() == 1, "expected one advanced controller recipe without AppFlux, got " + recipes.size());
        h.assertTrue(recipes.getFirst().value().getIngredients().stream().allMatch(i -> !i.isEmpty()), "recipe has unavailable inputs");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void controllerChannelsAndCardManagementWorkWithoutAppFlux(GameTestHelper h) {
        assertAbsent(h);
        var f = fixture(h);
        h.setBlock(C.south(4), ModBlocks.WIRELESS_RECEIVER.get());
        WirelessReceiverBlockEntity receiver = h.getBlockEntity(C.south(4));
        receiver.setFrequency(f.frequency);
        h.setBlock(C.south(5), AEBlocks.INTERFACE.block());
        h.runAfterDelay(50, () -> {
            h.assertTrue(f.host.getMainNode().isActive(), "advanced controller inactive with normal AE power");
            h.assertTrue(receiver.getMainNode().getGrid() == f.host.getMainNode().getGrid(), "wireless receiver not joined to controller grid");
            h.assertTrue(receiver.getMainNode().isActive(), "wireless receiver has no powered channel");
            var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
            OverloadedFrequencyCardItem.bindFrequency(card, f.frequency, h.getLevel().dimension(), h.absolutePos(C), f.player.getUUID());
            f.player.setItemInHand(InteractionHand.MAIN_HAND, card);
            ((OverloadedFrequencyCardItem) card.getItem()).use(h.getLevel(), f.player, InteractionHand.MAIN_HAND);
            h.assertTrue(f.player.containerMenu instanceof FrequencyMenu, "held card did not open frequency menu");
            var menu = (FrequencyMenu) f.player.containerMenu;
            h.assertTrue(menu.isCardMode() && menu.getCurrentFrequencyId() == f.frequency, "card menu lost frequency");
            h.assertTrue(menu.supportsManagement(), "card does not expose management pages");
            for (var mode : ManagementMode.values()) {
                var query = ManagementQuery.initial(mode);
                var snapshot = ManagementSnapshot.build(f.player, f.host, query);
                if (mode == ManagementMode.ENERGY) h.assertTrue(snapshot.total() == 0, "unexpected FE outputs without AppFlux");
                else h.assertTrue(snapshot.total() > 0, "empty " + mode + " page");
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    snapshot.write(buffer);
                    h.assertTrue(ManagementSnapshot.read(buffer).total() == snapshot.total(), "snapshot failed to round-trip");
                } finally { buffer.release(); }
                NetworkManagementMenu.open(f.player, f.host, mode, query, null);
                h.assertTrue(f.player.containerMenu instanceof NetworkManagementMenu && f.player.containerMenu.stillValid(f.player),
                        "invalid management menu: " + mode);
            }
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void feBindingAndRunningAeFallbackFailClosedWithoutAppFlux(GameTestHelper h) {
        assertAbsent(h);
        var f = fixture(h);
        var target = C.south(2);
        h.setBlock(target, WirelessEnergyTestMachine.BLOCK);
        h.runAfterDelay(40, () -> {
            var endpoints = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            String result = endpoints.setBound(f.player, f.frequency, h.getLevel(), h.absolutePos(target), Direction.NORTH, true);
            h.assertTrue(result.equals("unavailable"), "FE binding did not reject absent AppFlux: " + result);
            h.assertTrue(endpoints.inspect(f.frequency, f.player.getUUID()).isEmpty(), "failed FE binding retained an endpoint");
            double resultAe = WirelessAeFallback.supplement(f.host.getMainNode().getGrid(), true, 100, 5,
                    Actionable.MODULATE, PowerMultiplier.ONE, false);
            h.assertTrue(resultAe == 5, "FE fallback changed AE result without AppFlux");
            WirelessEnergyTestMachine machine = h.getBlockEntity(target);
            h.assertTrue(machine.received == 0, "unavailable FE binding delivered energy");
            h.succeed();
        });
    }

    private static void assertAbsent(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("appflux"), "test runtime still contains AppFlux");
        h.assertTrue(!AppFluxBridge.isAvailable() && AppFluxBridge.FE_KEY == null && AppFluxBridge.TRANSFER_RATE == 0,
                "bridge failed to disable without AppFlux");
        h.assertTrue(AppFluxBridge.getInductionCard() == null, "missing induction card not null");
        h.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("ae2lt:overloaded_power_supply")),
                "power supply incorrectly registered without AppFlux");
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void disabledFePreferenceCannotBeEnabledOrQueuedButMeStillWorks(GameTestHelper h) {
        assertAbsent(h);
        var f = fixture(h);
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        OverloadedFrequencyCardItem.bindFrequency(card, f.frequency, h.getLevel().dimension(), h.absolutePos(C), f.player.getUUID());
        f.player.setItemInHand(InteractionHand.MAIN_HAND, card);
        var context = context(f.player);
        ToggleFrequencyCardAutoConnectPacket.handle(new ToggleFrequencyCardAutoConnectPacket(
                java.util.Optional.of(InteractionHand.MAIN_HAND), false, true), context);
        h.assertTrue(!OverloadedFrequencyCardItem.getData(card).autoConnectEnergy(), "packet enabled FE without AppFlux");
        ToggleFrequencyCardAutoConnectPacket.handle(ToggleFrequencyCardAutoConnectPacket.forHand(InteractionHand.MAIN_HAND), context);
        h.assertTrue(OverloadedFrequencyCardItem.getData(card).autoConnect(), "ME auto toggle was blocked");
        // A card saved with AppFlux installed keeps its preference, but must not become an FE candidate.
        OverloadedFrequencyCardItem.setData(card, OverloadedFrequencyCardItem.getData(card).withAutoConnectEnergy(true));
        h.assertTrue(OverloadedFrequencyCardItem.findAutoConnectEnergyCard(f.player).isEmpty(), "legacy FE flag still active");
        h.assertTrue(OverloadedFrequencyCardItem.findAutoConnectCard(f.player).isPresent(), "ME candidate lost");
        FrequencyMenu.openHeldCard(f.player, InteractionHand.MAIN_HAND);
        h.assertTrue(!((FrequencyMenu) f.player.containerMenu).isAutoConnectEnergy(), "menu displays saved FE as active");
        var target = C.south(2);
        h.setBlock(target, WirelessEnergyTestMachine.BLOCK);
        var admissions = WirelessEnergyAdmissions.get(h.getLevel().getServer());
        admissions.queue(f.player, f.frequency, h.getLevel(), h.absolutePos(target), Direction.NORTH);
        h.assertTrue(admissions.inspect(h.getLevel(), h.absolutePos(target), f.player.getUUID()) == null,
                "FE admission created without AppFlux");
        h.assertTrue(OverloadedFrequencyCardItem.getData(card).autoConnectEnergy(), "saved preference was erased");
        h.succeed();
    }

    private static IPayloadContext context(MenuPlayer player) {
        return (IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield java.util.concurrent.CompletableFuture.completedFuture(null); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        AdvancedWirelessOverloadedControllerBlockEntity host = h.getBlockEntity(C);
        var player = new MenuPlayer(h.getLevel());
        var pos = h.absolutePos(C);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
        var frequency = WirelessFrequencyManager.get().createFrequency(player, "No AppFlux QA", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
        host.setFrequency(frequency.getId());
        return new Fixture(host, player, frequency.getId());
    }
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity host, MenuPlayer player, int frequency) {}
    private static final class MenuPlayer extends FakePlayer {
        private int nextId = 30;
        MenuPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "NoAppFluxQA")); }
        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
            int id = ++nextId;
            containerMenu = provider.createMenu(id, getInventory(), this);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
            try { writer.accept(buffer); } finally { buffer.release(); }
            return OptionalInt.of(id);
        }
    }
}
