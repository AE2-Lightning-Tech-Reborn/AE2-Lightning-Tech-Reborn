package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import appeng.api.config.PowerUnits;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCatalog;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fluids.FluidStack;

import static com.moakiee.ae2lt.machine.largeoverload.LargeFactoryRecipeAccess.Process.*;

/** Factory-local adapters. Optional mods are inspected only for explicitly supported exact recipe classes. */
@Mod.EventBusSubscriber(modid = "ae2lt")
public final class LargeFactoryRecipes {
    private static final Map<RecipeManager, Snapshot> CACHE = new WeakHashMap<>();
    private static long generation;
    public record Snapshot(Object sources, long generation, double aeToFE, double nativeMultiplier,
            List<LargeFactoryRecipe> recipes, Map<AEKey, List<LargeFactoryRecipe>> byOutput, List<LargeFactoryRecipe> catalysts) {
        public List<LargeFactoryRecipe> candidates(AEKey output) { return byOutput.getOrDefault(output, List.of()); }
    }
    private LargeFactoryRecipes() { }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) { CACHE.clear(); generation++; }
    }

    public static Snapshot get(RecipeManager manager) {
        var old = CACHE.get(manager);
        var sources = ((RecipeManagerByTypeAccess) manager).ae2lt$recipeSnapshot();
        double aeToFE = PowerUnits.AE.convertTo(PowerUnits.FE, 1);
        double nativeMultiplier = appeng.api.config.PowerMultiplier.CONFIG.multiplier;
        if (old != null && old.sources() == sources && old.aeToFE() == aeToFE
                && old.nativeMultiplier() == nativeMultiplier) return old;
        var result = new ArrayList<LargeFactoryRecipe>();
        for (var r : OverloadProcessingRecipeCatalog.recipes(manager)) {
            var inputs = new ArrayList<LargeFactoryRecipe.Requirement>();
            r.itemInputs().forEach(i -> inputs.add(item(i.ingredient(), i.count())));
            if (r.inputFluidAmount() > 0) {
                var alternatives = r.fluidInputAlternatives();
                inputs.add(new LargeFactoryRecipe.Requirement(k -> k instanceof AEFluidKey fluid
                        && alternatives.stream().anyMatch(f -> fluid.equals(AEFluidKey.of(f))), r.inputFluidAmount()));
            }
            var outputs = outputs(r.itemResults(), r.fluidResult());
            var original = manager.byKey(r.getId()).orElse(null);
            var process = original instanceof com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe
                    ? OVERLOAD : REACTION;
            result.add(new LargeFactoryRecipe(r.getId(), process, inputs, outputs, r.totalEnergy(),
                    LargeFactoryLightningCost.ordinary(r.lightningTier(), r.lightningCost()), null));
        }
        for (var holder : manager.getRecipes()) {
            try {
                var view = convert(holder);
                if (view != null) result.add(view);
            } catch (ReflectiveOperationException | IllegalArgumentException | ArithmeticException failure) {
                LogUtils.getLogger().warn("Large overload factory skipped unsupported recipe {}: {}", holder.getId(), failure.toString());
            }
        }
        result.sort(Comparator.comparing((LargeFactoryRecipe r) -> r.process().ordinal()).thenComparing(r -> r.id().toString()));
        var byOutput = new java.util.HashMap<AEKey, List<LargeFactoryRecipe>>();
        for (var recipe : result) for (var key : recipe.outputs().keySet())
            byOutput.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
        byOutput.replaceAll((key, candidates) -> List.copyOf(candidates));
        var snapshot = new Snapshot(sources, ++generation, aeToFE, nativeMultiplier, List.copyOf(result), Map.copyOf(byOutput),
                result.stream().filter(r -> r.catalyst() != null).toList());
        CACHE.put(manager, snapshot);
        return snapshot;
    }

    private static LargeFactoryRecipe convert(Recipe<?> r) throws ReflectiveOperationException {
        var inputs = new ArrayList<LargeFactoryRecipe.Requirement>();
        var process = java.util.Arrays.stream(LargeFactoryRecipeAccess.Process.values())
                .filter(p -> p.type().equals(BuiltInRegistries.RECIPE_TYPE.getKey(r.getType()))).findFirst().orElse(null);
        if (process == null || process.base()) return null;
        if (process == CRYSTAL_ASSEMBLER) {
            throw new IllegalArgumentException("ExtendedAE 1.20.1 has no crystal assembler recipe API");
        }
        Map<AEKey, Long> outputs;
        long energy;
        var lightning = LargeFactoryLightningCost.ordinary(LightningKey.Tier.HIGH_VOLTAGE, 0);
        java.util.function.Predicate<ItemStack> catalyst = null;
        if (r instanceof LightningSimulationRecipe recipe) {
            recipe.inputs().forEach(i -> inputs.add(item(i.ingredient(), i.count())));
            outputs = outputs(List.of(recipe.getResultStack()), FluidStack.EMPTY);
            energy = recipe.totalEnergy();
            lightning = LargeFactoryLightningCost.ordinary(recipe.lightningTier(), recipe.lightningCost());
        } else if (r instanceof LightningAssemblyRecipe recipe) {
            recipe.inputs().forEach(i -> inputs.add(item(i.ingredient(), i.count())));
            outputs = outputs(List.of(recipe.getResultStack()), FluidStack.EMPTY);
            energy = recipe.totalEnergy();
            lightning = LargeFactoryLightningCost.ordinary(recipe.lightningTier(), recipe.lightningCost());
        } else if (r instanceof FirmamentConversionRecipe recipe) {
            recipe.inputs().forEach(i -> inputs.add(item(i.ingredient(), i.count())));
            outputs = outputs(recipe.getResultStacks(), FluidStack.EMPTY);
            energy = 0;
            lightning = LargeFactoryLightningCost.FIRMAMENT;
        } else if (r instanceof CrystalCatalyzerRecipe recipe) {
            var fluid = recipe.fluidInput();
            if (!fluid.isEmpty()) inputs.add(exact(AEFluidKey.of(fluid), fluid.getAmount()));
            outputs = outputs(List.of(recipe.getOutputTemplate()), FluidStack.EMPTY);
            energy = recipe.energyPerCycle();
            lightning = LargeFactoryLightningCost.ordinary(recipe.lightningTier(), recipe.lightningCost());
            catalyst = recipe::catalystMatches;
        } else {
            String expected = switch (process) {
                case INTEGRATED_WORKSTATION -> "cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe";
                case CRYSTAL_AGGREGATOR -> "io.github.lounode.ae2cs.common.recipe.crystal_aggregator.CrystalAggregatorRecipe";
                case CRYSTAL_PULVERIZER -> "io.github.lounode.ae2cs.common.recipe.crystal_pulverizer.CrystalPulverizerRecipe";
                case CIRCUIT_ETCHER -> "io.github.lounode.ae2cs.common.recipe.circuit_etcher.CircuitEtcherRecipe";
                default -> "";
            };
            if (!r.getClass().getName().equals(expected)) throw new IllegalArgumentException("unrecognized recipe implementation");
            if (process == INTEGRATED_WORKSTATION) {
                for (Object value : (List<?>) call(r, "inputItems")) inputs.add(sized(value));
                var fluid = call(r, "inputFluid");
                if (fluid != null) inputs.add(fluid(fluid));
                outputs = outputs(List.of((ItemStack) call(r, "itemOutput")), (FluidStack) call(r, "fluidOutput"));
                energy = aeEnergyToFE(((Number) call(r, "energy")).longValue(), true);
            } else {
                if (process == CRYSTAL_PULVERIZER) inputs.add(sized(call(r, "input")));
                else for (Object value : (List<?>) call(r, "required")) inputs.add(sized(value));
                outputs = outputs(List.of((ItemStack) call(r, "result")), FluidStack.EMPTY);
                energy = aeEnergyToFE(((Number) call(r, "energyCost")).longValue(), false);
            }
        }
        return new LargeFactoryRecipe(r.getId(), process, inputs, outputs, energy, lightning, catalyst);
    }

    private static Object call(Object receiver, String name) throws ReflectiveOperationException {
        return receiver.getClass().getMethod(name).invoke(receiver);
    }
    private static long aeEnergyToFE(long amount, boolean nativeConfigMultiplier) {
        // NeoECO applies CONFIG; Crystal Science uses its unmultiplied AE buffer.
        double ae = nativeConfigMultiplier ? appeng.api.config.PowerMultiplier.CONFIG.multiply(amount) : amount;
        double fe = PowerUnits.AE.convertTo(PowerUnits.FE, ae);
        if (amount < 0 || !Double.isFinite(fe) || fe < 0 || fe >= 0x1p63) throw new ArithmeticException("AE recipe cost exceeds finite FE range");
        return (long) Math.ceil(fe);
    }
    private static LargeFactoryRecipe.Requirement sized(Object ingredient) throws ReflectiveOperationException {
        return item((Ingredient) call(ingredient, "ingredient"), ((Number) call(ingredient, "count")).longValue());
    }
    private static LargeFactoryRecipe.Requirement item(Ingredient ingredient, long count) {
        return new LargeFactoryRecipe.Requirement(k -> k instanceof AEItemKey item && ingredient.test(item.toStack()), count);
    }
    private static LargeFactoryRecipe.Requirement fluid(Object ingredient) throws ReflectiveOperationException {
        long amount = ((Number) call(ingredient, "amount")).longValue();
        Object matcher = call(ingredient, "ingredient");
        var method = matcher.getClass().getMethod("test", FluidStack.class);
        return new LargeFactoryRecipe.Requirement(k -> {
            if (!(k instanceof AEFluidKey fluid)) return false;
            try { return (boolean) method.invoke(matcher, fluid.toStack(1)); }
            catch (ReflectiveOperationException failure) { return false; }
        }, amount);
    }
    private static LargeFactoryRecipe.Requirement exact(AEKey key, long amount) {
        return new LargeFactoryRecipe.Requirement(key::equals, amount);
    }
    private static Map<AEKey, Long> outputs(List<ItemStack> items, FluidStack fluid) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var item : items) if (!item.isEmpty()) LargeFactoryAmounts.add(result, AEItemKey.of(item), item.getCount());
        if (!fluid.isEmpty()) LargeFactoryAmounts.add(result, AEFluidKey.of(fluid), fluid.getAmount());
        return result;
    }
}
