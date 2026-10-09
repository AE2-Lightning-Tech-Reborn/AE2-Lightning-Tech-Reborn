package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;

class AllowedOutputFilterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void disabledImportFilterAcceptsUnlistedOutputs() {
        var unrestricted = AllowedOutputFilter.unrestricted();
        var key = AEItemKey.of(Items.DIAMOND);
        assertFalse(unrestricted.isEmpty());
        assertTrue(unrestricted.matches(key));

        var restricted = new AllowedOutputFilter();
        assertTrue(restricted.isEmpty());
        assertFalse(restricted.matches(key));
    }

    @Test
    void feIsRejectedEvenWhenImportFilteringIsDisabledOrExplicitlyListed() {
        var fe = new ResourceKey("appflux:flux", "appflux:fe");
        assertFalse(AllowedOutputFilter.unrestricted().matches(fe));
        var strict = new AllowedOutputFilter();
        strict.allowStrict(fe);
        strict.allowIdOnly(fe);
        assertTrue(strict.isEmpty());
        assertFalse(strict.matches(fe));
    }

    @Test
    void sameResourceIdInOtherKeyTypesAndOtherFluxResourcesRemainReturnable() {
        for (var key : List.of(new ResourceKey("ae2:item", "appflux:fe"),
                new ResourceKey("ae2:fluid", "appflux:fe"),
                new ResourceKey("appflux:flux", "appflux:other"))) {
            assertTrue(AllowedOutputFilter.unrestricted().matches(key));
            var strict = new AllowedOutputFilter();
            strict.allowStrict(key);
            assertTrue(strict.matches(key));
            var idOnly = new AllowedOutputFilter();
            idOnly.allowIdOnly(key);
            assertTrue(idOnly.matches(key));
        }
    }

    // Identifier-only keys keep this regression independent of optional AppFlux initialization.
    private static final class ResourceKey extends AEKey {
        private final ResourceLocation id;
        private final AEKeyType type;
        ResourceKey(String typeId, String id) {
            this.id = new ResourceLocation(id);
            this.type = new AEKeyType(new ResourceLocation(typeId), ResourceKey.class, Component.literal(typeId)) {
                @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
                @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
            };
        }
        @Override public AEKeyType getType() { return type; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() { return id; }
        @Override public void writeToPacket(FriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id.toString()); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    }
}
