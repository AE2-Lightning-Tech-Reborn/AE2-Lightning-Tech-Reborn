package com.moakiee.ae2lt.mixin.appflux;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyCapCache", remap = false)
public interface EnergyCapCacheAccessor {
    @Accessor("world") ServerLevel ae2lt$world();
    @Accessor("pos") BlockPos ae2lt$pos();
}
