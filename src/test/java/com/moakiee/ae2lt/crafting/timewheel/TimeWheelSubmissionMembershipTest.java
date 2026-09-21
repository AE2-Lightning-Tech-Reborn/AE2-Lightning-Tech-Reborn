package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.api.crafting.cpu.ExtendedCraftingCpuCluster;
import com.moakiee.thunderbolt.core.crafting.cpu.DynamicCraftingCpuClusterIndex;
import com.moakiee.thunderbolt.mixin.ae2.crafting.ExtendedCraftingCpuServiceMixin;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeWheelSubmissionMembershipTest {
    @ParameterizedTest
    @CsvSource({
            "true,true,true,true", "true,false,true,true", "true,true,false,true", "true,false,false,true",
            "false,true,true,true", "false,false,true,true", "false,true,false,true", "false,false,false,true",
            "true,true,true,false", "false,true,true,false"})
    void cachedSelectionMustStillBelongToTheSubmittingGrid(
            boolean manual, boolean sameGrid, boolean published, boolean enoughCapacity) throws Exception {
        boolean accepted = sameGrid && published && enoughCapacity;
        long capacity = enoughCapacity ? 1024 : 4;
        var types = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("registry");
        types.setAccessible(true);
        var previous = types.get(null);
        if (previous == null) types.set(null, ForgeKeyTypeTestRegistry.create(LightningKey.HIGH_VOLTAGE.getType()));
        try {
            long[] extracted = {0};
            var storage = proxy(MEStorage.class, (name, args) -> {
                if (name.equals("extract")) {
                    if (args[2] == Actionable.MODULATE) extracted[0] += (long) args[1];
                    return args[1];
                }
                return null;
            });
            var storageService = proxy(IStorageService.class,
                    (name, args) -> name.equals("getInventory") ? storage : null);
            var index = new DynamicCraftingCpuClusterIndex<IGridNode, ExtendedCraftingCpuCluster>();
            var crafting = new ICraftingService[1];
            var grid = proxy(IGrid.class, (name, args) -> switch (name) {
                case "getCraftingService" -> crafting[0];
                case "getStorageService" -> storageService;
                default -> null;
            });
            crafting[0] = new CraftingService(grid, storageService,
                    proxy(IEnergyService.class, (name, args) -> null)) {
                @Override public boolean hasCpu(ICraftingCPU cpu) {
                    return index.clusters().stream().anyMatch(cluster -> cluster.containsCpu(cpu));
                }
            };
            var otherGrid = proxy(IGrid.class, (name, args) -> null);
            var source = IActionSource.empty();
            var host = proxy(TimeWheelCraftingCpuPoolHost.class, (name, args) -> switch (name) {
                case "isCpuActive" -> true;
                case "getGrid" -> sameGrid ? grid : otherGrid;
                case "getActionSource" -> source;
                case "getSelectionMode" -> CpuSelectionMode.ANY;
                default -> null;
            });
            var pool = new TimeWheelCraftingCpuPool(host, capacity, 0, 1, false);
            if (published) {
                index.addProvider(proxy(IGridNode.class, (name, args) -> null));
                index.refresh(node -> pool, cluster -> {});
            }
            var used = new KeyCounter();
            used.add(LightningKey.HIGH_VOLTAGE, 1);
            // Minimal admission fixture: no execution/tracker Mixin is needed to prove whether
            // an obsolete menu reference can remove initial materials and create a virtual CPU.
            var plan = new CraftingPlan(new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 1),
                    8, false, false, used, new KeyCounter(), new KeyCounter(), Map.of());
            // Invoke the actual dependency's submission callbacks, retaining its candidate index
            // and selection behavior. This is not a full Mixin-transformed game startup test.
            var hook = new SubmissionHooks();
            setHookField(hook, "grid", grid);
            setHookField(hook, "thunderbolt$extendedCpuClusterIndex", index);
            var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("submitJob", true);
            String name = manual ? "thunderbolt$submitToExplicitExtendedCpuCluster"
                    : "thunderbolt$submitToAutomaticExtendedCpuCluster";
            var method = java.util.Arrays.stream(ExtendedCraftingCpuServiceMixin.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            method.setAccessible(true);
            if (manual) method.invoke(hook, plan, null, pool, true, source, callback);
            else method.invoke(hook, plan, null, null, true, source, callback, null, new MutableObject<>());
            var result = callback.getReturnValue();
            assertEquals(accepted, result != null && result.successful());
            assertEquals(accepted ? 1 : 0, extracted[0]);
            assertEquals(accepted ? 1 : 0, pool.getActiveCpus().size());
            assertEquals(accepted ? capacity - 8 : capacity, pool.getAvailableStorage());
            if (!accepted && result != null) {
                assertEquals(sameGrid && published ? CraftingSubmitErrorCode.CPU_TOO_SMALL
                        : CraftingSubmitErrorCode.CPU_OFFLINE, result.errorCode());
            }
        } finally {
            types.set(null, previous);
        }
    }

    private static final class SubmissionHooks extends ExtendedCraftingCpuServiceMixin {
        @Override public void addLink(CraftingLink link) { }
    }

    private static void setHookField(SubmissionHooks hook, String name, Object value) throws Exception {
        var field = ExtendedCraftingCpuServiceMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(hook, value);
    }

    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            var result = call.apply(method.getName(), args);
            if (result != null) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == Optional.class) return Optional.empty();
            return null;
        }));
    }
}
