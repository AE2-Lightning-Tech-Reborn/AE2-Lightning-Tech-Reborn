package com.moakiee.ae2lt.machine.largeoverload;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeFactoryRecipeTest {
    @BeforeAll static void bootstrap() {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null)
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    private static AEItemKey key(net.minecraft.world.item.Item item) { return AEItemKey.of(item); }
    private static LargeFactoryRecipe recipe(List<LargeFactoryRecipe.Requirement> inputs, Map<AEKey, Long> outputs) {
        return new LargeFactoryRecipe(new ResourceLocation("test:recipe"), LargeFactoryRecipeAccess.Process.OVERLOAD,
                inputs, outputs, 5, new LargeFactoryLightningCost(1, 0), null);
    }
    private static LargeFactoryRecipe.Requirement exact(AEKey key, long count) { return new LargeFactoryRecipe.Requirement(key::equals, count); }

    @Test void everyOutputMustBePresentAtTheSameIntegralMultiplicity() {
        var input = key(Items.STONE);
        Map<AEKey, Long> outputs = Map.of(key(Items.DIAMOND), 1L, key(Items.EMERALD), 2L, key(Items.IRON_INGOT), 3L, key(Items.GOLD_INGOT), 4L);
        var recipe = recipe(List.of(exact(input, 2)), outputs);
        assertEquals(4, recipe.match(Map.of(input, 8L), LargeFactoryAmounts.scale(outputs, 4)));
        assertEquals(0, recipe.match(Map.of(input, 8L), Map.of(key(Items.DIAMOND), 4L)));
        var wrong = new java.util.HashMap<>(LargeFactoryAmounts.scale(outputs, 4));
        wrong.put(key(Items.GOLD_INGOT), 15L);
        assertEquals(0, recipe.match(Map.of(input, 8L), wrong));
    }

    @Test void overlappingIngredientsUseCapacityFlowInsteadOfGreedyAllocation() {
        var a = key(Items.IRON_INGOT); var b = key(Items.GOLD_INGOT);
        Map<AEKey, Long> output = Map.of(key(Items.DIAMOND), 1L);
        var recipe = recipe(List.of(new LargeFactoryRecipe.Requirement(k -> k.equals(a) || k.equals(b), 2), exact(a, 2)), output);
        assertEquals(1, recipe.match(Map.of(a, 2L, b, 2L), output));
        assertEquals(0, recipe.match(Map.of(a, 1L, b, 3L), output));
        assertEquals(0, recipe.match(Map.of(a, 3L, b, 2L), output), "unused ingredients must not disappear");
    }

    @Test void componentsAndFluidIdentityArePartOfTheExactSignature() {
        var named = new ItemStack(Items.IRON_INGOT);
        named.setHoverName(Component.literal("exact component"));
        var item = AEItemKey.of(named); var water = AEFluidKey.of(Fluids.WATER); var lava = AEFluidKey.of(Fluids.LAVA);
        Map<AEKey, Long> output = Map.of(key(Items.DIAMOND), 16L);
        var recipe = recipe(List.of(exact(item, 1), exact(water, 1000)), output);
        assertEquals(1, recipe.match(Map.of(item, 1L, water, 1000L), output));
        assertEquals(0, recipe.match(Map.of(key(Items.IRON_INGOT), 1L, water, 1000L), output));
        assertEquals(0, recipe.match(Map.of(item, 1L, lava, 1000L), output));
        assertEquals(0, recipe.match(Map.of(item, 1L, water, 999L), output));
    }

    @Test void quantitiesNearLongLimitDoNotCreateQuantitySizedSearchLoops() {
        var a = key(Items.IRON_INGOT); var b = key(Items.GOLD_INGOT);
        var recipe = recipe(List.of(new LargeFactoryRecipe.Requirement(k -> k.equals(a) || k.equals(b), 3), exact(a, 2)), Map.of(key(Items.DIAMOND), 1L));
        long count = Long.MAX_VALUE / 6;
        assertTimeout(Duration.ofSeconds(2), () -> assertEquals(count,
                recipe.match(Map.of(a, count * 2, b, count * 3), Map.of(key(Items.DIAMOND), count))));
        assertEquals(0, recipe.match(Map.of(a, Long.MAX_VALUE, b, Long.MAX_VALUE), Map.of(key(Items.DIAMOND), Long.MAX_VALUE)));
    }

    @Test void matcherAgreesWithExhaustiveTinyAllocationOracle() {
        var keys = List.of(key(Items.IRON_INGOT), key(Items.GOLD_INGOT), key(Items.COPPER_INGOT));
        var rng = new Random(731923);
        for (int trial = 0; trial < 300; trial++) {
            int[] supply = {rng.nextInt(4), rng.nextInt(4), rng.nextInt(4)};
            int[] masks = {1 + rng.nextInt(7), 1 + rng.nextInt(7), 1 + rng.nextInt(7)};
            int[] demand = {1 + rng.nextInt(3), 1 + rng.nextInt(3), 1 + rng.nextInt(3)};
            var requirements = new ArrayList<LargeFactoryRecipe.Requirement>();
            for (int r = 0; r < 3; r++) { final int mask = masks[r]; requirements.add(new LargeFactoryRecipe.Requirement(k -> (mask & (1 << keys.indexOf(k))) != 0, demand[r])); }
            var actual = new java.util.HashMap<AEKey, Long>();
            for (int k = 0; k < 3; k++) if (supply[k] > 0) actual.put(keys.get(k), (long) supply[k]);
            var output = Map.<AEKey, Long>of(key(Items.DIAMOND), 1L);
            boolean expected = allocate(0, 0, demand[0], supply.clone(), masks, demand);
            assertEquals(expected, recipe(requirements, output).match(actual, output) == 1, "trial " + trial);
        }
    }
    private static boolean allocate(int requirement, int key, int needed, int[] supply, int[] masks, int[] demand) {
        if (needed == 0) {
            if (requirement == demand.length - 1) return java.util.Arrays.stream(supply).sum() == 0;
            return allocate(requirement + 1, 0, demand[requirement + 1], supply, masks, demand);
        }
        if (key == supply.length) return false;
        int max = (masks[requirement] & (1 << key)) != 0 ? Math.min(needed, supply[key]) : 0;
        for (int take = 0; take <= max; take++) {
            supply[key] -= take;
            boolean success = allocate(requirement, key + 1, needed - take, supply, masks, demand);
            supply[key] += take;
            if (success) return true;
        }
        return false;
    }

    @Test void flatteningPreservesBorrowedTemplatesAndRejectsOverflow() {
        var key = key(Items.STONE);
        var first = new KeyCounter(); first.add(key, 6);
        var second = new KeyCounter(); second.add(key, 2);
        assertEquals(Map.of(key, 8L), LargeFactoryAmounts.flatten(new KeyCounter[]{first, second}));
        assertEquals(6, first.get(key)); assertEquals(2, second.get(key));
        first.set(key, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> LargeFactoryAmounts.flatten(new KeyCounter[]{first, second}));
        assertThrows(ArithmeticException.class, () -> LargeFactoryAmounts.scale(Map.of(key, Long.MAX_VALUE), 2));
    }

    @Test void flatteningPromotesMultipleKeysAndRejectsNegativeReceipts() {
        var stone = key(Items.STONE); var diamond = key(Items.DIAMOND);
        var first = new KeyCounter(); first.add(stone, 3);
        var second = new KeyCounter(); second.add(diamond, 2); second.add(stone, 5);
        assertEquals(Map.of(stone, 8L, diamond, 2L), LargeFactoryAmounts.flatten(new KeyCounter[]{first, second}));
        assertEquals(3, first.get(stone)); assertEquals(5, second.get(stone));
        second.set(diamond, -1);
        assertThrows(IllegalArgumentException.class, () -> LargeFactoryAmounts.flatten(new KeyCounter[]{first, second}));
        second.clear();
        assertEquals(Map.of(), LargeFactoryAmounts.flatten(new KeyCounter[]{second}));
        var scaled = LargeFactoryAmounts.scale(Map.of(stone, 3L), 4);
        assertEquals(Map.of(stone, 12L), scaled);
    }
}
