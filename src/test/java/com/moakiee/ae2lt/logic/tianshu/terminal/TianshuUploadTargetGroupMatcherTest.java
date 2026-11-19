package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TianshuUploadTargetGroupMatcherTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsTheReportedMekanismExtrasNameColorChange() {
        var captured = group(Component.translatable("block.mekanism_extras.cosmic_crushing_factory")
                .withColor(0xFF5FED));
        var current = group(Component.translatable("block.mekanism_extras.cosmic_crushing_factory")
                .withColor(0xFF81F1));

        assertNotEquals(captured, current);
        assertTrue(TianshuUploadTargetGroupMatcher.matches(captured, current));
    }

    @Test
    void acceptsStylesInSiblingsTranslationArgumentsAndTooltips() {
        var captured = group(nestedName(0xFF5FED), Component.literal("Ultimate").withColor(0xFF5FED));
        var current = group(nestedName(0xFF81F1), Component.literal("Ultimate").withColor(0xFF81F1));

        assertNotEquals(captured, current);
        assertTrue(TianshuUploadTargetGroupMatcher.matches(captured, current));
    }

    @Test
    void rejectsRenamedMachines() {
        assertFalse(TianshuUploadTargetGroupMatcher.matches(
                group(Component.literal("Factory A")), group(Component.literal("Factory B"))));
    }

    @Test
    void rejectsChangedTranslationArguments() {
        assertFalse(TianshuUploadTargetGroupMatcher.matches(
                group(Component.translatableWithFallback("test.factory", "Factory %s", "A")),
                group(Component.translatableWithFallback("test.factory", "Factory %s", "B"))));
    }

    @Test
    void rejectsChangedTooltipTextOrderAndCount() {
        var captured = group(Component.literal("Factory"), Component.literal("A"), Component.literal("B"));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured,
                group(Component.literal("Factory"), Component.literal("A"), Component.literal("C"))));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured,
                group(Component.literal("Factory"), Component.literal("B"), Component.literal("A"))));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured,
                group(Component.literal("Factory"), Component.literal("A"))));
    }

    @Test
    void comparesTheFullIconKeyIncludingComponents() {
        var captured = group(Component.literal("Factory"));
        var renamedIcon = new ItemStack(Items.IRON_INGOT);
        renamedIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Other machine"));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured,
                new PatternContainerGroup(AEItemKey.of(Items.GOLD_INGOT), captured.name(), List.of())));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured,
                new PatternContainerGroup(AEItemKey.of(renamedIcon), captured.name(), List.of())));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured,
                new PatternContainerGroup(null, captured.name(), List.of())));
    }

    @Test
    void acceptsGroupsWithoutIcons() {
        var name = Component.literal("Factory");
        assertTrue(TianshuUploadTargetGroupMatcher.matches(
                new PatternContainerGroup(null, name.withColor(0xFF5FED), List.of()),
                new PatternContainerGroup(null, name.copy().withColor(0xFF81F1), List.of())));
    }

    @Test
    void rejectsMissingGroups() {
        var captured = group(Component.literal("Factory"));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(captured, null));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(null, captured));
        assertFalse(TianshuUploadTargetGroupMatcher.matches(null, null));
    }

    private static PatternContainerGroup group(Component name, Component... tooltip) {
        return new PatternContainerGroup(AEItemKey.of(Items.IRON_INGOT), name, List.of(tooltip));
    }

    private static Component nestedName(int color) {
        return Component.translatableWithFallback("test.factory", "Factory %s",
                Component.literal("Cosmic").withColor(color))
                .append(Component.literal(" / Ultimate").withColor(color));
    }
}
