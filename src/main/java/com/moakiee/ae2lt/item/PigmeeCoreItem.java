package com.moakiee.ae2lt.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Reusable catalyst obtained by dropping an anvil onto an adult pig standing on an Overload
 * Crystal Block.
 *
 * <p>The stack-sensitive remainder API is shared by vanilla crafting, AE2 pattern decoding and
 * Thunderbolt batch dispatch. Returning the exact input stack therefore lets one core serve an
 * entire batch without any Pigmee-specific crafting hook.
 */
public final class PigmeeCoreItem extends Item {
    public PigmeeCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (instance instanceof ItemStack stack && !stack.isEmpty()) {
            return ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1));
        }
        if (instance instanceof ItemStackTemplate template) {
            return template.withCount(1);
        }
        return new ItemStackTemplate(instance.typeHolder(), 1);
    }
}
