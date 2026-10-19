package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputs;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import com.moakiee.thunderbolt.core.crafting.planner.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import java.math.BigInteger;
import java.util.*;

/** The native pattern contract for one physical copy, including concrete container returns. */
public final class BigProviderRecipe implements BigCraftingRecipe {
    private final AEItemKey definition;
    private final List<Map<AEKey, Long>> slots;
    private final CraftPattern<AEKey> pattern;
    private final Map<AEKey, Long> inputs, outputs, sharedInputs, sharedOutputs;

    private BigProviderRecipe(AEItemKey definition, List<Map<AEKey, Long>> slots,
            AEKey output, long amount, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs,
            Map<AEKey, Long> sharedInputs, Map<AEKey, Long> sharedOutputs) {
        this.definition = Objects.requireNonNull(definition);
        this.slots = slots.stream().map(Map::copyOf).toList();
        this.inputs = Map.copyOf(inputs);
        this.outputs = Map.copyOf(outputs);
        this.sharedInputs = Map.copyOf(sharedInputs);
        this.sharedOutputs = Map.copyOf(sharedOutputs);
        var extra = new LinkedHashMap<>(outputs);
        extra.compute(output, (k, n) -> Math.subtractExact(Objects.requireNonNull(n), amount));
        var pre = new ArrayList<CraftInput<AEKey>>();
        inputs.forEach((key, count) -> {
            long returned = Math.min(count, extra.getOrDefault(key, 0L));
            if (returned > 0) {
                pre.add(CraftInput.returned(key, returned));
                extra.put(key, extra.get(key) - returned);
            }
            if (count > returned) pre.add(CraftInput.of(key, count - returned));
        });
        var post = new ArrayList<CraftOutput<AEKey>>();
        extra.forEach((key, count) -> {
            if (count < 0) throw new IllegalArgumentException("invalid recipe output");
            if (count > 0) post.add(CraftOutput.of(key, count));
        });
        pattern = new CraftPattern<>(output, amount, pre, post, this);
    }

    public AEItemKey definition() { return definition; }
    public List<Map<AEKey, Long>> slots() { return slots; }
    public CraftPattern<AEKey> pattern() { return pattern; }
    public Map<AEKey, Long> inputs() { return inputs; }
    public Map<AEKey, Long> outputs() { return outputs; }
    public Map<AEKey, Long> sharedInputs() { return sharedInputs; }
    public Map<AEKey, Long> sharedOutputs() { return sharedOutputs; }

    public static BigProviderRecipe capture(IPatternDetails details,
            List<Map<AEKey, Long>> slots, Level level) {
        if (slots.size() != details.getInputs().length || details.getOutputs().length == 0) return null;
        var inputs = new LinkedHashMap<AEKey, Long>();
        var outputs = new LinkedHashMap<AEKey, Long>();
        var sharedInputs = new LinkedHashMap<AEKey, Long>();
        var sharedOutputs = new LinkedHashMap<AEKey, Long>();
        for (var output : details.getOutputs()) {
            if (output.what() == null || output.amount() <= 0) return null;
            outputs.merge(output.what(), output.amount(), Math::addExact);
        }
        for (int slot = 0; slot < slots.size(); slot++) {
            var input = details.getInputs()[slot];
            long units = 0;
            for (var entry : slots.get(slot).entrySet()) {
                var key = entry.getKey();
                long size = unitAmount(input, key);
                if (entry.getValue() <= 0 || size <= 0 || entry.getValue() % size != 0
                        || !input.isValid(key, level)) return null;
                long count = entry.getValue() / size;
                units = Math.addExact(units, count);
                inputs.merge(key, entry.getValue(), Math::addExact);
                boolean shared = SharedBatchInputs.isSharedInput(details, slot, key);
                if (shared) sharedInputs.merge(key, entry.getValue(), Math::addExact);
                var remainder = input.getRemainingKey(key);
                if (remainder != null) {
                    // AE2 returns one container per input template, not per physical base unit.
                    outputs.merge(remainder, count, Math::addExact);
                    if (shared) sharedOutputs.merge(remainder, count, Math::addExact);
                }
            }
            if (units != input.getMultiplier()) return null;
        }
        if (!sharedInputs.isEmpty() && details instanceof SharedBatchInputPattern shared) {
            var left = new HashMap<AEKey, Long>();
            for (var output : details.getOutputs()) {
                long count = Math.min(output.amount(), Math.max(0,
                        left.computeIfAbsent(output.what(), shared::sharedBatchOutputAmount)));
                if (count > 0) {
                    sharedOutputs.merge(output.what(), count, Math::addExact);
                    left.put(output.what(), left.get(output.what()) - count);
                }
            }
        }
        var primary = details.getPrimaryOutput();
        return new BigProviderRecipe(details.getDefinition(), slots, primary.what(), primary.amount(),
                inputs, outputs, sharedInputs, sharedOutputs);
    }

