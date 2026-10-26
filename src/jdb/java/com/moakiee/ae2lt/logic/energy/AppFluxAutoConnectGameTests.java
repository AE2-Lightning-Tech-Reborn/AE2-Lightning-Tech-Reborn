package com.moakiee.ae2lt.logic.energy;

import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("ae2lt_appflux_present")
@PrefixGameTestTemplate(false)
public final class AppFluxAutoConnectGameTests {
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void installedAppFluxKeepsRecipeAndIndependentAutoFeControl(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("appflux") && OverloadedFrequencyCardItem.isAutoConnectEnergyAvailable(), "AppFlux unavailable in present fixture");
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(holder -> holder.value()
                .getResultItem(h.getLevel().registryAccess()).is(ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().asItem())).toList();
        h.assertTrue(recipes.size() == 1 && recipes.getFirst().value().getIngredients().stream()
                .anyMatch(i -> i.test(new ItemStack(ModBlocks.OVERLOADED_POWER_SUPPLY.get()))), "wrong conditional recipe");
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "AppFluxQA"));
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        OverloadedFrequencyCardItem.bindFrequency(card, 123, h.getLevel().dimension(), BlockPos.ZERO, player.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        var context = (IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield java.util.concurrent.CompletableFuture.completedFuture(null); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var toggleFe = new ToggleFrequencyCardAutoConnectPacket(Optional.of(InteractionHand.MAIN_HAND), false, true);
        ToggleFrequencyCardAutoConnectPacket.handle(toggleFe, context);
        h.assertTrue(OverloadedFrequencyCardItem.getData(card).autoConnectEnergy() && !OverloadedFrequencyCardItem.getData(card).autoConnect(), "FE enable changed ME or failed");
        h.assertTrue(OverloadedFrequencyCardItem.findAutoConnectEnergyCard(player).isPresent(), "enabled FE card not selected with AppFlux");
        ToggleFrequencyCardAutoConnectPacket.handle(ToggleFrequencyCardAutoConnectPacket.forHand(InteractionHand.MAIN_HAND), context);
        ToggleFrequencyCardAutoConnectPacket.handle(toggleFe, context);
        h.assertTrue(!OverloadedFrequencyCardItem.getData(card).autoConnectEnergy() && OverloadedFrequencyCardItem.getData(card).autoConnect(), "FE disable changed ME or failed");
        h.succeed();
    }
}
