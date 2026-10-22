package com.moakiee.ae2lt.menu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardData;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.SyncFrequencyDetailPacket;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;

/**
 * Shared menu for wireless controllers and receiver-style frequency-bound devices.
 * Carries the block entity position and device type to the client; the
 * currently bound frequency id is auto-synced via a {@link DataSlot}.
 */
public class FrequencyMenu extends AEBaseMenu implements ISubMenu {

    public static final MenuType<FrequencyMenu> TYPE = IMenuTypeExtension.create(FrequencyMenu::clientCreate);
    private static final String ACTION_TOGGLE_AUTO_CONNECT = "toggleAutoConnect";
    private static final String ACTION_TOGGLE_AUTO_CONNECT_ENERGY = "toggleAutoConnectEnergy";

    static {
        MenuOpener.addOpener(TYPE, FrequencyMenu::openWithAe2MenuOpener);
    }

    private final BlockPos blockPos;
    private final boolean isController;
    private final boolean isAdvanced;
    private final String deviceName;

    @Nullable
    private final BlockEntity backingBlockEntity;

    /**
     * Card mode targets either the exact held stack or a card in a terminal's
     * upgrade slot. Both use the same frequency screen and access checks.
     */
    private final boolean cardMode;
    @Nullable
    private final ItemMenuHostLocator terminalLocator;
    @Nullable
    private final ServerPlayer cardPlayer;
    @Nullable
    private final InteractionHand heldCardHand;
    private final ItemStack heldCardStack;

    // AE2 sub-menu host, resolved once at construction. In card mode it is the
    // wireless terminal; in device mode it is an adapter that reopens the device's
    // own menu (the GUI the frequency screen was opened from). Mirrors how AE2's
    // own sub-menus store the host set by MenuOpener; {@link #getHost()} just
    // returns it. Null when there is no parent (e.g. a controller/receiver block
    // opened directly), in which case no back button is shown.
    @Nullable
    private ISubMenuHost cachedSubMenuHost;

    // Whether this menu has a parent to return to (drives the back button on the
    // client). Server side it tracks cachedSubMenuHost; client side it is synced.
    private final boolean hasParentMenu;
    private FrequencyPage initialPage = FrequencyPage.TAB_HOME;

    private final DataSlot freqIdSlot = DataSlot.standalone();
    private final DataSlot linkActiveSlot = DataSlot.standalone();
    private final DataSlot usedChannelsSlot = DataSlot.standalone();
    private final DataSlot maxChannelsSlot = DataSlot.standalone();
    private final DataSlot autoConnectEnergySlot = DataSlot.standalone();
    private final DataSlot autoConnectSlot = DataSlot.standalone();

    // channel counts iterate all grid nodes; throttle to once per 10 server ticks
    private static final int CHANNEL_REFRESH_INTERVAL = 10;
    private int channelRefreshCountdown = 0;

    // server constructor (device opened directly, e.g. controller/receiver block: no parent menu)
    public FrequencyMenu(int containerId, Inventory playerInv, BlockEntity be) {
        this(containerId, playerInv, be, null, null);
    }

    // server constructor (device mode). When opened from a device's own GUI via the
    // frequency config button, parentType/parentLocator point back at that GUI so the
    // back button can reopen it through AE2's native returnToMainMenu contract.
    public FrequencyMenu(int containerId, Inventory playerInv, BlockEntity be,
                         @Nullable MenuType<?> parentType, @Nullable MenuHostLocator parentLocator) {
        this(containerId, playerInv, be, parentType, parentLocator, null);
    }