    private static long unitAmount(IPatternDetails.IInput input, AEKey key) {
        for (var option : input.getPossibleInputs()) if (option.what().equals(key)) return option.amount();
        for (var option : input.getPossibleInputs())
            if (option.what().dropSecondary().equals(key.dropSecondary())) return option.amount();
        return 0;
    }

    public boolean matches(IPatternDetails current, Level level) {
        if (!definition.equals(current.getDefinition())) return false;
        try {
            var other = capture(current, slots, level);
            return other != null && BigExecutionProgram.arcs(pattern).equals(BigExecutionProgram.arcs(other.pattern))
                    && inputs.equals(other.inputs) && outputs.equals(other.outputs)
                    && sharedInputs.equals(other.sharedInputs) && sharedOutputs.equals(other.sharedOutputs);
        } catch (RuntimeException invalid) { return false; }
    }

    public CompoundTag save(RegistryAccess registries) {
        var tag = new CompoundTag();
        tag.putString("kind", "provider");
        tag.put("definition", definition.toTagGeneric());
        tag.put("output", pattern.output().toTagGeneric());
        tag.putLong("amount", pattern.outputAmount());
        var slotList = new ListTag();
        slots.forEach(slot -> slotList.add(write(slot, registries)));
        tag.put("slots", slotList);
        tag.put("inputs", write(inputs, registries));
        tag.put("outputs", write(outputs, registries));
        tag.put("sharedInputs", write(sharedInputs, registries));
        tag.put("sharedOutputs", write(sharedOutputs, registries));
        return tag;
    }

    public static BigProviderRecipe load(CompoundTag tag, RegistryAccess registries) {
        var slots = new ArrayList<Map<AEKey, Long>>();
        for (var slot : tag.getList("slots", Tag.TAG_LIST)) slots.add(read((ListTag) slot, registries));
        return new BigProviderRecipe((AEItemKey) AEKey.fromTagGeneric(tag.getCompound("definition")),
                slots, Objects.requireNonNull(AEKey.fromTagGeneric(tag.getCompound("output"))),
                tag.getLong("amount"), read(tag.getList("inputs", Tag.TAG_COMPOUND), registries),
                read(tag.getList("outputs", Tag.TAG_COMPOUND), registries),
                read(tag.getList("sharedInputs", Tag.TAG_COMPOUND), registries),
                read(tag.getList("sharedOutputs", Tag.TAG_COMPOUND), registries));
    }

    private static ListTag write(Map<AEKey, Long> values, RegistryAccess registries) {
        var exact = new LinkedHashMap<AEKey, BigInteger>();
        values.forEach((key, count) -> exact.put(key, BigInteger.valueOf(count)));
        return BigStackCodec.write(exact, registries);
    }

    private static Map<AEKey, Long> read(ListTag values, RegistryAccess registries) {
        var result = new LinkedHashMap<AEKey, Long>();
        BigStackCodec.read(values, registries).forEach((key, count) -> result.put(key, count.longValueExact()));
        return result;
    }
}
