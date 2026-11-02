package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Native player persistence, inventory custody and registered END-tick return delivery. */
@GameTestHolder("ae2lt_pattern_returns")
@PrefixGameTestTemplate(false)
public final class TianshuPatternReturnGameTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final BlockPos PLAYER_POS = new BlockPos(2, 2, 2);
    private static final BlockPos BARREL_POS = new BlockPos(3, 2, 3);
    private static final String RETURNS_KEY = "ae2ltTianshuPatternReturns";

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fullMainInventoryRetainsQueuedPatternAcrossNativePlayerSaveLoad(GameTestHelper helper) {
        var original = fullPlayer(helper, "R7FullReturn");
        var actualHeld = paidPattern(helper, "full-save");
        var expected = actualHeld.copy();
        enqueue(helper, original, actualHeld);
        assertQueued(helper, original, expected, 0, 1);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(!TianshuPatternReturnBuffer.forPlayer(original).deliver(original.getInventory()),
                    "A full native main inventory accepted the held pattern");
            assertFullMain(helper, original.getInventory());
            assertQueued(helper, original, expected, 0, 1);
        }
        var restored = nativePlayerRoundTrip(helper, original);
        assertFullMain(helper, restored.getInventory());
        assertQueued(helper, restored, expected, 0, 1);
        helper.assertTrue(!TianshuPatternReturnBuffer.forPlayer(restored).deliver(restored.getInventory()),
                "The full reloaded native inventory unexpectedly accepted the queued item");
        var reloadedAgain = nativePlayerRoundTrip(helper, restored);
        assertFullMain(helper, reloadedAgain.getInventory());
        assertQueued(helper, reloadedAgain, expected, 0, 1);
        helper.assertTrue(countInPlayer(reloadedAgain, expected) == 0,
                "Full-inventory persistence also materialized a physical pattern");
        assertNoDroppedPattern(helper, expected);
        LOG.info("TIANSHU_PATTERN_RETURN_FULL_SAVE_PASS mainSlots=36 queued=1 nativeSaves=2");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void emptySlotReceivesExactNbtOnceAndPlayerSaveDoesNotReplay(GameTestHelper helper) {
        var original = fullPlayer(helper, "R7OnceReturn");
        var actualHeld = paidPattern(helper, "deliver-once");
        var expected = actualHeld.copy();
        enqueue(helper, original, actualHeld);
        var restored = nativePlayerRoundTrip(helper, original);
        var barrel = barrel(helper);
        restored.getInventory().setItem(5, ItemStack.EMPTY);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).deliver(restored.getInventory()),
                "An empty native main slot did not receive the queued pattern");
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).pendingCount() == 0
                        && countInPlayer(restored, expected) == 1,
                "Delivery did not transfer exactly one item out of persistent queue custody");
        moveToBarrel(helper, restored, expected, barrel, 0);
        // Leave a real main slot empty during replay, so inventory fullness cannot hide duplication.
        helper.assertTrue(restored.getInventory().getFreeSlot() >= 0,
                "Replay setup did not leave actual player inventory space");
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(!TianshuPatternReturnBuffer.forPlayer(restored).deliver(restored.getInventory()),
                    "An already delivered queue entry replayed into the empty inventory");
        }
        var reloadedAgain = nativePlayerRoundTrip(helper, restored);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(reloadedAgain).pendingCount() == 0
                        && reloadedAgain.getInventory().getFreeSlot() >= 0,
                "Native player save/load recreated an already delivered queue entry");
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(!TianshuPatternReturnBuffer.forPlayer(reloadedAgain).deliver(reloadedAgain.getInventory()),
                    "Native reload replayed an already delivered pattern");
        }
        helper.assertTrue(countInPlayer(reloadedAgain, expected) == 0,
                "The paid pattern exists in both the player and its collection barrel");
        assertExactStack(helper, barrel.getItem(0), expected,
                "Collection barrel lost the delivered pattern's complete native NBT");
        assertNoDroppedPattern(helper, expected);
        LOG.info("TIANSHU_PATTERN_RETURN_ONCE_NBT_PASS delivered=1 queued=0 nativeSaves=2 replays=6");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void registeredEndTickDeliversOnePersistedEntryAndStartTickDoesNot(GameTestHelper helper) {
        var original = fullPlayer(helper, "R7TickReturn");
        var firstHeld = paidPattern(helper, "tick-first");
        var secondHeld = paidPattern(helper, "tick-second");
        var first = firstHeld.copy();
        var second = secondHeld.copy();
        enqueue(helper, original, firstHeld);
        enqueue(helper, original, secondHeld);
        var restored = nativePlayerRoundTrip(helper, original);
        assertQueued(helper, restored, first, 0, 2);
        assertQueued(helper, restored, second, 1, 2);
        playerTick(restored, TickEvent.Phase.END);
        assertFullMain(helper, restored.getInventory());
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).pendingCount() == 2,
                "The registered END tick lost entries while all 36 main slots were full");
        restored.getInventory().setItem(4, ItemStack.EMPTY);
        restored.getInventory().setItem(17, ItemStack.EMPTY);
        playerTick(restored, TickEvent.Phase.START);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).pendingCount() == 2
                        && countInPlayer(restored, first) == 0 && countInPlayer(restored, second) == 0,
                "The return handler delivered during START instead of END");
        playerTick(restored, TickEvent.Phase.END);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).pendingCount() == 1
                        && countInPlayer(restored, first) == 1 && countInPlayer(restored, second) == 0,
                "The registered END tick did not deliver exactly the first persisted item");
        assertQueued(helper, restored, second, 0, 1);
        playerTick(restored, TickEvent.Phase.END);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).pendingCount() == 0
                        && countInPlayer(restored, first) == 1 && countInPlayer(restored, second) == 1,
                "A second END tick did not deliver exactly the second persisted item");
        var barrel = barrel(helper);
        moveToBarrel(helper, restored, first, barrel, 0);
        moveToBarrel(helper, restored, second, barrel, 1);
        var reloadedAgain = nativePlayerRoundTrip(helper, restored);
        for (int i = 0; i < 3; i++) playerTick(reloadedAgain, TickEvent.Phase.END);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(reloadedAgain).pendingCount() == 0
                        && countInPlayer(reloadedAgain, first) == 0 && countInPlayer(reloadedAgain, second) == 0,
                "Automatic END ticks replayed delivered entries after native reload");
        assertExactStack(helper, barrel.getItem(0), first, "First automatic delivery changed complete NBT");
        assertExactStack(helper, barrel.getItem(1), second, "Second automatic delivery changed complete NBT");
        assertNoDroppedPattern(helper, first);
        LOG.info("TIANSHU_PATTERN_RETURN_END_TICK_PASS delivered=2 queued=0 nativeSaves=2");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void nativeRespawnTransfersPersistedQueueAndRemovedPlayerCannotDeliver(GameTestHelper helper) {
        var original = fullPlayer(helper, "R7CloneReturn");
        var actualHeld = paidPattern(helper, "native-respawn");
        var expected = actualHeld.copy();
        enqueue(helper, original, actualHeld);
        var respawned = new FakePlayer(helper.getLevel(), original.getGameProfile());
        respawned.restoreFrom(original, false);
        helper.assertTrue(respawned != original && respawned.getInventory() != original.getInventory(),
                "Native respawn did not use a new player and its native inventory");
        assertQueued(helper, respawned, expected, 0, 1);
        fillMain(respawned.getInventory());
        original.discard();
        original.getInventory().setItem(0, ItemStack.EMPTY);
        playerTick(original, TickEvent.Phase.END);
        helper.assertTrue(original.isRemoved() && countInPlayer(original, expected) == 0,
                "The removed pre-respawn player received the persisted return");
        assertQueued(helper, respawned, expected, 0, 1);
        var restored = nativePlayerRoundTrip(helper, respawned);
        assertFullMain(helper, restored.getInventory());
        assertQueued(helper, restored, expected, 0, 1);
        restored.getInventory().setItem(2, ItemStack.EMPTY);
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(restored).deliver(restored.getInventory()),
                "The native respawn player's saved queue could not deliver into a free main slot");
        var barrel = barrel(helper);
        moveToBarrel(helper, restored, expected, barrel, 0);
        var reloadedAgain = nativePlayerRoundTrip(helper, restored);
        helper.assertTrue(!TianshuPatternReturnBuffer.forPlayer(reloadedAgain).deliver(reloadedAgain.getInventory())
                        && TianshuPatternReturnBuffer.forPlayer(reloadedAgain).pendingCount() == 0
                        && countInPlayer(reloadedAgain, expected) == 0,
                "Native respawn persistence replayed an already collected return");
        assertExactStack(helper, barrel.getItem(0), expected, "Native respawn changed the returned item's complete NBT");
        assertNoDroppedPattern(helper, expected);
        LOG.info("TIANSHU_PATTERN_RETURN_RESPAWN_PASS delivered=1 queued=0 nativeSaves=2 removedPlayerDeliveries=0");
        helper.succeed();
    }

    private static FakePlayer fullPlayer(GameTestHelper helper, String name) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        var pos = helper.absolutePos(PLAYER_POS);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        fillMain(player.getInventory());
        assertFullMain(helper, player.getInventory());
        return player;
    }

    private static void fillMain(Inventory inventory) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
    }

    private static void assertFullMain(GameTestHelper helper, Inventory inventory) {
        helper.assertTrue(inventory.items.size() == 36 && inventory.getFreeSlot() == -1,
                "The real native inventory does not have 36 full main slots");
        for (int slot = 0; slot < 36; slot++) {
            var stack = inventory.getItem(slot);
            helper.assertTrue(stack.is(Items.STONE) && stack.getCount() == 64 && !stack.hasTag(),
                    "Full-inventory return changed native filler slot " + slot);
        }
    }

    private static void enqueue(GameTestHelper helper, ServerPlayer player, ItemStack actualHeld) {
        helper.assertTrue(actualHeld.getCount() == 1, "Queue setup must hand over one actual paid item");
        TianshuPatternReturnBuffer.forPlayer(player).enqueue(actualHeld);
        helper.assertTrue(actualHeld.isEmpty(), "Enqueue did not retire the caller's actual held item");
    }

    private static FakePlayer nativePlayerRoundTrip(GameTestHelper helper, ServerPlayer original) {
        // Exercise Entity/Player serialization plus native compressed NBT, rather than copying
        // just getPersistentData or manually writing the return queue to another player.
        var nativeSaved = original.saveWithoutId(new CompoundTag());
        int pending = TianshuPatternReturnBuffer.forPlayer(original).pendingCount();
        helper.assertTrue(nativeSaved.contains("Inventory", Tag.TAG_LIST)
                        && (pending == 0 || nativeSaved.getCompound("ForgeData")
                                .contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)),
                "Native player save omitted its inventory or Forge PlayerPersisted payload");
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nativeSaved, bytes);
            var nativeLoaded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
            var restored = new FakePlayer(helper.getLevel(), original.getGameProfile());
            restored.load(nativeLoaded);
            helper.assertTrue(restored != original && restored.getInventory() != original.getInventory()
                            && restored.isAlive() && !restored.isRemoved(),
                    "Native player reload did not restore a distinct live player's native inventory");
            return restored;
        } catch (IOException failure) {
            throw new IllegalStateException("Native compressed player save/load failed", failure);
        }
    }

    private static void assertQueued(GameTestHelper helper, ServerPlayer player,
                                     ItemStack expected, int index, int expectedCount) {
        helper.assertTrue(TianshuPatternReturnBuffer.forPlayer(player).pendingCount() == expectedCount,
                "Player queue has the wrong number of pending physical items");
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var entries = persisted.getList(RETURNS_KEY, Tag.TAG_COMPOUND);
        helper.assertTrue(entries.size() == expectedCount && index >= 0 && index < entries.size(),
                "Pending returns were not stored under native PlayerPersisted");
        assertExactStack(helper, ItemStack.of(entries.getCompound(index)), expected,
                "Player persistence changed a pending item's complete native NBT");
    }

    private static ItemStack paidPattern(GameTestHelper helper, String identity) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3)},
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)});
        pattern.setHoverName(Component.literal("R7 paid pattern " + identity));
        var detail = new CompoundTag();
        detail.putString("custody", identity);
        detail.putBoolean("substitute", true);
        detail.putLong("setting", 100_000_000_000L);
        detail.putIntArray("configuration", new int[] {1, 64, 4096});
        detail.putByteArray("opaque", new byte[] {1, 0, -1, 127});
        var members = new ListTag();
        var member = new CompoundTag();
        member.putString("recipe", "ae2lt:r7_native_return");
        member.putLongArray("limits", new long[] {1L, 9_000_000_000L});
        members.add(member);
        detail.put("members", members);
        pattern.getOrCreateTag().put("ae2ltReturnNativePayload", detail);
        helper.assertTrue(pattern.getCount() == 1
                        && PatternDetailsHelper.decodePattern(pattern, helper.getLevel()) != null,
                "The held return is not a real decodable AE2 encoded processing pattern");
        return pattern;
    }

    private static BarrelBlockEntity barrel(GameTestHelper helper) {
        helper.setBlock(BARREL_POS, Blocks.BARREL);
        return (BarrelBlockEntity) helper.getBlockEntity(BARREL_POS);
    }

    private static void moveToBarrel(GameTestHelper helper, ServerPlayer player, ItemStack expected,
                                     BarrelBlockEntity barrel, int barrelSlot) {
        int found = -1;
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (ItemStack.isSameItemSameTags(player.getInventory().getItem(slot), expected)) {
                helper.assertTrue(found < 0, "One queued pattern occupied multiple native player slots");
                found = slot;
            }
        }
        helper.assertTrue(found >= 0 && barrel.getItem(barrelSlot).isEmpty(),
                "The actual delivered item or empty native barrel slot was missing");
        var actual = player.getInventory().removeItemNoUpdate(found);
        assertExactStack(helper, actual, expected, "Native player delivery changed the complete item snapshot");
        barrel.setItem(barrelSlot, actual);
        helper.assertTrue(player.getInventory().getItem(found).isEmpty(),
                "The collected item remained in its native player source slot");
    }

    private static int countInPlayer(ServerPlayer player, ItemStack expected) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameTags(stack, expected)) count += stack.getCount();
        }
        return count;
    }

    private static void assertExactStack(GameTestHelper helper, ItemStack actual,
                                         ItemStack expected, String message) {
        helper.assertTrue(ItemStack.matches(actual, expected)
                        && actual.save(new CompoundTag()).equals(expected.save(new CompoundTag())), message);
    }

    private static void assertNoDroppedPattern(GameTestHelper helper, ItemStack expected) {
        var bounds = new AABB(helper.absolutePos(PLAYER_POS)).inflate(2);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                        .noneMatch(entity -> entity.getItem().is(expected.getItem())),
                "A full inventory return was dropped into the world instead of retained durably");
    }

    private static void playerTick(ServerPlayer player, TickEvent.Phase phase) {
        // Use the real Forge bus and production subscription, never a manually registered test listener.
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(phase, player));
    }
}
