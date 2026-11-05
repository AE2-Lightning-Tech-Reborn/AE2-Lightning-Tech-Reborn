package com.moakiee.ae2lt.mixin;

import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface PigmeeDimensionServerAccessor {
    @Accessor("storageSource") LevelStorageSource.LevelStorageAccess ae2lt$pigmeeStorageSource();
    @Accessor("executor") Executor ae2lt$pigmeeExecutor();
}
