package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

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
    private static com.moakiee.ae2lt.item.TianshuWirelessCraftingTerminalItem craftingTerminal;
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

    public static synchronized com.moakiee.ae2lt.item.TianshuWirelessCraftingTerminalItem craftingTerminal() {
        if (craftingTerminal == null) craftingTerminal = new com.moakiee.ae2lt.item.TianshuWirelessCraftingTerminalItem();
        return craftingTerminal;
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
                com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu.TYPE, craftingTerminal(),
                "item.ae2lt.wireless_tianshu_crafting_terminal");
        terminalRegistrationRequested = true;
    }

    /**
     * Fails fast if the WUT handler did not pick up the terminal definition.
     * Only call when ae2wtlib is present.
     */
    public static void verifyTerminalRegistration() {
        for (var name : java.util.List.of(TIANSHU_TERMINAL_NAME, TIANSHU_CRAFTING_NAME)) {
            var definition = WUTHandler.wirelessTerminals.get(name);
            var expected = name.equals(TIANSHU_TERMINAL_NAME)
                    ? ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()
                    : ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
            if (definition == null || !WUTHandler.terminalNames.contains(name) || definition.item() != expected)
                throw new IllegalStateException("AE2WTLib did not register Tianshu terminal " + name);
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
        // WTLib's initial sweep can precede our item-registry callback on Forge.
        for (var terminal : java.util.List.of(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get(),
                ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get())) {
            appeng.api.upgrades.Upgrades.add(de.mari_023.ae2wtlib.AE2wtlib.QUANTUM_BRIDGE_CARD, terminal, 1);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD, terminal, 2);
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("ae2wtlib")) TianshuWctIntegration.registerUpgrades();
    }
}
