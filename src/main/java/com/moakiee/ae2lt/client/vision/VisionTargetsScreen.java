package com.moakiee.ae2lt.client.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class VisionTargetsScreen extends Screen {
    private final List<Button> rows = new ArrayList<>();
    private List<Block> matches = List.of();
    private EditBox query;
    private EditBox color;
    private int offset;

    public VisionTargetsScreen() {
        super(Component.translatable("ae2lt.vision.targets"));
    }

    @Override
    protected void init() {
        int left = width / 2 - 120;
        rows.clear();
        query = addRenderableWidget(new EditBox(font, left, 32, 158, 18, Component.translatable("ae2lt.vision.search")));
        query.setHint(Component.translatable("ae2lt.vision.search"));
        color = addRenderableWidget(new EditBox(font, left + 166, 32, 74, 18, Component.translatable("ae2lt.vision.color")));
        color.setMaxLength(6);
        color.setValue("00FFFF");
        for (int i = 0; i < Math.max(1, (height - 100) / 21); i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                var block = matches.get(offset + row);
                if (VisionTargets.colors().containsKey(block)) VisionTargets.set(block, null);
                else if (color.getValue().matches("[0-9a-fA-F]{6}")) VisionTargets.set(block, Integer.parseInt(color.getValue(), 16));
                search();
            }).bounds(left, 56 + 21 * i, 240, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("ae2lt.vision.aim"), button -> {
            if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                query.setValue(BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(hit.getBlockPos()).getBlock()).toString());
            }
        }).bounds(left, height - 33, 154, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + 160, height - 33, 80, 20).build());
        query.setResponder(value -> { offset = 0; search(); });
        setInitialFocus(query);
        search();
    }

    private void search() {
        var text = query.getValue().toLowerCase(Locale.ROOT);
        matches = BuiltInRegistries.BLOCK.stream().filter(block -> !block.defaultBlockState().isAir())
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).toString().contains(text)
                        || block.getName().getString().toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.<Block, Boolean>comparing(block -> !VisionTargets.colors().containsKey(block))
                        .thenComparing(BuiltInRegistries.BLOCK::getKey)).toList();
        refreshRows();
    }

    private void refreshRows() {
        offset = Math.clamp(offset, 0, Math.max(0, matches.size() - rows.size()));
        for (int i = 0; i < rows.size(); i++) {
            Button row = rows.get(i);
            row.visible = offset + i < matches.size();
            if (!row.visible) continue;
            Block block = matches.get(offset + i);
            var rgb = VisionTargets.colors().get(block);
            var name = Component.literal(rgb == null ? "+ " : "✓ ").append(block.getName());
            row.setMessage(rgb == null ? name : name.withColor(rgb));
            row.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString())));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        offset -= (int) Math.signum(dy);
        refreshRows();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta) {
        super.render(graphics, x, y, delta);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffff);
    }
}
