package com.moakiee.ae2lt.logic.tianshu.terminal;

import com.moakiee.ae2lt.AE2LightningTech;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player-owned physical returns that survive a closed menu, logout and Forge player cloning. */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class TianshuPatternReturnBuffer {
    private static final String TAG_RETURNS = "ae2ltTianshuPatternReturns";
    private final CompoundTag playerData;

    TianshuPatternReturnBuffer(CompoundTag playerData) {
        this.playerData = Objects.requireNonNull(playerData);
    }

    public static TianshuPatternReturnBuffer forPlayer(ServerPlayer player) {
        return new TianshuPatternReturnBuffer(player.getPersistentData());
    }

    /** Takes custody only after the complete actual stack has been serialized and appended. */
    public void enqueue(ItemStack actualHeld) {
        if (actualHeld.isEmpty()) throw new IllegalArgumentException("Cannot enqueue an empty pattern return");
        commit(actualHeld, actualHeld.save(new CompoundTag()));
    }

    // Preparing is read-only and grants no item authority. Undo prepares while the real source
    // still owns its item, so a capability serialization failure cannot strand an extracted item.
    PreparedReturn prepare(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot prepare an empty pattern return");
        var serialized = stack.save(new CompoundTag());
        return new PreparedReturn(this, stack.copy(), serialized);
    }

    public int pendingCount() {
        return playerData.getCompound(Player.PERSISTED_NBT_TAG).getList(TAG_RETURNS, Tag.TAG_COMPOUND).size();
    }

    /** Delivers at most one return, retaining it when the native main inventory has no empty slot. */
    public boolean deliver(Inventory nativeInventory) {
        if (nativeInventory.getClass() != Inventory.class) return false;
        var persisted = playerData.getCompound(Player.PERSISTED_NBT_TAG);
        var pending = persisted.getList(TAG_RETURNS, Tag.TAG_COMPOUND);
        if (pending.isEmpty()) return false;
        int empty = -1;
        for (int slot = 0; slot < nativeInventory.items.size(); slot++) {
            if (nativeInventory.items.get(slot).isEmpty()) {
                empty = slot;
                break;
            }
        }
        if (empty < 0) return false;
        var returned = ItemStack.of(pending.getCompound(0));
        if (returned.isEmpty()) return false;
        // Native Inventory owns this slot and NonNullList.set invokes no inventory callbacks.
        // Retire the saved entry immediately after the physical assignment, before notification.
        nativeInventory.items.set(empty, returned);
        pending.remove(0);
        if (pending.isEmpty()) persisted.remove(TAG_RETURNS);
        nativeInventory.setChanged();
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.isAlive() && !player.isRemoved()) {
            forPlayer(player).deliver(player.getInventory());
        }
    }

    private void commit(ItemStack actualHeld, CompoundTag serialized) {
        var persisted = playerData.getCompound(Player.PERSISTED_NBT_TAG);
        ListTag pending = persisted.getList(TAG_RETURNS, Tag.TAG_COMPOUND);
        pending.add(serialized.copy());
        persisted.put(TAG_RETURNS, pending);
        playerData.put(Player.PERSISTED_NBT_TAG, persisted);
        actualHeld.setCount(0);
    }

    static final class PreparedReturn {
        private final TianshuPatternReturnBuffer owner;
        private final ItemStack expected;
        private final CompoundTag serialized;
        private boolean committed;

        private PreparedReturn(TianshuPatternReturnBuffer owner, ItemStack expected, CompoundTag serialized) {
            this.owner = owner;
            this.expected = expected;
            this.serialized = serialized;
        }

        void enqueue(ItemStack actualHeld) {
            if (committed) throw new IllegalStateException("Pattern return already committed");
            if (!ItemStack.matches(actualHeld, expected)) {
                // A generic provider that returns a different item must preserve that actual
                // item, never activate the original paid-pattern snapshot as its replacement.
                owner.enqueue(actualHeld);
            } else {
                owner.commit(actualHeld, serialized);
            }
            committed = true;
        }
    }
}
