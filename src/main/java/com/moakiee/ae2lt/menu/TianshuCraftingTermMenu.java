package com.moakiee.ae2lt.menu;

import appeng.api.config.FuzzyMode;
import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.helpers.InventoryAction;
import appeng.helpers.IMenuCraftingPacket;
import appeng.items.storage.ViewCellItem;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CarriedItemInventory;
import appeng.util.inv.PlayerInternalInventory;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalTarget;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalViewMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

/** Five manual workstations whose real inputs belong to the terminal host. */
public class TianshuCraftingTermMenu extends CraftingTermMenu implements TianshuMaintenanceMenu {
    public static final MenuType<TianshuCraftingTermMenu> TYPE = Ae2ltMenuBuilder.buildUnregistered(
            MenuTypeBuilder.create((MenuTypeBuilder.MenuFactory<TianshuCraftingTermMenu, TianshuCraftingTerminalHost>)
                    TianshuCraftingTermMenu::new, TianshuCraftingTerminalHost.class),
            new ResourceLocation("ae2lt", "tianshu_crafting_terminal"));
    public static final int CELL_CONFIG_SLOTS = 63;
    public static final int CELL_PAGE_SIZE = 9;

    @GuiSync(150) public TianshuWorkPage workPage = TianshuWorkPage.CRAFTING;
    @GuiSync(151) public int cellConfigRow;
    @GuiSync(152) public int cellConfigSize;
    @GuiSync(153) public int cellUpgradeSize;
    @GuiSync(154) public FuzzyMode cellFuzzyMode = FuzzyMode.IGNORE_ALL;
    @GuiSync(155) public int anvilCost;
    @GuiSync(156) public int stoneRecipe = -1;
    @GuiSync(157) public boolean maintenanceAvailable;
    @GuiSync(158) public boolean maintainableView;
    @Nullable private TianshuTerminalViewMode clientViewMode;
    @GuiSync(159) public int tianshuSelectionRevision;
    @GuiSync(160) public int cellUpgradeRow;
    @GuiSync(161) public CopyMode cellCopyMode = CopyMode.CLEAR_ON_REMOVE;
    @GuiSync(162) public String anvilItemName = "";
    private ItemStack namedAnvilInput = ItemStack.EMPTY;

    protected final TianshuCraftingTerminalHost tianshuHost;
    private final TianshuMaintenanceSession maintenanceSession;
    @Nullable private TianshuTerminalTarget boundTianshuTarget;
    private final FakeSlot globalReserveMarkSlot;
    private final SmithingMenu smithing;
    private final OverloadAlloyAnvilMenu anvil;
    private final StonecutterMenu stonecutter;
    // Native engines retain their callbacks; their inputs and configuration are mirrored to the host.
    private final CellWorkbenchBlockEntity cellWorkbench = new CellWorkbenchBlockEntity(
            AEBlocks.CELL_WORKBENCH.block().getBlockEntityType(), BlockPos.ZERO,
            AEBlocks.CELL_WORKBENCH.block().defaultBlockState()) {
        @Override public void saveChanges() {
            super.saveChanges();
            persistWorkstations();
        }
    };
    private final AppEngInternalInventory cellInventory =
            (AppEngInternalInventory) cellWorkbench.getSubInventory(ISegmentedInventory.CELLS);
    private ItemStack configuredCell = ItemStack.EMPTY;
    private final List<Slot> extraInputs = new ArrayList<>();
    private TianshuWorkstationStorage workstationStorage;
    private CompoundTag emptyWorkstations;
    private boolean updatingWorkstations;
    private boolean closed;

    public TianshuCraftingTermMenu(int id, Inventory inventory, TianshuCraftingTerminalHost host) {
        this(TYPE, id, inventory, host);
    }