    private FrequencyMenu(int containerId, Inventory playerInv, BlockEntity be,
                         @Nullable MenuType<?> parentType, @Nullable MenuHostLocator parentLocator,
                         @Nullable ISubMenuHost returnHost) {
        super(TYPE, containerId, playerInv, be);
        this.blockPos = be.getBlockPos();
        this.backingBlockEntity = be;
        this.cardMode = false;
        this.terminalLocator = null;
        this.cardPlayer = null;
        this.heldCardHand = null;
        this.heldCardStack = ItemStack.EMPTY;

        if (returnHost != null) {
            this.cachedSubMenuHost = returnHost;
        } else if (parentType != null && parentLocator != null) {
            this.cachedSubMenuHost = new DeviceMenuReturnHost(
                    new ItemStack(be.getBlockState().getBlock()), parentType, parentLocator);
        }
        this.hasParentMenu = this.cachedSubMenuHost != null;

        if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            this.isController = true;
            this.isAdvanced = ctrl.isAdvanced();
            this.deviceName = ctrl.isAdvanced()
                    ? "block.ae2lt.advanced_wireless_overloaded_controller"
                    : "block.ae2lt.wireless_overloaded_controller";
            this.freqIdSlot.set(ctrl.getFrequencyId());
            this.linkActiveSlot.set(ctrl.isFrequencyActive() ? 1 : 0);
            this.usedChannelsSlot.set(ctrl.getGridUsedChannels());
            this.maxChannelsSlot.set(ctrl.getGridMaxChannels());
        } else if (be instanceof FrequencyBindingHost bindingHost) {
            this.isController = false;
            this.isAdvanced = false;
            this.deviceName = bindingHost.getFrequencyBindingDeviceName();
            this.freqIdSlot.set(bindingHost.getFrequencyId());
            this.linkActiveSlot.set(bindingHost.isFrequencyConnected() ? 1 : 0);
            this.usedChannelsSlot.set(bindingHost.getGridUsedChannels());
            this.maxChannelsSlot.set(bindingHost.getGridMaxChannels());
        } else {
            this.isController = false;
            this.isAdvanced = false;
            this.deviceName = "block.ae2lt.wireless_receiver";
            this.freqIdSlot.set(-1);
            this.linkActiveSlot.set(0);
            this.usedChannelsSlot.set(0);
            this.maxChannelsSlot.set(0);
        }
        this.autoConnectSlot.set(0);

        registerDataSlots();
        registerClientActions();

