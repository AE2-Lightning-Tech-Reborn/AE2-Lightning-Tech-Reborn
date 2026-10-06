package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import com.moakiee.thunderbolt.core.crafting.planner.CraftGraph;
import com.moakiee.thunderbolt.core.crafting.planner.CraftInput;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPattern;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPlannerV2;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.junit.jupiter.api.Test;

/** Real encoded macro construction; no running server or provider is required. */
public class ClosedLoopAdaptiveSeedPatternTest {
    private static ClosedLoopPatternItem item;

    @Test
    void configuredThirtyTwoPublishesOnlyTheMinimumStartupRequirement() throws Exception {
        var fixture = fixture(32);
        var macro = fixture.macro();
        assertEquals(32, macro.closedLoopPayload().executionSeedMultiplier());
        assertEquals(8, macro.getInputs()[0].getPossibleInputs()[0].amount());
        assertEquals(Map.of(fixture.seed(), 8L), macro.totalReusableSeedRequirements());
        var task = (com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern)
                macro.expandPatternFirings(1000).keySet().iterator().next();
        assertEquals(8, task.initialSeed().get(fixture.seed()));
        assertEquals(8, task.inputSeed().get(fixture.seed()));
    }

    @Test
    void aThousandCyclesCanStartWithOneSeedSetDespiteTheConfiguredMaximum() throws Exception {
        var fixture = fixture(32);
        var macro = fixture.macro();
        var source = macro.reusableStockSource();
        var graph = CraftGraph.<AEKey>builder()
                .pattern(new CraftPattern<>(fixture.seed(), 8, List.of(
                        CraftInput.returnedFrom(fixture.seed(),
                                macro.getInputs()[0].getPossibleInputs()[0].amount(), source),
                        CraftInput.of(fixture.fuel(), 1)), macro))
                .stock(fixture.fuel(), 1000)
                .reusableStock(source.storageScope(), fixture.seed(), 8)
                .reusableStockRoute(source, fixture.seed(), List.of(fixture.seed()))
                .build();
        var plan = CraftPlannerV2.plan(graph, fixture.seed(), 8000);
        assertTrue(plan.feasible(), () -> "missing=" + plan.missing());
        assertEquals(8, plan.usedReusableStock().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(1000, plan.firings().values().stream().mapToLong(Long::longValue).sum());
    }

    public static Fixture fixture(int multiplier) throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var keyRegistry = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("registry");
        keyRegistry.setAccessible(true);
        if (keyRegistry.get(null) == null) {
            var registry = new net.minecraft.core.MappedRegistry<appeng.api.stacks.AEKeyType>(
                    appeng.api.stacks.AEKeyType.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable());
            for (var type : List.of(appeng.api.stacks.AEKeyType.items(),
                    appeng.api.stacks.AEKeyType.fluids(),
                    com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE.getType())) {
                Registry.register(registry, type.getId(), type);
            }
            registry.freeze();
            keyRegistry.set(null, registry);
        }
        if (item == null) {
            // Plain JUnit has no NeoForge registration phase. Temporarily open only the item
            // registry to install this fixture, then restore its frozen state immediately.
            var frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
            var holders = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
            frozen.setAccessible(true);
            holders.setAccessible(true);
            frozen.setBoolean(BuiltInRegistries.ITEM, false);
            holders.set(BuiltInRegistries.ITEM, new java.util.IdentityHashMap<>());
            try {
                var missingContent = ResourceLocation.fromNamespaceAndPath("ae2", "missing_content");
                if (!BuiltInRegistries.ITEM.containsKey(missingContent)) {
                    Registry.register(BuiltInRegistries.ITEM, missingContent, new Item(new Item.Properties()));
                }
                item = Registry.register(BuiltInRegistries.ITEM,
                        ResourceLocation.fromNamespaceAndPath("ae2lt_test", "adaptive_loop"),
                        new ClosedLoopPatternItem(new Item.Properties()));
            } finally {
                BuiltInRegistries.ITEM.freeze();
            }
        }
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var seed = AEItemKey.of(Items.WHEAT_SEEDS);
        var fuel = AEItemKey.of(Items.GOLD_INGOT);
        var raw = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            @Override public IInput[] getInputs() { return new IInput[] {input(seed, 8), input(fuel, 1)}; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(seed, 16)); }
        };
        var payload = new ClosedLoopPatternPayload(List.of(new ClosedLoopMemberPattern(
                SourcePatternSnapshot.fromItemStack(new ItemStack(Items.PAPER), registries), 1)),
                List.of(new GenericStack(seed, 8)), List.of(new GenericStack(fuel, 1)),
                List.of(new GenericStack(seed, 8)), multiplier, 1, true);
        var unsafeType = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeType.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var level = (Level) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), ServerLevel.class);
        for (Class<?> type = Level.class; type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (RegistryAccess.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    field.set(level, registries);
                }
            }
        }
        var levelData = Level.class.getDeclaredField("levelData");
        levelData.setAccessible(true);
        levelData.set(level, Proxy.newProxyInstance(WritableLevelData.class.getClassLoader(),
                new Class<?>[] {WritableLevelData.class}, (p, method, args) -> {
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }));
        var definition = AEItemKey.of(item.createStack(payload, registries));
        return new Fixture(new Ae2ClosedLoopPatternDetails(definition, payload, level,
                UUID.randomUUID(), ignored -> Map.of(seed, 8L), List.of(raw)), raw, seed, fuel, level);
    }

    public record Fixture(Ae2ClosedLoopPatternDetails macro, IPatternDetails raw,
                          AEKey seed, AEKey fuel, Level level) { }

    private static IPatternDetails.IInput input(AEKey key, long amount) {
        return new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(key, amount)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey actual, Level level) { return key.equals(actual); }
            @Override public AEKey getRemainingKey(AEKey actual) { return null; }
        };
    }
}
