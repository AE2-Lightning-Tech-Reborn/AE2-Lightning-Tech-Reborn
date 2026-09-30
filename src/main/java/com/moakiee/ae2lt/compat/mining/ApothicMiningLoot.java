package com.moakiee.ae2lt.compat.mining;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Evaluates only Earth's Boon loot data, without posting block events or spawning entities. */
public final class ApothicMiningLoot {
    private static final ResourceLocation BOON = ResourceLocation.parse("apothic_enchanting:earths_boon");
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("9e402e2d-a94b-4d25-9930-61e3b58d21e3"), "[AE2LT Mining]");
    private ApothicMiningLoot() {}

    public static void addDrops(ServerLevel level, BlockPos origin, BlockState state, ItemStack tool,
                                Consumer<ItemStack> output) {
        if (!ModList.get().isLoaded("apothic_enchanting")) return;
        DataComponentType<?> type = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.get(BOON);
        if (type == null) return;
        EnchantmentHelper.runIterationOnItem(tool, (enchantment, enchantmentLevel) -> {
            Object component = enchantment.value().effects().get(type);
            if (component == null) return;
            try {
                for (Object entry : (List<?>) Access.ENTRIES.invoke(component)) {
                    if (!(boolean) Access.MATCHES.invoke(entry, state)) continue;
                    @SuppressWarnings("unchecked")
                    var key = (ResourceKey<LootTable>) Access.TABLE.invoke(entry);
                    var table = level.getServer().reloadableRegistries().getLootTable(key);
                    if (table == LootTable.EMPTY) break;
                    var context = (LootContext) Access.CONTEXT.invoke(null, level, tool,
                            FakePlayerFactory.get(level, PROFILE), Vec3.atCenterOf(origin), state);
                    float chance = (float) Access.CHANCE.invoke(null, Access.DROP_CHANCE.invoke(entry),
                            context, enchantmentLevel, 0F);
                    if (level.random.nextFloat() <= chance) table.getRandomItems(context, output);
                    // Like the upstream handler, only the first matching entry applies.
                    break;
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Apothic Enchanting mining loot integration failed", e);
            }
        });
    }

    private static final class Access {
        private static final Method ENTRIES, MATCHES, TABLE, DROP_CHANCE, CONTEXT, CHANCE;
        static {
            try {
                var component = Class.forName("dev.shadowsoffire.apothic_enchanting.enchantments.components.BoonComponent");
                var entry = Class.forName(component.getName() + "$BoonData");
                ENTRIES = component.getMethod("entries");
                MATCHES = entry.getMethod("matches", BlockState.class);
                TABLE = entry.getMethod("lootTable");
                DROP_CHANCE = entry.getMethod("dropChance");
                CONTEXT = component.getMethod("boonContext", ServerLevel.class, ItemStack.class,
                        Entity.class, Vec3.class, BlockState.class);
                CHANCE = Class.forName("dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper")
                        .getMethod("processValue", List.class, LootContext.class, int.class, float.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Unsupported Apothic Enchanting mining API", e);
            }
        }
    }
}