        // initial sync to the player who just opened this menu
        if (playerInv.player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, SyncFrequencyListPacket.fromServer());
            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(sp, freqIdSlot.get());
            if (usesLocalConnectionList()) SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(sp, freqIdSlot.get());
        }
    }

    public static void openRemote(ServerPlayer player, BlockEntity be,
            com.moakiee.ae2lt.logic.management.RemoteDeviceLocator locator) {
        if (!locator.valid(player)) return;
        player.openMenu(new Ae2StyleFrequencyMenuProvider(be.getBlockState().getBlock().getName(), (id, inv) -> {
            var menu = new FrequencyMenu(id, inv, be, null, null, locator.session());
            menu.setLocator(locator);
            return menu;
        }, true), buffer -> writeExtraData(buffer, be, true));
    }

    // server constructor (card mode: targets a frequency card in a terminal upgrade slot)
    public FrequencyMenu(int containerId, Inventory playerInv, ItemMenuHostLocator terminalLocator) {
        this(containerId, playerInv, terminalLocator, null);
    }

    private FrequencyMenu(int containerId, Inventory playerInv, @Nullable ItemMenuHostLocator terminalLocator,
                          @Nullable InteractionHand heldCardHand) {
        super(TYPE, containerId, playerInv, null);
        if (terminalLocator != null) setLocator(terminalLocator);
        this.blockPos = BlockPos.ZERO;
        this.backingBlockEntity = null;
        this.cardMode = true;
        this.terminalLocator = terminalLocator;
        this.cardPlayer = playerInv.player instanceof ServerPlayer sp ? sp : null;
        this.heldCardHand = heldCardHand;
        this.heldCardStack = heldCardHand == null ? ItemStack.EMPTY : playerInv.player.getItemInHand(heldCardHand);
        this.isController = false;
        this.isAdvanced = false;
        this.deviceName = "item.ae2lt.overloaded_frequency_card";

        // Resolve the parent ISubMenuHost now, while the terminal is guaranteed
        // present, so the native back button can return reliably later.
        this.cachedSubMenuHost = resolveSubMenuHost();
        this.hasParentMenu = this.cachedSubMenuHost != null;

        var cardData = readCardData();
        int freqId = cardData.isBound() ? cardData.frequencyId() : -1;
        this.freqIdSlot.set(freqId);
        this.linkActiveSlot.set(readCardLinkActive(freqId));
        this.usedChannelsSlot.set(0);
        this.maxChannelsSlot.set(0);
        this.autoConnectSlot.set(cardData.autoConnect() ? 1 : 0);
        this.autoConnectEnergySlot.set(cardData.autoConnectEnergy() ? 1 : 0);

        registerDataSlots();
        registerClientActions();

        if (cardPlayer != null) {
            PacketDistributor.sendToPlayer(cardPlayer, SyncFrequencyListPacket.fromServer());
            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(cardPlayer, freqId);
            if (usesLocalConnectionList()) SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(cardPlayer, freqId);
        }
    }

    public static boolean openHeldCard(ServerPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof OverloadedFrequencyCardItem)) return false;
        var data = OverloadedFrequencyCardItem.getData(stack);
        if (data.isBound() && !data.canBeUsedBy(player.getUUID())) {
            player.displayClientMessage(Component.translatable("ae2lt.frequency_card.card_owner_mismatch")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        return player.openMenu(new Ae2StyleFrequencyMenuProvider(
                Component.translatable("item.ae2lt.overloaded_frequency_card"),
                (id, inv) -> new FrequencyMenu(id, inv, null, hand), false),
                buffer -> writeCardExtraData(buffer, data, false)).isPresent();
    }

    // client constructor (from network)
    private static FrequencyMenu clientCreate(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        boolean cardMode = buf.readBoolean();
        BlockPos pos = buf.readBlockPos();
        boolean controller = buf.readBoolean();
        boolean advanced = buf.readBoolean();
        String deviceName = buf.readUtf(256);
        int freqId = buf.readInt();
        boolean linkActive = buf.readBoolean();
        int used = buf.readInt();
        int max = buf.readInt();
        boolean autoConnect = buf.readBoolean();
        boolean autoConnectEnergy = buf.readBoolean();
        boolean hasParentMenu = buf.readBoolean();
        FrequencyPage page = buf.readEnum(FrequencyPage.class);
        var menu = new FrequencyMenu(containerId, playerInv, cardMode, pos, controller, advanced, deviceName,
                freqId, linkActive, used, max, autoConnect, autoConnectEnergy, hasParentMenu);
        menu.initialPage = page.isManagement() ? FrequencyPage.TAB_HOME : page;
        return menu;
    }

    // client-side constructor
    private FrequencyMenu(int containerId, Inventory playerInv, boolean cardMode, BlockPos pos, boolean isController,
                          boolean isAdvanced, String deviceName, int freqId, boolean linkActive, int used, int max,
                          boolean autoConnect, boolean autoConnectEnergy, boolean hasParentMenu) {
        super(TYPE, containerId, playerInv, null);
        this.blockPos = pos;
        this.isController = isController;
        this.isAdvanced = isAdvanced;
        this.deviceName = deviceName;
        this.backingBlockEntity = null;
        this.cardMode = cardMode;
        this.terminalLocator = null;
        this.cardPlayer = null;
        this.heldCardHand = null;
        this.heldCardStack = ItemStack.EMPTY;
        this.hasParentMenu = hasParentMenu;
        this.freqIdSlot.set(freqId);
        this.linkActiveSlot.set(linkActive ? 1 : 0);
        this.usedChannelsSlot.set(used);
        this.maxChannelsSlot.set(max);
        this.autoConnectSlot.set(autoConnect ? 1 : 0);
        this.autoConnectEnergySlot.set(autoConnectEnergy ? 1 : 0);
        registerDataSlots();
        registerClientActions();
    }

    private void registerDataSlots() {
        addDataSlot(freqIdSlot);
        addDataSlot(linkActiveSlot);
        addDataSlot(usedChannelsSlot);
        addDataSlot(maxChannelsSlot);
        addDataSlot(autoConnectSlot);
        addDataSlot(autoConnectEnergySlot);
    }

    private void registerClientActions() {
        registerClientAction(ACTION_TOGGLE_AUTO_CONNECT, () -> toggleAutoConnect(false));
        registerClientAction(ACTION_TOGGLE_AUTO_CONNECT_ENERGY, () -> toggleAutoConnect(true));
    }

    public void openManagement(ServerPlayer player, ManagementQuery view) {
        if (view == null || !view.valid() || getPlayerInventory().player != player
                || player.containerMenu != this || !stillValid(player)) return;
        if (cardMode) {
            NetworkManagementMenu.openFromCard(player, this, view);
        } else if (!hasParentMenu && backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity controller) {
            NetworkManagementMenu.open(player, controller, view.mode(), view, null);
        }
    }

    /** Resolve only a live, loaded advanced controller using the card still present at this entry point. */
    @Nullable
    WirelessOverloadedControllerBlockEntity resolveManagementController(ServerPlayer player) {
        if (!cardMode || player != cardPlayer || !stillValid(player)) return null;
        var data = readCardData();
        if (!data.isBound() || !data.canBeUsedBy(player.getUUID())) return null;
        var node = WirelessFrequencyManager.get(player.server).resolveAdvancedNode(data.frequencyId(), player.server);
        if (node == null || !(node.getOwner() instanceof WirelessOverloadedControllerBlockEntity controller)
                || controller.isRemoved() || controller.getLevel() == null
                || controller.getFrequencyId() != data.frequencyId()) return null;
        var pos = controller.getBlockPos();
        var level = player.server.getLevel(controller.getLevel().dimension());
        var chunk = level == null ? null : level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) == controller
                ? controller : null;
    }

    void reopenCardPage(ServerPlayer player, FrequencyPage page) {
        if (!cardMode || player != cardPlayer || !stillValid(player) || page.isManagement()) return;
        var data = readCardData();
        player.openMenu(new Ae2StyleFrequencyMenuProvider(Component.translatable(deviceName), (id, inv) -> {
            var menu = new FrequencyMenu(id, inv, terminalLocator, heldCardHand);
            menu.initialPage = page;
            return menu;
        }, true), buffer -> writeCardExtraData(buffer, data, hasParentMenu, page));
    }

    public static void openPage(ServerPlayer player, WirelessOverloadedControllerBlockEntity host, FrequencyPage page) {
        if (page.isManagement()) return;
        player.openMenu(new Ae2StyleFrequencyMenuProvider(host.getBlockState().getBlock().getName(), (id, inv) -> {
            var menu = new FrequencyMenu(id, inv, host);
            menu.initialPage = page;
            return menu;
        }, true), buffer -> writeExtraData(buffer, host, false, page));
    }

    private static boolean openWithAe2MenuOpener(Player player, MenuHostLocator locator, boolean returning) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        if (locator instanceof ItemMenuHostLocator terminalLocator) {
            ItemStack terminal = terminalLocator.locateItem(serverPlayer);
            if (!TerminalCardAccess.hasCard(terminal)) {
                return false;
            }
            serverPlayer.openMenu(new Ae2StyleFrequencyMenuProvider(
                    Component.translatable("item.ae2lt.overloaded_frequency_card"),
                    (id, inv) -> {
                        var menu = new FrequencyMenu(id, inv, terminalLocator);
                        menu.setLocator(terminalLocator);
                        menu.setReturnedFromSubScreen(returning);
                        return menu;
                    },
                    player.containerMenu instanceof AEBaseMenu
            ), buf -> writeCardExtraData(buf, terminal));
            return true;
        }

        FrequencyBindingHost bindingHost = locator.locate(serverPlayer, FrequencyBindingHost.class);
        if (bindingHost == null) {
            return false;
        }
        BlockEntity be = bindingHost.getFrequencyBindingBlockEntity();

        MenuType<?> parentType = null;
        MenuHostLocator parentLocator = null;
        if (serverPlayer.containerMenu instanceof AEBaseMenu parentMenu) {
            parentType = parentMenu.getType();
            parentLocator = locator;
        }
        final MenuType<?> fParentType = parentType;
        final MenuHostLocator fParentLocator = parentLocator;
        final boolean hasParent = fParentType != null && fParentLocator != null;

        serverPlayer.openMenu(new Ae2StyleFrequencyMenuProvider(
                be.getBlockState().getBlock().getName(),
                (id, inv) -> {
                    var menu = new FrequencyMenu(id, inv, be, fParentType, fParentLocator);
                    menu.setLocator(locator);
                    menu.setReturnedFromSubScreen(returning);
                    return menu;
                },
                player.containerMenu instanceof AEBaseMenu
        ), buf -> writeExtraData(buf, be, hasParent));
        return true;
    }

    @FunctionalInterface
    private interface FrequencyMenuFactory {
        FrequencyMenu create(int containerId, Inventory inventory);
    }

    /**
     * Same client-close behavior as AE2's MenuTypeBuilder provider: opening a
     * menu from an existing AEBaseMenu should be treated as a GUI switch, not as
     * a full close/reopen. This is what keeps the native submenu pointer/focus
     * behavior intact.
     */
    private record Ae2StyleFrequencyMenuProvider(Component title, FrequencyMenuFactory factory,
                                                 boolean openedFromAe2Menu) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return title;
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return factory.create(containerId, playerInventory);
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen() {
            return !openedFromAe2Menu;
        }
    }

    public static void writeExtraData(FriendlyByteBuf buf, BlockEntity be, boolean hasParentMenu) {
        writeExtraData(buf, be, hasParentMenu, FrequencyPage.TAB_HOME);
    }

    private static void writeExtraData(FriendlyByteBuf buf, BlockEntity be, boolean hasParentMenu, FrequencyPage page) {
        buf.writeBoolean(false);
        buf.writeBlockPos(be.getBlockPos());
        if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            buf.writeBoolean(true);
            buf.writeBoolean(ctrl.isAdvanced());
            buf.writeUtf(ctrl.isAdvanced()
                    ? "block.ae2lt.advanced_wireless_overloaded_controller"
                    : "block.ae2lt.wireless_overloaded_controller", 256);
            buf.writeInt(ctrl.getFrequencyId());
            buf.writeBoolean(ctrl.isFrequencyActive());
            buf.writeInt(ctrl.getGridUsedChannels());
            buf.writeInt(ctrl.getGridMaxChannels());
        } else if (be instanceof FrequencyBindingHost bindingHost) {
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeUtf(bindingHost.getFrequencyBindingDeviceName(), 256);
            buf.writeInt(bindingHost.getFrequencyId());
            buf.writeBoolean(bindingHost.isFrequencyConnected());
            buf.writeInt(bindingHost.getGridUsedChannels());
            buf.writeInt(bindingHost.getGridMaxChannels());
        } else {
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeUtf("block.ae2lt.wireless_receiver", 256);
            buf.writeInt(-1);
            buf.writeBoolean(false);
            buf.writeInt(0);
            buf.writeInt(0);
        }
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(hasParentMenu);
        buf.writeEnum(page);
    }

    /**
     * Writes the open-menu payload for card mode. The data is read from the
     * frequency card currently installed in {@code terminalStack}'s upgrade
     * inventory.
     */
    public static void writeCardExtraData(FriendlyByteBuf buf, ItemStack terminalStack) {
        writeCardExtraData(buf, TerminalCardAccess.readCardData(terminalStack), true);
    }

    private static void writeCardExtraData(FriendlyByteBuf buf, OverloadedFrequencyCardData cardData, boolean hasParent) {
        writeCardExtraData(buf, cardData, hasParent, FrequencyPage.TAB_HOME);
    }

    private static void writeCardExtraData(FriendlyByteBuf buf, OverloadedFrequencyCardData cardData, boolean hasParent, FrequencyPage page) {
        int freqId = cardData.isBound() ? cardData.frequencyId() : -1;
        buf.writeBoolean(true);
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeUtf("item.ae2lt.overloaded_frequency_card", 256);
        buf.writeInt(freqId);
        buf.writeBoolean(false);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeBoolean(cardData.autoConnect());
        buf.writeBoolean(cardData.autoConnectEnergy());
        buf.writeBoolean(hasParent);
        buf.writeEnum(page);
    }

    @Override
    public void broadcastChanges() {
        if (cardMode) {
            var cardData = readCardData();
            int real = cardData.isBound() ? cardData.frequencyId() : -1;
            if (freqIdSlot.get() != real) {
                freqIdSlot.set(real);
                if (cardPlayer != null && cardPlayer.containerMenu == this) {
                    SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(cardPlayer, real);
                    if (usesLocalConnectionList()) SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(cardPlayer, real);
                }
            }
            autoConnectEnergySlot.set(cardData.autoConnectEnergy() ? 1 : 0);
            int autoConnect = cardData.autoConnect() ? 1 : 0;
            if (autoConnectSlot.get() != autoConnect) {
                autoConnectSlot.set(autoConnect);
            }
            if (--channelRefreshCountdown <= 0) {
                channelRefreshCountdown = CHANNEL_REFRESH_INTERVAL;
                int active = readCardLinkActive(real);
                if (linkActiveSlot.get() != active) {
                    linkActiveSlot.set(active);
                }
            }
            super.broadcastChanges();
            return;
        }
        if (backingBlockEntity != null) {
            int real = readFreqIdFromBE();
            if (freqIdSlot.get() != real) {
                freqIdSlot.set(real);
                // player's new frequency: push fresh member + connection snapshots
                var lvl = backingBlockEntity.getLevel();
                if (lvl != null && !lvl.isClientSide()) {
                    for (var p : lvl.players()) {
                        if (p instanceof ServerPlayer sp && sp.containerMenu == this) {
                            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(sp, real);
                            if (usesLocalConnectionList()) SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(sp, real);
                        }
                    }
                }
            }

            int active = readLinkActiveFromBE();
            if (linkActiveSlot.get() != active) {
                linkActiveSlot.set(active);
            }

            if (--channelRefreshCountdown <= 0) {
                channelRefreshCountdown = CHANNEL_REFRESH_INTERVAL;
                int used = readUsedChannelsFromBE();
                if (usedChannelsSlot.get() != used) {
                    usedChannelsSlot.set(used);
                }
                int max = readMaxChannelsFromBE();
                if (maxChannelsSlot.get() != max) {
                    maxChannelsSlot.set(max);
                }
            }
        }
        super.broadcastChanges();
    }

    private int readFreqIdFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getFrequencyId();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getFrequencyId();
        }
        return -1;
    }

    private int readLinkActiveFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.isFrequencyActive() ? 1 : 0;
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.isFrequencyConnected() ? 1 : 0;
        }
        return 0;
    }

    private int readUsedChannelsFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getGridUsedChannels();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getGridUsedChannels();
        }
        return 0;
    }

    private int readMaxChannelsFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getGridMaxChannels();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getGridMaxChannels();
        }
        return 0;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        if (getLocator() instanceof com.moakiee.ae2lt.logic.management.RemoteDeviceLocator remote) return remote.valid(player);
        if (cardMode) {
            if (player != cardPlayer) return false;
            if (heldCardHand == null) return TerminalCardAccess.hasCard(resolveTerminalStack());
            var stack = resolveHeldCard();
            if (stack.isEmpty()) return false;
            var data = OverloadedFrequencyCardItem.getData(stack);
            return !data.isBound() || data.canBeUsedBy(player.getUUID());
        }
        if (backingBlockEntity != null) {
            if (backingBlockEntity.isRemoved() || backingBlockEntity.getLevel() == null) {
                return false;
            }

            if (player.level() != backingBlockEntity.getLevel()) {
                return false;
            }

            if (backingBlockEntity.getLevel().getBlockEntity(blockPos) != backingBlockEntity) {
                return false;
            }
        }

        return player.distanceToSqr(
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Nullable
    public BlockEntity getConfiguredBlockEntity(ServerPlayer player) {
        return !cardMode && stillValid(player) ? backingBlockEntity : null;
    }

    public boolean supportsManagement() { return cardMode || isController && !hasParentMenu; }
    public boolean usesLocalConnectionList() { return !supportsManagement(); }
    public FrequencyPage initialPage() { return initialPage; }
    public void clientOpenManagement(boolean energy) { clientOpenManagement(ManagementQuery.initial(energy)); }
    public void clientOpenManagement(ManagementQuery view) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.moakiee.ae2lt.network.NetworkManagementRequestPacket(containerId, view));
    }

    public boolean isController() {
        return isController;
    }

    public boolean isAdvanced() {
        return isAdvanced;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getCurrentFrequencyId() {
        return freqIdSlot.get();
    }

    public boolean isCardMode() {
        return cardMode;
    }

    /**
     * Whether a back button should be shown: true when this menu was opened as a
     * sub-menu of a parent GUI (card mode in a terminal, or device mode opened
     * from a machine's config button). False for controller/receiver blocks
     * opened directly, which are their own top-level menu (ESC closes).
     */
    public boolean hasParentMenu() {
        return hasParentMenu;
    }

    public boolean isAutoConnect() {
        return autoConnectSlot.get() != 0;
    }

    public void clientToggleAutoConnect() {
        sendClientAction(ACTION_TOGGLE_AUTO_CONNECT);
    }

    public boolean isAutoConnectEnergy() {
        return autoConnectEnergySlot.get() != 0;
    }

    public void clientToggleAutoConnectEnergy() {
        sendClientAction(ACTION_TOGGLE_AUTO_CONNECT_ENERGY);
    }

    public boolean isLinkActive() {
        return linkActiveSlot.get() != 0;
    }

    /**
     * Server-side: resolves the wireless terminal item stack this card menu is
     * bound to, or {@link ItemStack#EMPTY} when unavailable.
     */
    public ItemStack resolveTerminalStack() {
        if (terminalLocator == null || cardPlayer == null) {
            return ItemStack.EMPTY;
        }
        return terminalLocator.locateItem(cardPlayer);
    }

    // --- ISubMenu: card mode is a sub-menu of the wireless terminal (or any
    // AE2 ISubMenuHost device) it was opened from. The back button sends AE2's
    // native SwitchGuisPacket.returnToParentMenu(), whose server handler calls
    // getHost().returnToMainMenu(...) -- exactly how AE2's own AESubScreen works.
    // getLocator() is inherited from AEBaseMenu (set via setLocator in the
    // card-mode constructor). ---

    @Override
    @Nullable
    public ISubMenuHost getHost() {
        return cachedSubMenuHost;
    }

    @Nullable
    private ISubMenuHost resolveSubMenuHost() {
        if (terminalLocator == null || cardPlayer == null) {
            return null;
        }
        ItemStack terminal = resolveTerminalStack();
        if (terminal.getItem() instanceof IMenuItem menuItem
                && menuItem.getMenuHost(cardPlayer, terminalLocator, (BlockHitResult) null)
                        instanceof ISubMenuHost subHost) {
            return subHost;
        }
        return null;
    }

    /**
     * Sub-menu host used in device mode: the back button reopens the parent
     * device GUI (the menu the frequency screen was launched from) via AE2's
     * {@link MenuOpener}. All such device menus are registered through
     * {@code MenuTypeBuilder} and opened with a block-entity locator, so
     * reopening them is just {@code MenuOpener.open(type, player, locator)}.
     */
    private record DeviceMenuReturnHost(ItemStack icon, MenuType<?> parentType, MenuHostLocator parentLocator)
            implements ISubMenuHost {
        @Override
        public ItemStack getMainMenuIcon() {
            return icon;
        }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {
            MenuOpener.open(parentType, player, parentLocator);
        }
    }

    private ItemStack resolveHeldCard() {
        if (cardPlayer == null || heldCardHand == null) return ItemStack.EMPTY;
        var current = cardPlayer.getItemInHand(heldCardHand);
        return current == heldCardStack && current.getItem() instanceof OverloadedFrequencyCardItem ? current : ItemStack.EMPTY;
    }

    public OverloadedFrequencyCardData readCardData() {
        if (heldCardHand != null) return OverloadedFrequencyCardItem.getData(resolveHeldCard());
        return TerminalCardAccess.readCardData(resolveTerminalStack());
    }

    public boolean updateCard(java.util.function.UnaryOperator<OverloadedFrequencyCardData> mutation) {
        if (!cardMode || cardPlayer == null || cardPlayer.containerMenu != this || !stillValid(cardPlayer)) return false;
        if (heldCardHand == null) return TerminalCardAccess.updateCard(resolveTerminalStack(), mutation);
        OverloadedFrequencyCardItem.setData(resolveHeldCard(), mutation.apply(readCardData()));
        cardPlayer.getInventory().setChanged();
        return true;
    }

    private void toggleAutoConnect(boolean energy) {
        if (!cardMode || !isServerSide() || cardPlayer == null || !stillValid(cardPlayer)) {
            return;
        }
        var cardData = readCardData();
        if (cardData.isBound() && !cardData.canBeUsedBy(cardPlayer.getUUID())) {
            return;
        }
        if (updateCard(data -> energy ? data.toggleAutoConnectEnergy() : data.toggleAutoConnect())) {
            autoConnectSlot.set(readCardData().autoConnect() ? 1 : 0);
            broadcastChanges();
        }
    }

    private int readCardLinkActive(int freqId) {
        if (freqId <= 0 || cardPlayer == null) {
            return 0;
        }
        var manager = WirelessFrequencyManager.get();
        if (manager == null) {
            return 0;
        }
        // The card is only active when bound to an advanced transmitter.
        return manager.resolveAdvancedNode(freqId, cardPlayer.serverLevel().getServer()) != null ? 1 : 0;
    }

    public int getUsedChannels() {
        return usedChannelsSlot.get();
    }

    public int getMaxChannels() {
        return maxChannelsSlot.get();
    }

    /**
     * Validate that the player has a {@link FrequencyMenu} open with the given containerId token.
     * Returns the menu when valid, otherwise null.
     */
    @Nullable
    public static FrequencyMenu validateToken(ServerPlayer player, int token) {
        if (player.containerMenu instanceof FrequencyMenu fm && fm.containerId == token) {
            return fm.stillValid(player) ? fm : null;
        }
        return null;
    }
}
