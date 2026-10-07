package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.List;
import com.moakiee.ae2lt.network.LargeFactoryStatusPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla menu actions validate host identity, range and page on the server. */
public final class LargeFactoryMenu extends AbstractContainerMenu {
    public static final MenuType<LargeFactoryMenu> TYPE = IMenuTypeExtension.create(LargeFactoryMenu::clientCreate);
    public static final int MODE = 0, POWER = 1, PREVIEW = 2, SCAN = 3, PAGE_PREVIOUS = 4, PAGE_NEXT = 5,
            ENTRIES_PREVIOUS = 6, ENTRIES_NEXT = 7, RECOVER = 8, BUILD = 9, TOGGLE_ENTRY = 1000;
    private final BlockEntity host;
    private final BlockPos position;
    private final Inventory playerInventory;
    public final LargeFactoryComponent component;
    private final int machineSlots;
    private int page;
    private int entryPage;
    private long nextSync;
    private LargeFactorySnapshot sent;
    public LargeFactorySnapshot snapshot = LargeFactorySnapshot.EMPTY;

    private LargeFactoryMenu(int id, Inventory inventory, BlockPos position, BlockEntity host,
            LargeFactoryComponent component, IItemHandler items) {
        super(TYPE, id);
        this.host = host; this.position = position; this.playerInventory = inventory; this.component = component;
        machineSlots = items.getSlots();
        for (int slot = 0; slot < machineSlots; slot++) addSlot(new PagedSlot(items, slot));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory,
                col + row * 9 + 9, 12 + col * 18, 136 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 12 + col * 18, 194));
        positionSlots();
    }
    private static LargeFactoryMenu clientCreate(int id, Inventory inventory, FriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        var component = buf.readEnum(LargeFactoryComponent.class);
        int count = buf.readVarInt();
        if (count < 0 || count > 144 || component.isPatternHatch() && count != component.patternSlots()) throw new IllegalArgumentException("Invalid factory inventory size");
        return new LargeFactoryMenu(id, inventory, pos, null, component, new ItemStackHandler(count));
    }
    private static int slotCount(LargeFactoryComponent component) {
        return component.isPatternHatch() ? component.patternSlots()
                : component == LargeFactoryComponent.CRYSTAL_HATCH || component == LargeFactoryComponent.PROCESS_CORE_HATCH ? 9 : 0;
    }
    public static InteractionResult open(Level level, BlockPos pos, Player player) {
        var be = level.getBlockEntity(pos);
        if (be == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            var component = LargeFactoryRegistration.component(be.getBlockState());
            var inventory = be instanceof LargeFactoryHatchBlockEntity hatch ? hatch.inventory().toItemHandler()
                    : be instanceof LargeFactoryAuxBlockEntity aux ? aux.inventory() : new ItemStackHandler(0);
            server.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new LargeFactoryMenu(id, inv, pos, be, component, inventory),
                    be.getBlockState().getBlock().getName()), buf -> { buf.writeBlockPos(pos); buf.writeEnum(component); buf.writeVarInt(inventory.getSlots()); });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean stillValid(Player player) {
        return player.distanceToSqr(position.getCenter()) <= 64
                && (host == null || !host.isRemoved() && host.getLevel() == player.level() && player.level().getBlockEntity(position) == host);
    }
    public int page() { return page; }
    public int pages() { return Math.max(1, (machineSlots + 35) / 36); }
    public int machineSlots() { return machineSlots; }
    public void setPage(int page) { this.page = Math.clamp(page, 0, pages() - 1); positionSlots(); }
    private void positionSlots() {
        for (int i = 0; i < machineSlots; i++) {
            var slot = slots.get(i); int local = i % 36;
            slot.x = i / 36 == page ? 12 + local % 9 * 18 : -10000;
            slot.y = i / 36 == page ? 58 + local / 9 * 18 : -10000;
        }
    }
    private LargeFactoryControllerBlockEntity controller() {
        return host instanceof LargeFactoryControllerBlockEntity c ? c : host instanceof LargeFactoryHatchBlockEntity h ? h.controller()
                : host instanceof LargeFactoryAuxBlockEntity a ? a.controller() : null;
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) return false;
        var controller = controller();
        if (id == PAGE_PREVIOUS || id == PAGE_NEXT) { setPage(page + (id == PAGE_NEXT ? 1 : -1)); return true; }
        if (host == null) return false;
        if (id == MODE && host instanceof LargeFactoryHatchBlockEntity hatch) hatch.togglePassive();
        else if (id == BUILD && controller != null && player instanceof ServerPlayer server) controller.startBuild(server);
        else if (id == POWER && controller != null) controller.toggleNetworkEnergy();
        else if (id == PREVIEW && controller != null) controller.togglePreview();
        else if (id == SCAN && controller != null) controller.requestScan();
        else if (id == ENTRIES_PREVIOUS || id == ENTRIES_NEXT) entryPage = Math.clamp(entryPage + (id == ENTRIES_NEXT ? 1 : -1), 0, 42);
        else if (id >= TOGGLE_ENTRY && host instanceof LargeFactoryHatchBlockEntity hatch) {
            int index = id - TOGGLE_ENTRY;
            if (index / 6 != entryPage) return false;
            hatch.toggleEntry(index);
        } else if (id == RECOVER && host instanceof LargeFactoryHatchBlockEntity hatch) hatch.exportRecovery(player);
        else return false;
        nextSync = 0; sent = null;
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var original = slot.getItem().copy(); var stack = slot.getItem();
        if (!(index < machineSlots ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, machineSlots, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(playerInventory.player instanceof ServerPlayer player) || host == null || player.server.getTickCount() < nextSync) return;
        var controller = controller();
        var hatch = host instanceof LargeFactoryHatchBlockEntity h ? h : null;
        var rows = new ArrayList<LargeFactorySnapshot.Row>();
        var entries = hatch == null ? List.<LargeFactoryHatchBlockEntity.Entry>of() : hatch.entries();
        entryPage = Math.clamp(entryPage, 0, Math.max(0, (entries.size() - 1) / 6));
        for (int i = entryPage * 6; i < Math.min(entries.size(), entryPage * 6 + 6); i++) {
            var entry = entries.get(i); var recipe = entry.bound();
            long energy = 0;
            if (recipe != null) try { energy = LargeFactoryConfig.energy(recipe); } catch (ArithmeticException overflow) { energy = Long.MAX_VALUE; }
            rows.add(new LargeFactorySnapshot.Row(i, entry.slot, entry.pattern.getPrimaryOutput(), hatch.enabled(entry), entry.status,
                    recipe == null ? "" : recipe.id().toString(), entry.operations(), energy,
                    recipe == null ? 0 : recipe.lightning().highVoltage(), recipe == null ? 0 : recipe.lightning().extremeHighVoltage()));
        }
        var issues = new ArrayList<LargeFactorySnapshot.Issue>();
        if (controller != null && controller.lastScan() != null) for (var issue : controller.lastScan().diagnostics().stream().limit(6).toList()) {
            issues.add(new LargeFactorySnapshot.Issue(issue.problem().name().toLowerCase(java.util.Locale.ROOT), issue.position(),
                    issue.expected() == null ? "" : issue.expected().name().toLowerCase(java.util.Locale.ROOT)));
        }
        if (controller != null && issues.size() < 7) {
            int[] missing = controller.missing();
            if (missing[0] > 0) issues.add(new LargeFactorySnapshot.Issue("missing_frames", controller.getBlockPos(), Integer.toString(missing[0])));
            if (missing[1] > 0) issues.add(new LargeFactorySnapshot.Issue("missing_casings", controller.getBlockPos(), Integer.toString(missing[1])));
        }
        boolean formed = controller != null && controller.formed();
        var account = hatch == null ? null : hatch.account();
        snapshot = new LargeFactorySnapshot(hatch != null ? hatch.status() : controller == null ? "unformed" : controller.status(),
                formed, hatch != null && hatch.passive(), controller == null || controller.allowNetworkEnergy(),
                formed ? controller.core().name().toLowerCase(java.util.Locale.ROOT) : "",
                controller == null ? 0 : controller.energyStored(), controller == null ? 0 : controller.energyCapacity(),
                formed ? controller.budget().remainingOperations(host.getLevel().getGameTime()) : 0,
                formed ? LargeFactoryConfig.operations(controller.core()) : 0, account == null ? 0 : account.resources.size(),
                entries.size(), entryPage, List.copyOf(rows), List.copyOf(issues));
        if (!snapshot.equals(sent)) PacketDistributor.sendToPlayer(player, new LargeFactoryStatusPacket(containerId, snapshot));
        sent = snapshot; nextSync = player.server.getTickCount() + 4;
    }
    private final class PagedSlot extends SlotItemHandler {
        private final int index;
        PagedSlot(IItemHandler items, int index) { super(items, index, 0, 0); this.index = index; }
        @Override public boolean isActive() { return index / 36 == page; }
        @Override public boolean mayPickup(Player player) { return isActive() && super.mayPickup(player); }
        @Override public boolean mayPlace(ItemStack stack) { return isActive() && super.mayPlace(stack); }
    }
}
