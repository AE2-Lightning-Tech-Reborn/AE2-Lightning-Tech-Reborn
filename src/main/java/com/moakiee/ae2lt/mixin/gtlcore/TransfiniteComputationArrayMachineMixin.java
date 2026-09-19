package com.moakiee.ae2lt.mixin.gtlcore;

import java.lang.reflect.Method;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingLockSource;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.ae2lt.integration.gtlcore.TransfiniteExclusivePlanningUi;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.DefaultCraftingAlgorithmProviderState;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;

/**
 * Transfinite is not an AE grid node. The exclusive selection lives here, is
 * persisted through GTL custom NBT, and is shown under the CPU-selection button.
 * The ME interface mixin then publishes the same lock onto the grid.
 */
@Pseudo
@Mixin(
        targets = "org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine",
        remap = false)
public abstract class TransfiniteComputationArrayMachineMixin implements ExclusiveCraftingLockSource {
    @Unique
    private static final String AE2LT$ALGORITHM_TAG = "CraftingAlgorithmProvider";

    @Unique
    private final DefaultCraftingAlgorithmProviderState ae2lt$algorithmProvider =
            new DefaultCraftingAlgorithmProviderState(
                    ThunderboltV2PlanningEngine.ID,
                    ExclusiveCraftingPlanning.ownedAlgorithms(),
                    0,
                    this::ae2lt$algorithmChanged);

    @Unique
    private void ae2lt$algorithmChanged() {
        invokeNoArg("markDirty");
    }

    @Override
    public boolean ae2lt$isExclusiveLockActive() {
        return Boolean.TRUE.equals(invokeNoArg("isFormed"));
    }

    @Override
    public ResourceLocation ae2lt$getExclusiveAlgorithm() {
        return ExclusiveCraftingPlanning.normalize(ae2lt$algorithmProvider.getSelectedAlgorithm());
    }

    @Override
    public int ae2lt$getLockCpuPriority() {
        return 0;
    }

    @Override
    public int ae2lt$getLockProviderPriority() {
        return ae2lt$algorithmProvider.getPriority();
    }

    @Override
    public void ae2lt$cycleExclusiveAlgorithm() {
        var next = ExclusiveCraftingPlanning.cycle(ae2lt$algorithmProvider.getSelectedAlgorithm());
        ae2lt$algorithmProvider.setSelection(
                new CraftingAlgorithmSelection(next, ae2lt$algorithmProvider.getPriority()));
    }

    @Override
    public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) {
        if (selection == null) {
            return;
        }
        ae2lt$algorithmProvider.setSelection(
                new CraftingAlgorithmSelection(
                        ExclusiveCraftingPlanning.normalize(selection.algorithmId()),
                        selection.priority()));
    }

    @Inject(method = "saveCustomPersistedData", at = @At("RETURN"))
    private void ae2lt$saveExclusiveAlgorithm(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        var algorithmTag = new CompoundTag();
        ae2lt$algorithmProvider.writeToNBT(algorithmTag);
        tag.put(AE2LT$ALGORITHM_TAG, algorithmTag);
    }

    @Inject(method = "loadCustomPersistedData", at = @At("RETURN"))
    private void ae2lt$loadExclusiveAlgorithm(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AE2LT$ALGORITHM_TAG)) {
            ae2lt$algorithmProvider.readFromNBT(tag.getCompound(AE2LT$ALGORITHM_TAG));
        }
        var exclusive = ExclusiveCraftingPlanning.normalize(ae2lt$algorithmProvider.getSelectedAlgorithm());
        if (!exclusive.equals(ae2lt$algorithmProvider.getSelectedAlgorithm())) {
            ae2lt$algorithmProvider.setSelection(
                    new CraftingAlgorithmSelection(exclusive, ae2lt$algorithmProvider.getPriority()));
        }
    }

    @Inject(method = "createUIWidget", at = @At("RETURN"))
    private void ae2lt$addExclusiveAlgorithmSwitch(CallbackInfoReturnable<Object> cir) {
        TransfiniteExclusivePlanningUi.attach(
                cir.getReturnValue(),
                Boolean.TRUE.equals(invokeNoArg("isRemote")),
                this::ae2lt$addAlgorithmText,
                this::ae2lt$cycleExclusiveAlgorithm);
    }

    @Unique
    private void ae2lt$addAlgorithmText(List<Component> textList) {
        textList.add(TransfiniteExclusivePlanningUi.clickableAlgorithmLine(
                Component.translatable(
                        ExclusiveCraftingPlanning.translationKey(ae2lt$getExclusiveAlgorithm()))));
    }

    @Unique
    private Object invokeNoArg(String name) {
        try {
            Method method = findNoArg(getClass(), name);
            return method.invoke(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static Method findNoArg(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