    protected TianshuCraftingTermMenu(MenuType<?> type, int id, Inventory inventory, TianshuCraftingTerminalHost host) {
        super(type, id, inventory, host, true);
        tianshuHost = host;
        maintenanceSession = new TianshuMaintenanceSession(this, this::resolveBoundTianshu,
                () -> tianshuSelectionRevision, action -> sendClientAction("maintenanceAction", action));
        globalReserveMarkSlot = new FakeSlot(new AppEngInternalInventory(null, 1, 1), 0);
        globalReserveMarkSlot.x = globalReserveMarkSlot.y = -10000;
        addSlot(globalReserveMarkSlot, Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK);

        // These menus are computation/callback engines, without a packet synchronizer or a world block to clear.
        smithing = new SmithingMenu(id, inventory);
        anvil = new OverloadAlloyAnvilMenu(id, inventory, ContainerLevelAccess.NULL);
        stonecutter = new TianshuStonecutterMenu(id, inventory);
        addWorkSlots(smithing, 3, TianshuWorkPage.SMITHING, Ae2ltSlotSemantics.TIANSHU_SMITHING);
        addWorkSlots(anvil, 2, TianshuWorkPage.ANVIL, Ae2ltSlotSemantics.TIANSHU_ANVIL);
        addWorkSlots(stonecutter, 1, TianshuWorkPage.STONECUTTING, Ae2ltSlotSemantics.TIANSHU_STONECUTTING);

        cellInventory.setMaxStackSize(0, 1);
        var cellSlot = new AppEngSlot(cellInventory, 0) {
            @Override public void setChanged() { super.setChanged(); if (isServerSide()) refreshCell(); }
            @Override public boolean mayPlace(ItemStack stack) {
                return workPage == TianshuWorkPage.CELL && stack.getItem() instanceof ICellWorkbenchItem cell && cell.isEditable(stack);
            }
            @Override public boolean mayPickup(Player player) { return workPage == TianshuWorkPage.CELL; }
        };
        addSlot(cellSlot, Ae2ltSlotSemantics.TIANSHU_CELL);
        for (int i = 0; i < 8; i++) {
            final int inventoryIndex = i;
            addSlot(new AppEngSlot(cellProxy(true), i) {
                @Override public ItemStack getItem() { return getInventory().getStackInSlot(inventoryIndex); }
                @Override public boolean isSlotEnabled() { return isCellUpgradeVisible(inventoryIndex); }
                @Override public boolean mayPlace(ItemStack stack) { return isSlotEnabled() && super.mayPlace(stack); }
                @Override public boolean mayPickup(Player player) { return isSlotEnabled() && super.mayPickup(player); }
            }, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE);
        }
        // Fixed slot identities are essential: a delayed click from page 1 must never write page 2.
        for (int i = 0; i < CELL_CONFIG_SLOTS; i++) {
            final int inventoryIndex = i;
            addSlot(new FakeSlot(cellProxy(false), i) {
                @Override public ItemStack getItem() { return getInventory().getStackInSlot(inventoryIndex); }
                @Override public boolean isSlotEnabled() { return isCellMarkVisible(inventoryIndex); }
                @Override public boolean canSetFilterTo(ItemStack stack) { return isSlotEnabled() && super.canSetFilterTo(stack); }
            }, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
        }
        registerClientAction("workPage", TianshuWorkPage.class, this::setWorkPage);
        registerClientAction("cellConfigRow", Integer.class, this::setCellConfigRow);
        registerClientAction("cellUpgradeRow", Integer.class, this::setCellUpgradeRow);
        registerClientAction("cellFuzzy", FuzzyMode.class, this::setCellFuzzyMode);
        registerClientAction("cellCopyMode", CopyMode.class, this::setCellCopyMode);
        registerClientAction("anvilName", String.class, this::setAnvilName);
        registerClientAction("stoneRecipe", Integer.class, this::selectStoneRecipe);
        registerClientAction("workRecipe", String.class, this::fillWorkRecipe);
        registerClientAction("craftWorkRecipe", String.class, recipe -> fillWorkRecipe(recipe, true));
        registerClientAction("anvilRecipe", AnvilTransferRequest.class, this::fillAnvilRecipeFromRequest);
        registerClientAction("clearWorkToNetwork", TianshuWorkPage.class, page -> clearWorkInputs(page, false));
        registerClientAction("clearWorkToPlayer", TianshuWorkPage.class, page -> clearWorkInputs(page, true));
        registerClientAction("setMaintainableView", Boolean.class, this::setMaintainableView);
        registerClientAction("setTerminalViewMode", TianshuTerminalViewMode.class, this::setTerminalViewMode);
        registerClientAction("setTemporaryTerminalViewMode", TianshuTerminalViewMode.class, mode -> applyTerminalViewMode(mode, false));
        registerClientAction("maintenanceAction", TianshuMaintenanceSession.MaintenanceAction.class, maintenanceSession::maintenanceActionServer);
        updateSlotAccess();
        if (isServerSide()) {
            emptyWorkstations = snapshotWorkstations();
            workstationStorage = host.getWorkstationStorage();
            restoreWorkstations();
            workstationStorage.subscribe(this, this::restoreWorkstations);
            for (var engine : List.of(smithing, anvil, stonecutter))
                ((SimpleContainer) engine.getSlot(0).container).addListener(ignored -> persistWorkstations());
        }
    }

    private void addWorkSlots(AbstractContainerMenu engine, int inputCount, TianshuWorkPage page, SlotSemantic semantic) {
        for (int i = 0; i <= inputCount; i++) {
            var slot = new TianshuWorkSlot(engine.getSlot(i), () -> workPage == page, i == inputCount);
            addSlot(slot, semantic);
            if (i < inputCount) extraInputs.add(slot);
        }
    }

    private InternalInventory cellProxy(boolean upgrades) {
        return new InternalInventory() {
            private InternalInventory delegate() {
                return upgrades ? cellWorkbench.getUpgrades() : cellWorkbench.getConfig().createMenuWrapper();
            }
            @Override public int size() { return upgrades ? 8 : CELL_CONFIG_SLOTS; }
            @Override public int getSlotLimit(int slot) { var inv = delegate(); return slot < inv.size() ? inv.getSlotLimit(slot) : 0; }
            @Override public ItemStack getStackInSlot(int slot) { var inv = delegate(); return slot < inv.size() ? inv.getStackInSlot(slot) : ItemStack.EMPTY; }
            @Override public void setItemDirect(int slot, ItemStack stack) {
                var inv = delegate();
                if (slot < inv.size()) inv.setItemDirect(slot, stack);
            }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                var inv = delegate(); return slot < inv.size() && inv.isItemValid(slot, stack);
            }
        };
    }

    public ItemStack getCell() { return cellInventory.getStackInSlot(0); }
    public int getCellConfigRows() { return (cellConfigSize + 2) / 3; }
    public int getMaxCellConfigRow() { return Math.max(0, getCellConfigRows() - 3); }
    public int getMaxCellUpgradeRow() { return Math.max(0, cellUpgradeSize - 3); }
    public boolean isCellMarkVisible(int index) {
        return workPage == TianshuWorkPage.CELL && index >= cellConfigRow * 3
                && index < Math.min(cellConfigSize, cellConfigRow * 3 + CELL_PAGE_SIZE);
    }
    public boolean isCellUpgradeVisible(int index) {
        return workPage == TianshuWorkPage.CELL && index >= cellUpgradeRow
                && index < Math.min(cellUpgradeSize, cellUpgradeRow + 3);
    }
    public boolean hasCompactWorkArea() { return false; }
    public StonecutterMenu getStonecutter() { return stonecutter; }
    public OverloadAlloyAnvilMenu getAnvil() { return anvil; }
    public ItemStack getAnvilInput() { return anvil.getSlot(0).getItem(); }

