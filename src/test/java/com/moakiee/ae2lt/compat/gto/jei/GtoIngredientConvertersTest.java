package com.moakiee.ae2lt.compat.gto.jei;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Map;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GtoIngredientConvertersTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,1", "1,1", "2147483647,2147483647",
            "2147483648,2147483647", "9223372036854775807,2147483647", "-9223372036854775808,1"})
    void itemDisplayClampsOnlyCountAndRetainsExactNbtAndSourceLong(long amount, int displayed) {
        var item = new ItemStack(Items.PAPER);
        item.getOrCreateTag().putString("variant", "gto-jei");
        var key = AEItemKey.of(item);
        var source = new GenericStack(key, amount);
        var converter = new GtoItemIngredientConverter();

        var result = converter.getIngredientFromStack(source);
        assertNotNull(result);
        assertEquals(displayed, result.getCount());
        assertEquals(key, AEItemKey.of(result));
        assertEquals(item.getTag(), result.getTag());
        assertEquals(amount, source.amount());
        var reverse = converter.getStackFromIngredient(result);
        assertNotNull(reverse);
        assertEquals(key, reverse.what());
        assertEquals(displayed, reverse.amount());
        assertNull(converter.getIngredientFromStack(new GenericStack(AEFluidKey.of(Fluids.WATER), amount)));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,1", "1,1", "2147483647,2147483647",
            "2147483648,2147483647", "9223372036854775807,2147483647", "-9223372036854775808,1"})
    void fluidDisplayClampsOnlyAmountAndRetainsExactNbtAndSourceLong(long amount, int displayed) {
        var fluid = new FluidStack(Fluids.WATER, 1);
        var tag = new CompoundTag();
        tag.putString("variant", "gto-jei");
        fluid.setTag(tag);
        var key = AEFluidKey.of(fluid);
        var source = new GenericStack(key, amount);
        var converter = new GtoFluidIngredientConverter();

        var result = converter.getIngredientFromStack(source);
        assertNotNull(result);
        assertEquals(displayed, result.getAmount());
        assertEquals(key, AEFluidKey.of(result));
        assertEquals(fluid.getTag(), result.getTag());
        assertEquals(amount, source.amount());
        var reverse = converter.getStackFromIngredient(result);
        assertNotNull(reverse);
        assertEquals(key, reverse.what());
        assertEquals(displayed, reverse.amount());
        assertNull(converter.getIngredientFromStack(new GenericStack(AEItemKey.of(Items.PAPER), amount)));
    }

    @Test
    void emptyNativeIngredientsReturnNoGenericStack() {
        assertNull(new GtoItemIngredientConverter().getStackFromIngredient(ItemStack.EMPTY));
        assertNull(new GtoFluidIngredientConverter().getStackFromIngredient(FluidStack.EMPTY));
    }

    @Test
    void customAeKeyConverterKeepsLongAmountAndRegistrySnapshotsAreImmutable() throws Exception {
        var state = GtoIngredientConverters.class.getDeclaredField("snapshot");
        state.setAccessible(true);
        var previous = state.get(null);
        var constructor = previous.getClass().getDeclaredConstructor(List.class, Map.class);
        constructor.setAccessible(true);
        state.set(null, constructor.newInstance(List.of(), Map.of()));
        try {
            assertTrue(GtoIngredientConverters.getConverters().isEmpty(), "the legacy registry starts empty");
            IIngredientType<KeyIngredient> type = () -> KeyIngredient.class;
            var converter = converter(type);
            assertTrue(GtoIngredientConverters.register(converter));
            var firstSnapshot = GtoIngredientConverters.getConverters();
            assertEquals(List.of(converter), firstSnapshot);
            assertThrows(UnsupportedOperationException.class, () -> firstSnapshot.clear());
            assertFalse(GtoIngredientConverters.register(converter(type)), "the exact same type object is duplicate");
            assertSame(converter, GtoIngredientConverters.getConverter(type));
            IIngredientType<KeyIngredient> equivalentClass = () -> KeyIngredient.class;
            assertSame(converter, GtoIngredientConverters.getConverter(equivalentClass));
            assertNull(GtoIngredientConverters.getConverter((IIngredientType<String>) () -> String.class));

            var key = new TestKey();
            var source = new GenericStack(key, Long.MAX_VALUE);
            var ingredient = converter.getIngredientFromStack(source);
            assertNotNull(ingredient);
            var restored = converter.getStackFromIngredient(ingredient);
            assertNotNull(restored);
            assertSame(key, restored.what());
            assertEquals(Long.MAX_VALUE, restored.amount());
            assertNull(new GtoItemIngredientConverter().getIngredientFromStack(source));
            assertNull(new GtoFluidIngredientConverter().getIngredientFromStack(source));

            var equalType = new EqualType();
            assertTrue(GtoIngredientConverters.register(converter(equalType)));
            assertEquals(List.of(converter), firstSnapshot, "published snapshots cannot change after registration");
            var stableSnapshot = GtoIngredientConverters.getConverters();
            assertThrows(IllegalStateException.class, () -> GtoIngredientConverters.register(converter(new EqualType())));
            assertSame(stableSnapshot, GtoIngredientConverters.getConverters(), "a conflicting registration is atomic");
        } finally {
            state.set(null, previous);
        }
    }

    private static GtoIngredientConverter<KeyIngredient> converter(IIngredientType<KeyIngredient> type) {
        return new GtoIngredientConverter<>() {
            @Override public IIngredientType<KeyIngredient> getIngredientType() { return type; }
            @Override public KeyIngredient getIngredientFromStack(GenericStack stack) { return new KeyIngredient(stack); }
            @Override public GenericStack getStackFromIngredient(KeyIngredient ingredient) { return ingredient.stack(); }
        };
    }

    private record KeyIngredient(GenericStack stack) { }

    private static final class EqualType implements IIngredientType<KeyIngredient> {
        @Override public Class<? extends KeyIngredient> getIngredientClass() { return KeyIngredient.class; }
        @Override public boolean equals(Object other) { return other instanceof EqualType; }
        @Override public int hashCode() { return 7; }
    }

    private static final class TestKey extends AEKey {
        private static final AEKeyType TYPE = new AEKeyType(new ResourceLocation("ae2lt_test", "jei_key"),
                TestKey.class, Component.literal("test key")) {
            @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
            @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
        };
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return TYPE.getId(); }
        @Override public void writeToPacket(FriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal("test key"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    }
}
