package com.moakiee.ae2lt.debug;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.moakiee.thunderbolt.core.crafting.batch.TickProviderDispatchSchedule;
import com.moakiee.thunderbolt.core.crafting.support.CraftingProviderRevision;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises real Mixin-transformed AE2 provider mounting, without changing a saved network. */
@GameTestHolder("ae2lt_provider_cache")
@PrefixGameTestTemplate(false)
public final class ProviderCacheGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void globalRefreshInvalidatesWithinTheSameTick(GameTestHelper helper) {
        var service = new CraftingService(proxy(IGrid.class), proxy(IStorageService.class), proxy(IEnergyService.class));
        require(service instanceof CraftingProviderRevision, "CraftingService revision bridge was not applied");
        var state = (CraftingProviderRevision) service;
        var pattern = new Pattern(AEItemKey.of(Items.STONE));
        var first = new Provider(pattern);
        var second = new Provider(pattern);
        var schedule = new TickProviderDispatchSchedule();
        long tick = TickHandler.instance().getCurrentTick();
        schedule.beginTick(tick);
        require(copy(schedule.candidates(service, pattern, pattern)).isEmpty(), "unexpected initial providers");
        long before = state.thunderbolt$getCraftingProviderRevision();
        service.addGlobalCraftingProvider(first);
        require(state.thunderbolt$getCraftingProviderRevision() != before, "add did not change revision");
        require(copy(schedule.candidates(service, pattern, pattern)).equals(List.of(first)), "same-tick add stayed cached");
        schedule.recordFailure(pattern, first);
        before = state.thunderbolt$getCraftingProviderRevision();
        service.refreshGlobalCraftingProvider(first);
        require(state.thunderbolt$getCraftingProviderRevision() != before, "refresh did not change revision");
        require(copy(schedule.candidates(service, pattern, pattern)).equals(List.of(first)), "refresh retained a stale rejection");
        service.removeGlobalCraftingProvider(first);
        service.addGlobalCraftingProvider(second);
        require(copy(schedule.candidates(service, pattern, pattern)).equals(List.of(second)), "replacement returned disconnected provider");
        require(TickHandler.instance().getCurrentTick() == tick, "test must mutate several times in one tick");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nodeAndPatternChangesInvalidateRetainedSnapshots(GameTestHelper helper) throws Exception {
        var service = new CraftingService(proxy(IGrid.class), proxy(IStorageService.class), proxy(IEnergyService.class));
        var field = CraftingService.class.getDeclaredField("craftingProviders");
        field.setAccessible(true);
        var catalog = (NetworkCraftingProviders) field.get(service);
        require(catalog instanceof CraftingProviderRevision, "NetworkCraftingProviders mutation hook was not applied");
        var oldPattern = new Pattern(AEItemKey.of(Items.STONE));
        var newPattern = new Pattern(AEItemKey.of(Items.SAND));
        var provider = new Provider(oldPattern);
        var node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
                new Class<?>[] {IGridNode.class}, (p, method, args) -> switch (method.getName()) {
                    case "getService" -> args[0] == ICraftingProvider.class ? provider : null;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    case "toString" -> "ProviderCacheTestNode";
                    default -> null;
                });
        var schedule = new TickProviderDispatchSchedule();
        schedule.beginTick(TickHandler.instance().getCurrentTick());
        catalog.addProvider(node);
        require(copy(schedule.candidates(service, oldPattern, oldPattern)).equals(List.of(provider)), "node not mounted");
        var snapshot = schedule.candidates(service, oldPattern, oldPattern);
        schedule.beginTick(TickHandler.instance().getCurrentTick() + 1);
        require(schedule.candidates(service, oldPattern, oldPattern) == snapshot, "stable candidates rebuilt on tick boundary");
        catalog.removeProvider(node);
        provider.patterns = List.of(newPattern);
        catalog.addProvider(node);
        require(copy(schedule.candidates(service, oldPattern, oldPattern)).isEmpty(), "removed pattern stayed cached");
        require(copy(schedule.candidates(service, newPattern, newPattern)).equals(List.of(provider)), "new pattern absent after node refresh");
        catalog.removeProvider(node);
        require(copy(schedule.candidates(service, newPattern, newPattern)).isEmpty(), "disconnected node retained");
        helper.succeed();
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> null));
    }
    private static List<ICraftingProvider> copy(Iterable<ICraftingProvider> candidates) {
        var result = new ArrayList<ICraftingProvider>(); candidates.forEach(result::add); return result;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private record Pattern(AEItemKey output) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.STICK); }
        @Override public IInput[] getInputs() { return new IInput[0]; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
    }
    private static final class Provider implements ICraftingProvider {
        private List<IPatternDetails> patterns;
        Provider(IPatternDetails pattern) { patterns = List.of(pattern); }
        @Override public List<IPatternDetails> getAvailablePatterns() { return patterns; }
        @Override public boolean isBusy() { return false; }
        @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return false; }
    }
}
