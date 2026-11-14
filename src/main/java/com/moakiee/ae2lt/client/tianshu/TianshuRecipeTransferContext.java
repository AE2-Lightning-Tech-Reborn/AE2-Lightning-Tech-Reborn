package com.moakiee.ae2lt.client.tianshu;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Recipe-viewer context carrying recipe/category identity into the provider picker, retained
 * across menu reopenings while the same terminal draft is still present.
 * This class does not start encoding itself; the optional Alt shortcut starts it only after the
 * viewer confirms a successful transfer, while this remains a reusable metadata bridge.
 *
 * <p>The JEI/EMI capture behavior is adapted from ExtendedAE Plus [ClientPlus], revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e (GNU LGPL 3.0). Unlike ClientPlus, this bridge does
 * not expose multiple switchable search conditions: the picker has one source field and one
 * alias field. Viewer keywords are retained only as default values that can be copied into the
 * alias field.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class TianshuRecipeTransferContext {
    private static final int ENCODING_RESULT_GRACE_TICKS = 5;
    private static WeakReference<TianshuPatternEncodingTermMenu> owner = new WeakReference<>(null);
    private static Snapshot snapshot = Snapshot.EMPTY;
    private static ItemStack encodingSourcePattern = ItemStack.EMPTY;
    private static boolean encodingPending;
    private static boolean encodingAckReceived;
    private static int encodingBindingDeadline;
    private static final int MAX_RETAINED_TERMINALS = 64;
    private static final LinkedHashMap<TerminalKey, RetainedContext> retained = new LinkedHashMap<>();

    private TianshuRecipeTransferContext() {
    }

    /** Captures the stable recipe type and exact recipe ID available to recipe viewers. */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu, Object recipeBase) {
        captureVanillaRecipe(menu, recipeBase, List.of());
    }

    /**
     * Captures the stable vanilla recipe identity while retaining viewer-specific aliases.
     * The vanilla recipe type remains authoritative whenever a real recipe is available.
     */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu,
            Object recipeBase,
            Iterable<String> additionalAliases) {
        captureVanillaRecipe(menu, recipeBase, "", additionalAliases);
    }

    /**
     * Captures a registered Minecraft recipe type, falling back to the viewer category only for
     * display models that are not backed by {@link Recipe}.
     */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu,
            Object recipeBase,
            String fallbackSourceKey,
            Iterable<String> additionalAliases) {
        captureVanillaRecipe(menu, recipeBase, fallbackSourceKey, additionalAliases, List.of());
    }

    public static void captureVanillaRecipe(TianshuPatternEncodingTermMenu menu, Object recipeBase,
            String fallbackSourceKey, Iterable<String> additionalAliases, List<String> machineIds) {
        String sourceKey = sourceKeyFor(recipeBase, fallbackSourceKey);
        String recipeId = "";
        var defaultAliases = new ArrayList<String>();
        if (recipeBase instanceof RecipeHolder<?> holder) {
            recipeId = holder.id().toString();
            addDefaultAlias(defaultAliases, firstPathSegment(holder.id().getPath()));
            addDefaultAlias(defaultAliases,
                    TianshuUploadAliasRules.namespaceGlob(holder.id().getNamespace()));
        }
        if (additionalAliases != null) {
            additionalAliases.forEach(value -> addDefaultAlias(defaultAliases, value));
        }
        publish(menu, sourceKey, recipeId, defaultAliases, machineIds);
    }

    /** Resolves source identity without changing the terminal's transferred recipe context. */
    public static String sourceKeyFor(Object recipeBase, String fallbackSourceKey) {
        Recipe<?> recipe = switch (recipeBase) {
            case RecipeHolder<?> holder -> holder.value();
            case Recipe<?> direct -> direct;
            case null -> null;
            default -> null;
        };
        if (recipe != null) {
            var typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (typeId != null) return typeId.toString();
        }
        return fallbackSourceKey == null ? "" : fallbackSourceKey;
    }

    /** Publishes viewer metadata without exposing optional viewer types to common code. */
    public static synchronized void publish(
            TianshuPatternEncodingTermMenu menu,
            String sourceKey,
            String recipeId,
            Iterable<String> defaultAliases) {
        publish(menu, sourceKey, recipeId, defaultAliases, List.of());
    }

    public static synchronized void publish(TianshuPatternEncodingTermMenu menu, String sourceKey,
            String recipeId, Iterable<String> defaultAliases, List<String> machineIds) {
        if (menu == null) return;
        var aliases = new LinkedHashSet<String>();
        if (machineIds != null) machineIds.stream().filter(id -> id != null && !id.isBlank()).forEach(aliases::add);
        addSourceAliases(aliases, sourceKey);
        if (defaultAliases != null) {
            defaultAliases.forEach(value -> {
                if (value != null && !value.isBlank()) aliases.add(value);
            });
        }
        owner = new WeakReference<>(menu);
        snapshot = new Snapshot(
                sourceKey == null ? "" : sourceKey,
                recipeId == null ? "" : recipeId,
                List.copyOf(aliases), machineIds);
        resetPendingEncoding();
    }

    private static void addSourceAliases(LinkedHashSet<String> aliases, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return;
        aliases.add(sourceKey);
        var sourceId = ResourceLocation.tryParse(sourceKey);
        if (sourceId != null) {
            aliases.add(TianshuUploadAliasRules.namespaceGlob(sourceId.getNamespace()));
        }
    }

    public static synchronized Snapshot snapshotFor(TianshuPatternEncodingTermMenu menu) {
        restore(menu);
        return owner.get() == menu ? snapshot : Snapshot.EMPTY;
    }

    /** Keep metadata with the terminal draft, not the short-lived menu that displayed it. */
    public static synchronized void remember(TianshuPatternEncodingTermMenu menu) {
        if (owner.get() != menu) return;
        var key = terminalKey(menu);
        if (key == null) return;
        retained.remove(key);
        if (!snapshot.sourceKey().isBlank()) {
            retained.put(key, new RetainedContext(snapshot, menu.tianshuMode, draft(menu)));
            while (retained.size() > MAX_RETAINED_TERMINALS) retained.remove(retained.keySet().iterator().next());
        }
        // Screen changes also remove the old screen while keeping this menu alive. Preserve
        // any encoding acknowledgement still in flight; a newly restored menu resets it.
    }

    private static void restore(TianshuPatternEncodingTermMenu menu) {
        if (menu == null || owner.get() == menu) return;
        var saved = retained.get(terminalKey(menu));
        if (saved == null || saved.mode() != menu.tianshuMode || !sameDraft(saved.draft(), draft(menu))) return;
        owner = new WeakReference<>(menu);
        snapshot = saved.snapshot();
        resetPendingEncoding();
    }

    private static TerminalKey terminalKey(TianshuPatternEncodingTermMenu menu) {
        // AE2 populates the menu locator only on the server; derive it from the client host.
        var locator = switch (menu.getTarget()) {
            case AEBasePart part -> MenuLocators.forPart(part);
            case ItemMenuHost<?> item -> item.getLocator();
            default -> menu.getLocator();
        };
        return locator == null ? null
                : new TerminalKey(menu.getPlayer().level().dimension(), locator);
    }

    private static List<ItemStack> draft(TianshuPatternEncodingTermMenu menu) {
        var result = new ArrayList<ItemStack>();
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.PROCESSING_INPUTS,
                SlotSemantics.PROCESSING_OUTPUTS, SlotSemantics.SMITHING_TABLE_TEMPLATE,
                SlotSemantics.SMITHING_TABLE_BASE, SlotSemantics.SMITHING_TABLE_ADDITION,
                SlotSemantics.STONECUTTING_INPUT)) {
            menu.getSlots(semantic).forEach(slot -> result.add(slot.getItem().copy()));
        }
        return List.copyOf(result);
    }

    private static boolean sameDraft(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.matches(left.get(i), right.get(i))) return false;
        }
        return true;
    }

    @SubscribeEvent
    public static synchronized void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        retained.clear();
        owner = new WeakReference<>(null);
        snapshot = Snapshot.EMPTY;
        resetPendingEncoding();
    }

    private record TerminalKey(ResourceKey<Level> dimension, MenuHostLocator locator) { }
    private record RetainedContext(Snapshot snapshot, TianshuEncodingMode mode, List<ItemStack> draft) { }

    public static synchronized void clear(TianshuPatternEncodingTermMenu menu) {
        if (menu == null) return;
        retained.remove(terminalKey(menu));
        owner = new WeakReference<>(menu);
        snapshot = Snapshot.EMPTY;
        resetPendingEncoding();
    }

    /**
     * Marks an encode request before it is sent to the server. The encoded slot and GuiSync
     * acknowledgement are synchronized independently, so either one may reach the client first.
     */
    public static synchronized void beginEncoding(
            TianshuPatternEncodingTermMenu menu, ItemStack currentPattern) {
        restore(menu);
        if (owner.get() != menu || snapshot.sourceKey().isBlank()) {
            resetPendingEncoding();
            return;
        }
        encodingSourcePattern = copyOrEmpty(currentPattern);
        encodingPending = true;
        encodingAckReceived = false;
        encodingBindingDeadline = menu.getPlayer().tickCount + 40;
    }

    /** Observes the server acknowledgement without tying recipe metadata to the slot contents. */
    public static synchronized void acceptEncodedPattern(
            TianshuPatternEncodingTermMenu menu, ItemStack encodedPattern) {
        if (!encodingPending || owner.get() != menu || snapshot.sourceKey().isBlank()) return;
        if (!encodingAckReceived) {
            encodingAckReceived = true;
            encodingBindingDeadline =
                    menu.getPlayer().tickCount + ENCODING_RESULT_GRACE_TICKS;
        }
        if (isNewEncodingResult(encodedPattern)) resetPendingEncoding();
    }

    /**
     * Returns whether the current encoded stack is safe to use for a triggered upload. A short
     * grace period is needed only when the acknowledgement arrived before the slot update.
     */
    public static synchronized boolean isEncodingResultReady(
            TianshuPatternEncodingTermMenu menu, ItemStack current) {
        finishExpiredEncoding(menu);
        if (!encodingPending || owner.get() != menu || snapshot.sourceKey().isBlank()) {
            return true;
        }
        if (encodingAckReceived && isNewEncodingResult(current)) resetPendingEncoding();
        return !encodingPending;
    }

    private static void finishExpiredEncoding(TianshuPatternEncodingTermMenu menu) {
        if (!encodingPending || owner.get() != menu
                || menu.getPlayer().tickCount <= encodingBindingDeadline) {
            return;
        }
        // A missing acknowledgement means encoding failed; an acknowledged but unchanged stack
        // means the new encoding is byte-for-byte identical. Neither case invalidates metadata.
        resetPendingEncoding();
    }

    private static boolean isNewEncodingResult(ItemStack pattern) {
        return pattern != null
                && !pattern.isEmpty()
                && (encodingSourcePattern.isEmpty()
                        || !ItemStack.matches(encodingSourcePattern, pattern));
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static void resetPendingEncoding() {
        encodingSourcePattern = ItemStack.EMPTY;
        encodingPending = false;
        encodingAckReceived = false;
        encodingBindingDeadline = 0;
    }

    public static void addDefaultAlias(List<String> aliases, String value) {
        if (aliases != null && value != null && !value.isBlank() && !aliases.contains(value)) {
            aliases.add(value);
        }
    }

    public static String firstPathSegment(String path) {
        if (path == null || path.isBlank()) return "";
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }

    public record Snapshot(String sourceKey, String recipeId, List<String> defaultAliases, List<String> machineIds) {
        public Snapshot(String sourceKey, String recipeId, List<String> defaultAliases) {
            this(sourceKey, recipeId, defaultAliases, List.of());
        }
        private static final Snapshot EMPTY = new Snapshot("", "", List.of());

        public Snapshot {
            sourceKey = sourceKey == null ? "" : sourceKey;
            recipeId = recipeId == null ? "" : recipeId;
            defaultAliases = defaultAliases == null ? List.of() : List.copyOf(defaultAliases);
            machineIds = machineIds == null ? List.of() : machineIds.stream()
                    .filter(id -> id != null && !id.isBlank()).distinct().toList();
        }
    }
}
