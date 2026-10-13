package com.moakiee.ae2lt.client.compat;

import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.integration.modules.itemlists.TransferHelper;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyPacket;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyResultPacket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Adds a real-inventory refill before the existing handler, without registering any new machine handlers. */
public final class JeiWirelessSupplyClient {
    @FunctionalInterface
    public interface NativeTransfer {
        @Nullable IRecipeTransferError transfer(boolean maximum, boolean doTransfer, @Nullable IRecipeSlotsView slots);
    }
    private record Key(Object recipe, Object handler) {}
    private static final class Offer {
        final int id;
        final long requestedAt = Util.getMillis();
        boolean awaiting = true;
        List<ItemStack> items;
        List<ItemStack> craftable = List.of();
        long fingerprint = Long.MIN_VALUE;
        List<ItemStack> normalPlan = List.of();
        MissingPlan missingPlan;
        Offer(int id) { this.id = id; }
    }
    private record Pending(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
                           AbstractContainerMenu menu, Object recipe, IRecipeSlotsView slots,
                           Player player, boolean maximum, Screen screen, Offer offer, boolean taking, boolean craftMissing, boolean partial,
                           NativeTransfer nativeTransfer) {}
    private record MissingPlan(List<ItemStack> stored, List<ItemStack> craft, IRecipeSlotsView slots,
                               CraftingTermMenu.MissingIngredientSlots missing, boolean hasAvailable) {}
    private static final Map<Key, Offer> OFFERS = new LinkedHashMap<>(32, 0.75f, true);
    private static AbstractContainerMenu currentMenu;
    private static Pending pending;
    private static int sequence;
    private JeiWirelessSupplyClient() {}

    @Nullable
    public static IRecipeTransferError transfer(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
            AbstractContainerMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
            boolean maximum, boolean doTransfer, NativeTransfer nativeTransfer) {
        if (!AE2LTClientConfig.jeiWirelessSupply() || menu instanceof MEStorageMenu || menu == player.inventoryMenu
                || menu != player.containerMenu || TianshuWirelessIngredientSource.locate(player).isEmpty()) {
            return nativeTransfer.transfer(maximum, doTransfer, null);
        }
        var nativeError = nativeTransfer.transfer(maximum, false, null);
        // A missing handler/canHandle rejection remains authoritative, including custom handlers.
        if (nativeError != null && nativeError.getType() == IRecipeTransferError.Type.INTERNAL) return nativeError;
        if (allows(nativeError) && !maximum && !Screen.hasShiftDown()) {
            return doTransfer ? nativeTransfer.transfer(false, true, null) : nativeError;
        }
        if (currentMenu != menu) { clear(); currentMenu = menu; }
        if (pending != null && Util.getMillis() - pending.offer().requestedAt > 5000) pending = null;
        if (pending != null && doTransfer) return hint(false, "waiting");

        var key = new Key(recipe, handler);
        var offer = OFFERS.get(key);
        if (pending == null && (offer == null
                || Util.getMillis() - offer.requestedAt > (offer.awaiting ? 3000 : 1000))) {
            var wanted = queryItems(slots);
            if (wanted.isEmpty()) return doTransfer && allows(nativeError)
                    ? nativeTransfer.transfer(maximum, true, null) : nativeError;
            var refreshed = new Offer(++sequence);
            if (offer != null) {
                // Refresh the authorization without dropping the last server snapshot. JEI updates
                // buttons every tick, so returning the backpack-only error here makes them flicker.
                refreshed.items = offer.items;
                refreshed.craftable = offer.craftable;
            }
            offer = refreshed;
            if (!OFFERS.containsKey(key)) {
                while (OFFERS.size() >= 32) OFFERS.remove(OFFERS.keySet().iterator().next());
            }
            OFFERS.put(key, offer);
            PacketDistributor.sendToServer(new WirelessJeiSupplyPacket(menu.containerId, offer.id, false, wanted));
        }
        if (offer == null) return nativeError;
        var operation = new Pending(handler, menu, recipe, slots, player, maximum,
                Minecraft.getInstance().screen, offer, false, Screen.hasControlDown(), false, nativeTransfer);
        if (offer.items == null) {
            if (doTransfer) { pending = operation; return hint(false, "waiting"); }
            return nativeError;
        }
        var plan = plan(operation);
        if (plan.isEmpty()) {
            if (offer.missingPlan != null) {
                if (!doTransfer) return new CraftingFeedback(offer.missingPlan.missing(), operation.craftMissing());
                if (!offer.missingPlan.stored().isEmpty()) {
                    beginTake(operation, offer.missingPlan.stored(), true);
                    return hint(false, "waiting");
                }
                return finishPartial(operation);
            }
            return doTransfer && allows(nativeError) ? nativeTransfer.transfer(maximum, true, null) : nativeError;
        }
        if (doTransfer) {
            beginTake(operation, plan, false);
            return hint(false, "waiting");
        }
        return null;
    }

