package com.moakiee.ae2lt.mixin.ae2wtlib;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import appeng.menu.locator.MenuLocator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value = CraftingTerminalHandler.class, remap = false)
public interface CraftingTerminalHandlerAccessor {
    @Invoker("<init>") static CraftingTerminalHandler ae2lt$create(Player player) { throw new AssertionError(); }
    @Accessor("craftingTerminal") void ae2lt$setTerminal(ItemStack stack);
    @Accessor("menuHost") void ae2lt$setHost(WTMenuHost host);
    @Accessor("locator") void ae2lt$setLocator(MenuLocator locator);
}
