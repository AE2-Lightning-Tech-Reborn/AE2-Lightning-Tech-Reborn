package com.moakiee.ae2lt.menu;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * 集中管理所有自定义槽位的"空槽背景"贴图。
 *
 * <p>这些 sprite 来自 {@code assets/ae2lt/textures/block/slot/*.png},
 * 默认会被自动 stitch 进方块图集 ({@link net.minecraft.client.renderer.texture.TextureAtlas#LOCATION_BLOCKS}),
 * 通过 vanilla {@link Slot#setBackground(Identifier, Identifier)} 渲染。</p>
 *
 * <p>渲染流程:AE2 的 {@code AEBaseScreen.renderSlot} 在画完 {@code AppEngSlot.setIcon(Icon)}
 * 之后会回调 {@code super.renderSlot},vanilla 会用这里设置的 sprite 作为空槽提示。
 * 因此本工具与 AE2 自带 Icon 互不冲突——但同一个槽位最好只用其中一种,避免叠加。</p>
 */
public final class Ae2ltSlotBackgrounds {
    private static final Map<Slot, Identifier> ICONS = Collections.synchronizedMap(new WeakHashMap<>());

    public static final Identifier ELECTRO_CHIME_CRYSTAL = sprite("electro_chime_crystal");
    public static final Identifier FILTER_COMPONENT = sprite("filter_component");
    public static final Identifier LIGHTNING_COLLAPSE_MATRIX = sprite("lightning_collapse_matrix");

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "block/slot/" + name);
    }

    /** 给一个普通槽位绑定空槽背景图,sprite 必须已被 stitch 进方块图集。 */
    public static <T extends Slot> T withBackground(T slot, Identifier sprite) {
        ICONS.put(slot, sprite);
        return slot;
    }

    public static Identifier iconFor(Slot slot) {
        return ICONS.get(slot);
    }

    private Ae2ltSlotBackgrounds() {
    }
}