    /** Requests only item types occurring in this recipe, retaining exact returned components for JEI to compare. */
    private static List<ItemStack> queryItems(IRecipeSlotsView slots) {
        var wanted = new LinkedHashMap<Item, Integer>();
        for (var slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            var alternatives = slot.getItemStacks().map(ItemStack::getItem).distinct().toList();
            for (var item : alternatives) wanted.merge(item, 64, (a, b) -> Math.min(WirelessJeiInventoryPlan.MAX_COUNT, a + b));
            if (wanted.size() > WirelessJeiInventoryPlan.MAX_ENTRIES) return List.of();
        }
        return wanted.entrySet().stream().map(e -> new ItemStack(e.getKey(), e.getValue())).toList();
    }

    private static List<ItemStack> plan(Pending operation) {
        var offer = operation.offer();
        long fingerprint = operation.menu().getStateId();
        for (var slot : operation.menu().slots) {
            var stack = slot.getItem();
            fingerprint = fingerprint * 31 + java.util.Objects.hash(stack.getItem(), stack.getComponents()) * 31L + stack.getCount();
        }
        if (offer.fingerprint != fingerprint) {
            offer.fingerprint = fingerprint;
            offer.normalPlan = buildNormalPlan(operation);
            offer.missingPlan = offer.normalPlan.isEmpty() ? buildMissingPlan(operation) : null;
        }
        if (!operation.maximum()) return WirelessJeiInventoryPlan.copy(offer.normalPlan);
        return expandPlan(operation, offer.normalPlan);
    }

