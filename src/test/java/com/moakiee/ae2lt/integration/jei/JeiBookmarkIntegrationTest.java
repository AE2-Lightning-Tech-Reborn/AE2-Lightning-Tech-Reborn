package com.moakiee.ae2lt.integration.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.bookmarks.BookmarkType;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JeiBookmarkIntegrationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetAdapters() {
        JeiBookmarkAccessImpl.clearRuntime();
        FakeEaep.runtime = new Object();
        FakeEaep.items.clear();
        FakeEaep.fluids.clear();
        FakeEaep.fail = false;
        FakeEaep.attempts = 0;
    }

    @AfterEach
    void releaseRuntime() {
        JeiBookmarkAccessImpl.clearRuntime();
    }

    @Test
    void prefersEaepAndResolvesExactItemAndFluidOverloads() throws Exception {
        var overlay = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));
        installEaep();

        assertTrue(JeiBookmarkAccessImpl.isAvailable(true));
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);

        assertEquals(1, FakeEaep.items.size());
        assertEquals(Items.IRON_INGOT, FakeEaep.items.getFirst().getItem());
        assertEquals(1, FakeEaep.items.getFirst().getCount());
        assertEquals(1, FakeEaep.fluids.size());
        assertEquals(Fluids.WATER, FakeEaep.fluids.getFirst().getFluid());
        assertEquals(1000, FakeEaep.fluids.getFirst().getAmount());
        assertTrue(overlay.bookmarkList.ingredients.isEmpty());
    }

    @Test
    void absentEaepUsesJeiWithoutResolvingEaepClasses() throws Exception {
        var overlay = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));

        assertTrue(JeiBookmarkAccessImpl.isAvailable(false));
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), false);

        assertEquals(2, overlay.bookmarkList.ingredients.size());
        assertEquals(VanillaTypes.ITEM_STACK, overlay.bookmarkList.ingredients.get(0).getType());
        assertEquals(NeoForgeTypes.FLUID_STACK, overlay.bookmarkList.ingredients.get(1).getType());
        assertEquals(1, ((ItemStack) overlay.bookmarkList.ingredients.get(0).getIngredient()).getCount());
        assertEquals(1000, ((FluidStack) overlay.bookmarkList.ingredients.get(1).getIngredient()).getAmount());
        var resolved = JeiBookmarkAccessImpl.class.getDeclaredField("eaepResolved");
        resolved.setAccessible(true);
        assertEquals(false, resolved.get(null));
    }

    @Test
    void unavailableEaepRuntimeFallsBackAndCanBecomeAvailableLater() throws Exception {
        var overlay = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));
        installEaep();
        FakeEaep.runtime = null;

        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);
        assertEquals(2, overlay.bookmarkList.ingredients.size());
        assertTrue(FakeEaep.items.isEmpty());

        FakeEaep.runtime = new Object();
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);
        assertEquals(1, FakeEaep.items.size());
        assertEquals(1, FakeEaep.fluids.size());
        assertEquals(2, overlay.bookmarkList.ingredients.size());
    }

    @Test
    void missingEaepHelperClassFallsBackInsteadOfCrashing() {
        var overlay = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));

        assertTrue(JeiBookmarkAccessImpl.isAvailable(true));
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);

        assertEquals(2, overlay.bookmarkList.ingredients.size());
    }

    @Test
    void compatibleEaepWorksEvenWhenDirectJeiInternalsAreUnavailable() throws Exception {
        JeiBookmarkAccessImpl.setRuntime(runtime(null));
        assertFalse(JeiBookmarkAccessImpl.isAvailable(false));
        installEaep();

        assertTrue(JeiBookmarkAccessImpl.isAvailable(true));
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);

        assertEquals(1, FakeEaep.items.size());
        assertEquals(1, FakeEaep.fluids.size());
    }

    @Test
    void failingEaepFallsBackAndDoesNotRetryBrokenAdapter() throws Exception {
        var overlay = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));
        installEaep();
        FakeEaep.fail = true;

        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);

        assertEquals(1, FakeEaep.attempts);
        assertEquals(2, overlay.bookmarkList.ingredients.size());
        assertTrue(JeiBookmarkAccessImpl.isAvailable(true));
    }

    @Test
    void bookmarksPreserveItemComponentsAndDoNotToggleExistingEntries() {
        var overlay = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));
        var stack = new ItemStack(Items.IRON_INGOT, 64);
        var name = Component.literal("bookmark-component-marker");
        stack.set(DataComponents.CUSTOM_NAME, name);
        var key = AEItemKey.of(stack);

        JeiBookmarkAccessImpl.addMissingToBookmarks(List.of(key), false);
        JeiBookmarkAccessImpl.addMissingToBookmarks(List.of(key), false);

        assertEquals(1, overlay.bookmarkList.ingredients.size());
        var bookmarked = (ItemStack) overlay.bookmarkList.ingredients.getFirst().getIngredient();
        assertEquals(name, bookmarked.get(DataComponents.CUSTOM_NAME));
        assertEquals(64, stack.getCount());
    }

    @Test
    void brokenJeiWriterDisablesOnlyTheDirectPath() {
        var overlay = new FakeOverlay();
        overlay.bookmarkList.fail = true;
        JeiBookmarkAccessImpl.setRuntime(runtime(overlay));

        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), false);

        assertFalse(JeiBookmarkAccessImpl.isAvailable(false));
        assertTrue(overlay.bookmarkList.ingredients.isEmpty());
    }

    @Test
    void unavailableRuntimeClearsBothAdaptersAndNewRuntimeCanRecover() throws Exception {
        var first = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(first));
        installEaep();
        assertTrue(JeiBookmarkAccessImpl.isAvailable(true));

        JeiBookmarkAccessImpl.clearRuntime();
        assertFalse(JeiBookmarkAccessImpl.isAvailable(true));
        assertFalse(JeiBookmarkAccessImpl.isAvailable(false));
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), true);
        assertTrue(FakeEaep.items.isEmpty());

        var second = new FakeOverlay();
        JeiBookmarkAccessImpl.setRuntime(runtime(second));
        JeiBookmarkAccessImpl.addMissingToBookmarks(missing(), false);
        assertEquals(2, second.bookmarkList.ingredients.size());
        assertTrue(first.bookmarkList.ingredients.isEmpty());
    }

    @Test
    void modernJeiUsesPublicBookmarkManagerWithoutAccessingOverlayInternals() throws Exception {
        var runtime = new ModernRuntime();
        var writer = JeiBookmarkWriter.resolve(runtime, ITypedIngredient.class);
        var ingredient = new TypedIngredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.IRON_INGOT));

        writer.add(ingredient);

        assertEquals(List.of(ingredient), runtime.manager.ingredients);
    }

    @Test
    void legacyReflectionMatchesThePinnedJeiJar() throws Exception {
        var overlayType = Class.forName("mezz.jei.gui.overlay.bookmarks.BookmarkOverlay");
        var listType = Class.forName("mezz.jei.gui.bookmarks.BookmarkList");
        var factoryType = Class.forName("mezz.jei.gui.bookmarks.BookmarkFactory");
        assertEquals(listType, overlayType.getDeclaredField("bookmarkList").getType());
        assertEquals(factoryType, listType.getDeclaredField("bookmarkFactory").getType());
        assertNotNull(factoryType.getMethod("create", ITypedIngredient.class));
        assertNotNull(listType.getMethod("add", IBookmark.class));
    }

    @Test
    void incompatibleAdapterShapesAreRejectedInsteadOfGuessingOverloads() {
        assertThrows(NoSuchMethodException.class, () -> EaepBookmarkAdapter.resolve(Object.class));
        assertThrows(NoSuchMethodException.class, () -> JeiBookmarkWriter.resolve(new Object(), ITypedIngredient.class));
    }

    private static List<AEKey> missing() {
        return List.of(AEItemKey.of(Items.IRON_INGOT), AEFluidKey.of(Fluids.WATER));
    }

    private static void installEaep() throws Exception {
        var adapter = JeiBookmarkAccessImpl.class.getDeclaredField("eaep");
        adapter.setAccessible(true);
        adapter.set(null, EaepBookmarkAdapter.resolve(FakeEaep.class));
        var resolved = JeiBookmarkAccessImpl.class.getDeclaredField("eaepResolved");
        resolved.setAccessible(true);
        resolved.set(null, true);
    }

    private static IJeiRuntime runtime(FakeOverlay overlay) {
        var loader = JeiBookmarkIntegrationTest.class.getClassLoader();
        var manager = (IIngredientManager) Proxy.newProxyInstance(loader,
                new Class<?>[] {IIngredientManager.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("createTypedIngredient")) {
                        return Optional.of(typed(arguments[0], arguments[1]));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (IJeiRuntime) Proxy.newProxyInstance(loader, new Class<?>[] {IJeiRuntime.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getBookmarkOverlay" -> overlay;
                    case "getIngredientManager" -> manager;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static ITypedIngredient<?> typed(Object type, Object ingredient) {
        return new TypedIngredient<>((IIngredientType<Object>) type, ingredient);
    }

    private record TypedIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
        @Override
        public IIngredientType<T> getType() {
            return type;
        }

        @Override
        public T getIngredient() {
            return ingredient;
        }
    }

    public static final class FakeEaep {
        static Object runtime;
        static boolean fail;
        static int attempts;
        static final List<ItemStack> items = new ArrayList<>();
        static final List<FluidStack> fluids = new ArrayList<>();

        public static Object getRuntime() {
            return runtime;
        }

        public static void addBookmark(ItemStack stack) {
            attempts++;
            if (fail) {
                throw new IllegalStateException("simulated EAEP failure");
            }
            items.add(stack.copy());
        }

        public static void addBookmark(FluidStack stack) {
            fluids.add(stack.copy());
        }

        public static void addBookmark(Object ingredient) {
            throw new AssertionError("must resolve the exact item or fluid overload");
        }
    }

    public static final class FakeOverlay implements IBookmarkOverlay {
        private final FakeList bookmarkList = new FakeList();

        @Override
        public Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
            return Optional.empty();
        }

        @Override
        public <T> T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
            return null;
        }
    }

    public static final class FakeList {
        private final FakeFactory bookmarkFactory = new FakeFactory();
        final List<ITypedIngredient<?>> ingredients = new ArrayList<>();
        private final Set<AEKey> keys = new HashSet<>();
        boolean fail;

        public boolean add(IBookmark bookmark) {
            if (fail) {
                throw new IllegalStateException("simulated JEI failure");
            }
            var ingredient = ((FakeBookmark) bookmark).ingredient;
            Object value = ingredient.getIngredient();
            AEKey key = value instanceof ItemStack item ? AEItemKey.of(item) : AEFluidKey.of((FluidStack) value);
            if (!keys.add(key)) {
                return false;
            }
            ingredients.add(ingredient);
            return true;
        }

        public boolean add(Object ingredient) {
            throw new AssertionError("must resolve IBookmark overload");
        }
    }

    public static final class FakeFactory {
        public IBookmark create(ITypedIngredient<?> ingredient) {
            return new FakeBookmark(ingredient);
        }
    }

    private static final class FakeBookmark implements IBookmark {
        private final ITypedIngredient<?> ingredient;

        private FakeBookmark(ITypedIngredient<?> ingredient) {
            this.ingredient = ingredient;
        }

        @Override
        public BookmarkType getType() {
            return null;
        }

        @Override
        public IElement<?> getElement() {
            return null;
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void setVisible(boolean visible) {
        }
    }

    public static final class ModernRuntime {
        final ModernManager manager = new ModernManager();

        public ModernManager getBookmarkManager() {
            return manager;
        }

        public Object getBookmarkOverlay() {
            throw new AssertionError("public bookmark manager must take precedence");
        }
    }

    public static final class ModernManager {
        final List<ITypedIngredient<?>> ingredients = new ArrayList<>();

        public boolean add(ITypedIngredient<?> ingredient) {
            return ingredients.add(ingredient);
        }
    }
}
