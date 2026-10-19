package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPattern;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Map;

/** Exact accounting is independent of the provider's numeric dispatch API. */
public interface BigCraftingRecipe {
    AEItemKey definition();
    List<Map<AEKey, Long>> slots();
    CraftPattern<AEKey> pattern();
    boolean matches(IPatternDetails current, Level level);
    CompoundTag save(RegistryAccess registries);

    default KeyCounter[] template() {
        return slots().stream().map(slot -> {
            var counter = new KeyCounter();
            slot.forEach(counter::add);
            return counter;
        }).toArray(KeyCounter[]::new);
    }

    default IPatternDetails resolve(ICraftingService crafting, Level level) {
        for (var current : crafting.getCraftingFor(pattern().output())) {
            if (definition().equals(current.getDefinition()) && matches(current, level)) return current;
        }
        return null;
    }

    static BigCraftingRecipe load(CompoundTag tag, RegistryAccess registries) {
        return tag.getString("kind").equals("provider")
                ? BigProviderRecipe.load(tag, registries) : BigMatrixRecipe.load(tag, registries);
    }
}