    public void setWorkPage(TianshuWorkPage page) {
        if (isClientSide()) { sendClientAction("workPage", page); return; }
        if (!isMainWorkPage()) return;
        workPage = page;
        updateSlotAccess();
        broadcastChanges();
    }

    public void setCellConfigRow(int row) {
        if (isClientSide()) { sendClientAction("cellConfigRow", row); return; }
        if (!isMainWorkPage() || workPage != TianshuWorkPage.CELL) return;
        refreshCell();
        cellConfigRow = net.minecraft.util.Mth.clamp(row, 0, getMaxCellConfigRow());
        broadcastChanges();
    }

    public void setCellUpgradeRow(int row) {
        if (isClientSide()) { sendClientAction("cellUpgradeRow", row); return; }
        if (!isMainWorkPage() || workPage != TianshuWorkPage.CELL) return;
        refreshCell();
        cellUpgradeRow = net.minecraft.util.Mth.clamp(row, 0, getMaxCellUpgradeRow());
        broadcastChanges();
    }

    public void setCellCopyMode(CopyMode mode) {
        if (isClientSide()) { sendClientAction("cellCopyMode", mode); return; }
        if (!isMainWorkPage() || workPage != TianshuWorkPage.CELL) return;
        cellWorkbench.getConfigManager().putSetting(Settings.COPY_MODE, mode);
        broadcastChanges();
    }

    public void setCellFuzzyMode(FuzzyMode mode) {
        if (isClientSide()) { sendClientAction("cellFuzzy", mode); return; }
        if (isMainWorkPage() && workPage == TianshuWorkPage.CELL && getCell().getItem() instanceof ICellWorkbenchItem cell) {
            cell.setFuzzyMode(getCell(), mode);
            persistWorkstations();
        }
    }

    public void setAnvilName(String name) {
        if (name.length() > 50) return;
        if (isClientSide()) { anvilItemName = name; sendClientAction("anvilName", name); return; }
        if (isMainWorkPage() && workPage == TianshuWorkPage.ANVIL) setAnvilNameInternal(name);
    }

    private void setAnvilNameInternal(String name) {
        anvilItemName = name;
        namedAnvilInput = getAnvilInput().copy();
        anvil.setItemName(name);
        persistWorkstations();
    }

    private void updateAnvilInputName() {
        var input = getAnvilInput();
        if (!ItemStack.matches(namedAnvilInput, input))
            setAnvilNameInternal(input.isEmpty() ? "" : input.getHoverName().getString());
    }

    public void selectStoneRecipe(int index) {
        if (isClientSide()) { sendClientAction("stoneRecipe", index); return; }
        if (isMainWorkPage() && workPage == TianshuWorkPage.STONECUTTING) stonecutter.clickMenuButton(getPlayer(), index);
        persistWorkstations();
    }

    protected boolean isMainWorkPage() { return true; }
    public boolean canUseWorkstations() { return isMainWorkPage(); }

    @Override public InternalInventory getCraftingMatrix() {
        // Also gates AE2's recipe-transfer packet, which accesses this inventory without clicking a slot.
        return isMainWorkPage() && workPage == TianshuWorkPage.CRAFTING ? super.getCraftingMatrix() : InternalInventory.empty();
    }

    public void prepareCraftingTransfer() { setWorkPage(TianshuWorkPage.CRAFTING); }

    @Nullable private AbstractContainerMenu workEngine() {
        return switch (workPage) {
            case SMITHING -> smithing;
            case ANVIL -> anvil;
            case STONECUTTING -> stonecutter;
            default -> null;
        };
    }

    private int workInputCount() {
        return switch (workPage) {
            case SMITHING -> 3;
            case ANVIL -> 2;
            case STONECUTTING -> 1;
            default -> 0;
        };
    }

    public boolean isWorkResult(@Nullable Slot slot) {
        return slot instanceof TianshuWorkSlot work && work.result;
    }

