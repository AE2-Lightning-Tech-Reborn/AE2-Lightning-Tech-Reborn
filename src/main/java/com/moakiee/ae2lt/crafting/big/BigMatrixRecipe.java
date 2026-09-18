package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;

import com.moakiee.ae2lt.crafting.matrix.core.CopyAssembler;
import com.moakiee.ae2lt.crafting.matrix.core.MolecularCopyAssembler;
import com.moakiee.thunderbolt.core.crafting.planner.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.*;

/** One concrete physical recipe, checked by the real molecular assembler before bulk scaling. */
public final class BigMatrixRecipe {
    private final AEItemKey definition;
    private final List<Map<AEKey, Long>> slots;
    private final CraftPattern<AEKey> pattern;

    private BigMatrixRecipe(
            AEItemKey definition,
            List<Map<AEKey, Long>> slots,
            AEKey output,
            long amount,
            List<CraftInput<AEKey>> inputs,
            List<CraftOutput<AEKey>> outputs) {
        this.definition = Objects.requireNonNull(definition);
        this.slots = slots.stream().map(Map::copyOf).toList();
        pattern = new CraftPattern<>(output, amount, inputs, outputs, this);
    }

    public AEItemKey definition() {
        return definition;
    }

    public CraftPattern<AEKey> pattern() {
        return pattern;
    }

    public KeyCounter[] template() {
        var result = new KeyCounter[slots.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = new KeyCounter();
            var counter = result[i];
            slots.get(i).forEach(counter::add);
        }
        return result;
    }

    public static BigMatrixRecipe capture(
            IPatternDetails details, List<Map<AEKey, Long>> slots, Level level) {
        if (slots.size() != details.getInputs().length) return null;
        for (int i = 0; i < slots.size(); i++) {
            var input = details.getInputs()[i];
            long units = 0;
            for (var e : slots.get(i).entrySet()) {
                if (e.getValue() <= 0 || !input.isValid(e.getKey(), level)) return null;
                long size = 0;
                for (var option : input.getPossibleInputs())
                    if (option.what().dropSecondary().equals(e.getKey().dropSecondary())) {
                        size = option.amount();
                        break;
                    }
                if (size <= 0 || e.getValue() % size != 0) return null;
                units = Math.addExact(units, e.getValue() / size);
            }
            if (units != input.getMultiplier()) return null;
        }
        var pre = new LinkedHashMap<AEKey, Long>();
        slots.forEach(s -> s.forEach((k, n) -> pre.merge(k, n, Math::addExact)));
        var template = new KeyCounter[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            template[i] = new KeyCounter();
            var c = template[i];
            slots.get(i).forEach(c::add);
        }
        CopyAssembler.AssembledCopy actual =
                new MolecularCopyAssembler(level).assembleOneCopy(details, template);
        if (actual == null) return null;
        var post = new LinkedHashMap<AEKey, Long>();
        actual.remainders().forEach(s -> post.merge(s.key(), s.count(), Math::addExact));
        actual.sharedRemainders().forEach(s -> post.merge(s.key(), s.count(), Math::addExact));
        var returned = new HashSet<AEKey>();
        for (int i = 0; i < slots.size(); i++)
            for (var key : slots.get(i).keySet())
                if (key.equals(details.getInputs()[i].getRemainingKey(key))) returned.add(key);
        var inputs = new ArrayList<CraftInput<AEKey>>();
        pre.forEach(
                (k, n) -> {
                    boolean shared = returned.contains(k) && post.getOrDefault(k, 0L) >= n;
                    inputs.add(shared ? CraftInput.returned(k, n) : CraftInput.of(k, n));
                    if (shared) post.put(k, post.get(k) - n);
                });
        var outputs = new ArrayList<CraftOutput<AEKey>>();
        post.forEach(
                (k, n) -> {
                    if (n > 0) outputs.add(CraftOutput.of(k, n));
                });
        return new BigMatrixRecipe(
                details.getDefinition(),
                slots,
                actual.output(),
                actual.outputCount(),
                inputs,
                outputs);
    }

    public boolean matches(IPatternDetails current, Level level) {
        if (!definition.equals(current.getDefinition())) return false;
        try {
            var actual = capture(current, slots, level);
            if (actual == null) return false;
            var a = com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram.arcs(pattern);
            var b =
                    com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram.arcs(
                            actual.pattern);
            return a.equals(b);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("definition", definition.toTagGeneric(registries));
        var slotList = new ListTag();
        for (var slot : slots) {
            var converted = new LinkedHashMap<AEKey, BigInteger>();
            slot.forEach((k, n) -> converted.put(k, BigInteger.valueOf(n)));
            slotList.add(BigStackCodec.write(converted, registries));
        }
        tag.put("slots", slotList);
        tag.put("output", pattern.output().toTagGeneric(registries));
        tag.putLong("amount", pattern.outputAmount());
        var ordinary = new LinkedHashMap<AEKey, BigInteger>();
        var catalysts = new LinkedHashMap<AEKey, BigInteger>();
        for (var input : pattern.inputs())
            (input.returned() ? catalysts : ordinary)
                    .merge(input.key(), input.exactAmount(), BigInteger::add);
        var post = new LinkedHashMap<AEKey, BigInteger>();
        for (var out : pattern.byproducts())
            post.merge(out.key(), out.exactAmount(), BigInteger::add);
        tag.put("pre", BigStackCodec.write(ordinary, registries));
        tag.put("catalysts", BigStackCodec.write(catalysts, registries));
        tag.put("post", BigStackCodec.write(post, registries));
        return tag;
    }

    public static BigMatrixRecipe load(CompoundTag tag, HolderLookup.Provider registries) {
        var definition =
                (AEItemKey)
                        Objects.requireNonNull(
                                AEKey.fromTagGeneric(registries, tag.getCompound("definition")));
        var slots = new ArrayList<Map<AEKey, Long>>();
        for (var entry : tag.getList("slots", Tag.TAG_LIST)) {
            var slot = new LinkedHashMap<AEKey, Long>();
            BigStackCodec.read((ListTag) entry, registries)
                    .forEach((k, n) -> slot.put(k, n.longValueExact()));
            slots.add(slot);
        }
        var inputs = new ArrayList<CraftInput<AEKey>>();
        BigStackCodec.read(tag.getList("pre", Tag.TAG_COMPOUND), registries)
                .forEach((k, n) -> inputs.add(CraftInput.of(k, n.longValueExact())));
        BigStackCodec.read(tag.getList("catalysts", Tag.TAG_COMPOUND), registries)
                .forEach((k, n) -> inputs.add(CraftInput.returned(k, n.longValueExact())));
        var outputs = new ArrayList<CraftOutput<AEKey>>();
        BigStackCodec.read(tag.getList("post", Tag.TAG_COMPOUND), registries)
                .forEach((k, n) -> outputs.add(CraftOutput.exact(k, n)));
        return new BigMatrixRecipe(
                definition,
                slots,
                Objects.requireNonNull(AEKey.fromTagGeneric(registries, tag.getCompound("output"))),
                tag.getLong("amount"),
                inputs,
                outputs);
    }
}
