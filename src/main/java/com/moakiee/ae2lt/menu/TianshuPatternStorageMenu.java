package com.moakiee.ae2lt.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Edits the same physical pattern inventory exposed to the pattern access terminal. */
public final class TianshuPatternStorageMenu extends AEBaseMenu {
    public static final MenuType<TianshuPatternStorageMenu> TYPE = MenuTypeBuilder
            .create(TianshuPatternStorageMenu::new, TianshuPatternStorageBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("ae2lt.tianshu.pattern_storage.title"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "closed_loop_pattern_storage"));
    public static final int PATTERN_SLOTS = TianshuFunctionProfile.PATTERNS_PER_CLOSED_LOOP_STORAGE;
    public static final int ROWS = PATTERN_SLOTS / 9;

    private final TianshuPatternStorageBlockEntity host;
    private final List<Slot> patternSlots = new ArrayList<>();
    private final List<Slot> playerSlots = new ArrayList<>();

    public TianshuPatternStorageMenu(int id, Inventory inventory, TianshuPatternStorageBlockEntity host) {
        super(TYPE, id, inventory, host);
        this.host = host;
        // Client slot synchronization must not edit the block entity's logical repository.
        var patterns = isClientSide() ? new AppEngInternalInventory(null, PATTERN_SLOTS, 1)
                : host.getTerminalPatternInventory();
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            var slot = new AppEngSlot(patterns, i);
            slot.x = 8 + i % 9 * 18;
            slot.y = 18 + i / 9 * 18;
            patternSlots.add(addSlot(slot, SlotSemantics.ENCODED_PATTERN));
        }
        createPlayerInventorySlots(inventory);
        var backpack = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int i = 0; i < backpack.size(); i++) {
            var slot = backpack.get(i);
            slot.x = 8 + i % 9 * 18;
            slot.y = ROWS * 18 + 31 + i / 9 * 18;
            playerSlots.add(slot);
        }
        var hotbar = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int i = 0; i < hotbar.size(); i++) {
            var slot = hotbar.get(i);
            slot.x = 8 + i * 18;
            slot.y = ROWS * 18 + 89;
            playerSlots.add(slot);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide() || index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var source = getSlot(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        var original = source.getItem().copy();
        var remainder = original.copy();
        var destinations = isPlayerSideSlot(source) ? patternSlots : playerSlots;
        for (boolean occupied : new boolean[] {true, false}) {
            for (var destination : destinations) {
                if (destination.hasItem() != occupied) continue;
                remainder = destination.safeInsert(remainder);
                if (remainder.isEmpty()) break;
            }
            if (remainder.isEmpty()) break;
        }
        int moved = original.getCount() - remainder.getCount();
        if (moved <= 0) return ItemStack.EMPTY;
        // The warehouse returns reconstructed stacks; mutating getItem() would duplicate patterns.
        source.remove(moved);
        source.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return !host.isRemoved() && host.getLevel() != null && player.level() == host.getLevel()
                && host.getLevel().getBlockEntity(host.getBlockPos()) == host
                && player.distanceToSqr(host.getBlockPos().getCenter()) <= 64.0D;
    }
}
