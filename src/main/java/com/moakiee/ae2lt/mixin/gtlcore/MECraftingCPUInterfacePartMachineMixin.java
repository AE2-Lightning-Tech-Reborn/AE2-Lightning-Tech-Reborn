package com.moakiee.ae2lt.mixin.gtlcore;

import java.lang.reflect.Method;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

import net.minecraft.resources.ResourceLocation;

import com.mojang.logging.LogUtils;

import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingLockSource;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.ae2lt.integration.gtlcore.TransfiniteExclusivePlanningAccess;
import com.moakiee.thunderbolt.api.crafting.ConfigurableCraftingAlgorithmProvider;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmProvider;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;

/**
 * Transfinite's ME interface is the grid node. Thunderbolt only snapshots
 * {@link CraftingAlgorithmProvider} services, and exclusive planning walks the
 * same node for {@link ExclusiveCraftingLockSource}.
 */
@Pseudo
@Mixin(
        targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftingCPUInterfacePartMachine",
        remap = false)
public abstract class MECraftingCPUInterfacePartMachineMixin
        implements CraftingAlgorithmProvider, ConfigurableCraftingAlgorithmProvider, ExclusiveCraftingLockSource {
    @Unique
    private static final Logger AE2LT$LOG = LogUtils.getLogger();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$registerAlgorithmProvider(CallbackInfo ci) {
        try {
            Object mainNode = invokeNoArg("getMainNode");
            if (mainNode == null) {
                return;
            }
            Method addService = findAddService(mainNode.getClass());
            addService.invoke(mainNode, CraftingAlgorithmProvider.class, this);
        } catch (Throwable unavailable) {
            AE2LT$LOG.warn("Could not register Transfinite crafting algorithm provider", unavailable);
        }
    }

    @Unique
    private static Method findAddService(Class<?> type) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"addService".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters[0] == Class.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException("addService(Class, ?) on " + type.getName());
    }

    @Unique
    @Nullable
    private ExclusiveCraftingLockSource ae2lt$formedController() {
        return TransfiniteExclusivePlanningAccess.formedController(this);
    }

    @Unique
    private boolean ae2lt$nodeActive() {
        try {
            Object mainNode = invokeNoArg("getMainNode");
            if (mainNode == null) {
                return false;
            }
            Object active = mainNode.getClass().getMethod("isActive").invoke(mainNode);
            return Boolean.TRUE.equals(active);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean ae2lt$isExclusiveLockActive() {
        var controller = ae2lt$formedController();
        return controller != null && controller.ae2lt$isExclusiveLockActive() && ae2lt$nodeActive();
    }

    @Override
    public ResourceLocation ae2lt$getExclusiveAlgorithm() {
        var controller = ae2lt$formedController();
        return controller == null
                ? ExclusiveCraftingPlanning.normalize(null)
                : controller.ae2lt$getExclusiveAlgorithm();
    }

    @Override
    public int ae2lt$getLockCpuPriority() {
        var controller = ae2lt$formedController();
        return controller == null ? Integer.MIN_VALUE : controller.ae2lt$getLockCpuPriority();
    }

    @Override
    public int ae2lt$getLockProviderPriority() {
        var controller = ae2lt$formedController();
        return controller == null ? Integer.MIN_VALUE : controller.ae2lt$getLockProviderPriority();
    }

    @Override
    public void ae2lt$cycleExclusiveAlgorithm() {
        var controller = ae2lt$formedController();
        if (controller != null) {
            controller.ae2lt$cycleExclusiveAlgorithm();
        }
    }

    @Override
    public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) {
        var controller = ae2lt$formedController();
        if (controller != null) {
            controller.ae2lt$setExclusiveSelection(selection);
        }
    }

    @Override
    public ResourceLocation getProvidedAlgorithm() {
        return ThunderboltV2PlanningEngine.ID;
    }

    @Override
    public List<ResourceLocation> getProvidedAlgorithms() {
        return ExclusiveCraftingPlanning.ownedAlgorithms();
    }

    @Override
    public ResourceLocation getSelectedAlgorithm() {
        return ae2lt$isExclusiveLockActive()
                ? ae2lt$getExclusiveAlgorithm()
                : CraftingPlanningEngines.VANILLA_ID;
    }

    @Override
    public int getPriority() {
        var controller = ae2lt$formedController();
        return controller == null ? 0 : controller.ae2lt$getLockProviderPriority();
    }

    @Override
    public void setSelection(CraftingAlgorithmSelection selection) {
        ae2lt$setExclusiveSelection(selection);
    }

    @Unique
    @Nullable
    private Object invokeNoArg(String name) {
        try {
            Class<?> current = getClass();
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(this);
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }
}
