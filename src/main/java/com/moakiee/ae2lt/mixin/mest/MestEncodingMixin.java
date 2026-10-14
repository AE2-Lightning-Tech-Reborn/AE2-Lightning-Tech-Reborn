package com.moakiee.ae2lt.mixin.mest;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import com.lhy.mest.integration.MestEncodingHelper;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.client.tianshu.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both MEST viewer adapters converge here, preserving their native validation and feedback. */
@Mixin(value = MestEncodingHelper.class, remap = false)
public abstract class MestEncodingMixin {
    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$encode(MESTMenu outer, MestEncodingHelper.RecipeKind kind, RecipeHolder<?> recipe,
            List<List<GenericStack>> crafting, List<List<GenericStack>> inputs, List<GenericStack> outputs,
            Predicate<ItemStack> visible, CallbackInfo ci) {
        var panel = MestTianshuClient.recipePanel(outer);
        if (panel == null || !(panel.source.getMenu() instanceof TianshuPatternEncodingTermMenu menu)) return;
        ci.cancel();
        MestTianshuClient.input(menu, () -> {
            TianshuRecipeTransferContext.clear(menu);
            if (menu.tianshuMode == TianshuEncodingMode.CLOSED_LOOP) {
                if (!outputs.isEmpty() && menu.markClosedLoopPrimaryOutput(GenericStack.wrapInItemStack(outputs.getFirst()))) menu.autoFillClosedLoop();
                return null;
            }
            menu.resetProcessingEncoding();
            if (recipe != null) TianshuRecipeTransferContext.captureVanillaRecipe(menu, recipe, "", List.of());
            if (kind == MestEncodingHelper.RecipeKind.CRAFTING) EncodingHelper.encodeCraftingRecipe(menu, recipe, crafting, visible);
            else EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
            if (net.minecraft.client.gui.screens.Screen.hasAltDown()) menu.encodeAndUploadDirectly();
            return null;
        });
    }
}
