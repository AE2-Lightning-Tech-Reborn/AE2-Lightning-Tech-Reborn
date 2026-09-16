package com.moakiee.ae2lt.mixin.client;

import java.util.Map;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MenuScreens.class)
public interface MenuScreensAccessor {
    @Accessor("SCREENS")
    static Map<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>> ae2lt$getScreens() {
        throw new AssertionError();
    }
}