    /** The same four gestures and destination inventories as AE2's CraftingTermSlot.doClick. */
    private void craftWorkResult(TianshuWorkSlot result, InventoryAction action) {
        if (isClientSide() || !isMainWorkPage() || !allowsSlot(result.index)
                || !isValidMenu() || !stillValid(getPlayer())) return;
        var engine = workEngine();
        if (engine == null || !result.hasItem() || !result.mayPickup(getPlayer())) return;
        var output = result.getItem().copy();
        boolean toPlayer = action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL;
        int crafts = switch (action) {
            case CRAFT_ITEM -> 1;
            case CRAFT_STACK, CRAFT_SHIFT -> output.getMaxStackSize() / output.getCount();
            case CRAFT_ALL -> output.getMaxStackSize() * Inventory.INVENTORY_SIZE / output.getCount();
            default -> 0;
        };
        InternalInventory target = toPlayer ? new PlayerInternalInventory(getPlayerInventory())
                : new CarriedItemInventory(this);
        var page = workPage;
        int inputCount = workInputCount();
        boolean tookResult = false;
        for (int i = 0; i < crafts; i++) {
            // Components, batch size, XP and destination capacity are rechecked before every native callback.
            if (workPage != page || !isMainWorkPage() || !allowsSlot(result.index)
                    || !ItemStack.matches(output, result.getItem()) || !result.mayPickup(getPlayer())) break;
            boolean oversizedSingle = action == InventoryAction.CRAFT_ITEM && getCarried().isEmpty();
            if (!oversizedSingle && !target.simulateAdd(output).isEmpty()) break;
            var before = new ArrayList<ItemStack>(inputCount);
            for (int j = 0; j < inputCount; j++) before.add(engine.getSlot(j).getItem().copy());
            ResourceLocation stoneId = engine == stonecutter && stonecutter.getSelectedRecipeIndex() >= 0
                    ? stonecutter.getRecipes().get(stonecutter.getSelectedRecipeIndex()).getId() : null;

            var crafted = result.remove(output.getCount());
            if (crafted.isEmpty()) break;
            result.onTake(getPlayer(), crafted.copy());
            tookResult = true;
            // Replenish only emptied inputs, from ME, using the complete original item key.
            // Variable-count anvil recipes restore the amount the native menu actually consumed.
            var filter = ViewCellItem.createItemFilter(getViewCells());
            if (canInteractWithGrid()) for (int j = 0; j < inputCount; j++) {
                var input = engine.getSlot(j);
                var key = AEItemKey.of(before.get(j));
                if (input.hasItem() || key == null || !isKeyVisible(key) || filter != null && !filter.isListed(key)) continue;
                long extracted = StorageHelper.poweredExtraction(powerSource, storage, key,
                        before.get(j).getCount(), getActionSource());
                if (extracted > 0) input.set(key.toStack((int) extracted));
            }
            // Native stonecutting resets selection when its last input is consumed.
            if (stoneId != null) selectStoneRecipe(stoneId);

            if (oversizedSingle) setCarried(crafted);
            else {
                var remaining = target.addItems(crafted);
                if (!remaining.isEmpty()) { getPlayer().drop(remaining, false); break; }
            }
        }
        if (tookResult) {
            // Native engines use NULL world access, so their sound callbacks never run.
            // Emit once per successful gesture, including a stack or shift-click batch.
            var sound = switch (page) {
                case SMITHING -> SoundEvents.SMITHING_TABLE_USE;
                case ANVIL -> SoundEvents.ANVIL_USE;
                case STONECUTTING -> SoundEvents.UI_STONECUTTER_TAKE_RESULT;
                default -> throw new IllegalStateException("Unexpected workstation: " + page);
            };
            var player = getPlayer();
            float pitch = page == TianshuWorkPage.STONECUTTING ? 1.0F : 0.9F + player.getRandom().nextFloat() * 0.1F;
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.BLOCKS, 1.0F, pitch);
        }
        broadcastChanges();
    }

    private void selectStoneRecipe(ResourceLocation id) {
        for (int i = 0; i < stonecutter.getNumRecipes(); i++) {
            if (stonecutter.getRecipes().get(i).getId().equals(id)) {
                stonecutter.clickMenuButton(getPlayer(), i);
                persistWorkstations();
                return;
            }
        }
    }

    public void clearWorkInputs(boolean toPlayer) {
        if (!isMainWorkPage()) return;
        if (workPage == TianshuWorkPage.CRAFTING) {
            if (toPlayer) super.clearToPlayerInventory(); else super.clearCraftingGrid();
        } else if (isClientSide()) sendClientAction(toPlayer ? "clearWorkToPlayer" : "clearWorkToNetwork", workPage);
        else clearWorkInputs(workPage, toPlayer);
    }

    @Override public void clearToPlayerInventory() {
        if (isMainWorkPage() && workPage == TianshuWorkPage.CRAFTING) super.clearToPlayerInventory();
    }

    private void clearWorkInputs(TianshuWorkPage page, boolean toPlayer) {
        if (!isMainWorkPage() || workPage != page || workEngine() == null) return;
        var engine = workEngine();
        var playerInv = new PlayerInternalInventory(getPlayerInventory());
        for (int i = 0; i < workInputCount(); i++) {
            var input = engine.getSlot(i);
            var remaining = input.getItem().copy();
            if (toPlayer) {
                // Native clear-to-player order: filled slots first, hotbar right-to-left, then main inventory.
                for (boolean empty : new boolean[]{false, true}) {
                    for (int j = 8; j >= 0; j--) if (playerInv.getStackInSlot(j).isEmpty() == empty)
                        remaining = playerInv.getSlotInv(j).addItems(remaining);
                    for (int j = 9; j < Inventory.INVENTORY_SIZE; j++) if (playerInv.getStackInSlot(j).isEmpty() == empty)
                        remaining = playerInv.getSlotInv(j).addItems(remaining);
                }
            } else remaining = transferStackToMenu(remaining.copy());
            input.set(remaining);
        }
        broadcastChanges();
    }

    private record WorkIngredient(Predicate<ItemStack> predicate, int amount) {
        boolean test(ItemStack stack) { return predicate.test(stack); }
        boolean optional() { return test(ItemStack.EMPTY); }
    }

    public record WorkRecipeAvailability(MissingIngredientSlots missing, int requiredSlots) {
        public boolean canTransfer() { return requiredSlots > 0 && missing.missingSlots().size() < requiredSlots; }
    }

    /** Only input templates cross the client action. The native anvil owns the output and all costs. */
    public record AnvilTransferRequest(String left, String right, boolean craftMissing) {}

    private List<WorkIngredient> workIngredients(Recipe<?> recipe) {
        if (recipe instanceof SmithingRecipe smith) return List.of(
                new WorkIngredient(smith::isTemplateIngredient, 1),
                new WorkIngredient(smith::isBaseIngredient, 1),
                new WorkIngredient(smith::isAdditionIngredient, 1));
        if (recipe instanceof StonecutterRecipe stone) return List.of(new WorkIngredient(
                stack -> stone.matches(new SimpleContainer(stack), getPlayer().level()), 1));
        return List.of();
    }

    private List<WorkIngredient> anvilIngredients(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty() || left.getCount() != 1
                || right.getCount() < 1 || right.getCount() > Math.min(64, right.getMaxStackSize())) return List.of();
        return List.of(anvilIngredient(left, true), anvilIngredient(right, false));
    }

    private WorkIngredient anvilIngredient(ItemStack template, boolean left) {
        var sample = template.copy();
        return new WorkIngredient(stack -> {
            if (stack.isEmpty() || !ItemStack.isSameItem(sample, stack)) return false;
            // JEI/EMI repair examples use illustrative damage. Any damaged instance is valid;
            // a newly crafted undamaged tool cannot stand in for the tool being repaired.
            if (left && sample.isDamaged() && !stack.isDamaged()) return false;
            if (sample.hasTag()) for (var key : sample.getTag().getAllKeys()) {
                if (key.equals("Damage") || key.equals("RepairCost")) continue;
                if (!Objects.equals(sample.getTag().get(key), stack.hasTag() ? stack.getTag().get(key) : null)) return false;
            }
            return true;
        }, sample.getCount());
    }

    public WorkRecipeAvailability getWorkRecipeAvailability(Recipe<?> recipe) {
        return workRecipeAvailability(recipe instanceof SmithingRecipe ? smithing : stonecutter, workIngredients(recipe));
    }

    public WorkRecipeAvailability getAnvilRecipeAvailability(ItemStack left, ItemStack right) {
        return workRecipeAvailability(anvil, anvilIngredients(left, right));
    }

    /** Client hint only. Reserve each available unit once, including repeated ingredients. */
    private WorkRecipeAvailability workRecipeAvailability(AbstractContainerMenu engine, List<WorkIngredient> ingredients) {
        var missing = new HashSet<Integer>();
        var craftable = new HashSet<Integer>();
        var reservedNetwork = new HashMap<AEItemKey, Long>();
        int[] reservedPlayer = new int[getPlayerInventory().items.size()];
        var filter = ViewCellItem.createItemFilter(getViewCells());
        var repo = getClientRepo();
        var entries = repo != null && isPowered() ? repo.getAllEntries() : java.util.Set.<appeng.menu.me.common.GridInventoryEntry>of();
        int required = 0;
        if (!isMainWorkPage()) return new WorkRecipeAvailability(new MissingIngredientSlots(missing, craftable), 0);
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i);
            if (ingredient.optional()) continue;
            required++;
            var current = engine.getSlot(i).getItem();
            AEItemKey bound = !current.isEmpty() && ingredient.test(current) ? AEItemKey.of(current) : null;
            int needed = ingredient.amount() - (bound != null ? current.getCount() : 0);
            for (var entry : entries) {
                if (needed <= 0) break;
                if (!(entry.getWhat() instanceof AEItemKey key) || (filter != null && !filter.isListed(key))
                        || (bound != null && !bound.equals(key)) || !ingredient.test(key.getReadOnlyStack())) continue;
                int used = (int) Math.min(needed, Math.max(0, entry.getStoredAmount() - reservedNetwork.getOrDefault(key, 0L)));
                if (used > 0) { bound = key; needed -= used; reservedNetwork.merge(key, (long) used, Long::sum); }
            }
            for (int j = 0; j < reservedPlayer.length && needed > 0; j++) {
                var stack = getPlayerInventory().getItem(j);
                if (isPlayerInventorySlotLocked(j) || stack.isEmpty() || !ingredient.test(stack)
                        || (bound != null && !bound.matches(stack))) continue;
                int used = Math.min(needed, Math.max(0, stack.getCount() - reservedPlayer[j]));
                if (used > 0) { bound = AEItemKey.of(stack); needed -= used; reservedPlayer[j] += used; }
            }
            if (needed <= 0) continue;
            boolean canCraft = false;
            for (var entry : entries) {
                if (entry.isCraftable() && entry.getWhat() instanceof AEItemKey key
                        && (filter == null || filter.isListed(key)) && (bound == null || bound.equals(key))
                        && ingredient.test(key.getReadOnlyStack())) { canCraft = true; break; }
            }
            if (canCraft) craftable.add(i); else missing.add(i);
        }
        return new WorkRecipeAvailability(new MissingIngredientSlots(missing, craftable), required);
    }

    public boolean canFillWorkRecipe(Recipe<?> recipe) { return getWorkRecipeAvailability(recipe).canTransfer(); }

    public void fillWorkRecipe(String recipeName) { fillWorkRecipe(recipeName, false); }

    public void fillWorkRecipe(String recipeName, boolean craftMissing) {
        if (recipeName == null || recipeName.length() > 256) return;
        if (isClientSide()) { sendClientAction(craftMissing ? "craftWorkRecipe" : "workRecipe", recipeName); return; }
        if (!isMainWorkPage() || !isValidMenu() || !stillValid(getPlayer())) return;
        var id = ResourceLocation.tryParse(recipeName);
        if (id == null) return;
        var holder = getPlayer().level().getRecipeManager().byKey(id).orElse(null);
        if (holder == null) return;
        var ingredients = workIngredients(holder);
        if (ingredients.isEmpty()) return;
        workPage = holder instanceof SmithingRecipe ? TianshuWorkPage.SMITHING : TianshuWorkPage.STONECUTTING;
        updateSlotAccess();
        var engine = workEngine();
        var toCraft = fillWorkInputs(engine, ingredients, craftMissing);
        if (engine == stonecutter) selectStoneRecipe(id);
        broadcastChanges();
        if (!toCraft.isEmpty()) startAutoCrafting(toCraft);
    }

    public void fillAnvilRecipe(ItemStack left, ItemStack right, boolean craftMissing) {
        if (anvilIngredients(left, right).isEmpty()) return;
        if (isClientSide()) {
            sendClientAction("anvilRecipe", new AnvilTransferRequest(
                    left.save(new CompoundTag()).toString(), right.save(new CompoundTag()).toString(), craftMissing));
            return;
        }
        if (!isMainWorkPage() || !isValidMenu() || !stillValid(getPlayer())) return;
        workPage = TianshuWorkPage.ANVIL;
        updateSlotAccess();
        var toCraft = fillWorkInputs(anvil, anvilIngredients(left, right), craftMissing);
        // Transfer can finish while JEI/EMI still covers the terminal's name editor.
        // Initialize the native name now, so a repair never silently removes a custom name.
        var input = anvil.getSlot(0).getItem();
        if (!input.isEmpty()) setAnvilNameInternal(input.getHoverName().getString());
        broadcastChanges();
        if (!toCraft.isEmpty()) startAutoCrafting(toCraft);
    }

    private void fillAnvilRecipeFromRequest(AnvilTransferRequest request) {
        if (request == null || request.left() == null || request.right() == null
                || request.left().length() > 16384 || request.right().length() > 16384) return;
        ItemStack left, right;
        try {
            left = ItemStack.of(TagParser.parseTag(request.left()));
            right = ItemStack.of(TagParser.parseTag(request.right()));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException error) { return; }
        fillAnvilRecipe(left, right, request.craftMissing());
    }

    private List<IMenuCraftingPacket.AutoCraftEntry> fillWorkInputs(AbstractContainerMenu engine,
            List<WorkIngredient> ingredients, boolean craftMissing) {
        var toCraft = new LinkedHashMap<AEItemKey, List<Integer>>();
        var filter = ViewCellItem.createItemFilter(getViewCells());
        var grid = canInteractWithGrid() && getNetworkNode() != null ? getNetworkNode().getGrid() : null;
        var craftables = grid != null && craftMissing ? grid.getCraftingService().getCraftables(AEItemKey.filter()) : java.util.Set.<appeng.api.stacks.AEKey>of();
        boolean touchedStorage = false;
        for (int i = 0; i < ingredients.size(); i++) {
            var slot = engine.getSlot(i);
            var ingredient = ingredients.get(i);
            var current = slot.getItem();
            if (!current.isEmpty() && !ingredient.test(current)) {
                var remaining = current.copy();
                var key = AEItemKey.of(remaining);
                if (grid != null && key != null) {
                    long inserted = StorageHelper.poweredInsert(powerSource, storage, key, remaining.getCount(), getActionSource());
                    remaining.shrink((int) inserted);
                    touchedStorage |= inserted > 0;
                }
                getPlayerInventory().add(remaining);
                slot.set(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                if (slot.hasItem()) continue;
                current = ItemStack.EMPTY;
            }
            if (ingredient.optional()) continue;
            int needed = Math.min(ingredient.amount(), slot.getMaxStackSize()) - current.getCount();
            if (needed <= 0) continue;
            if (grid != null) {
                var candidates = new ArrayList<AEItemKey>();
                var available = grid.getStorageService().getCachedInventory();
                for (var entry : available) {
                    if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key
                            && isKeyVisible(key) && (filter == null || filter.isListed(key))
                            && (current.isEmpty() || key.matches(current)) && ingredient.test(key.getReadOnlyStack())) candidates.add(key);
                }
                candidates.sort((a, b) -> Long.compare(available.get(b), available.get(a)));
                for (var key : candidates) {
                    if (!current.isEmpty() && !key.matches(current)) continue;
                    int extracted = (int) StorageHelper.poweredExtraction(powerSource, storage, key, needed, getActionSource());
                    if (extracted > 0) {
                        touchedStorage = true;
                        current = key.toStack(current.getCount() + extracted);
                        needed -= extracted;
                        if (needed == 0) break;
                    }
                }
            }
            for (int j = 0; j < getPlayerInventory().items.size() && needed > 0; j++) {
                var stack = getPlayerInventory().getItem(j);
                if (isPlayerInventorySlotLocked(j) || stack.isEmpty() || !ingredient.test(stack)
                        || (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, stack))) continue;
                var taken = stack.split(Math.min(needed, stack.getCount()));
                current = taken.copyWithCount(current.getCount() + taken.getCount());
                needed -= taken.getCount();
                getPlayerInventory().setChanged();
            }
            slot.set(current);
            if (needed <= 0) continue;
            for (var candidate : craftables) {
                if (candidate instanceof AEItemKey key && isKeyVisible(key)
                        && (filter == null || filter.isListed(key)) && (current.isEmpty() || key.matches(current))
                        && ingredient.test(key.getReadOnlyStack())) {
                    var slots = toCraft.computeIfAbsent(key, ignored -> new ArrayList<>());
                    for (int unit = 0; unit < needed; unit++) slots.add(i);
                    break;
                }
            }
        }
        if (touchedStorage) grid.getStorageService().invalidateCache();
        return toCraft.entrySet().stream().map(entry -> new IMenuCraftingPacket.AutoCraftEntry(entry.getKey(), entry.getValue())).toList();
    }

    private void refreshCell() {
        var stack = getCell();
        cellCopyMode = cellWorkbench.getConfigManager().getSetting(Settings.COPY_MODE);
        if (configuredCell != stack) { cellConfigRow = cellUpgradeRow = 0; configuredCell = stack; }
        if (stack.getItem() instanceof ICellWorkbenchItem cell && cell.isEditable(stack)) {
            cellConfigSize = Math.min(CELL_CONFIG_SLOTS, cell.getConfigInventory(stack).size());
            cellUpgradeSize = Math.min(8, cell.getUpgrades(stack).size());
            cellFuzzyMode = cell.getFuzzyMode(stack);
        } else {
            cellConfigSize = cellUpgradeSize = 0;
            cellFuzzyMode = FuzzyMode.IGNORE_ALL;
        }
        if (cellCopyMode == CopyMode.KEEP_ON_REMOVE) cellConfigSize = CELL_CONFIG_SLOTS;
        cellConfigRow = net.minecraft.util.Mth.clamp(cellConfigRow, 0, getMaxCellConfigRow());
        cellUpgradeRow = net.minecraft.util.Mth.clamp(cellUpgradeRow, 0, getMaxCellUpgradeRow());
    }

    public void updateSlotAccess() {
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT)) {
            for (var slot : getSlots(semantic)) {
                if (slot instanceof AppEngSlot aeSlot) aeSlot.setActive(workPage == TianshuWorkPage.CRAFTING);
            }
        }
    }

    protected boolean allowsSlot(int index) {
        if (index < 0 || index >= slots.size()) return true;
        var slot = slots.get(index);
        if (slot instanceof AppEngSlot aeSlot && !aeSlot.isSlotEnabled()) return false;
        var semantic = getSlotSemantic(slot);
        if (semantic == Ae2ltSlotSemantics.TIANSHU_CELL && workPage != TianshuWorkPage.CELL) return false;
        return slot.isActive();
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!allowsSlot(slot)) return;
        if (slot >= 0 && slot < slots.size() && isWorkResult(slots.get(slot))) {
            if (type == ClickType.PICKUP && (button == 0 || button == 1))
                craftWorkResult((TianshuWorkSlot) slots.get(slot), button == 1 ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM);
            else if (type == ClickType.QUICK_MOVE) craftWorkResult((TianshuWorkSlot) slots.get(slot), InventoryAction.CRAFT_SHIFT);
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        if (!allowsSlot(slot)) return;
        if (slot >= 0 && slot < slots.size() && slots.get(slot) instanceof TianshuWorkSlot work) {
            if (work.result) craftWorkResult(work, action);
            else if (action == InventoryAction.MOVE_REGION) clearWorkInputs(false);
            return;
        }
        super.doAction(player, action, slot, id);
    }
    @Override public void setFilter(int slot, ItemStack stack) { if (allowsSlot(slot)) super.setFilter(slot, stack); }
    @Override public boolean canDragTo(Slot slot) { return allowsSlot(slot.index) && super.canDragTo(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return allowsSlot(slot.index) && !(slot instanceof TianshuWorkSlot work && work.result) && super.canTakeItemForPickAll(stack, slot);
    }

    protected boolean isValidQuickMoveDestination(Slot slot, ItemStack stack, boolean fromPlayer) {
        // Like AE2's crafting matrix, work inputs are filled explicitly, not by a failed Shift-to-ME transfer.
        return !(slot instanceof TianshuWorkSlot) && allowsSlot(slot.index)
                && !(slot instanceof appeng.menu.slot.FakeSlot)
                && !(slot instanceof appeng.menu.slot.CraftingMatrixSlot) && slot.mayPlace(stack);
    }

    private boolean isWorkPlayerSideSlot(Slot slot) {
        return slot.container == getPlayerInventory();
    }

    private ItemStack quickMovePlayerStack(Player player, Slot source) {
        if (isClientSide() || !source.mayPickup(player) || !source.hasItem()) return ItemStack.EMPTY;
        int transferred = source.getItem().getCount() - transferStackToMenu(source.getItem().copy()).getCount();
        if (transferred > 0) source.remove(transferred);
        var remaining = source.getItem().copy();
        if (remaining.isEmpty()) return ItemStack.EMPTY;
        var template = remaining;
        var destinations = slots.stream().filter(slot -> !isWorkPlayerSideSlot(slot)
                && isValidQuickMoveDestination(slot, template, true)).toList();
        for (boolean occupied : new boolean[] {true, false}) {
            for (var destination : destinations) {
                if (destination.hasItem() == occupied) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
            if (remaining.isEmpty()) break;
        }
        if (!ItemStack.matches(source.getItem(), remaining)) source.setByPlayer(remaining);
        // AEBaseMenu's generic fallback writes directly to the first FakeSlot, including hidden pages.
        // Ghost configuration here uses explicit active-slot actions instead.
        return ItemStack.EMPTY;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !allowsSlot(index)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot instanceof TianshuWorkSlot work) {
            if (work.result) craftWorkResult(work, InventoryAction.CRAFT_SHIFT);
            else if (isServerSide() && slot.mayPickup(player)) {
                int transferred = slot.getItem().getCount() - transferStackToMenu(slot.getItem().copy()).getCount();
                if (transferred > 0) slot.remove(transferred);
            }
            // Like AE2, handle the bounded action once instead of vanilla's repeated quick-move loop.
            return ItemStack.EMPTY;
        }
        if (isWorkPlayerSideSlot(slot)) return quickMovePlayerStack(player, slot);
        return super.quickMoveStack(player, index);
    }

    private final com.moakiee.ae2lt.crafting.big.BigTerminalStock bigStock =
            new com.moakiee.ae2lt.crafting.big.BigTerminalStock();

    @Override public java.math.BigInteger getBigStock(appeng.api.stacks.AEKey key) {
        return bigStock.get(key);
    }

    @Override public void applyBigStock(java.util.Map<appeng.api.stacks.AEKey, java.math.BigInteger> changes) {
        bigStock.apply(changes);
    }

    @Override public void broadcastChanges() {
        if (isServerSide() && maintenanceSession != null) {
            updateAnvilInputName();
            refreshCell();
            if (boundTianshuTarget == null) {
                var target = tianshuHost.selectTianshuTarget();
                if (target != null) {
                    boundTianshuTarget = target;
                    tianshuSelectionRevision++;
                    maintenanceSession.invalidateTarget();
                }
            }
            var target = resolveBoundTianshu();
            maintenanceAvailable = target != null && target.getFunctionProfile().supportsInventoryMaintenance();
            anvilCost = anvil.getCost();
            stoneRecipe = stonecutter.getSelectedRecipeIndex();
        }
        super.broadcastChanges();
        if (isServerSide() && maintenanceSession != null) {
            maintenanceSession.sendMaintenanceSummaryIfNeeded();
            bigStock.sync(this, tianshuHost);
        }
    }

    @Override public void onServerDataSync() {
        super.onServerDataSync();
        updateSlotAccess();
        anvil.setMaximumCost(anvilCost);
        if (stoneRecipe >= 0) stonecutter.clickMenuButton(getPlayer(), stoneRecipe);
    }

    @Override public void removed(Player player) {
        if (isServerSide() && !closed) {
            persistWorkstations();
            workstationStorage.unsubscribe(this);
            closed = true;
        }
        super.removed(player);
    }

    private void persistWorkstations() {
        if (workstationStorage == null || updatingWorkstations || closed) return;
        updatingWorkstations = true;
        try {
            updateAnvilInputName();
            var data = snapshotWorkstations();
            workstationStorage.update(data.equals(emptyWorkstations) ? new CompoundTag() : data, this);
        } finally {
            updatingWorkstations = false;
        }
    }

    private CompoundTag snapshotWorkstations() {
        var data = new CompoundTag();
        var inputs = new ListTag();
        for (var slot : extraInputs) inputs.add(slot.getItem().save(new CompoundTag()));
        data.put("inputs", inputs);
        data.putString("anvilName", anvilItemName);
        int selected = stonecutter.getSelectedRecipeIndex();
        if (selected >= 0 && selected < stonecutter.getNumRecipes())
            data.putString("stoneRecipe", stonecutter.getRecipes().get(selected).getId().toString());
        var cell = new CompoundTag();
        cellWorkbench.saveAdditional(cell);
        data.put("cellWorkbench", cell);
        return data;
    }

    private void restoreWorkstations() {
        if (workstationStorage == null || updatingWorkstations || closed) return;
        updatingWorkstations = true;
        try {
            var data = workstationStorage.read();
            var inputs = data.getList("inputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < extraInputs.size(); i++) {
                var stack = ItemStack.of(inputs.getCompound(i));
                if (!ItemStack.matches(extraInputs.get(i).getItem(), stack)) extraInputs.get(i).set(stack);
            }
            setAnvilNameInternal(data.getString("anvilName"));
            var selected = ResourceLocation.tryParse(data.getString("stoneRecipe"));
            if (selected != null) {
                for (int i = 0; i < stonecutter.getNumRecipes(); i++)
                    if (stonecutter.getRecipes().get(i).getId().equals(selected)) stonecutter.clickMenuButton(getPlayer(), i);
            }
            var currentCell = new CompoundTag();
            cellWorkbench.saveAdditional(currentCell);
            var savedCell = data.getCompound("cellWorkbench");
            if (!currentCell.equals(savedCell)) {
                // AE2's inventory NBT reader overlays present slots; absence does not clear old slots.
                cellInventory.setItemDirect(0, ItemStack.EMPTY);
                cellWorkbench.getConfig().clear();
                cellWorkbench.getConfigManager().putSetting(Settings.COPY_MODE, CopyMode.CLEAR_ON_REMOVE);
                cellWorkbench.loadTag(savedCell);
                cellWorkbench.onChangeInventory(cellInventory, 0);
            }
            refreshCell();
            anvilCost = anvil.getCost();
            stoneRecipe = stonecutter.getSelectedRecipeIndex();
        } finally {
            updatingWorkstations = false;
        }
    }

    @Nullable private TianshuSupercomputerPortBlockEntity resolveBoundTianshu() { return tianshuHost.resolveTianshuTarget(boundTianshuTarget); }
    @Override public MEStorageMenu maintenanceMenu() { return this; }
    @Override public TianshuMaintenanceSession getMaintenanceSession() { return maintenanceSession; }
    @Override public int getTianshuSelectionRevision() { return tianshuSelectionRevision; }
    @Override public boolean isMaintenanceAvailable() { return maintenanceAvailable; }
    @Override public boolean isMaintainableView() {
        return getTerminalViewMode() == TianshuTerminalViewMode.MAINTAINABLE;
    }
    @Override public FakeSlot getGlobalReserveMarkSlot() { return globalReserveMarkSlot; }
    @Override public boolean showsCraftables() { return maintainableView || super.showsCraftables(); }
    @Override public TianshuTerminalViewMode getTerminalViewMode() {
        return isClientSide() && clientViewMode != null ? clientViewMode
                : TianshuTerminalViewMode.from(getConfigManager().getSetting(Settings.VIEW_MODE), maintainableView);
    }

    @Override public void setTerminalViewMode(TianshuTerminalViewMode mode) { applyTerminalViewMode(mode, true); }

    @Override public void setMaintainableViewTemporarily(boolean enabled) {
        applyTerminalViewMode(enabled ? TianshuTerminalViewMode.MAINTAINABLE
                : TianshuTerminalViewMode.from(getTerminalViewMode().viewItems(), false), false);
    }

    private void applyTerminalViewMode(TianshuTerminalViewMode mode, boolean persist) {
        if (mode == null) return;
        // Keep the newest local selection while older server config/sync packets are in flight.
        if (isClientSide()) clientViewMode = mode;
        maintainableView = mode == TianshuTerminalViewMode.MAINTAINABLE;
        getConfigManager().putSetting(Settings.VIEW_MODE, mode.viewItems());
        if (isClientSide()) sendClientAction(persist ? "setTerminalViewMode" : "setTemporaryTerminalViewMode", mode);
        else {
            broadcastChanges();
        }
    }
}
