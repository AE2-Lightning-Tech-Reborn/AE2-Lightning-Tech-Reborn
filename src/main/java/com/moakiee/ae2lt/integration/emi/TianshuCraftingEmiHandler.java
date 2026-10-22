package com.moakiee.ae2lt.integration.emi;

import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import dev.emi.emi.api.widget.Widget;

/** The server resolves workstation recipes; only native recipe IDs or anvil input templates are sent. */
public final class TianshuCraftingEmiHandler<M extends TianshuCraftingTermMenu> extends EmiUseCraftingRecipeHandler<M> {
    public TianshuCraftingEmiHandler(Class<M> menuClass) { super(menuClass); }
    private static Recipe<?> backing(EmiRecipe recipe) {
        var nativeRecipe=recipe.getBackingRecipe();
        if(nativeRecipe!=null)return nativeRecipe;
        var level=Minecraft.getInstance().level;
        return level==null || recipe.getId()==null ? null : level.getRecipeManager().byKey(recipe.getId()).orElse(null);
    }
    private static boolean anvil(EmiRecipe recipe) { return recipe.getCategory().getId().toString().equals("minecraft:anvil"); }
    private static boolean workstation(EmiRecipe recipe) {
        var nativeRecipe=backing(recipe);
        return nativeRecipe instanceof SmithingRecipe || nativeRecipe instanceof StonecutterRecipe || anvil(recipe);
    }
    private static ItemStack input(EmiRecipe recipe,int slot) {
        if(slot>=recipe.getInputs().size())return ItemStack.EMPTY;
        return recipe.getInputs().get(slot).getEmiStacks().stream().map(stack->stack.getItemStack())
                .filter(stack->!stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
    }
    private static TianshuCraftingTermMenu.WorkRecipeAvailability availability(TianshuCraftingTermMenu menu,EmiRecipe recipe) {
        return anvil(recipe) ? menu.getAnvilRecipeAvailability(input(recipe,0),input(recipe,1))
                : menu.getWorkRecipeAvailability(backing(recipe));
    }
    @Override public boolean supportsRecipe(EmiRecipe recipe) { return workstation(recipe) || super.supportsRecipe(recipe); }
    @Override public boolean canCraft(EmiRecipe recipe,EmiCraftContext<M> context) {
        if(!context.getScreenHandler().canUseWorkstations())return false;
        return workstation(recipe) ? availability(context.getScreenHandler(),recipe).canTransfer() : super.canCraft(recipe,context);
    }
    @Override public boolean craft(EmiRecipe recipe,EmiCraftContext<M> context) {
        if(!canCraft(recipe,context))return false;
        var menu=context.getScreenHandler();
        if(!workstation(recipe)) { menu.prepareCraftingTransfer();return super.craft(recipe,context); }
        if(anvil(recipe))menu.fillAnvilRecipe(input(recipe,0),input(recipe,1),Screen.hasControlDown());
        else menu.fillWorkRecipe(backing(recipe).getId().toString(),Screen.hasControlDown());
        Minecraft.getInstance().setScreen(context.getScreen());
        return true;
    }
    @Override public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe,EmiCraftContext<M> context) {
        if(!workstation(recipe))return super.getTooltip(recipe,context);
        var available=availability(context.getScreenHandler(),recipe);
        if(available.missing().totalSize()==0)return List.of();
        var lines=appeng.integration.modules.jeirei.TransferHelper.createCraftingTooltip(available.missing(),Screen.hasControlDown());
        return lines.stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList();
    }
    @Override public void render(EmiRecipe recipe,EmiCraftContext<M> context,List<Widget> widgets,GuiGraphics graphics) {
        if(!workstation(recipe))super.render(recipe,context,widgets,graphics);
    }
}