    private static List<ItemStack> buildNormalPlan(Pending operation) {
        if (allows(preview(operation, List.of()))) return List.of();
        // Pick a usable alternative for each input first. Adding all tag variants can fill the
        // last empty inventory slots before a different required ingredient has a chance to fit.
        var result = new ArrayList<ItemStack>();
        var inputs = new ArrayList<>(operation.slots().getSlotViews(RecipeIngredientRole.INPUT));
        inputs.sort(java.util.Comparator.comparingLong(input -> input.getItemStacks().count()));
        var existing = operation.menu().slots.stream()
                .filter(s -> !s.isFake() && s.mayPickup(operation.player()) && s.mayPlace(s.getItem()))
                .map(s -> s.getItem().copy()).toList();
        for (var input : inputs) {
            int required = input.getItemStacks().mapToInt(ItemStack::getCount).max().orElse(0);
            for (var stack : existing) {
                if (stack.isEmpty() || input.getItemStacks().noneMatch(s -> equivalent(s, stack))) continue;
                int used = Math.min(required, stack.getCount());
                stack.shrink(used); required -= used;
                if (required == 0) break;
            }
            if (required == 0) continue;
            var candidates = operation.offer().items.stream()
                    .filter(offer -> input.getItemStacks().anyMatch(s -> equivalent(s, offer)))
                    .sorted(java.util.Comparator.<ItemStack>comparingInt(offer ->
                            result.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, offer)) ? 0
                            : operation.player().getInventory().items.stream()
                                    .anyMatch(s -> ItemStack.isSameItemSameComponents(s, offer)) ? 1 : 2)
                            .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed())).toList();
            for (var offered : candidates) {
                int needed = Math.min(required, input.getItemStacks().filter(s -> equivalent(s, offered))
                        .mapToInt(ItemStack::getCount).max().orElse(0));
                int planned = result.stream().filter(s -> ItemStack.isSameItemSameComponents(s, offered)).mapToInt(ItemStack::getCount).sum();
                int previous = result.size();
                addFitting(operation.player(), result, offered, Math.min(needed, offered.getCount() - planned));
                if (result.size() > previous) break;
            }
        }
        if (!allows(preview(operation, result))) {
            // A custom handler may require a different combination; retain the broader native
            // preview path when all relevant alternatives can fit.
            result.clear();
            addAllAlternatives(operation, result);
        }
        if (!allows(preview(operation, result))) return List.of();
        return minimize(operation, result);
    }

    @Nullable
    private static MissingPlan buildMissingPlan(Pending operation) {
        var existing = operation.menu().slots.stream()
                .filter(s -> !s.isFake() && s.mayPickup(operation.player()) && s.mayPlace(s.getItem()))
                .map(s -> s.getItem().copy()).toList();
        var available = WirelessJeiInventoryPlan.copy(operation.offer().items);
        var stored = new ArrayList<ItemStack>();
        var craft = new LinkedHashMap<appeng.api.stacks.AEItemKey, Integer>();
        var missingSlots = new java.util.HashSet<Integer>();
        var craftableSlots = new java.util.HashSet<Integer>();
        var replacements = new java.util.IdentityHashMap<IRecipeSlotView, IRecipeSlotView>();
        var inputs = operation.slots().getSlotViews(RecipeIngredientRole.INPUT);
        boolean hasAvailable = false;
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            int required = input.getItemStacks().mapToInt(ItemStack::getCount).max().orElse(0);
            int remaining = required;
            for (var stack : existing) {
                if (stack.isEmpty() || input.getItemStacks().noneMatch(s -> equivalent(s, stack))) continue;
                int used = Math.min(remaining, stack.getCount());
                stack.shrink(used); remaining -= used;
                if (remaining == 0) break;
            }
            for (var stack : available) {
                if (remaining == 0) break;
                if (stack.isEmpty() || input.getItemStacks().noneMatch(s -> equivalent(s, stack))) continue;
                int used = Math.min(remaining, stack.getCount());
                stored.add(stack.copyWithCount(used));
                stack.shrink(used); remaining -= used;
            }
            hasAvailable |= required > remaining;
            if (remaining == 0) continue;
            var output = operation.offer().craftable.stream()
                    .filter(s -> input.getItemStacks().anyMatch(wanted -> equivalent(wanted, s))).findFirst().orElse(null);
            if (output == null) missingSlots.add(index);
            else {
                craftableSlots.add(index);
                craft.merge(appeng.api.stacks.AEItemKey.of(output), remaining, Integer::sum);
            }
            replacements.put(input, partialSlot(input, required - remaining));
        }
        if ((missingSlots.isEmpty() && craftableSlots.isEmpty()) || (!hasAvailable && craftableSlots.isEmpty())) return null;
        IRecipeSlotsView partialSlots = () -> operation.slots().getSlotViews().stream()
                .map(s -> replacements.getOrDefault(s, s)).toList();
        if (!allows(preview(operation, stored, partialSlots))) return null;
        return new MissingPlan(stored, craft.entrySet().stream().map(e -> e.getKey().toStack(e.getValue())).toList(),
                partialSlots, new CraftingTermMenu.MissingIngredientSlots(missingSlots, craftableSlots), hasAvailable);
    }

    private static IRecipeSlotView partialSlot(IRecipeSlotView original, int count) {
        var manager = Internal.getJeiRuntime().getIngredientManager();
        var ingredients = original.getItemStacks().map(s -> s.copyWithCount(count))
                .filter(s -> !s.isEmpty()).map(s -> manager.createTypedIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, s)
                        .orElseThrow()).toList();
        return (IRecipeSlotView) java.lang.reflect.Proxy.newProxyInstance(IRecipeSlotView.class.getClassLoader(),
                new Class<?>[]{IRecipeSlotView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAllIngredients")) return ingredients.stream();
                    if (method.getName().equals("getAllIngredientsList")) return ingredients;
                    if (method.getName().equals("getDisplayedIngredient") && method.getParameterCount() == 0)
                        return ingredients.stream().findFirst();
                    if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    return method.invoke(original, args);
                });
    }

    private static void addAllAlternatives(Pending operation, List<ItemStack> result) {
        for (var offered : operation.offer().items) {
            int needed = 0;
            for (var input : operation.slots().getSlotViews(RecipeIngredientRole.INPUT)) {
                int count = input.getItemStacks().filter(s -> equivalent(s, offered)).mapToInt(ItemStack::getCount).max().orElse(0);
                needed = Math.min(WirelessJeiInventoryPlan.MAX_COUNT, needed + count);
            }
            int amount = Math.min(needed, offered.getCount());
            if (amount > 0) addFitting(operation.player(), result, offered, amount);
        }
    }

    private static List<ItemStack> minimize(Pending operation, List<ItemStack> result) {
        // Let the existing handler identify the actual deficit, including items already in machine slots.
        for (int i = result.size() - 1; i >= 0; i--) {
            var stack = result.get(i);
            int low = 0, high = stack.getCount();
            while (low < high) {
                int middle = (low + high) >>> 1;
                var candidate = WirelessJeiInventoryPlan.copy(result);
                if (middle == 0) candidate.remove(i); else candidate.set(i, stack.copyWithCount(middle));
                if (allows(preview(operation, candidate))) high = middle; else low = middle + 1;
            }
            if (low == 0) result.remove(i); else result.set(i, stack.copyWithCount(low));
        }
        return result;
    }

    private static List<ItemStack> expandPlan(Pending operation, List<ItemStack> minimum) {
        var targets = new LinkedHashMap<appeng.api.stacks.AEItemKey, Integer>();
        for (var input : operation.slots().getSlotViews(RecipeIngredientRole.INPUT)) {
            var candidates = operation.offer().items.stream()
                    .filter(offer -> input.getItemStacks().anyMatch(s -> equivalent(s, offer)))
                    .sorted(java.util.Comparator.comparingInt(offer -> {
                        if (minimum.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, offer))) return 0;
                        if (operation.menu().slots.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s.getItem(), offer))) return 1;
                        return 2;
                    })).toList();
            if (!candidates.isEmpty()) {
                var chosen = candidates.get(0);
                targets.merge(appeng.api.stacks.AEItemKey.of(chosen), Math.min(64, chosen.getMaxStackSize()), Integer::sum);
            }
        }
        var result = WirelessJeiInventoryPlan.copy(minimum);
        for (var entry : targets.entrySet()) {
            var stack = entry.getKey().toStack();
            int present = operation.menu().slots.stream().filter(s -> !s.isFake())
                    .map(s -> s.getItem()).filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
            int planned = result.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
            int available = operation.offer().items.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack))
                    .mapToInt(ItemStack::getCount).sum();
            int additional = Math.min(entry.getValue() - present - planned, available - planned);
            if (additional > 0) addFitting(operation.player(), result, stack, additional);
        }
        return allows(preview(operation, result)) ? result : minimum;
    }

    private static void addFitting(Player player, List<ItemStack> plan, ItemStack stack, int limit) {
        int low = 0, high = limit;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            var candidate = WirelessJeiInventoryPlan.copy(plan);
            candidate.add(stack.copyWithCount(middle));
            if (WirelessJeiInventoryPlan.insert(player.getInventory().items, candidate) != null) low = middle;
            else high = middle - 1;
        }
        if (low > 0) plan.add(stack.copyWithCount(low));
    }

    private static boolean equivalent(ItemStack a, ItemStack b) {
        return Internal.getJeiRuntime().getJeiHelpers().getStackHelper().isEquivalent(a, b, UidContext.Recipe);
    }

    @Nullable
    private static IRecipeTransferError preview(Pending operation, List<ItemStack> materials) {
        return preview(operation, materials, null);
    }

    @Nullable
    private static IRecipeTransferError preview(Pending operation, List<ItemStack> materials, @Nullable IRecipeSlotsView slots) {
        var inventory = operation.player().getInventory().items;
        var simulated = WirelessJeiInventoryPlan.insert(inventory, materials);
        if (simulated == null) return hint(false, "no_space");
        var original = new ArrayList<>(inventory);
        try {
            for (int i = 0; i < 36; i++) inventory.set(i, simulated.get(i));
            return operation.nativeTransfer().transfer(slots == null && operation.maximum(), false, slots);
        } finally {
            for (int i = 0; i < 36; i++) inventory.set(i, original.get(i));
        }
    }

    private static void beginTake(Pending operation, List<ItemStack> plan, boolean partial) {
        pending = new Pending(operation.handler(), operation.menu(), operation.recipe(), operation.slots(),
                operation.player(), operation.maximum(), operation.screen(), operation.offer(), true, operation.craftMissing(), partial, operation.nativeTransfer());
        PacketDistributor.sendToServer(new WirelessJeiSupplyPacket(operation.menu().containerId, operation.offer().id, true,
                partial && operation.craftMissing() && !operation.offer().missingPlan.craft().isEmpty(), plan));
    }

    private static void beginCraft(Pending operation) {
        pending = new Pending(operation.handler(), operation.menu(), operation.recipe(), operation.slots(),
                operation.player(), operation.maximum(), operation.screen(), operation.offer(), true, true, true, operation.nativeTransfer());
        PacketDistributor.sendToServer(new WirelessJeiSupplyPacket(operation.menu().containerId, operation.offer().id,
                false, true, operation.offer().missingPlan.craft()));
    }

    @Nullable
    private static IRecipeTransferError finishPartial(Pending operation) {
        var plan = operation.offer().missingPlan;
        if (plan.hasAvailable()) {
            var error = operation.nativeTransfer().transfer(false, false, plan.slots());
            if (!allows(error)) return error;
            error = operation.nativeTransfer().transfer(false, true, plan.slots());
            if (!allows(error)) return error;
        }
        if (operation.craftMissing() && !plan.craft().isEmpty()) beginCraft(operation);
        else clear();
        return null;
    }

    public static void receive(WirelessJeiSupplyResultPacket packet) {
        try {
            applyResult(packet);
        } catch (RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Existing JEI handler failed after wireless refill", error);
            clear();
            var player = Minecraft.getInstance().player;
            if (player != null) player.displayClientMessage(Component.translatable("ae2lt.tianshu.jei_supply.retry"), true);
        }
    }

    private static void applyResult(WirelessJeiSupplyResultPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu != currentMenu
                || currentMenu.containerId != packet.containerId() || !AE2LTClientConfig.jeiWirelessSupply()) { clear(); return; }
        for (var offer : OFFERS.values()) {
            if (offer.id == packet.requestId() && (packet.status() == 1 || packet.status() == 0)) {
                offer.awaiting = false;
                // A failed query must also replace the cached snapshot (e.g. a disconnected terminal).
                offer.items = packet.status() == 1 ? WirelessJeiInventoryPlan.copy(packet.items()) : List.of();
                offer.craftable = packet.status() == 1 ? WirelessJeiInventoryPlan.copy(packet.craftable()) : List.of();
                offer.fingerprint = Long.MIN_VALUE;
            }
        }
        var operation = pending;
        if (operation == null || operation.offer().id != packet.requestId()) return;
        // A click can use the cached snapshot while its refresh is still in flight. That query
        // reply is not the extraction acknowledgement; keep waiting for the take/craft result.
        if (operation.taking() && packet.status() == 1) return;
        pending = null;
        if (packet.status() == 3) { clear(); return; }
        if (minecraft.screen != operation.screen()) return;
        if (!operation.taking() && packet.status() == 1) {
            var materials = plan(operation);
            if (!materials.isEmpty()) { beginTake(operation, materials, false); return; }
            if (operation.offer().missingPlan != null) {
                if (!operation.offer().missingPlan.stored().isEmpty()) {
                    beginTake(operation, operation.offer().missingPlan.stored(), true); return;
                }
                if (allows(finishPartial(operation))) return;
            }
        } else if (operation.taking() && packet.status() == 2) {
            if (operation.partial()) {
                if (allows(finishPartial(operation))) {
                    if (minecraft.screen instanceof RecipesGui gui) gui.onClose();
                    return;
                }
            }
            var error = operation.nativeTransfer().transfer(operation.maximum(), false, null);
            if (allows(error)) {
                error = operation.nativeTransfer().transfer(operation.maximum(), true, null);
                if (allows(error) && minecraft.screen instanceof RecipesGui gui) gui.onClose();
                clear();
                return;
            }
        }
        OFFERS.clear();
        minecraft.player.displayClientMessage(Component.translatable("ae2lt.tianshu.jei_supply.retry"), true);
    }

    public static void clear() { OFFERS.clear(); pending = null; currentMenu = null; }
    private static boolean allows(@Nullable IRecipeTransferError error) { return error == null || error.getType().allowsTransfer; }
    private record CraftingFeedback(CraftingTermMenu.MissingIngredientSlots missing, boolean craftMissing)
            implements IRecipeTransferError {
        @Override public Type getType() { return Type.COSMETIC; }
        @Override public int getButtonHighlightColor() {
            return missing.anyMissing() ? TransferHelper.ORANGE_PLUS_BUTTON_COLOR : TransferHelper.BLUE_PLUS_BUTTON_COLOR;
        }
        @Override public int getMissingCountHint() { return missing.missingSlots().size(); }
        @Override public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.addAll(TransferHelper.createCraftingTooltip(missing, craftMissing, true));
        }
        @Override public void showError(GuiGraphics graphics, int mouseX, int mouseY,
                IRecipeSlotsView slots, int recipeX, int recipeY) {
            var pose = graphics.pose();
            pose.pushPose(); pose.translate(recipeX, recipeY, 0);
            var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            for (int i = 0; i < inputs.size(); i++) {
                if (missing.missingSlots().contains(i)) inputs.get(i).drawHighlight(graphics, TransferHelper.RED_SLOT_HIGHLIGHT_COLOR);
                else if (missing.craftableSlots().contains(i)) inputs.get(i).drawHighlight(graphics, TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR);
            }
            pose.popPose();
        }
    }
    private static IRecipeTransferError hint(boolean allowed, String message) {
        return new IRecipeTransferError() {
            @Override public Type getType() { return allowed ? Type.COSMETIC : Type.USER_FACING; }
            @Override public void getTooltip(ITooltipBuilder tooltip) { tooltip.add(Component.translatable("ae2lt.tianshu.jei_supply." + message)); }
        };
    }
}
