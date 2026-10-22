package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import de.mari_023.ae2wtlib.AE2wtlib;

import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;

import de.mari_023.ae2wtlib.UpgradeHelper;
import de.mari_023.ae2wtlib.wut.WUTHandler;

/**
 * AE2WTLib integration entry point (Forge API).
 *
 * <p>Registers the wireless Tianshu terminal with ae2wtlib's WUT handler so it
 * appears in the Wireless Universal Terminal and accepts the overloaded
 * frequency card in its upgrade slots. ae2wtlib is optional: every public
 * entry point here must only be called after an ae2wtlib presence check, since
 * this class (and {@link TianshuWTItem} / {@link TianshuWTMenuHost}) reference
 * ae2wtlib types and would otherwise fail to load. AE2WTLib's own
 * {@code UpgradeHelper} queues upgrades until its terminal registration has
 * finished, which makes the registration order safe.</p>
 */
public final class Ae2wtlibIntegration {
    public static final String TIANSHU_TERMINAL_NAME = "tianshu_pattern_encoding";
    public static final String TIANSHU_TERMINAL_DESCRIPTION_ID =
            "item.ae2lt.wireless_tianshu_pattern_encoding_terminal";

    public static final String TIANSHU_CRAFTING_NAME = "tianshu_crafting";
    public static final String TIANSHU_CRAFTING_DESCRIPTION_ID = "item.ae2lt.wireless_tianshu_crafting_terminal";
    private static TianshuCraftingWTItem craftingTerminal;
    public static synchronized TianshuCraftingWTItem craftingTerminal() {
        if (craftingTerminal == null) craftingTerminal = new TianshuCraftingWTItem();
        return craftingTerminal;
    }
    private static TianshuWTItem tianshuTerminal;
    private static boolean terminalRegistrationRequested;

    private Ae2wtlibIntegration() {
    }

    /**
     * Shared by the DeferredRegister and AE2WTLib's terminal definition. It cannot be
     * created during mod construction because {@code ItemWT} needs the intrusive item
     * registry to be writable. Only call when ae2wtlib is present.
     */
    public static synchronized TianshuWTItem terminal() {
        if (tianshuTerminal == null) {
            tianshuTerminal = new TianshuWTItem();
        }
        return tianshuTerminal;
    }

    /**
     * Adds the external terminal when the item registry opens. AE2WTLib has registered its own
     * terminals by then, so the Tianshu terminal follows them in the universal-terminal selector.
     */
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.ITEMS.getRegistryKey())) {
            registerTerminal();
        }
    }

    public static synchronized void registerTerminal() {
        if (terminalRegistrationRequested) {
            return;
        }

        WUTHandler.addTerminal(
                TIANSHU_TERMINAL_NAME,
                terminal()::tryOpen,
                TianshuWTMenuHost::new,
                TianshuWirelessPatternEncodingTermMenu.TYPE,
                terminal(),
                TIANSHU_TERMINAL_DESCRIPTION_ID);
        WUTHandler.addTerminal(TIANSHU_CRAFTING_NAME, craftingTerminal()::tryOpen,
                com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost::new,
                com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu.TYPE, craftingTerminal(), TIANSHU_CRAFTING_DESCRIPTION_ID);
        terminalRegistrationRequested = true;
    }

    /**
     * Fails fast if the WUT handler did not pick up the terminal definition.
     * Only call when ae2wtlib is present.
     */
    public static void verifyTerminalRegistration() {
        var definition = WUTHandler.wirelessTerminals.get(TIANSHU_TERMINAL_NAME);
        var expected = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
        if (definition == null || !WUTHandler.terminalNames.contains(TIANSHU_TERMINAL_NAME)
                || definition.item() != expected) {
            throw new IllegalStateException("AE2WTLib did not register Tianshu terminal " + TIANSHU_TERMINAL_NAME);
        }
        var craftingDefinition = WUTHandler.wirelessTerminals.get(TIANSHU_CRAFTING_NAME);
        if (craftingDefinition == null || !WUTHandler.terminalNames.contains(TIANSHU_CRAFTING_NAME)
                || craftingDefinition.item() != ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get()) {
            throw new IllegalStateException("AE2WTLib did not register Tianshu terminal " + TIANSHU_CRAFTING_NAME);
        }
    }

    /**
     * Registers the overloaded frequency card as a one-slot upgrade for every
     * ae2wtlib wireless terminal (and the universal terminal). If ae2wtlib has
     * not finished its own upgrade registration yet, {@link UpgradeHelper}
     * queues this and applies it once it is ready.
     */
    public static void register() {
        UpgradeHelper.addUpgradeToAllTerminals(ModItems.OVERLOADED_FREQUENCY_CARD.get(), 1);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(TianshuWctIntegration.class);
        var crafting = ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
        Upgrades.add(AE2wtlib.MAGNET_CARD, crafting, 1);
        Upgrades.add(AE2wtlib.QUANTUM_BRIDGE_CARD, crafting, 1);
        Upgrades.add(AEItems.ENERGY_CARD, crafting, 2);
        // WTLib's initial sweep can precede our item-registry callback on Forge.
        var terminal = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
        appeng.api.upgrades.Upgrades.add(de.mari_023.ae2wtlib.AE2wtlib.QUANTUM_BRIDGE_CARD, terminal, 1);
        appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD, terminal, 2);
    }
}
