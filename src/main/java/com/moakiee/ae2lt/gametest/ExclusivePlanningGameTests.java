package com.moakiee.ae2lt.gametest;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingLockSource;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.PlanningRequest;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class ExclusivePlanningGameTests {
    private ExclusivePlanningGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v2AcceptsNodeLessRequesterOnlyOnLockedGrid(GameTestHelper helper) {
        var lock = new TestLock();
        var service = proxy(ICraftingService.class, (proxy, method, args) -> null);
        var node = proxy(IGridNode.class, (proxy, method, args) -> switch (method.getName()) {
            case "getService" -> null;
            case "getOwner" -> lock;
            default -> null;
        });
        var grid = proxy(IGrid.class, (proxy, method, args) -> switch (method.getName()) {
            case "getMachines" -> Set.of();
            case "getNodes" -> List.of(node);
            case "getCraftingService" -> service;
            default -> null;
        });
        ICraftingSimulationRequester requester = () -> null;
        var request = new PlanningRequest(helper.getLevel(), service, null,
                AEItemKey.of(Items.STICK), 1L, null, requester);
        var engine = ThunderboltV2PlanningEngine.INSTANCE;

        helper.assertTrue(engine.check(grid, request), "Exclusive V2 must accept a node-less requester");
        lock.active = false;
        helper.assertTrue(!engine.check(grid, request), "Unlocked V2 must retain Thunderbolt's node check");
        helper.succeed();
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static final class TestLock implements ExclusiveCraftingLockSource {
        private boolean active = true;

        @Override
        public boolean ae2lt$isExclusiveLockActive() {
            return active;
        }

        @Override
        public ResourceLocation ae2lt$getExclusiveAlgorithm() {
            return ThunderboltV2PlanningEngine.ID;
        }

        @Override
        public int ae2lt$getLockCpuPriority() {
            return 0;
        }

        @Override
        public int ae2lt$getLockProviderPriority() {
            return 0;
        }

        @Override
        public void ae2lt$cycleExclusiveAlgorithm() {
        }

        @Override
        public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) {
        }
    }
}
