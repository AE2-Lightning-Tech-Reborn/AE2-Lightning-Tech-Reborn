package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.logic.world.PlayerWorldActions;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FirmamentCoreUpgradeHandler {
    private FirmamentCoreUpgradeHandler() {}

    // Sneaking with an item bypasses normal block use, so handle the upgrade interaction here.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PlayerWorldActions.isCheckingInteraction() || event.isCanceled()
                || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE
                || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof FirmamentConversionCoreBlockEntity core)) {
            return;
        }
        event.setCanceled(true);
        if (core.isAdvanced()) {
            event.setCancellationResult(InteractionResult.FAIL);
        } else if (event.getLevel().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else {
            event.setCancellationResult(core.tryUpgrade(event.getEntity(), event.getHand())
                    ? InteractionResult.CONSUME : InteractionResult.FAIL);
        }
    }
}
