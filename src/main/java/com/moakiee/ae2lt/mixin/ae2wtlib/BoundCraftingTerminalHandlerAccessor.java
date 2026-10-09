package com.moakiee.ae2lt.mixin.ae2wtlib;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=CraftingTerminalHandler.class,remap=false)
public interface BoundCraftingTerminalHandlerAccessor {
    @Invoker("<init>") static CraftingTerminalHandler create(Player player) {throw new AssertionError("Mixin not applied");}
    @Accessor("craftingTerminal") void setTerminal(ItemStack stack);
}
