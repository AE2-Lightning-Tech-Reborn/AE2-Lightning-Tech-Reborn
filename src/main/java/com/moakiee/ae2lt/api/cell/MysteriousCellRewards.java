package com.moakiee.ae2lt.api.cell;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Optional rare prize supplied by an addon during common setup on both logical sides. */
public final class MysteriousCellRewards {
    private static volatile Reward rareReward;

    private MysteriousCellRewards() {}

    public record Reward(String translationSuffix, IntSupplier weight, Supplier<ItemStack> item) {
        public Reward {
            Objects.requireNonNull(translationSuffix);
            Objects.requireNonNull(weight);
            Objects.requireNonNull(item);
        }
    }

    public static synchronized void registerRareReward(Reward reward) {
        Objects.requireNonNull(reward);
        if (rareReward != null) throw new IllegalStateException("A rare mysterious-cell reward is already registered");
        rareReward = reward;
    }

    public static boolean hasRareReward() { return rareReward != null; }
    public static int rareWeight() {
        var reward = rareReward;
        return reward == null ? 0 : Math.clamp(reward.weight().getAsInt(), 0, 10000);
    }
    public static String suffix() {
        var reward = rareReward;
        return reward == null ? "lightning_rod" : reward.translationSuffix();
    }
    public static ItemStack createRareReward() {
        var reward = rareReward;
        return reward == null ? ItemStack.EMPTY : reward.item().get().copy();
    }
}
