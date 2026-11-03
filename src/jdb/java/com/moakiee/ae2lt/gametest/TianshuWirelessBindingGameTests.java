package com.moakiee.ae2lt.gametest;

import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.WirelessAccessPointMenu;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class TianshuWirelessBindingGameTests {
    private TianshuWirelessBindingGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void accessPointBindsAndRebindsWirelessPatternTerminal(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        bindAndRebind(helper, false);
    }

    private static void bindAndRebind(GameTestHelper helper, boolean crafting) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TianshuBinding"));
        var terminal = new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        terminal.getOrCreateTag().putDouble("internalCurrentPower", 12345.0);
        terminal.setHoverName(Component.literal("Keep terminal data"));
        terminal.getOrCreateTagElement("tianshuWorkstations").put("testInput",
                new ItemStack(Items.IRON_INGOT, 7).save(new net.minecraft.nbt.CompoundTag()));

        for (int offset = 0; offset < 2; offset++) {
            BlockPos pos = new BlockPos(3 + offset, 3, 3);
            helper.setBlock(pos, AEBlocks.WIRELESS_ACCESS_POINT.block());
            var accessPoint = (WirelessAccessPointBlockEntity) helper.getBlockEntity(pos);
            var menu = new WirelessAccessPointMenu(7 + offset, player.getInventory(), accessPoint);
            player.containerMenu = menu;
            try {
                var input = menu.getSlots(SlotSemantics.MACHINE_INPUT).get(0);
                var output = menu.getSlots(SlotSemantics.MACHINE_OUTPUT).get(0);
                helper.assertFalse(input.mayPlace(new ItemStack(Items.STONE)),
                        "An unrelated item must not fit the access point binding slot");
                helper.assertTrue(input.mayPlace(terminal), "Access point must accept the Tianshu terminal");
                var expected = terminal.copy();
                WirelessTerminalItem.LINKABLE_HANDLER.link(expected,
                        GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));

                menu.setCarried(terminal);
                menu.clicked(input.index, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().isEmpty() && !input.hasItem(),
                        "Binding must consume exactly the carried terminal");
                helper.assertTrue(output.getItem().getCount() == 1
                                && ItemStack.isSameItemSameTags(expected, output.getItem()),
                        "Binding must update only the access point and preserve other NBT");
                menu.clicked(output.index, 0, ClickType.PICKUP, player);
                helper.assertTrue(!output.hasItem() && menu.getCarried().getCount() == 1
                                && ItemStack.isSameItemSameTags(expected, menu.getCarried()),
                        "Bound terminal must be retrievable exactly once");
                terminal = menu.getCarried();
                menu.setCarried(ItemStack.EMPTY);
            } finally {
                menu.removed(player);
                player.containerMenu = player.inventoryMenu;
            }
        }
        helper.succeed();
    }
}
