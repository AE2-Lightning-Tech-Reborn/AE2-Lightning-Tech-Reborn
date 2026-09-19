package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPoolProvider;

/**
 * GTLCore HEAD-cancels {@code CraftingService.onServerEndTick} on three of four
 * ticks, which also skips Thunderbolt's FIELD injectors. Mixin 0.8.5 prepares
 * every HEAD injector against the original first insn, then injects
 * low-to-high priority with {@code insertBefore} that original insn, so the
 * earlier mixin is outermost. This mixin must stay below GTLCore's default
 * 1000: TimeWheel ticks first, GTL may then cancel the original. Waiting keys
 * are left to Thunderbolt's FIELD injector on uncancelled ticks so AE2's
 * snapshot/rebuild still notifies watchers. The pool's same-tick guard no-ops
 * Thunderbolt's FIELD visit on that remaining tick.
 */
@Mixin(value = CraftingService.class, remap = false, priority = 900)
public abstract class TimeWheelCraftingServiceMixin {
    @Unique
    private final Set<IGridNode> ae2lt$timeWheelProviderNodes =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private long ae2lt$lastTimeWheelCraftingLogicChangeTick;

    @Unique
    private boolean ae2lt$lastTimeWheelCraftingLogicChangeTickInitialized;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private IEnergyService energyGrid;

    @Shadow
    private boolean updateList;

    @Shadow
    private long lastProcessedCraftingLogicChangeTick;

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void ae2lt$tickTimeWheelPools(CallbackInfo ci) {
        var seenPools = new IdentityHashMap<TimeWheelCraftingCpuPool, Boolean>();
        boolean listChanged = false;
        long latest = Long.MIN_VALUE;
        for (var node : List.copyOf(this.ae2lt$timeWheelProviderNodes)) {
            var pool = ae2lt$resolveTimeWheelPool(node);
            if (pool == null || seenPools.put(pool, Boolean.TRUE) != null) {
                continue;
            }
            latest = Math.max(latest, pool.tickCraftingLogic(
                    this.energyGrid, (CraftingService) (Object) this));
            if (pool.consumeCpuListChanged()) {
                listChanged = true;
            }
        }
        if (listChanged) {
            this.updateList = true;
        }
        if (listChanged
                || !this.ae2lt$lastTimeWheelCraftingLogicChangeTickInitialized
                || latest != this.ae2lt$lastTimeWheelCraftingLogicChangeTick) {
            this.ae2lt$lastTimeWheelCraftingLogicChangeTickInitialized = true;
            this.ae2lt$lastTimeWheelCraftingLogicChangeTick = latest;
            this.lastProcessedCraftingLogicChangeTick = -1L;
        }
    }

    @Inject(method = "addNode", at = @At("TAIL"))
    private void ae2lt$indexTimeWheelNode(IGridNode gridNode, CompoundTag savedData, CallbackInfo ci) {
        if (ae2lt$getTimeWheelProvider(gridNode) != null) {
            this.ae2lt$timeWheelProviderNodes.add(gridNode);
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void ae2lt$forgetTimeWheelNode(IGridNode gridNode, CallbackInfo ci) {
        this.ae2lt$timeWheelProviderNodes.remove(gridNode);
    }

    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void ae2lt$reindexTimeWheelNodes(CallbackInfo ci) {
        this.ae2lt$timeWheelProviderNodes.clear();
        for (var machineClass : this.grid.getMachineClasses()) {
            for (var node : this.grid.getMachineNodes(machineClass)) {
                if (ae2lt$getTimeWheelProvider(node) != null) {
                    this.ae2lt$timeWheelProviderNodes.add(node);
                }
            }
        }
    }

    @Unique
    @Nullable
    private static TimeWheelCraftingCpuPoolProvider ae2lt$getTimeWheelProvider(IGridNode node) {
        var service = node.getService(TimeWheelCraftingCpuPoolProvider.class);
        if (service != null) {
            return service;
        }
        return node.getOwner() instanceof TimeWheelCraftingCpuPoolProvider provider ? provider : null;
    }

    @Unique
    @Nullable
    private static TimeWheelCraftingCpuPool ae2lt$resolveTimeWheelPool(IGridNode node) {
        var provider = ae2lt$getTimeWheelProvider(node);
        return provider != null ? provider.getTimeWheelCraftingCpuPool() : null;
    }
}
